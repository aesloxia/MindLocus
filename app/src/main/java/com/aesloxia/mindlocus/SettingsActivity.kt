package com.aesloxia.mindlocus

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aesloxia.mindlocus.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val adapter by lazy { AppAdapter() }
    private val prefs by lazy { PreferenceManager(this) }
    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var isWaitingForTag = false

    private val qrScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("QR_CODE_DATA")?.let { handleTagScanned(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupTagManagement()
        setupAppList()
    }

    private fun setupTagManagement() {
        binding.tvCurrentTag.text = "Current Key: ${prefs.registeredTag ?: "None"}"

        binding.btnChangeTag.setOnClickListener {
            isWaitingForTag = true
            Toast.makeText(this, "Tap your NFC tag now", Toast.LENGTH_SHORT).show()
        }

        binding.btnChangeQR.setOnClickListener {
            qrScannerLauncher.launch(Intent(this, QrScannerActivity::class.java))
        }
    }

    private fun setupAppList() {
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
        lifecycleScope.launch {
            adapter.setApps(AppUtils.getLockableApps(this@SettingsActivity, prefs.blockedApps))
        }
    }

    private fun saveBlockedApps() { prefs.blockedApps = adapter.getSelectedPackages() }

    override fun onPause() {
        super.onPause()
        saveBlockedApps()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, null, null, null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isWaitingForTag && (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action || NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action)) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { handleTagScanned(it.id.joinToString("") { b -> "%02x".format(b) }) }
            isWaitingForTag = false
        }
    }

    private fun handleTagScanned(tagId: String) {
        prefs.registeredTag = tagId
        binding.tvCurrentTag.text = "Current Key: $tagId"
        Toast.makeText(this, "Key updated successfully!", Toast.LENGTH_SHORT).show()
    }
}
