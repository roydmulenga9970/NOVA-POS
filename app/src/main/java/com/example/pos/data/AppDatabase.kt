package com.example.pos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Product::class, Sale::class, SaleItem::class, CashSession::class, CashTransaction::class], 
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun cashSessionDao(): CashSessionDao

    companion object Companion {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sessionId to sales table
                db.execSQL("ALTER TABLE sales ADD COLUMN sessionId INTEGER")
                
                // Ensure session tables exist (if not already created in previous dev steps)
                db.execSQL("CREATE TABLE IF NOT EXISTS `cash_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cashierName` TEXT NOT NULL, `openingTime` INTEGER NOT NULL, `closingTime` INTEGER, `openingCash` REAL NOT NULL, `expectedCash` REAL NOT NULL, `closingCash` REAL, `difference` REAL, `differenceReason` TEXT, `status` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `cash_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `reason` TEXT NOT NULL, `notes` TEXT, `timestamp` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cash_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cashierName` TEXT NOT NULL, `openingTime` INTEGER NOT NULL, `closingTime` INTEGER, `openingCash` REAL NOT NULL, `closingCash` REAL, `expectedCash` REAL NOT NULL, `difference` REAL, `differenceReason` TEXT, `status` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `cash_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `reason` TEXT NOT NULL, `notes` TEXT, `timestamp` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "products", "isSynced")) {
                    db.execSQL("ALTER TABLE products ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                }
                if (!columnExists(db, "products", "lastUpdated")) {
                    db.execSQL("ALTER TABLE products ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT " + System.currentTimeMillis())
                }
                if (!columnExists(db, "sales", "amountReceived")) {
                    db.execSQL("ALTER TABLE sales ADD COLUMN amountReceived REAL NOT NULL DEFAULT 0.0")
                }
                if (!columnExists(db, "sales", "changeGiven")) {
                    db.execSQL("ALTER TABLE sales ADD COLUMN changeGiven REAL NOT NULL DEFAULT 0.0")
                }
                if (!columnExists(db, "sales", "isSynced")) {
                    db.execSQL("ALTER TABLE sales ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                }
            }

            private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
                val cursor = db.query("PRAGMA table_info($tableName)")
                cursor.use {
                    val nameIndex = it.getColumnIndex("name")
                    if (nameIndex == -1) return false
                    while (it.moveToNext()) {
                        if (it.getString(nameIndex) == columnName) {
                            return true
                        }
                    }
                }
                return false
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val duplicates = mutableListOf<String>()
                db.query("SELECT barcode FROM products GROUP BY barcode HAVING COUNT(*) > 1").use { cursor ->
                    while (cursor.moveToNext()) duplicates.add(cursor.getString(0))
                }
                for (barcode in duplicates) {
                    var survivorId = -1
                    db.query("SELECT MAX(id) FROM products WHERE barcode = ?", arrayOf(barcode)).use { cursor ->
                        if (cursor.moveToFirst()) survivorId = cursor.getInt(0)
                    }
                    if (survivorId != -1) {
                        db.execSQL("UPDATE products SET currentInventory = (SELECT SUM(currentInventory) FROM products WHERE barcode = ?) WHERE id = ?", arrayOf(barcode, survivorId))
                        db.execSQL("UPDATE sale_items SET productId = ? WHERE productId IN (SELECT id FROM products WHERE barcode = ? AND id != ?)", arrayOf(survivorId, barcode, survivorId))
                        db.execSQL("DELETE FROM products WHERE barcode = ? AND id != ?", arrayOf(barcode, survivorId))
                    }
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS `products_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `barcode` TEXT NOT NULL, `description` TEXT NOT NULL, `sellingPrice` REAL NOT NULL, `costPrice` REAL NOT NULL, `currentInventory` INTEGER NOT NULL, `imagePath` TEXT, `isSynced` INTEGER NOT NULL DEFAULT 0, `lastUpdated` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO products_new (id, name, barcode, description, sellingPrice, costPrice, currentInventory, imagePath, isSynced, lastUpdated) SELECT id, name, barcode, description, sellingPrice, costPrice, currentInventory, imagePath, isSynced, lastUpdated FROM products")
                db.execSQL("DROP TABLE products")
                db.execSQL("ALTER TABLE products_new RENAME TO products")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
