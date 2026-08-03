package com.example.pos

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import com.example.pos.AppConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AboutSupportActivity : ComponentActivity() {

    private val topBarColor = Color.parseColor("#101a24")
    private val backgroundColor = Color.parseColor("#f0f2f5")
    private val accentColor = Color.parseColor("#00a3e0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val backBtn = TextView(this).apply {
            text = "←"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._18ssp)
            setTextColor(Color.WHITE)
            setPadding(0, 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), 0)
            setOnClickListener { finish() }
        }

        val titleText = TextView(this).apply {
            text = "About & Support"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._14ssp)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        topBar.addView(backBtn)
        topBar.addView(titleText)
        mainLayout.addView(topBar)

        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, -1) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
            setPadding(p, p, p, p)
        }

        // --- APP INFO SECTION ---
        val appInfoCard = createCardContainer()
        val logoIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_dialog_info) // Placeholder logo
            layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._60sdp), resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._60sdp)).apply {
                gravity = Gravity.CENTER
                setMargins(0, 0, 0, 16)
            }
        }
        val appName = TextView(this).apply {
            text = "Nova POS"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._16ssp)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
        }
        val versionInfo = getAppVersionInfo()
        val appVersion = TextView(this).apply {
            text = "Version: ${versionInfo.first} (${versionInfo.second})"
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._10ssp)
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        }
        val companyName = TextView(this).apply {
            text = AppConfig.COMPANY_NAME
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 0)
        }

        appInfoCard.addView(logoIcon)
        appInfoCard.addView(appName)
        appInfoCard.addView(appVersion)
        appInfoCard.addView(companyName)
        content.addView(appInfoCard)

        // --- SUPPORT SECTION ---
        content.addView(createSectionHeader("Contact Support"))
        val supportCard = createCardContainer()
        
        supportCard.addView(createSupportAction("Call Support (Zamtel)", AppConfig.SUPPORT_PHONE_1) { dialNumber(AppConfig.SUPPORT_PHONE_1) })
        supportCard.addView(createDivider())
        supportCard.addView(createSupportAction("Call Support (Airtel)", AppConfig.SUPPORT_PHONE_2) { dialNumber(AppConfig.SUPPORT_PHONE_2) })
        supportCard.addView(createDivider())
        supportCard.addView(createSupportAction("WhatsApp Business", AppConfig.WHATSAPP_NUMBER) { openWhatsApp(AppConfig.WHATSAPP_NUMBER) })
        supportCard.addView(createDivider())
        supportCard.addView(createSupportAction("Email Us", AppConfig.SUPPORT_EMAIL) { sendEmail(AppConfig.SUPPORT_EMAIL) })
        supportCard.addView(createDivider())
        supportCard.addView(createSupportAction("Visit Website", AppConfig.WEBSITE_URL) { openUrl(AppConfig.WEBSITE_URL) })
        
        content.addView(supportCard)

        // --- ACTIONS SECTION ---
        content.addView(createSectionHeader("Help & Feedback"))
        val actionsCard = createCardContainer()
        
        actionsCard.addView(createSupportAction("Report a Bug", "Found an issue? Let us know.") { reportBug(versionInfo.first) })
        actionsCard.addView(createDivider())
        actionsCard.addView(createSupportAction("Request a Feature", "Suggest a new improvement.") { requestFeature(versionInfo.first) })
        actionsCard.addView(createDivider())
        actionsCard.addView(createSupportAction("Check for Updates", "Stay up to date.") { checkForUpdates() })
        
        content.addView(actionsCard)

        // --- LEGAL SECTION ---
        content.addView(createSectionHeader("Legal"))
        val legalCard = createCardContainer()
        legalCard.addView(createSupportAction("Privacy Policy", "View our privacy practices") { showLegalDialog("Privacy Policy", AppConfig.PRIVACY_POLICY_CONTENT) })
        legalCard.addView(createDivider())
        legalCard.addView(createSupportAction("Terms & Conditions", "View our terms of service") { showLegalDialog("Terms & Conditions", AppConfig.TERMS_CONDITIONS_CONTENT) })
        content.addView(legalCard)

        // --- COPYRIGHT ---
        val copyright = TextView(this).apply {
            text = AppConfig.COPYRIGHT_TEXT
            textSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp)
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp), 0, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._24sdp))
        }
        content.addView(copyright)

        scroll.addView(content)
        mainLayout.addView(scroll)
        setContentView(mainLayout)
    }

    private fun getAppVersionInfo(): Pair<String, String> {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            val versionName = packageInfo.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
            Pair(versionName ?: "1.0", versionCode)
        } catch (e: Exception) {
            Pair("1.0", "1")
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
        textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp)
        setTextColor(accentColor)
        setTypeface(null, Typeface.BOLD)
        setPadding(16, 8, 16, 8)
    }

    private fun createSupportAction(title: String, sub: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16, 24, 16, 24)
        isClickable = true
        setOnClickListener { onClick() }
        addView(TextView(context).apply { text = title; textSize = resources.getDimension(com.intuit.ssp.R.dimen._11ssp); setTextColor(Color.BLACK); setTypeface(null, Typeface.BOLD) })
        addView(TextView(context).apply { text = sub; textSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp); setTextColor(Color.GRAY) })
    }

    private fun createDivider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(-1, 1)
        setBackgroundColor(Color.LTGRAY)
    }

    private fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        startActivity(intent)
    }

    private fun openWhatsApp(number: String) {
        val url = "https://wa.me/${number.replace(" ", "").replace("-", "")}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun sendEmail(email: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
        startActivity(intent)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun reportBug(version: String) {
        val body = """
            User Name: 
            Business Name: 
            Device Model: ${Build.MODEL}
            Android Version: ${Build.VERSION.RELEASE}
            App Version: $version
            
            Steps to Reproduce:
            1. 
            
            Expected Behavior:
            
            Actual Behavior:
            
            Additional Notes:
        """.trimIndent()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AppConfig.SUPPORT_EMAIL}")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Bug Report - Nova POS v$version")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(intent)
    }

    private fun requestFeature(version: String) {
        val body = """
            Feature Description:
            
            Business Need:
            
            Expected Behavior:
            
            Additional Comments:
        """.trimIndent()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AppConfig.SUPPORT_EMAIL}")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Feature Request - Nova POS v$version")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(intent)
    }

    private fun checkForUpdates() {
        NovaUpdateEngine(this).checkForUpdate { url ->
            if (url != null) {
                NovaUpdateEngine(this).checkUpdate {}
            } else {
                Toast.makeText(this, "You are using the latest version.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLegalDialog(title: String, content: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("Close", null)
            .show()
    }
}
