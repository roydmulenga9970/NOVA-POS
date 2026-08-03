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

class AdminLoginActivity : ComponentActivity() {

    private lateinit var pinDisplay: TextView
    private var currentPin = ""
    private val prefs by lazy { getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE) }
    
    private val DEFAULT_ADMIN_PIN = "1234" 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!prefs.contains("admin_pin")) {
            prefs.edit().putString("admin_pin", DEFAULT_ADMIN_PIN).apply()
        }

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
            text = "Admin Login"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._20ssp)
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp))
        }

        pinDisplay = TextView(this).apply {
            id = View.generateViewId()
            text = "Enter PIN"
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

        container.addView(title)
        container.addView(pinDisplay)
        container.addView(gridLayout)
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
                if (currentPin.length < 4) {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                } else {
                    val savedPin = prefs.getString("admin_pin", DEFAULT_ADMIN_PIN)
                    if (savedPin == currentPin) {
                        navigateToDashboard()
                    } else {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                        currentPin = ""
                        updateDisplay()
                    }
                }
            }
            else -> {
                if (currentPin.length < 6) {
                    currentPin += input
                    updateDisplay()
                }
            }
        }
    }

    private fun updateDisplay() {
        if (currentPin.isEmpty()) {
            pinDisplay.text = "Enter PIN"
            pinDisplay.setTextColor(Color.GRAY)
        } else {
            pinDisplay.text = "•".repeat(currentPin.length)
            pinDisplay.setTextColor(Color.BLACK)
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, AdminDashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
