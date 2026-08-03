package com.example.pos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pos.barcode.BarcodeProductLookup
import com.example.pos.barcode.BarcodeScannerController
import com.example.pos.data.AppDatabase
import com.example.pos.data.Product
import com.example.pos.data.Sale
import com.example.pos.data.SaleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CounterActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CounterActivity"
        private const val SCANNER_TAG = "CounterBarcodeScan"
    }

    private val topBarColor = Color.parseColor("#101a24")
    private val backgroundColor = Color.parseColor("#f0f2f5")
    private val accentColor = Color.parseColor("#00a3e0")
    private val currencySymbol = "K"

    private lateinit var searchInput: EditText
    private lateinit var productsGrid: GridLayout
    private lateinit var cartLayout: LinearLayout
    private lateinit var totalText: TextView
    private lateinit var moneyReceivedInput: EditText
    private lateinit var changeText: TextView
    
    private val cart = mutableMapOf<Int, CartItem>()
    private var allProducts = listOf<Product>()
    private var filteredProducts = listOf<Product>()

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScannerController: BarcodeScannerController
    private var toneGenerator: ToneGenerator? = null

    private lateinit var mainRootLayout: ConstraintLayout
    private lateinit var bottomSection: ConstraintLayout
    private var restockButton: FrameLayout? = null
    private var isRestockAlertIgnored = false
    private val alertHandler = Handler(Looper.getMainLooper())
    private val alertRunnable = object : Runnable {
        override fun run() {
            checkRestockAlert()
            alertHandler.postDelayed(this, 30000)
        }
    }

    data class CartItem(val product: Product, var quantity: Int)

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Log.i(SCANNER_TAG, "camera permission result granted=$isGranted")
        if (isGranted) {
            openBarcodeScanner()
        } else {
            Toast.makeText(this, "Camera permission is required for barcode scanning", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        barcodeScannerController = BarcodeScannerController(this, cameraExecutor, SCANNER_TAG)
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

        // Session Guard
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@CounterActivity)
            val session = db.cashSessionDao().getOpenSession()
            if (session == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CounterActivity, "Please open a Cash Session first", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        mainRootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(backgroundColor)
        }

        val topSection = ConstraintLayout(this).apply {
            id = View.generateViewId()
            val padding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
            setPadding(padding, padding, padding, padding)
        }

        val searchBarLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._4sdp)
            }
            val p8 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
            val p4 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)
            setPadding(p8, p4, p8, p4)
        }

        searchInput = EditText(this).apply {
            hint = "Search products..."
            background = null
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterProducts(s.toString())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        val scanBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                Log.i(SCANNER_TAG, "scan button tapped")
                if (checkCameraPermission()) {
                    openBarcodeScanner()
                } else {
                    Log.i(SCANNER_TAG, "camera permission missing, requesting")
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }

        searchBarLayout.addView(searchInput)
        searchBarLayout.addView(scanBtn)

        val productsScroll = ScrollView(this).apply {
            id = View.generateViewId()
        }
        productsGrid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = true
            layoutParams = FrameLayout.LayoutParams(-1, -2)
        }
        productsScroll.addView(productsGrid)

        topSection.addView(searchBarLayout)
        topSection.addView(productsScroll)

        val topSectionSet = ConstraintSet()
        topSectionSet.clone(topSection)
        topSectionSet.connect(searchBarLayout.id, ConstraintSet.TOP, topSection.id, ConstraintSet.TOP)
        topSectionSet.connect(searchBarLayout.id, ConstraintSet.START, topSection.id, ConstraintSet.START)
        topSectionSet.connect(searchBarLayout.id, ConstraintSet.END, topSection.id, ConstraintSet.END)
        
        topSectionSet.connect(productsScroll.id, ConstraintSet.TOP, searchBarLayout.id, ConstraintSet.BOTTOM, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp))
        topSectionSet.connect(productsScroll.id, ConstraintSet.BOTTOM, topSection.id, ConstraintSet.BOTTOM)
        topSectionSet.connect(productsScroll.id, ConstraintSet.START, topSection.id, ConstraintSet.START)
        topSectionSet.connect(productsScroll.id, ConstraintSet.END, topSection.id, ConstraintSet.END)
        topSectionSet.applyTo(topSection)

        bottomSection = ConstraintLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.WHITE)
            elevation = resources.getDimension(com.intuit.sdp.R.dimen._10sdp)
            val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
            setPadding(p, p, p, p)
        }

        val cartTitle = TextView(this).apply {
            id = View.generateViewId()
            text = "Current Order"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._14ssp)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }

        val cartHeader = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)
            setPadding(0, p, 0, p)
            setBackgroundColor(Color.parseColor("#F8F9FA"))
        }
        cartHeader.addView(TextView(this).apply { text = "ITEM"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setTypeface(null, Typeface.BOLD); textSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp) })
        cartHeader.addView(TextView(this).apply { text = "QTY"; layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._30sdp), -2); gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); textSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp) })
        cartHeader.addView(TextView(this).apply { text = "TOTAL"; layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._50sdp), -2); gravity = Gravity.END; setTypeface(null, Typeface.BOLD); textSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp) })
        cartHeader.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp), 1) })

        val cartScroll = ScrollView(this).apply {
            id = View.generateViewId()
        }
        cartLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cartScroll.addView(cartLayout)

        val summaryLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FAFAFA"))
                setStroke(2, Color.LTGRAY)
                cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._4sdp)
            }
            val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
            setPadding(p, p, p, p)
        }

        val totalAndMoneyLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        totalText = TextView(this).apply {
            text = "Total: $currencySymbol 0.00"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._14ssp)
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        totalAndMoneyLayout.addView(totalText)

        moneyReceivedInput = EditText(this).apply {
            hint = "Received"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._80sdp), -2)
            gravity = Gravity.END
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    calculateChange()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        totalAndMoneyLayout.addView(moneyReceivedInput)
        summaryLayout.addView(totalAndMoneyLayout)

        changeText = TextView(this).apply {
            text = "Change: $currencySymbol 0.00"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
            gravity = Gravity.END
        }
        summaryLayout.addView(changeText)

        val checkoutBtn = Button(this).apply {
            text = "CHECKOUT"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
            setBackgroundColor(accentColor)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { performCheckout() }
            val params = LinearLayout.LayoutParams(-1, -2)
            params.topMargin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
            layoutParams = params
        }
        summaryLayout.addView(checkoutBtn)

        bottomSection.addView(cartTitle)
        bottomSection.addView(cartHeader)
        bottomSection.addView(cartScroll)
        bottomSection.addView(summaryLayout)

        val bottomSet = ConstraintSet()
        bottomSet.clone(bottomSection)
        bottomSet.connect(cartTitle.id, ConstraintSet.TOP, bottomSection.id, ConstraintSet.TOP)
        bottomSet.connect(cartTitle.id, ConstraintSet.START, bottomSection.id, ConstraintSet.START)
        
        bottomSet.connect(cartHeader.id, ConstraintSet.TOP, cartTitle.id, ConstraintSet.BOTTOM, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp))
        bottomSet.connect(cartHeader.id, ConstraintSet.START, bottomSection.id, ConstraintSet.START)
        bottomSet.connect(cartHeader.id, ConstraintSet.END, bottomSection.id, ConstraintSet.END)

        bottomSet.connect(summaryLayout.id, ConstraintSet.BOTTOM, bottomSection.id, ConstraintSet.BOTTOM)
        bottomSet.connect(summaryLayout.id, ConstraintSet.START, bottomSection.id, ConstraintSet.START)
        bottomSet.connect(summaryLayout.id, ConstraintSet.END, bottomSection.id, ConstraintSet.END)

        bottomSet.connect(cartScroll.id, ConstraintSet.TOP, cartHeader.id, ConstraintSet.BOTTOM)
        bottomSet.connect(cartScroll.id, ConstraintSet.BOTTOM, summaryLayout.id, ConstraintSet.TOP, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp))
        bottomSet.connect(cartScroll.id, ConstraintSet.START, bottomSection.id, ConstraintSet.START)
        bottomSet.connect(cartScroll.id, ConstraintSet.END, bottomSection.id, ConstraintSet.END)
        bottomSet.constrainHeight(cartScroll.id, 0)
        bottomSet.applyTo(bottomSection)

        mainRootLayout.addView(topSection)
        mainRootLayout.addView(bottomSection)

        val mainSet = ConstraintSet()
        mainSet.clone(mainRootLayout)
        mainSet.connect(topSection.id, ConstraintSet.TOP, mainRootLayout.id, ConstraintSet.TOP)
        mainSet.connect(topSection.id, ConstraintSet.START, mainRootLayout.id, ConstraintSet.START)
        mainSet.connect(topSection.id, ConstraintSet.END, mainRootLayout.id, ConstraintSet.END)
        mainSet.connect(topSection.id, ConstraintSet.BOTTOM, bottomSection.id, ConstraintSet.TOP)
        mainSet.setVerticalWeight(topSection.id, 0.55f)

        mainSet.connect(bottomSection.id, ConstraintSet.TOP, topSection.id, ConstraintSet.BOTTOM)
        mainSet.connect(bottomSection.id, ConstraintSet.START, mainRootLayout.id, ConstraintSet.START)
        mainSet.connect(bottomSection.id, ConstraintSet.END, mainRootLayout.id, ConstraintSet.END)
        mainSet.connect(bottomSection.id, ConstraintSet.BOTTOM, mainRootLayout.id, ConstraintSet.BOTTOM)
        mainSet.setVerticalWeight(bottomSection.id, 0.45f)
        
        mainSet.constrainHeight(topSection.id, 0)
        mainSet.constrainHeight(bottomSection.id, 0)
        mainSet.applyTo(mainRootLayout)

        setContentView(mainRootLayout)
        loadProducts()
        alertHandler.post(alertRunnable)
    }

    private fun checkRestockAlert() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@CounterActivity)
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
                Log.e("CounterActivity", "Error checking restock", e)
            }
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
                Toast.makeText(this@CounterActivity, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
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
                promptAdminPassword {
                    val intent = Intent(this@CounterActivity, AdminDashboardActivity::class.java).apply {
                        putExtra("IS_CASHIER", true)
                        putExtra("SHOW_REORDER", true)
                    }
                    startActivity(intent)
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

        val mainSet = ConstraintSet()
        mainSet.clone(mainRootLayout)
        mainSet.connect(restockButton!!.id, ConstraintSet.BOTTOM, bottomSection.id, ConstraintSet.TOP, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp))
        mainSet.connect(restockButton!!.id, ConstraintSet.END, mainRootLayout.id, ConstraintSet.END, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp))
        mainSet.applyTo(mainRootLayout)
    }

    private fun removeRestockButton() {
        restockButton?.let {
            mainRootLayout.removeView(it)
            restockButton = null
        }
    }

    private fun checkCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun loadProducts() {
        lifecycleScope.launch(Dispatchers.IO) {
            allProducts = AppDatabase.getDatabase(this@CounterActivity).productDao().getAllProducts()
            filteredProducts = allProducts
            Log.d(TAG, "loadProducts: loaded ${allProducts.size} products")
            withContext(Dispatchers.Main) {
                updateProductsGrid()
            }
        }
    }

    private fun filterProducts(query: String) {
        filteredProducts = if (query.isEmpty()) allProducts
        else allProducts.filter { it.name.contains(query, ignoreCase = true) || it.barcode.contains(query) }
        updateProductsGrid()
    }

    private fun updateProductsGrid() {
        productsGrid.removeAllViews()
        val screenWidth = resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth / 2.3).toInt()

        for (product in filteredProducts) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._8sdp); setStroke(1, Color.LTGRAY) }
                val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
                setPadding(p, p, p, p)
                layoutParams = GridLayout.LayoutParams().apply { width = cardWidth; height = cardWidth + resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._40sdp); setMargins(p/2, p/2, p/2, p/2) }
                setOnClickListener { addToCart(product) }
            }

            val img = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(cardWidth - 40, cardWidth - 40)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(android.R.drawable.ic_menu_gallery)
                if (product.imagePath != null) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(product.imagePath)
                        if (bitmap != null) setImageBitmap(bitmap)
                    } catch (e: Exception) {}
                }
            }

            val name = TextView(this).apply { text = product.name; textSize = resources.getDimension(com.intuit.ssp.R.dimen._10ssp); maxLines = 1; gravity = Gravity.CENTER; setPadding(0, 8, 0, 2) }
            val price = TextView(this).apply { text = "$currencySymbol ${product.sellingPrice}"; textSize = resources.getDimension(com.intuit.ssp.R.dimen._10ssp); setTypeface(null, Typeface.BOLD); setTextColor(accentColor) }

            card.addView(img)
            card.addView(name)
            card.addView(price)
            productsGrid.addView(card)
        }
    }

    private fun addToCart(product: Product) {
        Log.i(TAG, "addToCart: id=${product.id} name='${product.name}' barcode='${product.barcode}'")
        val existing = cart[product.id]
        if (existing != null) existing.quantity++
        else cart[product.id] = CartItem(product, 1)
        updateCartUI()
        playBeep()
    }

    private fun openBarcodeScanner() {
        Log.i(SCANNER_TAG, "openBarcodeScanner: in-memory catalog size=${allProducts.size}")
        barcodeScannerController.start(object : BarcodeScannerController.Listener {
            override fun onBarcodeDetected(rawValue: String, formatName: String) {
                Log.i(SCANNER_TAG, "onBarcodeDetected: raw='$rawValue' format=$formatName")
                handleScannedBarcode(rawValue, formatName)
            }

            override fun onScanError(message: String) {
                Log.e(SCANNER_TAG, "onScanError: $message")
            }

            override fun onScannerClosed() {
                Log.i(SCANNER_TAG, "onScannerClosed")
            }
        })
    }

    private fun handleScannedBarcode(rawValue: String, formatName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val product = BarcodeProductLookup.findProduct(this@CounterActivity, rawValue)
            withContext(Dispatchers.Main) {
                if (product != null) {
                    Log.i(SCANNER_TAG, "product match: id=${product.id} storedBarcode='${product.barcode}'")
                    addToCart(product)
                    Toast.makeText(
                        this@CounterActivity,
                        "Added: ${product.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    barcodeScannerController.stop()
                } else {
                    Log.w(SCANNER_TAG, "no product for scanned='$rawValue' format=$formatName")
                    Toast.makeText(
                        this@CounterActivity,
                        "Product not found: $rawValue",
                        Toast.LENGTH_LONG
                    ).show()
                    barcodeScannerController.resetDetection()
                }
            }
        }
    }

    private fun updateCartUI() {
        cartLayout.removeAllViews()
        var total = 0.0
        val p12 = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
        for (item in cart.values) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, p12, 0, p12) }
            val name = TextView(this).apply { text = item.product.name; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp); maxLines = 1 }
            val qty = TextView(this).apply { text = "${item.quantity}"; layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._30sdp), -2); gravity = Gravity.CENTER; textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp) }
            val price = TextView(this).apply { text = "$currencySymbol ${String.format("%.2f", item.product.sellingPrice * item.quantity)}"; layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._50sdp), -2); gravity = Gravity.END; setTypeface(null, Typeface.BOLD); textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp) }
            val remove = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_delete); setBackgroundColor(Color.TRANSPARENT); layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp)); setOnClickListener { cart.remove(item.product.id); updateCartUI() } }

            row.addView(name)
            row.addView(qty)
            row.addView(price)
            row.addView(remove)
            cartLayout.addView(row)
            cartLayout.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 1); setBackgroundColor(Color.parseColor("#EEEEEE")) })
            total += item.product.sellingPrice * item.quantity
        }
        totalText.text = "Total: $currencySymbol ${String.format("%.2f", total)}"
        calculateChange()
    }

    private fun calculateChange() {
        val totalStr = totalText.text.toString().replace("Total: $currencySymbol ", "")
        val total = totalStr.toDoubleOrNull() ?: 0.0
        val received = moneyReceivedInput.text.toString().toDoubleOrNull() ?: 0.0
        val change = received - total
        changeText.text = "Change: $currencySymbol ${String.format("%.2f", if (change > 0) change else 0.0)}"
        changeText.setTextColor(if (change >= 0) Color.BLACK else Color.RED)
    }

    private fun performCheckout() {
        if (cart.isEmpty()) { Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show(); return }
        val totalStr = totalText.text.toString().replace("Total: $currencySymbol ", "")
        val total = totalStr.toDoubleOrNull() ?: 0.0
        val received = moneyReceivedInput.text.toString().toDoubleOrNull() ?: 0.0
        if (received < total) { Toast.makeText(this, "Insufficient money received", Toast.LENGTH_SHORT).show(); return }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@CounterActivity)
            val session = db.cashSessionDao().getOpenSession()
            if (session == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CounterActivity, "Session expired or closed. Cannot process sale.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val sale = Sale(
                totalAmount = total, 
                amountReceived = received, 
                changeGiven = received - total,
                sessionId = session.id
            )

            val saleItems = cart.values.map {
                SaleItem(
                    saleId = 0, // Will be set by executeSaleTransaction
                    productId = it.product.id, 
                    productName = it.product.name, 
                    quantity = it.quantity, 
                    unitPrice = it.product.sellingPrice, 
                    subtotal = it.product.sellingPrice * it.quantity
                )
            }

            try {
                // Use the atomic transaction method to insert sale, items and update inventory
                db.saleDao().executeSaleTransaction(sale, saleItems)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CounterActivity, "Sale Completed Successfully!", Toast.LENGTH_LONG).show()
                    cart.clear()
                    moneyReceivedInput.text.clear()
                    updateCartUI()
                    loadProducts() // Refresh inventory grid
                    checkRestockAlert() // Re-check after sales
                }
            } catch (e: Exception) {
                Log.e(TAG, "Checkout failed", e)
                withContext(Dispatchers.Main) {
                    if (e is com.example.pos.data.InsufficientStockException) {
                        showShortageDialog(e.shortages)
                    } else {
                        Toast.makeText(this@CounterActivity, "Error: Could not complete sale. ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showShortageDialog(shortages: List<com.example.pos.data.StockShortage>) {
        val message = StringBuilder("The following items have insufficient stock:\n\n")
        for (s in shortages) {
            val deficit = s.requested - s.available
            message.append("• ${s.productName}: Available ${s.available}, Requested ${s.requested} (Shortage: $deficit)\n")
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Insufficient Inventory")
            .setMessage(message.toString())
            .setPositiveButton("Adjust Cart", null)
            .show()
    }

    private fun playBeep() { try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (e: Exception) {} }

    override fun onDestroy() { 
        super.onDestroy()
        barcodeScannerController.stop()
        cameraExecutor.shutdown()
        toneGenerator?.release()
        alertHandler.removeCallbacks(alertRunnable)
    }
}
