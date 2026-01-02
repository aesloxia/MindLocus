package com.aesloxia.mindlocus

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aesloxia.mindlocus.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val adapter by lazy { AppAdapter() }
    private val prefs by lazy { PreferenceManager(this) }
    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }

    private val qrScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("QR_CODE_DATA")?.let { handleTagScanned(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStep1() // Permissions
        setupStep2() // App Selection
        setupStep3() // Tag Registration

        binding.btnNext.setOnClickListener { handleNext() }
        binding.btnBack.setOnClickListener { handleBack() }
        binding.btnSkip.setOnClickListener { handleSkip() }
        
        updateNavigationButtons()
    }

    private fun setupStep1() {
        binding.switchUsageStats.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasUsageStatsPermission()) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isNotificationServiceEnabled()) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        binding.switchAdmin.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isAdminEnabled()) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@OnboardingActivity, UninstallProtectionReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_description))
                }
                startActivity(intent)
            }
        }
        binding.switchBattery.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isIgnoringBatteryOptimizations()) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
    }

    private fun isIgnoringBatteryOptimizations() = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, NotificationBlockerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun isAdminEnabled(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val cn = ComponentName(this, UninstallProtectionReceiver::class.java)
        return dpm.isAdminActive(cn)
    }

    private fun setupStep2() {
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
        lifecycleScope.launch {
            adapter.setApps(AppUtils.getLockableApps(this@OnboardingActivity, prefs.blockedApps))
        }
    }

    private fun setupStep3() {
        binding.btnScanQR.setOnClickListener { qrScannerLauncher.launch(Intent(this, QrScannerActivity::class.java)) }
    }

    private fun handleNext() {
        when (binding.viewFlipper.displayedChild) {
            0 -> moveNext(50, "Continue")
            1 -> if (hasUsageStatsPermission() && Settings.canDrawOverlays(this) && isNotificationServiceEnabled()) moveNext(75, "Continue")
                 else Toast.makeText(this, "Required permissions missing (Usage, Overlay, and Notification)", Toast.LENGTH_SHORT).show()
            2 -> { saveSelectedApps(); moveNext(100, "Finish") }
            3 -> finishOnboarding()
        }
    }

    private fun moveNext(progress: Int, buttonText: String) {
        binding.viewFlipper.showNext()
        binding.onboardingProgress.progress = progress
        binding.btnNext.text = buttonText
        updateNavigationButtons()
    }

    private fun handleBack() {
        if (binding.viewFlipper.displayedChild > 0) {
            binding.viewFlipper.showPrevious()
            val (progress, text) = when (binding.viewFlipper.displayedChild) {
                0 -> 25 to "Get Started"
                1 -> 50 to "Continue"
                2 -> 75 to "Continue"
                else -> 100 to "Finish"
            }
            binding.onboardingProgress.progress = progress
            binding.btnNext.text = text
            updateNavigationButtons()
        }
    }

    private fun handleSkip() {
        if (binding.viewFlipper.displayedChild == 2) moveNext(100, "Finish")
        else if (binding.viewFlipper.displayedChild == 3) finishOnboarding()
    }

    private fun updateNavigationButtons() {
        val step = binding.viewFlipper.displayedChild
        binding.btnBack.visibility = if (step > 0) View.VISIBLE else View.GONE
        binding.btnSkip.visibility = if (step == 2 || step == 3) View.VISIBLE else View.GONE
    }

    private fun saveSelectedApps() { prefs.blockedApps = adapter.getSelectedPackages() }

    private fun finishOnboarding() {
        prefs.isFirstRun = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onResume() {
        super.onResume()
        binding.switchUsageStats.isChecked = hasUsageStatsPermission()
        binding.switchOverlay.isChecked = Settings.canDrawOverlays(this)
        binding.switchNotifications.isChecked = isNotificationServiceEnabled()
        binding.switchAdmin.isChecked = isAdminEnabled()
        binding.switchBattery.isChecked = isIgnoringBatteryOptimizations()
        nfcAdapter?.enableForegroundDispatch(this, null, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action || NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { handleTagScanned(it.id.joinToString("") { b -> "%02x".format(b) }) }
        }
    }

    private fun handleTagScanned(tagId: String) {
        if (binding.viewFlipper.displayedChild == 3) {
            prefs.registeredTag = tagId
            binding.ivTagIcon.setImageResource(android.R.drawable.checkbox_on_background)
            binding.ivTagIcon.imageTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            binding.tvTagTitle.text = "Tag Registered!"
            binding.tvTagSubtitle.text = "Ready to start focusing."
        }
    }
}
