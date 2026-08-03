package com.example.pos

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.pos.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SessionDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getIntExtra("SESSION_ID", -1)
        
        val root = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#f0f2f5"))
            setPadding(32, 32, 32, 32)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@SessionDetailActivity)
            val session = db.cashSessionDao().getSessionById(sessionId) ?: return@launch
            val txs = db.cashSessionDao().getTransactionsForSession(sessionId)
            val summary = if (session.status == "CLOSED") null else try { db.cashSessionDao().getSessionSummary(sessionId) } catch(e: Exception) { null }

            withContext(Dispatchers.Main) {
                root.addView(TextView(this@SessionDetailActivity).apply { 
                    text = "Session Audit Log"; textSize = 22f; setTypeface(null, Typeface.BOLD) 
                })
                
                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(session.openingTime))
                root.addView(TextView(this@SessionDetailActivity).apply { 
                    text = "Cashier: ${session.cashierName}\nOpened: $dateStr\nSession ID: #$sessionId"; setPadding(0, 16, 0, 16)
                })

                // Reconciliation Section
                val reconCard = LinearLayout(this@SessionDetailActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 24, 24, 24)
                    setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                }
                
                val diff = session.difference ?: 0.0
                val reconText = StringBuilder()
                reconText.append("Opening Cash: K ${String.format("%.2f", session.openingCash)}\n")
                if (session.status == "CLOSED") {
                    reconText.append("Closing Cash: K ${String.format("%.2f", session.closingCash ?: 0.0)}\n")
                    reconText.append("Expected Cash: K ${String.format("%.2f", session.expectedCash)}\n")
                    reconText.append("Difference: K ${String.format("%.2f", diff)} (${if (diff == 0.0) "Balanced" else if (diff > 0) "Over" else "Short"})\n")
                    if (!session.differenceReason.isNullOrBlank()) {
                        reconText.append("Reason: ${session.differenceReason}")
                    }
                } else if (summary != null) {
                    reconText.append("Current Sales: K ${String.format("%.2f", summary.totalSales)}\n")
                    reconText.append("Current Cash In: K ${String.format("%.2f", summary.totalCashIn)}\n")
                    reconText.append("Current Cash Out: K ${String.format("%.2f", summary.totalCashOut)}\n")
                    reconText.append("Expected Cash: K ${String.format("%.2f", summary.expectedCash)}")
                }
                
                reconCard.addView(TextView(this@SessionDetailActivity).apply { 
                    text = reconText.toString(); setTypeface(Typeface.MONOSPACE) 
                })
                root.addView(reconCard)

                root.addView(TextView(this@SessionDetailActivity).apply { 
                    text = "Transaction History"; textSize = 18f; setTypeface(null, Typeface.BOLD); setPadding(0, 32, 0, 16)
                })

                if (txs.isEmpty()) {
                    root.addView(TextView(this@SessionDetailActivity).apply { text = "No cash adjustments recorded."; gravity = Gravity.CENTER; setPadding(0, 32, 0, 0) })
                }

                for (tx in txs) {
                    val card = LinearLayout(this@SessionDetailActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(24, 24, 24, 24)
                        setBackgroundColor(Color.WHITE)
                        val color = if (tx.type == "CASH_IN") Color.parseColor("#4CAF50") else Color.RED
                        
                        addView(TextView(this@SessionDetailActivity).apply { 
                            text = if (tx.type == "CASH_IN") "+" else "-"
                            setTextColor(color); setTypeface(null, Typeface.BOLD)
                            setPadding(0,0,24,0)
                        })
                        addView(TextView(this@SessionDetailActivity).apply { 
                            text = "${tx.reason}\n${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tx.timestamp))}"
                            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                        })
                        addView(TextView(this@SessionDetailActivity).apply { 
                            text = "K ${String.format("%.2f", tx.amount)}"; setTextColor(color); setTypeface(null, Typeface.BOLD)
                        })
                    }
                    root.addView(card)
                    root.addView(View(this@SessionDetailActivity).apply { layoutParams = LinearLayout.LayoutParams(-1, 2); setBackgroundColor(Color.LTGRAY) })
                }
            }
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
