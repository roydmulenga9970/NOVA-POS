package com.example.pos

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.ktx.Firebase
import java.io.File

/**
 * NovaUpdateEngine handles the remote update check using Firebase Remote Config and DownloadManager.
 */
class NovaUpdateEngine(private val context: Context) {

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        Firebase.remoteConfig
    }

    init {
        try {
            FirebaseApp.initializeApp(context)
        } catch (e: Exception) {
            Log.e("UpdateEngine", "Firebase initialization failed", e)
        }

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0 
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        val defaults = mapOf(
            "latest_version_code" to BuildConfig.VERSION_CODE.toLong(),
            "update_url" to ""
        )
        remoteConfig.setDefaultsAsync(defaults)
    }

    /**
     * Checks for updates and executes a callback with the update URL if found.
     */
    fun checkForUpdate(callback: (String?) -> Unit) {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val latestVersionCode = remoteConfig.getLong("latest_version_code")
                    val updateUrl = remoteConfig.getString("update_url")
                    val currentVersionCode = getAppVersionCode()
                    
                    if (latestVersionCode > currentVersionCode && updateUrl.isNotEmpty()) {
                        callback(updateUrl)
                    } else {
                        callback(null)
                    }
                } else {
                    callback(null)
                }
            }
    }

    fun checkUpdate(onProceed: () -> Unit) {
        checkForUpdate { url ->
            if (url != null) {
                showUpdateDialog(url, onProceed)
            } else {
                onProceed()
            }
        }
    }

    private fun getAppVersionCode(): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            BuildConfig.VERSION_CODE.toLong()
        }
    }

    private fun showUpdateDialog(updateUrl: String, onDismiss: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle("New Update Available")
            .setMessage("A newer version of Nova POS is available. Please update to get the latest features.")
            .setCancelable(true)
            .setOnCancelListener { onDismiss() }
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstall(updateUrl)
                Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                onDismiss() // Allow entering app while downloading
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
                onDismiss() // Proceed to app
            }
            .show()
    }

    fun downloadAndInstall(apkUrl: String) {
        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "nova_update.apk")
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Nova Update")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destination))

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    promptInstall(destination)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(onComplete, filter)
        }
    }

    private fun promptInstall(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
