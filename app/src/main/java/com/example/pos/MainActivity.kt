package com.example.pos

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            val padding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp)
            setPadding(padding, padding, padding, padding)
        }

        val title = TextView(this).apply {
            text = "POS System Active"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._18ssp)
            setPadding(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp))
        }

        val openDashboardBtn = Button(this).apply {
            text = "Open Admin Dashboard"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AdminLoginActivity::class.java))
            }
        }

        val logoutBtn = Button(this).apply {
            text = "Logout Account"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
            setOnClickListener {
                Firebase.auth.signOut()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }

        rootLayout.addView(title)
        rootLayout.addView(openDashboardBtn)
        rootLayout.addView(logoutBtn)

        setContentView(rootLayout)
        
        // Auto-redirect to the selection page since account and app-pass are verified
        startActivity(Intent(this, AccessSelectionActivity::class.java))
        finish()
    }
}
