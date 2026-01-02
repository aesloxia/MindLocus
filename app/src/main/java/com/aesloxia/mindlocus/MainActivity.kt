package com.aesloxia.mindlocus

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
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
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aesloxia.mindlocus.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { PreferenceManager(this) }
    private val nfcAdapter by lazy { try { NfcAdapter.getDefaultAdapter(this) } catch (e: Exception) { null } }

    private val qrScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val qrCode = result.data?.getStringExtra("QR_CODE_DATA")
            qrCode?.let { handleTagScanned(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.isFirstRun) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setSupportActionBar(binding.toolbar)
            
            setupUI()
            updateStatus()
            checkPermissions()
        } catch (e: Exception) {
            // Handle initialization error
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            if (!prefs.isBlocking) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                Toast.makeText(this, "End focus session to access settings", Toast.LENGTH_SHORT).show()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setupUI() {
        binding.btnScanQR.setOnClickListener {
            qrScannerLauncher.launch(Intent(this, QrScannerActivity::class.java))
        }
        
        binding.btnDebugToggle.setOnClickListener { toggleBlocking() }
        
        binding.btnResetOnboarding.setOnClickListener { 
            prefs.isFirstRun = true
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }

        // Universal Permissions
        binding.switchUsageStats.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasUsageStatsPermission()) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isNotificationServiceEnabled()) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.switchBattery.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isIgnoringBatteryOptimizations()) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
        binding.switchAdmin.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isAdminEnabled()) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, UninstallProtectionReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_description))
                }
                startActivity(intent)
            }
        }

        // Manufacturer Specific UI
        setupManufacturerUI()
    }

    private fun setupManufacturerUI() {
        val brand = Build.MANUFACTURER.lowercase()
        binding.tvManufacturerTitle.text = "${ManufacturerUtils.getManufacturerLabel()} Fixes"
        
        // Expansion Logic
        binding.manufacturerHeader.setOnClickListener {
            val isVisible = binding.manufacturerContent.visibility == View.VISIBLE
            binding.manufacturerContent.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding.ivExpandArrow.rotation = if (isVisible) 0f else 180f
        }

        // Show relevant buttons based on brand
        if (brand.contains("xiaomi")) {
            binding.manufacturerCard.visibility = View.VISIBLE
            binding.btnFixAutostart.visibility = View.VISIBLE
            binding.btnFixPopups.visibility = View.VISIBLE
            binding.tvManufacturerInstructions.text = "MIUI requires manual activation of background features to ensure blocking reliability."
            
            binding.btnFixAutostart.setOnClickListener {
                try {
                    val intent = Intent().apply {
                        component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    openAppSettings()
                }
            }
            
            binding.btnFixPopups.setOnClickListener {
                try {
                    val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                        setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        putExtra("extra_pkgname", packageName)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    openAppSettings()
                }
            }
        } else if (brand.contains("samsung") || brand.contains("oppo") || brand.contains("huawei") || brand.contains("realme") || brand.contains("vivo")) {
            binding.manufacturerCard.visibility = View.VISIBLE
            binding.btnFixAutostart.visibility = View.VISIBLE
            binding.btnFixPopups.visibility = View.GONE
            binding.btnFixAutostart.text = "Open Battery/Startup Settings"
            binding.btnFixAutostart.setOnClickListener {
                try {
                    startActivity(ManufacturerUtils.getBackgroundSettingsIntent(this))
                } catch (e: Exception) {
                    openAppSettings()
                }
            }
        } else {
            binding.manufacturerCard.visibility = View.GONE
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun updateStatus() {
        if (!::binding.isInitialized) return

        val isBlocking = prefs.isBlocking
        val hasTags = prefs.registeredTags.isNotEmpty()

        binding.statusTitle.text = if (isBlocking) "Blocking Active" else "Blocking Inactive"
        binding.statusSubtitle.text = if (isBlocking) "Your space is protected" else (if (hasTags) "Ready to lock" else "Add keys in settings to start")
        
        binding.btnScanQR.visibility = if (hasTags) View.VISIBLE else View.GONE

        // Quietly update switches
        binding.switchUsageStats.setOnCheckedChangeListener(null)
        binding.switchOverlay.setOnCheckedChangeListener(null)
        binding.switchNotifications.setOnCheckedChangeListener(null)
        binding.switchAdmin.setOnCheckedChangeListener(null)
        binding.switchBattery.setOnCheckedChangeListener(null)

        binding.switchUsageStats.isChecked = hasUsageStatsPermission()
        binding.switchOverlay.isChecked = Settings.canDrawOverlays(this)
        binding.switchNotifications.isChecked = isNotificationServiceEnabled()
        binding.switchAdmin.isChecked = isAdminEnabled()
        binding.switchBattery.isChecked = isIgnoringBatteryOptimizations()

        setupUI()

        val colorRes = if (isBlocking) android.R.color.holo_blue_dark else android.R.color.darker_gray
        binding.statusCard.setStrokeColor(ContextCompat.getColor(this, colorRes))
    }

    private fun toggleBlocking() {
        val newStatus = !prefs.isBlocking
        prefs.isBlocking = newStatus
        val serviceIntent = Intent(this, AppBlockerService::class.java).apply {
            action = if (newStatus) "START_BLOCKING" else "STOP_BLOCKING"
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        } catch (e: Exception) {}
        updateStatus()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
                   else @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isIgnoringBatteryOptimizations() = (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, NotificationBlockerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun isAdminEnabled(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, UninstallProtectionReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    private fun checkPermissions() {
        if (!hasUsageStatsPermission() || !Settings.canDrawOverlays(this) || !isIgnoringBatteryOptimizations() || !isNotificationServiceEnabled() || !isAdminEnabled()) {
            Toast.makeText(this, "Permissions needed for full protection", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            updateStatus()
            enableNfcForegroundDispatch()
        }
    }

    private fun enableNfcForegroundDispatch() {
        try {
            val nfc = nfcAdapter ?: return
            if (!nfc.isEnabled) return
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            nfc.enableForegroundDispatch(this, pendingIntent, null, null)
        } catch (e: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { nfcAdapter?.disableForegroundDispatch(this) } catch (e: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action || NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                            else @Suppress("DEPRECATION") intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { handleTagScanned(it.id.joinToString("") { b -> "%02x".format(b) }) }
        }
    }

    private fun handleTagScanned(tagId: String) {
        if (prefs.registeredTags.contains(tagId)) toggleBlocking()
        else Toast.makeText(this, "Unknown Key", Toast.LENGTH_SHORT).show()
    }
}
