package com.example.pos.barcode

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX + ML Kit barcode scanner with lifecycle-safe cleanup and stage logging.
 */
class BarcodeScannerController(
    private val activity: ComponentActivity,
    private val cameraExecutor: ExecutorService,
    private val logTag: String = TAG
) {
    interface Listener {
        /** Called on the main thread when ML Kit returns a barcode value. */
        fun onBarcodeDetected(rawValue: String, formatName: String)

        /** Called on the main thread when scanning cannot continue. */
        fun onScanError(message: String)

        /** Called on the main thread when the scanner UI is closed. */
        fun onScannerClosed()
    }

    private var dialog: Dialog? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var barcodeScanner: BarcodeScanner? = null
    private val isProcessingFrame = AtomicBoolean(false)
    private val hasDeliveredResult = AtomicBoolean(false)

    fun start(listener: Listener) {
        Log.i(logTag, "start: opening scanner UI")
        stop()

        val scannerDialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog = scannerDialog

        val container = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val previewView = PreviewView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        val overlay = ScannerOverlayView(activity)
        val hint = TextView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = 48
            }
            text = "Point camera at barcode"
            setTextColor(Color.WHITE)
            textSize = 16f
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }

        container.addView(previewView)
        container.addView(overlay)
        container.addView(hint)
        scannerDialog.setContentView(container)

        scannerDialog.setOnDismissListener {
            Log.i(logTag, "dialog dismissed -> releasing camera")
            releaseCamera()
            listener.onScannerClosed()
        }

        scannerDialog.show()
        scannerDialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        Log.d(logTag, "start: requesting ProcessCameraProvider")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                bindCamera(provider, previewView, listener)
            } catch (e: Exception) {
                Log.e(logTag, "start: ProcessCameraProvider failed", e)
                listener.onScanError("Camera initialization failed: ${e.localizedMessage}")
                scannerDialog.dismiss()
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    fun stop() {
        Log.i(logTag, "stop: closing scanner")
        dialog?.dismiss()
        dialog = null
        releaseCamera()
    }

    fun resetDetection() {
        Log.d(logTag, "resetDetection: ready for next barcode")
        hasDeliveredResult.set(false)
        isProcessingFrame.set(false)
    }

    private fun releaseCamera() {
        try {
            cameraProvider?.unbindAll()
            Log.d(logTag, "releaseCamera: unbound all use cases")
        } catch (e: Exception) {
            Log.e(logTag, "releaseCamera: unbind failed", e)
        }
        cameraProvider = null

        try {
            barcodeScanner?.close()
            Log.d(logTag, "releaseCamera: ML Kit scanner closed")
        } catch (e: Exception) {
            Log.e(logTag, "releaseCamera: scanner close failed", e)
        }
        barcodeScanner = null

        hasDeliveredResult.set(false)
        isProcessingFrame.set(false)
    }

    @OptIn(ExperimentalGetImage::class)
    private fun bindCamera(
        provider: ProcessCameraProvider,
        previewView: PreviewView,
        listener: Listener
    ) {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()

        val scanner = BarcodeScanning.getClient(options)
        barcodeScanner = scanner

        val preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
                Log.d(logTag, "bindCamera: preview use case created")
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            if (hasDeliveredResult.get()) {
                imageProxy.close()
                return@setAnalyzer
            }

            if (!isProcessingFrame.compareAndSet(false, true)) {
                imageProxy.close()
                return@setAnalyzer
            }

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                Log.w(logTag, "analyzer: frame has null image")
                isProcessingFrame.set(false)
                imageProxy.close()
                return@setAnalyzer
            }

            val rotation = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotation)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isEmpty()) {
                        Log.v(logTag, "analyzer: ML Kit returned 0 barcodes")
                        return@addOnSuccessListener
                    }

                    if (hasDeliveredResult.get()) return@addOnSuccessListener

                    for (barcode in barcodes) {
                        Log.d(
                            logTag,
                            "analyzer: detected format=${formatName(barcode.format)} " +
                                "raw='${barcode.rawValue}' display='${barcode.displayValue}'"
                        )
                    }

                    val first = barcodes.first()
                    val rawValue = first.rawValue?.takeIf { it.isNotBlank() }
                        ?: first.displayValue?.takeIf { it.isNotBlank() }
                        ?: ""

                    if (rawValue.isEmpty()) {
                        Log.w(logTag, "analyzer: barcode had empty raw/display value")
                        return@addOnSuccessListener
                    }

                    if (!hasDeliveredResult.compareAndSet(false, true)) return@addOnSuccessListener

                    Log.i(logTag, "analyzer: delivering barcode '$rawValue' to listener")
                    activity.runOnUiThread {
                        listener.onBarcodeDetected(rawValue, formatName(first.format))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(logTag, "analyzer: ML Kit process failed", e)
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Scanner error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        listener.onScanError(e.localizedMessage ?: "ML Kit failure")
                    }
                }
                .addOnCompleteListener {
                    isProcessingFrame.set(false)
                    imageProxy.close()
                }
        }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                activity,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            Log.i(logTag, "bindCamera: bound preview + analysis to activity lifecycle")
        } catch (e: Exception) {
            Log.e(logTag, "bindCamera: bindToLifecycle failed", e)
            activity.runOnUiThread {
                listener.onScanError("Camera bind failed: ${e.localizedMessage}")
                dialog?.dismiss()
            }
        }
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        else -> "UNKNOWN($format)"
    }

    private class ScannerOverlayView(context: Context) : View(context) {
        private val maskPaint = Paint().apply { color = Color.parseColor("#60000000") }
        private val framePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val linePaint = Paint().apply {
            color = Color.RED
            strokeWidth = 4f
        }
        private var linePosition = 0f
        private var goingDown = true

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val rectWidth = w * 0.8f
            val rectHeight = rectWidth * 0.6f
            val left = (w - rectWidth) / 2f
            val top = (h - rectHeight) / 2f
            val right = left + rectWidth
            val bottom = top + rectHeight

            canvas.drawRect(0f, 0f, w, top, maskPaint)
            canvas.drawRect(0f, top, left, bottom, maskPaint)
            canvas.drawRect(right, top, w, bottom, maskPaint)
            canvas.drawRect(0f, bottom, w, h, maskPaint)
            canvas.drawRect(left, top, right, bottom, framePaint)

            if (linePosition < top || linePosition > bottom) linePosition = top
            canvas.drawLine(left + 10, linePosition, right - 10, linePosition, linePaint)

            if (goingDown) {
                linePosition += 10f
                if (linePosition >= bottom - 10) goingDown = false
            } else {
                linePosition -= 10f
                if (linePosition <= top + 10) goingDown = true
            }
            invalidate()
        }
    }

    companion object {
        private const val TAG = "BarcodeScanner"
    }
}
