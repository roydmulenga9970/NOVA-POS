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

class CashierLoginActivity : ComponentActivity() {

    private lateinit var staffSpinner: Spinner
    private lateinit var pinDisplay: TextView
    private var currentPin = ""
    private var selectedStaff = ""
    
    private val cashierPrefs by lazy { getSharedPreferences("CashierPrefs", Context.MODE_PRIVATE) }

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
            text = "Cashier Login"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._20ssp)
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp))
        }

        val allCashiers = cashierPrefs.all.keys.toList()
        
        if (allCashiers.isEmpty()) {
            val errorText = TextView(this).apply {
                text = "No cashiers registered.\n\nPlease log in as Admin to add staff in Settings."
                textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
                setTextColor(Color.RED)
                gravity = Gravity.CENTER
                val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
                setPadding(p, p, p, p)
            }
            val backBtn = Button(this).apply {
                text = "Back to Selection"
                textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
                setOnClickListener { finish() }
            }
            container.addView(title)
            container.addView(errorText)
            container.addView(backBtn)
            scrollView.addView(container)
            rootLayout.addView(scrollView)
            setContentView(rootLayout)
            return
        }

        staffSpinner = Spinner(this).apply {
            id = View.generateViewId()
            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp)
            layoutParams = params
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, allCashiers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        staffSpinner.adapter = adapter
        
        staffSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStaff = allCashiers[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
        container.addView(staffSpinner)
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
                    val savedPin = cashierPrefs.getString(selectedStaff, "")
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
        val intent = Intent(this, AdminDashboardActivity::class.java).apply {
            putExtra("IS_CASHIER", true)
            putExtra("STAFF_NAME", selectedStaff)
        }
        startActivity(intent)
        finish()
    }
}
