package com.example.pos

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
     private lateinit var updateEngine: NovaUpdateEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateEngine = NovaUpdateEngine(this)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        val logoImage = ImageView(this).apply {
            val resId = resources.getIdentifier("nova_app", "drawable", packageName)
            if (resId != 0) {
                setImageResource(resId)
            } else {
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
            val size = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._200sdp)
            layoutParams = LinearLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        rootLayout.addView(logoImage)
        setContentView(rootLayout)

        // Perform Update Check
        checkUpdate()
    }

    private fun checkUpdate() {
        // Use the new checkUpdate API which handles the dialog internally
        updateEngine.checkUpdate {
            // This callback runs if NO update is found or fetch fails
            // Proceed after a short splash delay
            if (!isFinishing && !isDestroyed) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        checkAuthAndNavigate()
                    }
                }, 2000)
            }
        }
    }

    private fun checkAuthAndNavigate() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            startActivity(Intent(this, AppPasswordActivity::class.java))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
