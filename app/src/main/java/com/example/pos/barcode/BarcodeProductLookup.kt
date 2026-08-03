package com.example.pos.barcode

import android.content.Context
import android.util.Log
import com.example.pos.data.AppDatabase
import com.example.pos.data.Product

/**
 * Resolves a scanned barcode to a [Product] using Room with normalization fallbacks.
 */
object BarcodeProductLookup {

    private const val TAG = "BarcodeProductLookup"

    suspend fun findProduct(context: Context, rawBarcode: String): Product? {
        val normalized = BarcodeNormalizer.normalize(rawBarcode)
        if (normalized.isEmpty()) {
            Log.w(TAG, "findProduct: empty barcode after normalize")
            return null
        }

        val dao = AppDatabase.getDatabase(context).productDao()
        val candidates = BarcodeNormalizer.candidates(rawBarcode)

        Log.d(TAG, "findProduct: raw='$rawBarcode' candidates=$candidates")

        for (candidate in candidates) {
            val exact = dao.getProductByBarcode(candidate)
            if (exact != null) {
                Log.i(TAG, "findProduct: exact DB hit barcode='${exact.barcode}' id=${exact.id} name='${exact.name}'")
                return exact
            }
        }

        val allProducts = dao.getAllProducts()
        Log.d(TAG, "findProduct: no exact hit, scanning ${allProducts.size} products with fuzzy match")

        val fuzzy = allProducts.find { BarcodeNormalizer.matches(it.barcode, rawBarcode) }
        if (fuzzy != null) {
            Log.i(TAG, "findProduct: fuzzy hit stored='${fuzzy.barcode}' id=${fuzzy.id} name='${fuzzy.name}'")
        } else {
            Log.w(TAG, "findProduct: no product for '$rawBarcode' (checked ${candidates.size} candidates)")
        }
        return fuzzy
    }
}
