package com.emitt

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.emitt.data.Encoding
import com.emitt.nfc.EmulationService
import com.emitt.nfc.Emulator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
        // Switch between reading tags and being one as emulation starts and stops
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                Emulator.state.map { it != null }.distinctUntilChanged().collect { applyNfcMode(it) }
            }
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

    override fun onPause() {
        super.onPause()
        runCatching { nfc?.disableReaderMode(this) }
        runCatching { nfc?.let { CardEmulation.getInstance(it).unsetPreferredService(this) } }
    }

    /**
     * While EMITT is open it owns NFC. Normally every tap goes to the current operation (or the
     * inspector). While the phone is emulating a tag, reader mode has to be off (it disables card
     * emulation), and EMITT asks to be the preferred tag service so other apps can't answer first.
     */
    private fun applyNfcMode(emulating: Boolean) {
        val adapter = nfc?.takeIf { it.isEnabled } ?: return
        if (emulating) {
            runCatching { adapter.disableReaderMode(this) }
            // Some phones (Samsung's "Embedded tag") also claim the NDEF tag AID, so EMITT asks to win while open.
            // The service was only just enabled, so Android may not have registered it yet: retry briefly.
            lifecycleScope.launch {
                val ce = CardEmulation.getInstance(adapter)
                val service = ComponentName(this@MainActivity, EmulationService::class.java)
                repeat(20) {
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || Emulator.state.value == null) return@launch
                    if (runCatching { ce.setPreferredService(this@MainActivity, service) }.getOrDefault(false)) return@launch
                    delay(250)
                }
            }
        } else {
            runCatching { CardEmulation.getInstance(adapter).unsetPreferredService(this) }
            adapter.enableReaderMode(
                this,
                { tag -> vm.onTag(tag) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) },
            )
        }
    }
}
