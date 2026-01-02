package com.aesloxia.mindlocus

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aesloxia.mindlocus.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { PreferenceManager(this) }
    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var isRegistering = false

    private val qrScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("QR_CODE_DATA")?.let { handleTagScanned(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.isFirstRun) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupUI()
        updateStatus()
        checkPermissions()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setupUI() {
        binding.btnScanQR.setOnClickListener { qrScannerLauncher.launch(Intent(this, QrScannerActivity::class.java)) }
        binding.btnRegisterTag.setOnClickListener {
            isRegistering = true
            binding.statusTitle.text = "Waiting for Tag..."
            binding.statusSubtitle.text = "Tap NFC or scan QR to register"
            binding.statusCard.setStrokeColor(ContextCompat.getColorStateList(this, com.google.android.material.R.color.material_dynamic_primary50))
        }
        binding.btnDebugToggle.setOnClickListener { toggleBlocking() }
        binding.btnResetOnboarding.setOnClickListener { 
            prefs.isFirstRun = true
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }

        binding.switchUsageStats.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasUsageStatsPermission()) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isNotificationServiceEnabled()) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.switchBattery.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isIgnoringBatteryOptimizations()) startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }

    private fun updateStatus() {
        val isBlocking = prefs.isBlocking
        binding.statusTitle.text = if (isBlocking) "Blocking Active" else "Blocking Inactive"
        binding.statusSubtitle.text = if (isBlocking) "Your space is protected" else (prefs.registeredTag?.let { "Ready to lock" } ?: "Scan to register key")
        
        binding.switchUsageStats.isChecked = hasUsageStatsPermission()
        binding.switchOverlay.isChecked = Settings.canDrawOverlays(this)
        binding.switchNotifications.isChecked = isNotificationServiceEnabled()
        binding.switchBattery.isChecked = isIgnoringBatteryOptimizations()

        val color = if (isBlocking) com.google.android.material.R.color.material_dynamic_primary50 else com.google.android.material.R.color.material_dynamic_neutral90
        binding.statusCard.setStrokeColor(ContextCompat.getColorStateList(this, color))
    }

    private fun toggleBlocking() {
        val newStatus = !prefs.isBlocking
        prefs.isBlocking = newStatus
        val serviceIntent = Intent(this, AppBlockerService::class.java).apply {
            action = if (newStatus) "START_BLOCKING" else "STOP_BLOCKING"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        Toast.makeText(this, "Focus Mode ${if (newStatus) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
                   else appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isIgnoringBatteryOptimizations() = (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, NotificationBlockerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun checkPermissions() {
        if (!hasUsageStatsPermission() || !Settings.canDrawOverlays(this) || !isIgnoringBatteryOptimizations() || !isNotificationServiceEnabled()) {
            Toast.makeText(this, "Grant all permissions for full focus protection", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            updateStatus()
            nfcAdapter?.enableForegroundDispatch(this, null, null, null)
        }
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
        if (isRegistering) {
            prefs.registeredTag = tagId
            isRegistering = false
            Toast.makeText(this, "Tag Registered!", Toast.LENGTH_SHORT).show()
            updateStatus()
        } else if (tagId == prefs.registeredTag) {
            toggleBlocking()
        } else {
            Toast.makeText(this, "Unknown Tag", Toast.LENGTH_SHORT).show()
        }
    }
}
