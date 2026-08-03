package com.example.pos.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.*
import com.example.pos.data.AppDatabase
import com.example.pos.data.Product
import com.example.pos.data.Sale
import com.example.pos.data.SaleItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure()
        val db = AppDatabase.getDatabase(applicationContext)
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(userId)

        return try {
            Log.d("SyncWorker", "Starting background sync...")

            // 1. PUSH Products
            val unsyncedProducts = db.productDao().getAllProducts().filter { !it.isSynced }
            for (product in unsyncedProducts) {
                val productMap = hashMapOf(
                    "name" to product.name,
                    "barcode" to product.barcode,
                    "description" to product.description,
                    "sellingPrice" to product.sellingPrice,
                    "costPrice" to product.costPrice,
                    "currentInventory" to product.currentInventory,
                    "lastUpdated" to product.lastUpdated
                )
                userDoc.collection("products").document(product.barcode).set(productMap).await()
                db.productDao().update(product.copy(isSynced = true))
            }

            // 2. PUSH Sales
            val unsyncedSales = db.saleDao().getAllSales().filter { !it.isSynced }
            for (sale in unsyncedSales) {
                val items = db.saleDao().getItemsForSale(sale.id)
                val saleDocId = "sale_${sale.timestamp}" 
                val saleMap = hashMapOf(
                    "timestamp" to sale.timestamp,
                    "totalAmount" to sale.totalAmount,
                    "amountReceived" to sale.amountReceived,
                    "changeGiven" to sale.changeGiven,
                    "isVoided" to sale.isVoided,
                    "items" to items.map {
                        hashMapOf(
                            "productId" to it.productId,
                            "productName" to it.productName,
                            "quantity" to it.quantity,
                            "unitPrice" to it.unitPrice,
                            "subtotal" to it.subtotal
                        )
                    }
                )
                userDoc.collection("sales").document(saleDocId).set(saleMap).await()
                db.saleDao().markSaleSynced(sale.id)
            }

            // 3. PUSH Settings (Admin Name, PINs)
            pushSettings(userId)

            // 4. PULL all data
            downloadDataInternal(applicationContext, db, userDoc)

            Log.d("SyncWorker", "Sync completed successfully.")
            showSyncNotification(applicationContext)
            
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Push sync failed", e)
            Result.retry()
        }
    }

    private suspend fun pushSettings(userId: String) {
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(userId)

        // Admin Prefs
        val adminPrefs = applicationContext.getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE)
        val adminMap = hashMapOf(
            "admin_name" to adminPrefs.getString("admin_name", "Admin"),
            "admin_pin" to adminPrefs.getString("admin_pin", "1234")
        )
        userDoc.collection("settings").document("admin").set(adminMap).await()

        // App Prefs (Access Password)
        val appPrefs = applicationContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val appMap = hashMapOf(
            "app_access_password" to appPrefs.getString("app_access_password", "1234")
        )
        userDoc.collection("settings").document("app").set(appMap).await()

        // Cashier accounts
        val cashierPrefs = applicationContext.getSharedPreferences("CashierPrefs", Context.MODE_PRIVATE)
        val allCashiers = cashierPrefs.all
        if (allCashiers.isNotEmpty()) {
            userDoc.collection("settings").document("cashiers").set(allCashiers).await()
        }
    }

    private fun showSyncNotification(context: Context) {
        val channelId = "sync_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Data Synchronization", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Nova POS")
            .setContentText("Cloud data synchronization complete.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    companion object {
        private var productListener: ListenerRegistration? = null

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmediateSyncTask",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "PeriodicSyncTask",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        suspend fun downloadData(context: Context) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = AppDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()
            val userDoc = firestore.collection("users").document(userId)
            
            downloadDataInternal(context, db, userDoc)
        }

        private suspend fun downloadDataInternal(context: Context, db: AppDatabase, userDoc: com.google.firebase.firestore.DocumentReference) {
            try {
                // 1. PULL Products
                val productsSnapshot = userDoc.collection("products").get().await()
                for (doc in productsSnapshot.documents) {
                    val barcode = doc.getString("barcode") ?: doc.id
                    val remoteLastUpdated = doc.getLong("lastUpdated") ?: 0L
                    val local = db.productDao().getProductByBarcode(barcode)
                    
                    if (local == null || (local.isSynced && remoteLastUpdated > local.lastUpdated)) {
                        val remoteProduct = Product(
                            name = doc.getString("name") ?: "",
                            barcode = barcode,
                            description = doc.getString("description") ?: "",
                            sellingPrice = doc.getDouble("sellingPrice") ?: 0.0,
                            costPrice = doc.getDouble("costPrice") ?: 0.0,
                            currentInventory = doc.getLong("currentInventory")?.toInt() ?: 0,
                            isSynced = true,
                            lastUpdated = remoteLastUpdated
                        )
                        db.productDao().insert(remoteProduct)
                    }
                }

                // 2. PULL Sales
                val salesSnapshot = userDoc.collection("sales").get().await()
                val localSales = db.saleDao().getAllSales()
                for (doc in salesSnapshot.documents) {
                    val remoteTimestamp = doc.getLong("timestamp") ?: 0L
                    if (localSales.none { it.timestamp == remoteTimestamp }) {
                        val remoteSale = Sale(
                            timestamp = remoteTimestamp,
                            totalAmount = doc.getDouble("totalAmount") ?: 0.0,
                            amountReceived = doc.getDouble("amountReceived") ?: 0.0,
                            changeGiven = doc.getDouble("changeGiven") ?: 0.0,
                            isVoided = doc.getBoolean("isVoided") ?: false,
                            isSynced = true
                        )
                        val newLocalId = db.saleDao().insertSale(remoteSale).toInt()
                        val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                        val saleItems = items.map {
                            SaleItem(
                                saleId = newLocalId,
                                productId = (it["productId"] as? Long)?.toInt() ?: 0,
                                productName = it["productName"] as? String ?: "",
                                quantity = (it["quantity"] as? Long)?.toInt() ?: 0,
                                unitPrice = (it["unitPrice"] as? Double) ?: 0.0,
                                subtotal = (it["subtotal"] as? Double) ?: 0.0
                            )
                        }
                        db.saleDao().insertSaleItems(saleItems)
                    }
                }

                // 3. PULL Settings
                val adminDoc = userDoc.collection("settings").document("admin").get().await()
                if (adminDoc.exists()) {
                    context.getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE).edit {
                        putString("admin_name", adminDoc.getString("admin_name"))
                        putString("admin_pin", adminDoc.getString("admin_pin"))
                    }
                }

                val appDoc = userDoc.collection("settings").document("app").get().await()
                if (appDoc.exists()) {
                    context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit {
                        putString("app_access_password", appDoc.getString("app_access_password"))
                    }
                }

                val cashierDoc = userDoc.collection("settings").document("cashiers").get().await()
                if (cashierDoc.exists()) {
                    context.getSharedPreferences("CashierPrefs", Context.MODE_PRIVATE).edit {
                        cashierDoc.data?.forEach { (name, pin) ->
                            putString(name, pin.toString())
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("SyncWorker", "Download reconciliation failed", e)
            }
        }

        fun startRealtimeSync(context: Context) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = AppDatabase.getDatabase(context)
            val userDoc = FirebaseFirestore.getInstance().collection("users").document(userId)

            productListener = userDoc.collection("products").addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                snapshots?.documentChanges?.forEach { dc ->
                    val doc = dc.document
                    val barcode = doc.getString("barcode") ?: doc.id
                    val remoteLastUpdated = doc.getLong("lastUpdated") ?: 0L
                    CoroutineScope(Dispatchers.IO).launch {
                        val local = db.productDao().getProductByBarcode(barcode)
                        if (local == null || (local.isSynced && remoteLastUpdated > local.lastUpdated)) {
                            val remoteProduct = Product(
                                name = doc.getString("name") ?: "",
                                barcode = barcode,
                                description = doc.getString("description") ?: "",
                                sellingPrice = doc.getDouble("sellingPrice") ?: 0.0,
                                costPrice = doc.getDouble("costPrice") ?: 0.0,
                                currentInventory = doc.getLong("currentInventory")?.toInt() ?: 0,
                                isSynced = true,
                                lastUpdated = remoteLastUpdated
                            )
                            db.productDao().insert(remoteProduct)
                        }
                    }
                }
            }
        }

        fun stopRealtimeSync() {
            productListener?.remove()
            productListener = null
        }
    }
}
