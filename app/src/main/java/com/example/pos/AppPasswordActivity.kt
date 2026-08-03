package com.example.pos

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class AppPasswordActivity : ComponentActivity() {

    private lateinit var pinDisplay: TextView
    private var currentPin = ""
    private val prefs by lazy { getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }

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
            text = "App Access Password"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._18ssp)
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp))
        }

        pinDisplay = TextView(this).apply {
            id = View.generateViewId()
            text = "Enter Password"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._24ssp)
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._32sdp))
        }

        val gridLayout = GridLayout(this).apply {
            id = View.generateViewId()
            columnCount = 3
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        val buttons = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")
        for (label in buttons) {
            gridLayout.addView(createNumberButton(label))
        }

        val logoutBtn = Button(this).apply {
            id = View.generateViewId()
            text = "Logout Account"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                Firebase.auth.signOut()
                startActivity(Intent(this@AppPasswordActivity, LoginActivity::class.java))
                finish()
            }
            val params = LinearLayout.LayoutParams(-2, -2)
            params.gravity = Gravity.CENTER
            params.topMargin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._32sdp)
            layoutParams = params
        }

        container.addView(title)
        container.addView(pinDisplay)
        container.addView(gridLayout)
        container.addView(logoutBtn)
        scrollView.addView(container)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
    }

    private fun createNumberButton(label: String): Button {
        val size = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._60sdp)
        val margin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
        return Button(this).apply {
            text = label
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._18ssp)
            setTextColor(Color.WHITE)
            setBackgroundColor(if (label == "OK") Color.parseColor("#4CAF50") else if (label == "C") Color.parseColor("#F44336") else Color.parseColor("#2196F3"))
            
            val params = GridLayout.LayoutParams()
            params.width = size
            params.height = size
            params.setMargins(margin, margin, margin, margin)
            layoutParams = params
            
            setOnClickListener {
                handleInput(label)
            }
        }
    }

    private fun handleInput(input: String) {
        when (input) {
            "C" -> {
                currentPin = ""
                updateDisplay()
            }
            "OK" -> {
                val savedPass = prefs.getString("app_access_password", "1234")
                if (currentPin == savedPass) {
                    startActivity(Intent(this, AccessSelectionActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    currentPin = ""
                    updateDisplay()
                }
            }
            else -> {
                if (currentPin.length < 8) {
                    currentPin += input
                    updateDisplay()
                }
            }
        }
    }

    private fun updateDisplay() {
        if (currentPin.isEmpty()) {
            pinDisplay.text = "Enter Password"
            pinDisplay.setTextColor(Color.GRAY)
        } else {
            pinDisplay.text = "•".repeat(currentPin.length)
            pinDisplay.setTextColor(Color.BLACK)
        }
    }
}
