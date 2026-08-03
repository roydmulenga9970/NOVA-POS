package com.example.pos

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.example.pos.data.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AdminDashboardActivity : ComponentActivity() {

    private val sidebarColor = Color.parseColor("#1a2b3c")
    private val topBarColor = Color.parseColor("#101a24")
    private val backgroundColor = Color.parseColor("#f0f2f5")
    private val accentColor = Color.parseColor("#00a3e0")

    private lateinit var managerInfo: TextView
    private lateinit var sidebarScroll: ScrollView
    private lateinit var menuIcon: TextView
    private lateinit var contentLayout: LinearLayout
    private lateinit var overviewTitle: TextView
    private var isSidebarOpen = false
    private var isCashierMode = false
    private var staffName = "Admin"

    private lateinit var mainRootLayout: ConstraintLayout
    private var isRestockAlertIgnored = false
    private var restockButton: FrameLayout? = null
    
    private val alertHandler = Handler(Looper.getMainLooper())
    private val alertRunnable = object : Runnable {
        override fun run() {
            checkRestockAlert()
            alertHandler.postDelayed(this, 30000)
        }
    }

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            timeHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isCashierMode = intent.getBooleanExtra("IS_CASHIER", false)
        staffName = if (isCashierMode) {
            intent.getStringExtra("STAFF_NAME") ?: "Cashier"
        } else {
            getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE).getString("admin_name", "Admin") ?: "Admin"
        }

        mainRootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(backgroundColor)
        }

        val topBar = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(topBarColor)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._40sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), 0)
        }

        val logoText = TextView(this).apply {
            text = "NOVA"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._16ssp)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        managerInfo = TextView(this).apply {
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._10ssp)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }

        topBar.addView(logoText)
        topBar.addView(managerInfo)

        val contentScroll = ScrollView(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(-1, 0)
        }

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
            setPadding(padding, padding, padding, padding)
        }
        
        overviewTitle = TextView(this).apply {
            text = if (isCashierMode) "Cashier Dashboard" else "Dashboard Overview"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._18ssp)
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }
        
        contentScroll.addView(contentLayout)

        sidebarScroll = ScrollView(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._180sdp), -1)
            setBackgroundColor(sidebarColor)
            visibility = View.GONE
            translationX = -resources.getDimension(com.intuit.sdp.R.dimen._180sdp)
            elevation = resources.getDimension(com.intuit.sdp.R.dimen._10sdp)
        }
        
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._40sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._20sdp))
        }

        if (!isCashierMode) {
            createExpandableSidebarItem("Dashboard", listOf("Overview", "Analytics"), sidebar)
        }

        // New unified Cash Sessions menu item
        createSidebarItem("Cash Sessions", "💳", {
            val intent = Intent(this, CashSessionsActivity::class.java)
            intent.putExtra("STAFF_NAME", staffName)
            startActivity(intent)
            if (isSidebarOpen) toggleSidebar()
        }, sidebar)
        
        if (!isCashierMode) {
            createExpandableSidebarItem("Inventory", listOf(
                "Inventory",
                "  📂 Products",
                "    Add New Item",
                "  📦 Stock Control",
                "    Current Inventory",
                "  📊 Reports",
                "    Inventory Value",
                "    Reorder List"
            ), sidebar)

            createExpandableSidebarItem("Settings", listOf("Add Cashier", "Set Admin Name", "Security", "About & Support"), sidebar)
        }

        createExpandableSidebarItem("Reports", listOf("Daily Sales"), sidebar)

        val logoutItem = TextView(this).apply {
            text = "🚪 Logout Account"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
            setTextColor(Color.WHITE)
            setPadding(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp))
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { performGlobalLogout() }
        }
        sidebar.addView(logoutItem)
        sidebarScroll.addView(sidebar)

        menuIcon = TextView(this).apply {
            id = View.generateViewId()
            text = "☰"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._20ssp)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._40sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._40sdp))
            isClickable = true
            elevation = resources.getDimension(com.intuit.sdp.R.dimen._10sdp)
            setOnClickListener { toggleSidebar() }
        }

        mainRootLayout.addView(topBar)
        mainRootLayout.addView(contentScroll)
        mainRootLayout.addView(sidebarScroll)
        mainRootLayout.addView(menuIcon)

        val set = ConstraintSet()
        set.clone(mainRootLayout)

        set.connect(topBar.id, ConstraintSet.TOP, mainRootLayout.id, ConstraintSet.TOP)
        set.connect(topBar.id, ConstraintSet.START, mainRootLayout.id, ConstraintSet.START)
        set.connect(topBar.id, ConstraintSet.END, mainRootLayout.id, ConstraintSet.END)
        set.constrainHeight(topBar.id, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._45sdp))

        set.connect(contentScroll.id, ConstraintSet.TOP, topBar.id, ConstraintSet.BOTTOM)
        set.connect(contentScroll.id, ConstraintSet.BOTTOM, mainRootLayout.id, ConstraintSet.BOTTOM)
        set.connect(contentScroll.id, ConstraintSet.START, mainRootLayout.id, ConstraintSet.START)
        set.connect(contentScroll.id, ConstraintSet.END, mainRootLayout.id, ConstraintSet.END)

        set.connect(menuIcon.id, ConstraintSet.TOP, mainRootLayout.id, ConstraintSet.TOP)
        set.connect(menuIcon.id, ConstraintSet.START, mainRootLayout.id, ConstraintSet.START)

        set.connect(sidebarScroll.id, ConstraintSet.TOP, mainRootLayout.id, ConstraintSet.TOP)
        set.connect(sidebarScroll.id, ConstraintSet.BOTTOM, mainRootLayout.id, ConstraintSet.BOTTOM)
        set.connect(sidebarScroll.id, ConstraintSet.START, mainRootLayout.id, ConstraintSet.START)

        set.applyTo(mainRootLayout)

        setContentView(mainRootLayout)
        
        updateTime()
        // Always show overview; users must navigate to Cash Sessions to start work
        showOverview()
        
        alertHandler.post(alertRunnable)
        timeHandler.post(timeRunnable)
    }

    private fun createSidebarItem(label: String, icon: String, onClick: () -> Unit, parent: LinearLayout) {
        val item = TextView(this).apply {
            text = "$icon  $label"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
            setTextColor(Color.LTGRAY)
            setPadding(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp))
            isClickable = true
            setOnClickListener { onClick() }
        }
        parent.addView(item)
    }

    private fun checkRestockAlert() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                val lowStock = db.productDao().getLowStockProducts()
                val hasLowStock = lowStock.any { it.barcode.isNotBlank() }
                
                withContext(Dispatchers.Main) {
                    if (hasLowStock && !isRestockAlertIgnored) {
                        showRestockFloatingButton()
                    } else if (!hasLowStock) {
                        removeRestockButton()
                        isRestockAlertIgnored = false 
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminDashboard", "Error checking restock", e)
            }
        }
    }

    private fun showRestockFloatingButton() {
        if (restockButton != null || isRestockAlertIgnored) return

        restockButton = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            elevation = resources.getDimension(com.intuit.sdp.R.dimen._15sdp)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p8 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
            val p4 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)
            setPadding(p8, p4, p8, p4)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._30sdp)
                setStroke(2, Color.parseColor("#E0E0E0"))
            }
        }

        val mainAction = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p16 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
            val p8 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
            setPadding(p16, p8, p16, p8)
            background = GradientDrawable().apply {
                setColor(Color.RED)
                cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._20sdp)
            }
            isClickable = true
            setOnClickListener {
                if (isCashierMode) {
                    promptAdminPassword {
                        showReorderList()
                        if (isSidebarOpen) toggleSidebar()
                    }
                } else {
                    showReorderList()
                    if (isSidebarOpen) toggleSidebar()
                }
            }
        }

        mainAction.addView(TextView(this).apply {
            text = "Restock Now"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._14ssp)
            setTextColor(Color.GRAY)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val p8 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
            setPadding(p8, 0, p8, 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                marginStart = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)
            }
            isClickable = true
            setOnClickListener {
                isRestockAlertIgnored = true
                removeRestockButton()
                alertHandler.postDelayed({ isRestockAlertIgnored = false }, 300000)
            }
        }

        container.addView(mainAction)
        container.addView(closeBtn)
        restockButton?.addView(container)
        mainRootLayout.addView(restockButton)

        val set = ConstraintSet()
        set.clone(mainRootLayout)
        set.connect(restockButton!!.id, ConstraintSet.BOTTOM, mainRootLayout.id, ConstraintSet.BOTTOM, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._80sdp))
        set.connect(restockButton!!.id, ConstraintSet.END, mainRootLayout.id, ConstraintSet.END, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp))
        set.applyTo(mainRootLayout)

        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
        } catch (e: Exception) {}
    }

    private fun removeRestockButton() {
        restockButton?.let {
            mainRootLayout.removeView(it)
            restockButton = null
        }
    }

    private fun promptAdminPassword(onSuccess: () -> Unit) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Admin Authentication Required")
        val input = EditText(this).apply {
            hint = "Enter Admin PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        builder.setView(input)
        builder.setPositiveButton("Verify") { _, _ ->
            val pin = input.text.toString()
            val savedPin = getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE).getString("admin_pin", "1234")
            if (pin == savedPin) {
                onSuccess()
            } else {
                Toast.makeText(this@AdminDashboardActivity, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun updateStock(product: Product, addQty: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                val updatedProduct = product.copy(currentInventory = product.currentInventory + addQty)
                db.productDao().update(updatedProduct)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminDashboardActivity, "Stock updated for ${product.name}", Toast.LENGTH_SHORT).show()
                    showReorderList()
                    checkRestockAlert() 
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminDashboardActivity, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performGlobalLogout() {
        Firebase.auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showOverview() {
        contentLayout.removeAllViews()
        contentLayout.addView(overviewTitle)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val netSales = db.saleDao().getTodayNetSales(startOfDay) ?: 0.0
                val transactions = db.saleDao().getTodayTransactionCount(startOfDay)
                val lowStock = db.productDao().getLowStockProducts()
                val topSellers = db.saleDao().getTopSellers()

                withContext(Dispatchers.Main) {
                    val kpiGrid = GridLayout(this@AdminDashboardActivity).apply { columnCount = 2; useDefaultMargins = true; setPadding(0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)) }
                    kpiGrid.addView(createStatCard("Net Sales Today", "K ${String.format(Locale.getDefault(), "%.2f", netSales)}", "Transactions: $transactions"))
                    val avgTicket = if (transactions > 0) netSales / transactions else 0.0
                    kpiGrid.addView(createStatCard("Performance", "${transactions} Sales", "Avg Ticket: K ${String.format(Locale.getDefault(), "%.2f", avgTicket)}"))
                    contentLayout.addView(kpiGrid)

                    contentLayout.addView(createSectionTitle("Low Stock Alerts"))
                    if (lowStock.isEmpty()) contentLayout.addView(createEmptyStateText("All stock levels are healthy."))
                    else contentLayout.addView(createListViewCard(lowStock.map { "${it.name} - ${it.currentInventory} left" }))

                    contentLayout.addView(createSectionTitle("Top Sellers"))
                    if (topSellers.isEmpty()) contentLayout.addView(createEmptyStateText("No sales data available yet."))
                    else contentLayout.addView(createListViewCard(topSellers.mapIndexed { index, seller -> "${index + 1}. ${seller.productName} (${seller.totalQty} sold)" }))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("AdminDashboard", "Error in showOverview", e)
                }
            }
        }
    }

    private fun showAnalytics() {
        contentLayout.removeAllViews()
        overviewTitle.text = "Analytics Dashboard"
        contentLayout.addView(overviewTitle)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                val peakHours = db.saleDao().getPeakHours()
                val sales = db.saleDao().getRecentSales(5)

                withContext(Dispatchers.Main) {
                    contentLayout.addView(createSectionTitle("Sales Trends (Latest)"))
                    val chartPlaceholder = View(this@AdminDashboardActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(-1, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._120sdp)).apply { setMargins(0, 8, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)) }
                        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._10sdp); setStroke(2, Color.LTGRAY) }
                    }
                    val chartContainer = FrameLayout(this@AdminDashboardActivity)
                    chartContainer.addView(chartPlaceholder)
                    val salesSummary = if (sales.isEmpty()) "No sales recorded yet." 
                                      else sales.joinToString(" | ") { "K${String.format(Locale.getDefault(), "%.0f", it.totalAmount)}" }
                    chartContainer.addView(TextView(this@AdminDashboardActivity).apply { text = "[ Revenue Graph ]\n$salesSummary"; gravity = Gravity.CENTER; setTextColor(Color.GRAY); textSize = resources.getDimension(com.intuit.ssp.R.dimen._10ssp) })
                    contentLayout.addView(chartContainer)

                    contentLayout.addView(createSectionTitle("Peak Hours"))
                    if (peakHours.isEmpty()) contentLayout.addView(createEmptyStateText("No traffic data available."))
                    else contentLayout.addView(createListViewCard(peakHours.map { 
                        val h = it.hour; val hourStr = if (h < 12) "$h:00 AM" else if (h == 12) "12:00 PM" else "${h - 12}:00 PM"
                        "$hourStr - ${it.count} transactions"
                    }))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("AdminDashboard", "Error in showAnalytics", e)
                }
            }
        }
    }

    private fun showSalesHistory() {
        contentLayout.removeAllViews()
        overviewTitle.text = "Sales History"
        contentLayout.addView(overviewTitle)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                val sales = db.saleDao().getRecentSales(100)

                withContext(Dispatchers.Main) {
                    if (sales.isEmpty()) {
                        contentLayout.addView(createEmptyStateText("No transactions found."))
                    } else {
                        for (sale in sales) {
                            val card = LinearLayout(this@AdminDashboardActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setBackground(GradientDrawable().apply { 
                                    setColor(if (sale.isVoided) Color.parseColor("#FFE5E5") else Color.WHITE)
                                    cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._8sdp)
                                    setStroke(2, Color.LTGRAY)
                                })
                                val p16 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
                                val p12 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
                                setPadding(p16, p12, p16, p12)
                                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)) }
                            }

                            val info = LinearLayout(this@AdminDashboardActivity).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                            }
                            val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(sale.timestamp))
                            info.addView(TextView(this@AdminDashboardActivity).apply {
                                text = "Sale #${sale.id} - $date"
                                textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
                                setTypeface(null, Typeface.BOLD) 
                            })
                            info.addView(TextView(this@AdminDashboardActivity).apply { 
                                text = "Total: K ${String.format(Locale.getDefault(), "%.2f", sale.totalAmount)} ${if (sale.isVoided) "(VOIDED)" else ""}"
                                textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
                                setTextColor(if (sale.isVoided) Color.RED else Color.BLACK) 
                            })

                            val actions = LinearLayout(this@AdminDashboardActivity).apply { orientation = LinearLayout.HORIZONTAL }
                            
                            val reprintBtn = Button(this@AdminDashboardActivity).apply {
                                text = "Reprint"
                                textSize = resources.getDimension(com.intuit.ssp.R.dimen._8ssp)
                                setOnClickListener { reprintReceipt(sale) }
                            }
                            
                            val voidBtn = Button(this@AdminDashboardActivity).apply {
                                text = "Void"
                                textSize = resources.getDimension(com.intuit.ssp.R.dimen._8ssp)
                                isEnabled = !sale.isVoided
                                setOnClickListener { showVoidConfirmation(sale) }
                            }

                            actions.addView(reprintBtn)
                            actions.addView(voidBtn)
                            card.addView(info)
                            card.addView(actions)
                            contentLayout.addView(card)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("AdminDashboard", "Error in showSalesHistory", e)
                }
            }
        }
    }

    private fun reprintReceipt(sale: Sale) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                val items = db.saleDao().getItemsForSale(sale.id)
                withContext(Dispatchers.Main) {
                    val builder = StringBuilder("DUPLICATE RECEIPT\n----------------\n")
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(sale.timestamp))
                    builder.append("Sale ID: ${sale.id}\nDate: $date\n\n")
                    for (item in items) {
                        builder.append("${item.productName} x${item.quantity} = K${item.subtotal}\n")
                    }
                    builder.append("\n----------------\nTOTAL: K${String.format(Locale.getDefault(), "%.2f", sale.totalAmount)}")
                    if (sale.isVoided) builder.append("\n*** VOIDED ***")
                    
                    android.app.AlertDialog.Builder(this@AdminDashboardActivity)
                        .setTitle("Receipt Preview")
                        .setMessage(builder.toString())
                        .setPositiveButton("Print", null)
                        .show()
                }
            } catch (e: Exception) {
                Log.e("AdminDashboard", "Error reprinting receipt", e)
            }
        }
    }

    private fun showVoidConfirmation(sale: Sale) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Void Sale")
            .setMessage("Are you sure you want to void Sale #${sale.id}? Inventory will be restored.")
            .setPositiveButton("Yes") { _, _ -> voidSale(sale) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun voidSale(sale: Sale) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                db.saleDao().executeVoidTransaction(sale.id)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminDashboardActivity, "Sale Voided Successfully", Toast.LENGTH_SHORT).show()
                    showSalesHistory()
                }
            } catch (e: Exception) {
                Log.e("AdminDashboard", "Error voiding sale", e)
            }
        }
    }

    private fun showCurrentInventory() {
        contentLayout.removeAllViews()
        overviewTitle.text = "Current Inventory"
        contentLayout.addView(overviewTitle)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                val products = db.productDao().getAllProducts()

                withContext(Dispatchers.Main) {
                    if (products.isEmpty()) {
                        contentLayout.addView(createEmptyStateText("No products in inventory."))
                    } else {
                        val items = products.map { "${it.name} (Barcode: ${it.barcode}) - ${it.currentInventory} in stock" }
                        contentLayout.addView(createListViewCard(items))
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminDashboard", "Error showing inventory", e)
            }
        }
    }

    private fun showInventoryValue() {
        contentLayout.removeAllViews()
        overviewTitle.text = "Inventory Value Report"
        contentLayout.addView(overviewTitle)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                val products = db.productDao().getAllProducts()
                val totalCostValue = products.sumOf { it.costPrice * it.currentInventory }
                val totalRetailValue = products.sumOf { it.sellingPrice * it.currentInventory }

                withContext(Dispatchers.Main) {
                    val kpiGrid = GridLayout(this@AdminDashboardActivity).apply { columnCount = 1; useDefaultMargins = true; setPadding(0, 16, 0, 16) }
                    kpiGrid.addView(createStatCard("Total Inventory Cost", "K ${String.format(Locale.getDefault(), "%.2f", totalCostValue)}", "Based on cost price"))
                    kpiGrid.addView(createStatCard("Total Potential Revenue", "K ${String.format(Locale.getDefault(), "%.2f", totalRetailValue)}", "Based on selling price"))
                    kpiGrid.addView(createStatCard("Potential Profit", "K ${String.format(Locale.getDefault(), "%.2f", totalRetailValue - totalCostValue)}", "Gross margin"))
                    contentLayout.addView(kpiGrid)
                }
            } catch (e: Exception) {
                Log.e("AdminDashboard", "Error showing inventory value", e)
            }
        }
    }

    private fun showReorderList() {
        contentLayout.removeAllViews()
        overviewTitle.text = "Reorder List (Low Stock)"
        contentLayout.addView(overviewTitle)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                val lowStock = db.productDao().getLowStockProducts().filter { it.barcode.isNotBlank() }

                withContext(Dispatchers.Main) {
                    if (lowStock.isEmpty()) {
                        contentLayout.addView(createEmptyStateText("All stock levels are above threshold."))
                    } else {
                        for (product in lowStock) {
                            val card = LinearLayout(this@AdminDashboardActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                val p16 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
                                val p12 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
                                setPadding(p16, p12, p16, p12)
                                background = GradientDrawable().apply {
                                    setColor(Color.WHITE)
                                    cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._8sdp)
                                    setStroke(2, Color.LTGRAY)
                                }
                                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)) }
                            }

                            val info = LinearLayout(this@AdminDashboardActivity).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                            }
                            info.addView(TextView(this@AdminDashboardActivity).apply { 
                                text = product.name
                                textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(Color.BLACK) 
                            })
                            info.addView(TextView(this@AdminDashboardActivity).apply { 
                                text = "Remaining: ${product.currentInventory}"
                                textSize = resources.getDimension(com.intuit.ssp.R.dimen._10ssp)
                            })

                            val qtyInput = EditText(this@AdminDashboardActivity).apply {
                                hint = "Qty"
                                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                                textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
                                layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._60sdp), -2).apply { marginEnd = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp) }
                            }

                            val addBtn = Button(this@AdminDashboardActivity).apply {
                                text = "RESTOCK"
                                textSize = resources.getDimension(com.intuit.ssp.R.dimen._10ssp)
                                setTextColor(Color.WHITE)
                                background = GradientDrawable().apply {
                                    setColor(this@AdminDashboardActivity.accentColor)
                                    cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._6sdp)
                                }
                                val p12btn = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
                                setPadding(p12btn, 0, p12btn, 0)
                                setOnClickListener {
                                    val qtyStr = qtyInput.text.toString()
                                    val qty = qtyStr.toIntOrNull() ?: 0
                                    if (qty > 0) {
                                        if (isCashierMode) promptAdminPassword { updateStock(product, qty) }
                                        else updateStock(product, qty)
                                    } else {
                                        Toast.makeText(this@AdminDashboardActivity, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            card.addView(info)
                            card.addView(qtyInput)
                            card.addView(addBtn)
                            contentLayout.addView(card)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminDashboard", "Error showing reorder list", e)
            }
        }
    }

    private fun showSecuritySettings() {
        contentLayout.removeAllViews()
        overviewTitle.text = "Security Settings"
        contentLayout.addView(overviewTitle)
        
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp), 0, 0) }
        
        val appPassCard = createSettingsOptionCard("App Access Password", "Manage the global app password") {
            showChangeAppPasswordDialog()
        }
        val pinCard = createSettingsOptionCard("Register PIN Codes", "Manage access codes for cashiers") {
            manageCashierPins()
        }
        val passCard = createSettingsOptionCard("Password Resets", "Change admin security code") {
            showPasswordResetOptions()
        }
        val bioEnabled = getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE).getBoolean("biometric_enabled", false)
        val bioCard = createSettingsOptionCard("Biometric Login", "Status: ${if (bioEnabled) "Enabled" else "Disabled"}") {
            toggleBiometrics()
        }
        
        layout.addView(appPassCard)
        layout.addView(pinCard)
        layout.addView(passCard)
        layout.addView(bioCard)
        contentLayout.addView(layout)
    }

    private fun showChangeAppPasswordDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Change App Access Password")
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp); setPadding(p, p/2, p, p/2) }
        val oldPinInput = EditText(this).apply { hint = "Current Password"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        val newPinInput = EditText(this).apply { hint = "New Password (4 digits)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        layout.addView(oldPinInput); layout.addView(newPinInput)
        builder.setView(layout)

        builder.setPositiveButton("Change") { _, _ ->
            val oldPin = oldPinInput.text.toString()
            val newPin = newPinInput.text.toString()
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val currentSaved = prefs.getString("app_access_password", "1234")
            
            if (oldPin == currentSaved && newPin.length >= 4) {
                prefs.edit().putString("app_access_password", newPin).apply()
                Toast.makeText(this@AdminDashboardActivity, "App Access Password changed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@AdminDashboardActivity, "Incorrect current password or invalid new one", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showPasswordResetOptions() {
        val options = arrayOf("Change Admin PIN", "Reset App Account Password")
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Reset Option")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> changeAdminPin()
                    1 -> resetAppPassword()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetAppPassword() {
        val user = Firebase.auth.currentUser
        val email = user?.email
        if (email != null) {
            Firebase.auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this@AdminDashboardActivity, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@AdminDashboardActivity, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Toast.makeText(this@AdminDashboardActivity, "No signed-in user found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun manageCashierPins() {
        contentLayout.removeAllViews()
        overviewTitle.text = "Manage Cashier PINs"
        contentLayout.addView(overviewTitle)

        val prefs = getSharedPreferences("CashierPrefs", Context.MODE_PRIVATE)
        val allCashiers = prefs.all

        if (allCashiers.isEmpty()) {
            contentLayout.addView(createEmptyStateText("No cashiers found."))
        } else {
            val listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
            for ((name, pin) in allCashiers) {
                val card = createSettingsOptionCard(name, "Current PIN: $pin") {
                    showEditCashierDialog(name, pin.toString())
                }
                listLayout.addView(card)
            }
            contentLayout.addView(listLayout)
        }
        
        val backBtn = Button(this).apply {
            text = "Back to Security"
            setOnClickListener { showSecuritySettings() }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER; setMargins(0, 16, 0, 0) }
        }
        contentLayout.addView(backBtn)
    }

    private fun showEditCashierDialog(name: String, currentPin: String) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Edit Cashier: $name")
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val p = 24; setPadding(p, p/2, p, p/2) }
        val pinInput = EditText(this).apply { 
            hint = "New 4-6 Digit PIN"
            setText(currentPin)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD 
        }
        layout.addView(pinInput)
        builder.setView(layout)
        
        builder.setPositiveButton("Update") { _, _ ->
            val newPin = pinInput.text.toString().trim()
            if (newPin.length >= 4) {
                getSharedPreferences("CashierPrefs", Context.MODE_PRIVATE).edit().putString(name, newPin).apply()
                Toast.makeText(this@AdminDashboardActivity, "PIN updated", Toast.LENGTH_SHORT).show()
                manageCashierPins()
            } else {
                Toast.makeText(this@AdminDashboardActivity, "Invalid PIN", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNeutralButton("Delete Cashier") { _, _ ->
            getSharedPreferences("CashierPrefs", Context.MODE_PRIVATE).edit().remove(name).apply()
            Toast.makeText(this@AdminDashboardActivity, "Cashier removed", Toast.LENGTH_SHORT).show()
            manageCashierPins()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun changeAdminPin() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Change Admin PIN")
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val p = 24; setPadding(p, p/2, p, p/2) }
        val oldPinInput = EditText(this).apply { hint = "Current PIN"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        val newPinInput = EditText(this).apply { hint = "New PIN (4-6 digits)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        layout.addView(oldPinInput); layout.addView(newPinInput)
        builder.setView(layout)

        builder.setPositiveButton("Change") { _, _ ->
            val oldPin = oldPinInput.text.toString()
            val newPin = newPinInput.text.toString()
            val prefs = getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE)
            val currentSaved = prefs.getString("admin_pin", "1234")
            
            if (oldPin == currentSaved && newPin.length >= 4) {
                prefs.edit().putString("admin_pin", newPin).apply()
                Toast.makeText(this@AdminDashboardActivity, "Admin PIN changed successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@AdminDashboardActivity, "Incorrect current PIN or invalid new PIN", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun toggleBiometrics() {
        val prefs = getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("biometric_enabled", false)
        prefs.edit().putBoolean("biometric_enabled", !isEnabled).apply()
        Toast.makeText(this@AdminDashboardActivity, "Biometric login ${if (!isEnabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        showSecuritySettings()
    }

    private fun showDailySales() {
        contentLayout.removeAllViews()
        overviewTitle.text = "Daily Sales Report"
        contentLayout.addView(overviewTitle)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@AdminDashboardActivity)
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val sales = db.saleDao().getAllSales().filter { it.timestamp >= startOfDay }

                withContext(Dispatchers.Main) {
                    if (sales.isEmpty()) {
                        contentLayout.addView(createEmptyStateText("No sales recorded today."))
                    } else {
                        val summary = sales.map { 
                            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.timestamp))
                            "[$time] Sale #${it.id} - K ${String.format(Locale.getDefault(), "%.2f", it.totalAmount)} ${if (it.isVoided) "(VOID)" else ""}"
                        }
                        contentLayout.addView(createListViewCard(summary))
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminDashboard", "Error showing daily sales", e)
            }
        }
    }

    private fun showDrawerLogs() {
        contentLayout.removeAllViews()
        overviewTitle.text = "Cash Drawer Logs"
        contentLayout.addView(overviewTitle)
        contentLayout.addView(createEmptyStateText("Logs module being developed."))
    }

    private fun createSettingsOptionCard(title: String, desc: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val p16 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
        setPadding(p16, p16, p16, p16)
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._8sdp); setStroke(2, Color.LTGRAY) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)) }
        isClickable = true
        setOnClickListener { onClick() }
        
        addView(TextView(this@AdminDashboardActivity).apply { text = title; textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp); setTypeface(null, Typeface.BOLD); setTextColor(Color.BLACK) })
        addView(TextView(this@AdminDashboardActivity).apply { text = desc; textSize = resources.getDimension(com.intuit.ssp.R.dimen._10ssp); setTextColor(Color.GRAY); setPadding(0, 4, 0, 0) })
    }

    private fun createEmptyStateText(message: String) = TextView(this).apply { text = message; textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp); setTextColor(Color.GRAY); setPadding(16, 16, 16, 16); gravity = Gravity.CENTER }
    private fun createSectionTitle(title: String) = TextView(this).apply { text = title; textSize = resources.getDimension(com.intuit.ssp.R.dimen._14ssp); setTextColor(Color.DKGRAY); setTypeface(null, Typeface.BOLD); setPadding(0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)) }

    private fun createListViewCard(items: List<String>) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackground(GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._8sdp); setStroke(2, Color.LTGRAY) })
        val p16 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
        val p8 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
        setPadding(p16, p8, p16, p8)
        for (item in items) {
            addView(TextView(this@AdminDashboardActivity).apply { text = item; textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp); setPadding(0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)); setTextColor(Color.BLACK) })
            if (items.indexOf(item) < items.size - 1) addView(View(this@AdminDashboardActivity).apply { layoutParams = LinearLayout.LayoutParams(-1, 1); setBackgroundColor(Color.LTGRAY) })
        }
    }

    private fun toggleSidebar() {
        val sidebarWidth = resources.getDimension(com.intuit.sdp.R.dimen._180sdp)
        if (isSidebarOpen) { 
            menuIcon.text = "☰"
            sidebarScroll.animate().translationX(-sidebarWidth).withEndAction { sidebarScroll.visibility = View.GONE }.start() 
        } else { 
            menuIcon.text = "✕"
            sidebarScroll.visibility = View.VISIBLE
            sidebarScroll.animate().translationX(0f).start() 
        }
        isSidebarOpen = !isSidebarOpen
    }

    private fun updateTime() {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val role = if (isCashierMode) "Cashier" else "Manager"
        managerInfo.text = "$staffName ($role)  $currentTime"
    }

    override fun onDestroy() { 
        super.onDestroy()
        timeHandler.removeCallbacks(timeRunnable) 
        alertHandler.removeCallbacks(alertRunnable)
    }

    private fun createExpandableSidebarItem(label: String, subItems: List<String>, parent: LinearLayout) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val topLevel = TextView(this).apply { text = "▶ $label"; textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp); setTextColor(Color.LTGRAY); setPadding(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)); isClickable = true }
        val subContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setBackgroundColor(Color.parseColor("#14212e")) }
        for (sub in subItems) {
            val subItem = TextView(this).apply {
                text = sub; textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp); setTextColor(Color.GRAY)
                val indent = if (sub.startsWith("    ")) resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._60sdp) else if (sub.startsWith("  ")) resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._40sdp) else resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._40sdp)
                setPadding(indent, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._10sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._10sdp))
                if (sub.trim().startsWith("📂") || sub.trim().startsWith("📦") || sub.trim().startsWith("🚚") || sub.trim().startsWith("📊") || sub.trim() == "Inventory") { setTypeface(null, Typeface.BOLD); setTextColor(Color.LTGRAY) }
                isClickable = true
                setOnClickListener {
                    val actionLabel = sub.trim()
                    when (actionLabel) {
                        "Overview" -> { showOverview(); if (isSidebarOpen) toggleSidebar() }
                        "Analytics" -> { showAnalytics(); if (isSidebarOpen) toggleSidebar() }
                        "Sales History" -> { showSalesHistory(); if (isSidebarOpen) toggleSidebar() }
                        "Set Admin Name" -> showSetAdminNameDialog()
                        "Add Cashier" -> showAddCashierDialog()
                        "Add New Item" -> { val intent = Intent(this@AdminDashboardActivity, AddProductActivity::class.java); startActivity(intent) }
                        "Current Inventory" -> { showCurrentInventory(); if (isSidebarOpen) toggleSidebar() }
                        "Inventory Value" -> { showInventoryValue(); if (isSidebarOpen) toggleSidebar() }
                        "Reorder List" -> {
                            if (isCashierMode) {
                                promptAdminPassword { showReorderList(); if (isSidebarOpen) toggleSidebar() }
                            } else {
                                showReorderList(); if (isSidebarOpen) toggleSidebar()
                            }
                        }
                        "Security" -> { showSecuritySettings(); if (isSidebarOpen) toggleSidebar() }
                        "About & Support" -> {
                            val intent = Intent(this@AdminDashboardActivity, AboutSupportActivity::class.java)
                            startActivity(intent)
                        }
                        "Daily Sales" -> { showDailySales(); if (isSidebarOpen) toggleSidebar() }
                        "Manage Sessions" -> {
                            val intent = Intent(this@AdminDashboardActivity, CashSessionsActivity::class.java)
                            intent.putExtra("STAFF_NAME", staffName)
                            startActivity(intent)
                            if (isSidebarOpen) toggleSidebar()
                        }
                        "Drawer Logs" -> { showDrawerLogs(); if (isSidebarOpen) toggleSidebar() }
                        else -> Toast.makeText(this@AdminDashboardActivity, "Selected: $actionLabel", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            subContainer.addView(subItem)
        }
        topLevel.setOnClickListener { if (subContainer.visibility == View.VISIBLE) { subContainer.visibility = View.GONE; topLevel.text = "▶ $label" } else { subContainer.visibility = View.VISIBLE; topLevel.text = "▼ $label" } }
        container.addView(topLevel); container.addView(subContainer); parent.addView(container)
    }

    private fun createStatCard(title: String, mainVal: String, subVal: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackground(GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._8sdp); setStroke(2, Color.LTGRAY) })
            val p16 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
            setPadding(p16, p16, p16, p16)
            val params = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f))
            params.width = 0; params.setMargins(0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)); layoutParams = params
            addView(TextView(this@AdminDashboardActivity).apply { text = title; textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp); setTextColor(Color.BLACK); setTypeface(null, Typeface.BOLD) })
            addView(TextView(this@AdminDashboardActivity).apply { text = mainVal; textSize = resources.getDimension(com.intuit.ssp.R.dimen._13ssp); setTextColor(Color.parseColor("#2c3e50")); setPadding(0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)) })
            addView(TextView(this@AdminDashboardActivity).apply { text = subVal; textSize = resources.getDimension(com.intuit.ssp.R.dimen._10ssp); setTextColor(Color.GRAY) })
        }
    }

    private fun showSetAdminNameDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Set Admin Name")
        val nameInput = EditText(this).apply { 
            hint = "Enter Name"
            setText(if (staffName != "Admin") staffName else "")
        }
        builder.setView(nameInput)
        builder.setPositiveButton("Save") { _, _ -> 
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty()) { 
                getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE).edit().putString("admin_name", name).apply()
                staffName = name // Update local variable so UI reflects changes immediately
                updateTime() 
            } 
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showAddCashierDialog() {
        val builder = android.app.AlertDialog.Builder(this); builder.setTitle("Add New Cashier")
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 24) }
        val nameInput = EditText(this).apply { hint = "Cashier Name" }
        val pinInput = EditText(this).apply { hint = "4-6 Digit PIN"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        layout.addView(nameInput); layout.addView(pinInput); builder.setView(layout)
        builder.setPositiveButton("Add") { _, _ -> val name = nameInput.text.toString().trim(); val pin = pinInput.text.toString().trim(); if (name.isNotEmpty() && pin.length >= 4) { val prefs = getSharedPreferences("CashierPrefs", Context.MODE_PRIVATE); prefs.edit().putString(name, pin).apply(); Toast.makeText(this@AdminDashboardActivity, "Cashier $name added successfully", Toast.LENGTH_SHORT).show() } else { Toast.makeText(this@AdminDashboardActivity, "Invalid Name or PIN (min 4 digits)", Toast.LENGTH_SHORT).show() } }
        builder.setNegativeButton("Cancel", null).show()
    }
}
