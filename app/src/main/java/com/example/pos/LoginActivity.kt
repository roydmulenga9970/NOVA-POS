package com.example.pos

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.example.pos.sync.SyncWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/**
 * LoginActivity handles authentication via Firebase Email/Password and Google Sign-In.
 * Enhanced with deep diagnostics for Firebase credential debugging.
 */
class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val TAG = "FirebaseSignInDebug"

    private val googleSignInLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val idToken = account.idToken
            
            if (idToken != null) {
                Log.i(TAG, "Google ID Token acquired successfully. Length: ${idToken.length}")
                // Log the first few chars to verify it's a JWT (starts with ey...)
                Log.d(TAG, "Token preview: ${idToken.take(10)}...")
                firebaseAuthWithGoogle(idToken)
            } else {
                Log.e(TAG, "CRITICAL: Google ID Token is null. This usually happens if the WEB_CLIENT_ID is incorrect or SHA-1 is missing.")
                Toast.makeText(this, "Internal Error: Null ID Token", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            // Common codes: 
            // 10: DEVELOPER_ERROR (Usually Client ID mismatch or SHA-1 not in console)
            // 12500: SIGN_IN_FAILED (SHA-1 not registered)
            Log.e(TAG, "Google Sign-In API Error. Code: ${e.statusCode}, Message: ${e.message}", e)
            handleGoogleSignInError(e.statusCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth

        // 1. Run Diagnostics
        logDeviceDiagnostics()
        logAppSignature()

        // 2. Initialize Google Sign-In
        setupGoogleSignIn()

        // 3. UI Initialization
        initializeUI()
    }

    /**
     * Runtime check of the app's SHA-1 fingerprint. 
     * Compare this output in Logcat with your Firebase Console settings.
     */
    private fun logAppSignature() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    val signatures = if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                    for (signature in signatures) {
                        val fingerprint = getSHA1(signature.toByteArray())
                        Log.i(TAG, "Detected App SHA-1: $fingerprint")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signatures = packageInfo.signatures
                if (signatures != null) {
                    for (signature in signatures) {
                        val fingerprint = getSHA1(signature.toByteArray())
                        Log.i(TAG, "Detected App SHA-1: $fingerprint")
                    }
                }
            }
            Log.i(TAG, "Verify the detected SHA-1 matches the one in Firebase Console -> Project Settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log app signature", e)
        }
    }

    private fun getSHA1(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(data)
        return md.digest().joinToString(":") { String.format("%02X", it) }
    }

    private fun logDeviceDiagnostics() {
        val now = Date()
        val df = DateFormat.getDateTimeInstance()
        df.timeZone = TimeZone.getDefault()
        Log.i(TAG, "--- Environment Diagnostics ---")
        Log.i(TAG, "Current Device Time: ${df.format(now)}")
        Log.i(TAG, "Timezone: ${TimeZone.getDefault().id}")
        Log.i(TAG, "Package Name: $packageName")
        Log.i(TAG, "-------------------------------")
    }

    private fun setupGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)
        Log.d(TAG, "Configuring GoogleSignIn with Web Client ID: $webClientId")

        if (webClientId.contains("placeholder") || webClientId.isBlank()) {
            Log.e(TAG, "FATAL: No valid Web Client ID found in strings.xml.")
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.d(TAG, "Attempting Firebase signInWithCredential...")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.i(TAG, "Firebase Authentication SUCCESS for user: ${auth.currentUser?.email}")
                    recoverAndNext()
                } else {
                    val ex = task.exception
                    Log.e(TAG, "Firebase Authentication FAILURE", ex)
                    
                    if (ex is FirebaseAuthInvalidCredentialsException) {
                        Log.e(TAG, "ERROR DETAILS: The credential (idToken) is rejected by Firebase.")
                        Log.e(TAG, "POSSIBLE FIXES: ")
                        Log.e(TAG, "1. Ensure the SHA-1 from Logcat matches Firebase Console.")
                        Log.i(TAG, "2. Ensure you used the 'Web Client ID' (client_type 3) in strings.xml.")
                        Log.e(TAG, "3. If using Play App Signing, add the SHA-1 from Play Console to Firebase.")
                    }
                    
                    Toast.makeText(this, "Auth Failed: ${ex?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun handleGoogleSignInError(statusCode: Int) {
        val msg = when (statusCode) {
            10 -> "Developer Error (10): Check SHA-1 and Client ID in Firebase."
            12500 -> "Sign-In Failed (12500): SHA-1 mismatch."
            else -> "Google Error code: $statusCode"
        }
        Log.e(TAG, "Google Sign-In Error: $msg")
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun initializeUI() {
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(Color.WHITE)
        }

        val scrollView = ScrollView(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp)
            setPadding(padding, padding, padding, padding)
        }

        val title = TextView(this).apply {
            id = View.generateViewId()
            text = "POS Account Access"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._18ssp)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp))
        }

        val emailEdit = EditText(this).apply {
            id = View.generateViewId()
            hint = "Email"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
        }

        val passwordEdit = EditText(this).apply {
            id = View.generateViewId()
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
        }

        val loginBtn = Button(this).apply {
            id = View.generateViewId()
            text = "Login with Email"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
        }

        val registerBtn = Button(this).apply {
            id = View.generateViewId()
            text = "Create Account"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
        }

        val googleBtn = Button(this).apply {
            id = View.generateViewId()
            text = "Sign in with Google"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
        }

        loginBtn.setOnClickListener {
            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Email Sign-In Success")
                        recoverAndNext()
                    } else {
                        Log.e(TAG, "Email Sign-In Failure", task.exception)
                        Toast.makeText(this, "Auth Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        registerBtn.setOnClickListener {
            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Registration Success")
                        recoverAndNext()
                    } else {
                        Log.e(TAG, "Registration Failure", task.exception)
                        Toast.makeText(this, "Registration Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        googleBtn.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        container.addView(title)
        container.addView(emailEdit)
        container.addView(passwordEdit)
        container.addView(loginBtn)
        container.addView(registerBtn)
        container.addView(googleBtn)
        scrollView.addView(container)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
    }

    private fun recoverAndNext() {
        lifecycleScope.launch {
            try {
                SyncWorker.downloadData(this@LoginActivity)
            } catch (e: Exception) {
                Log.e(TAG, "Data recovery failed", e)
            }
            startPasswordActivity()
        }
    }

    private fun startPasswordActivity() {
        startActivity(Intent(this, AppPasswordActivity::class.java))
        finish()
    }
}
