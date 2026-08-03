package com.example.pos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import java.util.Locale

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: Product)

    @Update
    suspend fun update(product: Product)

    @Query("UPDATE products SET currentInventory = currentInventory - :quantity, isSynced = 0, lastUpdated = :timestamp WHERE id = :productId")
    suspend fun decrementInventory(productId: Int, quantity: Int, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM products")
    suspend fun getAllProducts(): List<Product>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Int): Product?

    @Query("SELECT * FROM products WHERE currentInventory <= 5")
    suspend fun getLowStockProducts(): List<Product>
}

data class StockShortage(val productName: String, val available: Int, val requested: Int)
class InsufficientStockException(val shortages: List<StockShortage>) : Exception("Insufficient stock")

@Dao
abstract class SaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSale(sale: Sale): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSaleItems(items: List<SaleItem>)

    @Query("SELECT currentInventory FROM products WHERE id = :productId")
    abstract suspend fun getProductStock(productId: Int): Int

    @Query("UPDATE products SET currentInventory = currentInventory - :quantity, isSynced = 0, lastUpdated = :timestamp WHERE id = :productId")
    abstract suspend fun decrementProductInventory(productId: Int, quantity: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE products SET currentInventory = currentInventory + :quantity, isSynced = 0, lastUpdated = :timestamp WHERE id = :productId")
    abstract suspend fun incrementProductInventory(productId: Int, quantity: Int, timestamp: Long = System.currentTimeMillis())

    @Transaction
    open suspend fun executeSaleTransaction(sale: Sale, items: List<SaleItem>) {
        val shortages = mutableListOf<StockShortage>()
        for (item in items) {
            val available = getProductStock(item.productId)
            if (available < item.quantity) {
                shortages.add(StockShortage(item.productName, available, item.quantity))
            }
        }

        if (shortages.isNotEmpty()) {
            throw InsufficientStockException(shortages)
        }

        val saleId = insertSale(sale).toInt()
        val itemsWithSaleId = items.map { it.copy(saleId = saleId) }
        insertSaleItems(itemsWithSaleId)
        for (item in itemsWithSaleId) {
            decrementProductInventory(item.productId, item.quantity)
        }
    }

    @Transaction
    open suspend fun executeVoidTransaction(saleId: Int) {
        voidSale(saleId)
        val items = getItemsForSale(saleId)
        for (item in items) {
            incrementProductInventory(item.productId, item.quantity)
        }
    }

    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    abstract suspend fun getAllSales(): List<Sale>

    @Query("SELECT * FROM sales ORDER BY timestamp DESC LIMIT :limit")
    abstract suspend fun getRecentSales(limit: Int): List<Sale>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    abstract suspend fun getItemsForSale(saleId: Int): List<SaleItem>

    @Query("UPDATE sales SET isVoided = 1 WHERE id = :saleId")
    abstract suspend fun voidSale(saleId: Int)

    @Query("UPDATE sales SET isSynced = 1 WHERE id = :saleId")
    abstract suspend fun markSaleSynced(saleId: Int)

    @Query("SELECT SUM(totalAmount) FROM sales WHERE timestamp >= :startOfDay AND isVoided = 0")
    abstract suspend fun getTodayNetSales(startOfDay: Long): Double?

    @Query("SELECT COUNT(*) FROM sales WHERE timestamp >= :startOfDay AND isVoided = 0")
    abstract suspend fun getTodayTransactionCount(startOfDay: Long): Int

    @Query("SELECT productName, SUM(quantity) as totalQty FROM sale_items WHERE saleId IN (SELECT id FROM sales WHERE isVoided = 0) GROUP BY productId ORDER BY totalQty DESC LIMIT 5")
    abstract suspend fun getTopSellers(): List<TopSeller>

    @Query("SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch') AS INTEGER) as hour, COUNT(*) as count FROM sales WHERE isVoided = 0 GROUP BY hour ORDER BY count DESC")
    abstract suspend fun getPeakHours(): List<PeakHour>
}

data class TopSeller(val productName: String, val totalQty: Int)
data class PeakHour(val hour: Int, val count: Int)
