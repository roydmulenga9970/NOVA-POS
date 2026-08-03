package com.example.pos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pos.barcode.BarcodeScannerController
import com.example.pos.data.AppDatabase
import com.example.pos.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AddProductActivity : ComponentActivity() {

    companion object {
        private const val SCANNER_TAG = "AddProductBarcodeScan"
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }

    private val topBarColor = Color.parseColor("#101a24")
    private val backgroundColor = Color.parseColor("#f0f2f5")
    private val accentColor = Color.parseColor("#00a3e0")

    private lateinit var productImage: ImageView
    private lateinit var nameInput: EditText
    private lateinit var barcodeInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var sellingPriceInput: EditText
    private lateinit var costPriceInput: EditText
    private lateinit var inventoryInput: EditText
    private lateinit var logoText: TextView
    
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScannerController: BarcodeScannerController
    private var toneGenerator: ToneGenerator? = null
    private var capturedBitmap: Bitmap? = null
    private var editingProductId: Int = -1

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            productImage.setImageBitmap(bitmap)
            productImage.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

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

        editingProductId = intent.getIntExtra(EXTRA_PRODUCT_ID, -1)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(backgroundColor)
        }

        // --- TOP BAR ---
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, 140)
            setBackgroundColor(topBarColor)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._40sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), 0) 
        }

        logoText = TextView(this).apply {
            text = if (editingProductId == -1) "Add New Product" else "Edit Product"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._14ssp)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        val saveBtn = Button(this).apply {
            text = "SAVE"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; weight = 1f }
            textAlignment = View.TEXT_ALIGNMENT_TEXT_END
            setOnClickListener { saveProduct() }
        }

        topBar.addView(logoText)
        topBar.addView(saveBtn)
        mainLayout.addView(topBar)

        // --- CONTENT ---
        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, -1) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp)) }

        // Image Placeholder
        val imageContainer = FrameLayout(this).apply {
            val size = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._100sdp)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER; setMargins(0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)) }
            background = GradientDrawable().apply { setColor(Color.LTGRAY); cornerRadius = size.toFloat() / 2 }
            setOnClickListener { 
                if (checkCameraPermission()) takePictureLauncher.launch(null)
                else requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        productImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(android.R.drawable.ic_menu_camera)
        }
        imageContainer.addView(productImage)
        content.addView(imageContainer)

        // Basic Info Card
        val infoCard = createCardContainer()
        infoCard.addView(createSectionHeader("📂 Products"))
        
        val nameLayout = createLabeledInput("Product Name", "e.g. Coca Cola")
        nameInput = nameLayout.findViewById(1002)
        infoCard.addView(nameLayout)
        
        val barcodeLayout = createBarcodeActionsRow("Barcode", "Scan or enter barcode")
        barcodeInput = barcodeLayout.findViewById(1001)
        infoCard.addView(barcodeLayout)
        
        val descLayout = createLabeledInput("Description", "Product details...")
        descriptionInput = descLayout.findViewById(1002)
        infoCard.addView(descLayout)
        content.addView(infoCard)

        // Stock Control Card
        val stockCard = createCardContainer()
        stockCard.addView(createSectionHeader("📦 Stock Control"))
        val pricingRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        
        val sPriceLayout = createLabeledInput("Selling Price", "0.00", 1f)
        sellingPriceInput = sPriceLayout.findViewById(1002)
        pricingRow.addView(sPriceLayout)
        
        val cPriceLayout = createLabeledInput("Cost Price", "0.00", 1f)
        costPriceInput = cPriceLayout.findViewById(1002)
        pricingRow.addView(cPriceLayout)
        stockCard.addView(pricingRow)

        val inventoryLayout = createLabeledInput("Current Inventory", "0")
        inventoryInput = inventoryLayout.findViewById(1002)
        stockCard.addView(inventoryLayout)
        
        content.addView(stockCard)

        scroll.addView(content)
        mainLayout.addView(scroll)
        setContentView(mainLayout)

        if (editingProductId != -1) {
            loadProductData(editingProductId)
        }
    }

    private fun loadProductData(id: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val product = AppDatabase.getDatabase(this@AddProductActivity).productDao().getProductById(id)
            withContext(Dispatchers.Main) {
                product?.let {
                    nameInput.setText(it.name)
                    barcodeInput.setText(it.barcode)
                    descriptionInput.setText(it.description)
                    sellingPriceInput.setText(it.sellingPrice.toString())
                    costPriceInput.setText(it.costPrice.toString())
                    inventoryInput.setText(it.currentInventory.toString())
                    if (!it.imagePath.isNullOrEmpty()) {
                        val file = File(it.imagePath)
                        if (file.exists()) {
                            capturedBitmap = BitmapFactory.decodeFile(it.imagePath)
                            productImage.setImageBitmap(capturedBitmap)
                            productImage.scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    }
                }
            }
        }
    }

    private fun checkCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun generateBarcode() {
        val randomNum = (1000000000L..9999999999L).random()
        val generated = "20$randomNum"
        barcodeInput.setText(generated)
        playBeep()
        Toast.makeText(this, "Unique Barcode Generated", Toast.LENGTH_SHORT).show()
    }

    private fun saveProduct() {
        val name = nameInput.text.toString().trim()
        val barcode = barcodeInput.text.toString().trim()
        val description = descriptionInput.text.toString().trim()
        val sellingPrice = sellingPriceInput.text.toString().toDoubleOrNull() ?: 0.0
        val costPrice = costPriceInput.text.toString().toDoubleOrNull() ?: 0.0
        val inventory = inventoryInput.text.toString().toIntOrNull() ?: 0

        if (name.isEmpty() || barcode.isEmpty()) {
            Toast.makeText(this, "Name and Barcode are required", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@AddProductActivity)
            
            // Uniqueness Validation
            val existingProduct = db.productDao().getProductByBarcode(barcode)
            if (existingProduct != null && existingProduct.id != editingProductId) {
                withContext(Dispatchers.Main) {
                    showDuplicateDialog(existingProduct)
                }
                return@launch
            }

            var imagePath: String? = null
            capturedBitmap?.let { bitmap ->
                val fileName = "prod_${System.currentTimeMillis()}.jpg"
                val file = File(filesDir, fileName)
                try {
                    val out = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()
                    imagePath = file.absolutePath
                } catch (e: Exception) {
                    Log.e("SaveProduct", "Failed to save image", e)
                }
            }

            val product = Product(
                id = if (editingProductId != -1) editingProductId else 0,
                name = name,
                barcode = barcode,
                description = description,
                sellingPrice = sellingPrice,
                costPrice = costPrice,
                currentInventory = inventory,
                imagePath = imagePath ?: if (editingProductId != -1) existingProduct?.imagePath else null,
                lastUpdated = System.currentTimeMillis()
            )

            if (editingProductId == -1) {
                db.productDao().insert(product)
            } else {
                db.productDao().update(product)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@AddProductActivity, if (editingProductId == -1) "Product Saved" else "Product Updated", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showDuplicateDialog(existing: Product) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Product Already Exists")
            .setMessage("A product with barcode '${existing.barcode}' already exists: ${existing.name}.\n\nWould you like to edit the existing product instead?")
            .setPositiveButton("Edit Existing") { _, _ ->
                editingProductId = existing.id
                logoText.text = "Edit Product"
                loadProductData(editingProductId)
                Toast.makeText(this, "Switched to editing existing product", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openBarcodeScanner() {
        if (!checkCameraPermission()) {
            Log.i(SCANNER_TAG, "camera permission missing, requesting")
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        Log.i(SCANNER_TAG, "openBarcodeScanner")
        barcodeScannerController.start(object : BarcodeScannerController.Listener {
            override fun onBarcodeDetected(rawValue: String, formatName: String) {
                Log.i(SCANNER_TAG, "onBarcodeDetected: raw='$rawValue' format=$formatName")
                playBeep()
                barcodeInput.setText(rawValue.trim())
                Toast.makeText(this@AddProductActivity, "Barcode captured", Toast.LENGTH_SHORT).show()
                barcodeScannerController.stop()
            }

            override fun onScanError(message: String) {
                Log.e(SCANNER_TAG, "onScanError: $message")
            }

            override fun onScannerClosed() {
                Log.i(SCANNER_TAG, "onScannerClosed")
            }
        })
    }

    private fun playBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.e("Beep", "Failed to play beep", e)
        }
    }

    private fun createCardContainer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
        setPadding(p, p, p, p)
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = resources.getDimension(com.intuit.sdp.R.dimen._8sdp) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)) }
    }

    private fun createSectionHeader(title: String) = TextView(this).apply {
        text = title
        textSize = resources.getDimension(com.intuit.ssp.R.dimen._12ssp)
        setTextColor(Color.BLACK)
        setTypeface(null, Typeface.BOLD)
        setPadding(16, 8, 16, 24)
    }

    private fun createLabeledInput(label: String, hint: String, weight: Float = 0f) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (weight > 0) layoutParams = LinearLayout.LayoutParams(0, -2, weight)
        setPadding(16, 16, 16, 16)
        addView(TextView(context).apply { text = label; textSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp); setTextColor(Color.GRAY) })
        addView(EditText(context).apply { 
            this.hint = hint
            background = null
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
            setPadding(0, 8, 0, 8)
            id = 1002
        })
        addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(-1, 2); setBackgroundColor(Color.LTGRAY) })
    }

    private fun createBarcodeActionsRow(label: String, hint: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16, 16, 16, 16)
        addView(TextView(context).apply { text = label; textSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp); setTextColor(Color.GRAY) })
        
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        
        val input = EditText(context).apply { 
            this.hint = hint
            background = null
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
            setPadding(0, 8, 0, 8)
            id = 1001
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        
        val scanBtn = Button(context).apply { 
            text = "SCAN"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp)
            setTextColor(accentColor)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { openBarcodeScanner() }
        }
        
        val genBtn = Button(context).apply { 
            text = "GENERATE"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp)
            setTextColor(accentColor)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { generateBarcode() }
        }
        
        row.addView(input)
        row.addView(scanBtn)
        row.addView(genBtn)
        addView(row)
        addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(-1, 2); setBackgroundColor(Color.LTGRAY) })
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeScannerController.stop()
        toneGenerator?.release()
        cameraExecutor.shutdown()
    }
}
