package com.emitt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emitt.data.Encoding
import com.emitt.ui.EmittRoot
import com.emitt.ui.MainViewModel
import com.emitt.ui.Tab
import com.emitt.ui.theme.EmittTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var nfc: NfcAdapter? = null
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        handleShare(intent)
        setContent {
            val config by vm.themes.config.collectAsStateWithLifecycle()
            EmittTheme(config) { EmittRoot(vm) }
        }
        // Update notifications need this on Android 13+; ask once, on first launch
        if (Build.VERSION.SDK_INT >= 33 && !vm.settings.askedNotifications &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            vm.settings.askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Text or links shared into EMITT become a new draft. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        vm.newDraft(Encoding.parse(text))
        vm.settingsOpen = false
        vm.tab = Tab.CREATE
    }

    // While EMITT is open it owns NFC: every tap goes to the current operation (or the inspector).
    override fun onResume() {
        super.onResume()
        nfc?.takeIf { it.isEnabled }?.enableReaderMode(
            this,
            { tag -> vm.onTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) },
        )
    }

    override fun onPause() {
        super.onPause()
        runCatching { nfc?.disableReaderMode(this) }
    }
}
