package com.example.pos

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class AccessSelectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = "Select Access Mode"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._18ssp)
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._32sdp))
        }

        val cashierBtn = Button(this).apply {
            id = View.generateViewId()
            text = "Cashier Access"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
            val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
            setPadding(p, p, p, p)
            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
            layoutParams = params
            setOnClickListener {
                startActivity(Intent(this@AccessSelectionActivity, CashierLoginActivity::class.java))
            }
        }

        val adminBtn = Button(this).apply {
            id = View.generateViewId()
            text = "Admin Access"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
            val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            setOnClickListener {
                startActivity(Intent(this@AccessSelectionActivity, AdminLoginActivity::class.java))
            }
        }

        val logoutBtn = Button(this).apply {
            id = View.generateViewId()
            text = "Logout Account"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                Firebase.auth.signOut()
                startActivity(Intent(this@AccessSelectionActivity, LoginActivity::class.java))
                finish()
            }
            val params = LinearLayout.LayoutParams(-2, -2)
            params.gravity = Gravity.CENTER
            params.topMargin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._32sdp)
            layoutParams = params
        }

        container.addView(title)
        container.addView(cashierBtn)
        container.addView(adminBtn)
        container.addView(logoutBtn)
        scrollView.addView(container)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
    }
}
