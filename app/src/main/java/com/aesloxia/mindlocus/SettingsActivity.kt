package com.aesloxia.mindlocus

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private val tagAdapter by lazy { TagAdapter { tagId -> removeTag(tagId) } }
    private val prefs by lazy { PreferenceManager(this) }
    private val nfcAdapter by lazy { try { NfcAdapter.getDefaultAdapter(this) } catch (e: Exception) { null } }
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
        binding.rvTags.layoutManager = LinearLayoutManager(this)
        binding.rvTags.adapter = tagAdapter
        updateTagList()

        binding.btnRegisterTag.setOnClickListener {
            isWaitingForTag = true
            Toast.makeText(this, "Tap your NFC tag now", Toast.LENGTH_SHORT).show()
        }

        binding.btnRegisterQR.setOnClickListener {
            qrScannerLauncher.launch(Intent(this, QrScannerActivity::class.java))
        }
    }

    private fun updateTagList() {
        tagAdapter.setTags(prefs.registeredTags.toList())
    }

    private fun removeTag(tagId: String) {
        prefs.removeTag(tagId)
        updateTagList()
        Toast.makeText(this, "Key removed", Toast.LENGTH_SHORT).show()
    }

    private fun setupAppList() {
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
        
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lifecycleScope.launch {
            val apps = AppUtils.getLockableApps(this@SettingsActivity, prefs.blockedApps)
            adapter.setApps(apps)
        }
    }

    private fun saveBlockedApps() {
        if (::binding.isInitialized) {
            prefs.blockedApps = adapter.getSelectedPackages()
        }
    }

    override fun onPause() {
        super.onPause()
        saveBlockedApps()
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (e: Exception) { /* Hardware issue */ }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (nfcAdapter?.isEnabled == true) {
                val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.app.PendingIntent.FLAG_MUTABLE else 0
                )
                nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
            }
        } catch (e: Exception) { /* Hardware issue */ }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isWaitingForTag && (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action)) {
            
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            
            tag?.let { handleTagScanned(it.id.joinToString("") { b -> "%02x".format(b) }) }
            isWaitingForTag = false
        }
    }

    private fun handleTagScanned(tagId: String) {
        prefs.addTag(tagId)
        updateTagList()
        Toast.makeText(this, "New key added!", Toast.LENGTH_SHORT).show()
    }
}
