package com.example.pos

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.example.pos.data.AppDatabase
import com.example.pos.data.CashSession
import com.example.pos.data.CashTransaction
import com.example.pos.data.SessionSummary
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CashSessionsActivity : ComponentActivity() {

    private val topBarColor = Color.parseColor("#101a24")
    private val backgroundColor = Color.parseColor("#f0f2f5")
    private val accentColor = Color.parseColor("#00a3e0")
    private val currencySymbol = "K"

    private lateinit var contentLayout: LinearLayout
    private var staffName = "Staff"
    private var activeSession: CashSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        staffName = intent.getStringExtra("STAFF_NAME") ?: "Staff"

        val root = ConstraintLayout(this).apply { setBackgroundColor(backgroundColor) }

        val topBar = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(topBarColor)
            gravity = Gravity.CENTER_VERTICAL
            val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
            setPadding(p, 0, p, 0)
        }

        val backBtn = TextView(this).apply {
            text = "←"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._20ssp)
            setTextColor(Color.WHITE)
            setPadding(0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), 0)
            setOnClickListener { finish() }
        }

        val title = TextView(this).apply {
            text = "Cash Sessions"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._16ssp)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        topBar.addView(backBtn)
        topBar.addView(title)

        val scroll = ScrollView(this).apply { id = View.generateViewId() }
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
            setPadding(p, p, p, p)
        }
        scroll.addView(contentLayout)

        root.addView(topBar)
        root.addView(scroll)

        val set = ConstraintSet()
        set.clone(root)
        set.connect(topBar.id, ConstraintSet.TOP, root.id, ConstraintSet.TOP)
        set.constrainHeight(topBar.id, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._45sdp))
        set.connect(scroll.id, ConstraintSet.TOP, topBar.id, ConstraintSet.BOTTOM)
        set.connect(scroll.id, ConstraintSet.BOTTOM, root.id, ConstraintSet.BOTTOM)
        set.constrainHeight(scroll.id, 0)
        set.applyTo(root)

        setContentView(root)
        refreshUI()
    }

    private fun refreshUI() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@CashSessionsActivity)
            activeSession = db.cashSessionDao().getOpenSession()
            val history = db.cashSessionDao().getAllSessions()

            withContext(Dispatchers.Main) {
                contentLayout.removeAllViews()
                addCurrentSessionSection()
                if (activeSession != null) {
                    addRegisterAccessSection()
                    addCashAdjustmentSections()
                    addCloseSessionSection()
                }
                addHistorySection(history)
            }
        }
    }

    private fun addCurrentSessionSection() {
        contentLayout.addView(createSectionTitle("Current Session Status"))
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(com.intuit.sdp.R.dimen._8sdp)
            cardElevation = 4f
            val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
            val container = LinearLayout(this@CashSessionsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(p, p, p, p)
            }
            
            if (activeSession == null) {
                container.addView(createStatusIndicator("CLOSED", Color.RED))
                container.addView(TextView(this@CashSessionsActivity).apply { 
                    text = "No active cash session."
                    setPadding(0, 16, 0, 16) 
                })
                container.addView(MaterialButton(this@CashSessionsActivity).apply {
                    text = "OPEN NEW SESSION"
                    setBackgroundColor(accentColor)
                    setOnClickListener { showOpenSessionDialog() }
                })
            } else {
                container.addView(createStatusIndicator("OPEN", Color.GREEN))
                val session = activeSession!!
                val info = """
                    Cashier: ${session.cashierName}
                    Opened: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.openingTime))}
                    Opening Cash: $currencySymbol ${String.format("%.2f", session.openingCash)}
                """.trimIndent()

                container.addView(TextView(this@CashSessionsActivity).apply { text = info; setPadding(0, 16, 0, 8) })
                val expectedText = TextView(this@CashSessionsActivity).apply { 
                    text = "Expected Cash: Calculating..."
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(accentColor)
                }
                container.addView(expectedText)
                updateExpectedCashUI(session.id, expectedText)
            }
            addView(container)
        }
        contentLayout.addView(card)
    }

    private fun addRegisterAccessSection() {
        val btn = MaterialButton(this).apply {
            text = "ENTER REGISTER (POS)"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 32, 0, 0) }
            setIconResource(android.R.drawable.ic_menu_add)
            setOnClickListener { startActivity(Intent(this@CashSessionsActivity, CounterActivity::class.java)) }
        }
        contentLayout.addView(btn)
    }

    private fun addCashAdjustmentSections() {
        contentLayout.addView(createSectionTitle("Cash Adjustments"))
        val horizontalLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        
        val cashIn = createAdjustmentCard("Cash In", Color.parseColor("#4CAF50")) { a, r -> saveTransaction("CASH_IN", a, r) }
        val cashOut = createAdjustmentCard("Cash Out", Color.parseColor("#F44336")) { a, r -> saveTransaction("CASH_OUT", a, r) }
        
        val params = LinearLayout.LayoutParams(0, -2, 1f)
        cashIn.layoutParams = params.apply { marginEnd = 8 }
        cashOut.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        
        horizontalLayout.addView(cashIn)
        horizontalLayout.addView(cashOut)
        contentLayout.addView(horizontalLayout)
    }

    private fun addCloseSessionSection() {
        contentLayout.addView(MaterialButton(this).apply {
            text = "CLOSE SESSION"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setOnClickListener { showCloseSessionDialog() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 48 }
        })
    }

    private fun addHistorySection(history: List<CashSession>) {
        contentLayout.addView(createSectionTitle("Session History"))
        val historyContainer = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 24f }
            elevation = 2f
        }

        if (history.isEmpty()) {
            historyContainer.addView(TextView(this).apply { text = "No previous sessions found."; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER })
        } else {
            for (session in history) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 24, 32, 24)
                    isClickable = true
                    setOnClickListener {
                        val intent = Intent(this@CashSessionsActivity, SessionDetailActivity::class.java)
                        intent.putExtra("SESSION_ID", session.id)
                        startActivity(intent)
                    }
                }
                val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(session.openingTime))
                row.addView(TextView(this).apply { text = "$date - ${session.cashierName}"; setTypeface(null, Typeface.BOLD) })
                val diff = session.difference ?: 0.0
                val statusText = when {
                    session.status == "OPEN" -> "Active"
                    diff == 0.0 -> "Balanced"
                    diff > 0 -> "Over (+$diff)"
                    else -> "Short ($diff)"
                }
                
                row.addView(TextView(this).apply { 
                    text = "Status: $statusText"
                    setTextColor(if (diff < 0) Color.RED else if (diff > 0) Color.BLUE else Color.GRAY)
                    textSize = 12f
                })
                historyContainer.addView(row)
                historyContainer.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 1); setBackgroundColor(Color.LTGRAY) })
            }
        }
        contentLayout.addView(historyContainer)
    }

    private fun showOpenSessionDialog() {
        val input = EditText(this).apply { 
            hint = "0.00"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            gravity = Gravity.CENTER
            textSize = 24f 
        }
        AlertDialog.Builder(this)
            .setTitle("Opening Cash Amount")
            .setMessage("Enter the amount of cash currently in the drawer.")
            .setView(input)
            .setPositiveButton("Start Session") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull() ?: 0.0
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@CashSessionsActivity).cashSessionDao().insertSession(CashSession(cashierName = staffName, openingCash = amount))
                    refreshUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCloseSessionDialog() {
        val session = activeSession ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@CashSessionsActivity)
            val summary = db.cashSessionDao().getSessionSummary(session.id)
            withContext(Dispatchers.Main) {
                val root = LinearLayout(this@CashSessionsActivity).apply { 
                    orientation = LinearLayout.VERTICAL
                    setPadding(64, 32, 64, 32)
                }
                
                val breakdown = """
                    Opening Cash: $currencySymbol ${String.format("%.2f", summary.openingCash)}
                    Sales (+): $currencySymbol ${String.format("%.2f", summary.totalSales)}
                    Cash In (+): $currencySymbol ${String.format("%.2f", summary.totalCashIn)}
                    Cash Out (-): $currencySymbol ${String.format("%.2f", summary.totalCashOut)}
                    ----------------------
                    Expected: $currencySymbol ${String.format("%.2f", summary.expectedCash)}
                """.trimIndent()
                
                root.addView(TextView(this@CashSessionsActivity).apply { 
                    text = breakdown; setTypeface(Typeface.MONOSPACE); textSize = 14f 
                })
                
                val countedInput = EditText(this@CashSessionsActivity).apply { 
                    hint = "Counted Cash Amount"
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                val reasonInput = EditText(this@CashSessionsActivity).apply { 
                    hint = "Reason for difference (Required)"
                    visibility = View.GONE 
                }
                
                countedInput.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        val counted = s.toString().toDoubleOrNull() ?: 0.0
                        reasonInput.visibility = if (Math.abs(counted - summary.expectedCash) > 0.01) View.VISIBLE else View.GONE
                    }
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                })
                
                root.addView(countedInput)
                root.addView(reasonInput)

                AlertDialog.Builder(this@CashSessionsActivity)
                    .setTitle("Final Reconciliation")
                    .setView(root)
                    .setPositiveButton("Close Session") { _, _ ->
                        val counted = countedInput.text.toString().toDoubleOrNull() ?: 0.0
                        val diff = counted - summary.expectedCash
                        val reason = reasonInput.text.toString()
                        if (Math.abs(diff) > 0.01 && reason.isBlank()) {
                            Toast.makeText(this@CashSessionsActivity, "A reason is required for cash discrepancy", Toast.LENGTH_LONG).show()
                        } else {
                            lifecycleScope.launch(Dispatchers.IO) {
                                db.cashSessionDao().closeSession(session.id, counted, summary.expectedCash, diff, reason)
                                refreshUI()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun createAdjustmentCard(title: String, color: Int, onSave: (Double, String) -> Unit) = MaterialCardView(this).apply {
        val container = LinearLayout(this@CashSessionsActivity).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16) 
        }
        container.addView(TextView(this@CashSessionsActivity).apply { text = title; setTextColor(color); setTypeface(null, Typeface.BOLD) })
        val amount = EditText(this@CashSessionsActivity).apply { hint = "Amount"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val reason = EditText(this@CashSessionsActivity).apply { hint = "Reason" }
        val btn = MaterialButton(this@CashSessionsActivity).apply { 
            text = "SAVE"; setBackgroundColor(color); textSize = 10f
            setOnClickListener {
                val a = amount.text.toString().toDoubleOrNull() ?: 0.0
                val r = reason.text.toString()
                if (a > 0 && r.isNotBlank()) { 
                    onSave(a, r)
                    amount.text.clear(); reason.text.clear() 
                } else Toast.makeText(context, "Fields required", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(amount); container.addView(reason); container.addView(btn)
        addView(container)
    }

    private fun saveTransaction(type: String, amount: Double, reason: String) {
        activeSession?.let { session ->
            lifecycleScope.launch(Dispatchers.IO) {
                AppDatabase.getDatabase(this@CashSessionsActivity).cashSessionDao().insertTransaction(
                    CashTransaction(sessionId = session.id, type = type, amount = amount, reason = reason)
                )
                refreshUI()
            }
        }
    }

    private fun updateExpectedCashUI(sid: Int, tv: TextView) {
        lifecycleScope.launch(Dispatchers.IO) {
            val sum = AppDatabase.getDatabase(this@CashSessionsActivity).cashSessionDao().getSessionSummary(sid)
            withContext(Dispatchers.Main) { 
                tv.text = "Expected Cash: $currencySymbol ${String.format("%.2f", sum.expectedCash)}" 
            }
        }
    }

    private fun createStatusIndicator(label: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(View(this@CashSessionsActivity).apply { 
            layoutParams = LinearLayout.LayoutParams(24, 24).apply { marginEnd = 16 }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        })
        addView(TextView(this@CashSessionsActivity).apply { text = "Status: $label"; setTypeface(null, Typeface.BOLD) })
    }

    private fun createSectionTitle(t: String) = TextView(this).apply { 
        text = t; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.GRAY)
        setPadding(0, 48, 0, 16)
    }
}
