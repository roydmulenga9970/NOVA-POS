package com.example.pos.barcode

import android.util.Log

/**
 * Normalizes and compares barcodes across common retail formats (UPC-A vs EAN-13, whitespace, etc.).
 */
object BarcodeNormalizer {

    private const val TAG = "BarcodeNormalizer"

    fun normalize(raw: String): String = raw.trim()

    /**
     * Returns lookup candidates for a scanned value, including UPC-A ↔ EAN-13 variants.
     */
    fun candidates(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        val result = linkedSetOf(trimmed)
        val digitsOnly = trimmed.filter { it.isDigit() }

        if (digitsOnly.isNotEmpty()) {
            result.add(digitsOnly)
        }

        when (digitsOnly.length) {
            12 -> result.add("0$digitsOnly")
            13 -> {
                if (digitsOnly.startsWith("0")) {
                    result.add(digitsOnly.drop(1))
                }
            }
        }

        Log.d(TAG, "candidates(raw='$raw') -> $result")
        return result.toList()
    }

    fun matches(stored: String, scanned: String): Boolean {
        val storedSet = candidates(stored).toSet()
        val scannedSet = candidates(scanned).toSet()
        val match = storedSet.intersect(scannedSet).isNotEmpty()
        Log.d(TAG, "matches(stored='$stored', scanned='$scanned') -> $match")
        return match
    }
}
