package com.emitt.ui

import android.app.Application
import android.nfc.NdefMessage
import android.nfc.Tag
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emitt.BuildConfig
import com.emitt.UpdateWorker
import com.emitt.data.AppUpdate
import com.emitt.data.Library
import com.emitt.data.Payload
import com.emitt.data.SavedItem
import com.emitt.data.Settings
import com.emitt.data.UpdateChecker
import com.emitt.data.UpdateResult
import com.emitt.data.problem
import com.emitt.data.summary
import com.emitt.flipper.FlipperLink
import com.emitt.flipper.FlipperNfc
import com.emitt.flipper.FlipperNfcFile
import com.emitt.nfc.NdefCodec
import com.emitt.nfc.TagOps
import com.emitt.nfc.TagReport
import com.emitt.nfc.TagResult
import com.emitt.nfc.UltralightType
import com.emitt.qr.QrStyle
import com.emitt.ui.theme.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val path: String) {
    CREATE("CREATE", "create"),
    SCAN("SCAN", "scan"),
    TAGS("TAGS", "nfc"),
    FLIPPER("FLIPPER", "flipper"),
    LIBRARY("LIBRARY", "lib"),
}

/** A Flipper .nfc file opened from storage: [text] is kept so it can be re-sent unchanged. */
class ImportedFile(val name: String, val text: String, val file: FlipperNfc)

enum class Output(val label: String) { QR("QR code"), NFC("NFC tag"), FLIPPER("Flipper") }

/** What the next tag tapped against the phone should be used for. */
sealed interface NfcOp {
    val prompt: String
    data class Write(val message: NdefMessage, val label: String) : NfcOp { override val prompt get() = "Hold a tag to the phone to write $label" }
    data object Erase : NfcOp { override val prompt get() = "Hold the tag to erase" }
    data object Lock : NfcOp { override val prompt get() = "Hold the tag to lock it. This can't be undone." }
    data object CopyRead : NfcOp { override val prompt get() = "Hold the tag to copy from" }
    data class CopyWrite(val message: NdefMessage) : NfcOp { override val prompt get() = "Now hold the tag to copy onto" }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val themes = ThemeRepository.get(app)
    val library = Library.get(app)
    val settings = Settings(app)

    var tab by mutableStateOf(Tab.CREATE)
    var settingsOpen by mutableStateOf(false)
    /** Saved or draft code shown full screen. */
    var fullscreen by mutableStateOf<Pair<String, QrStyle>?>(null)

    // ---- Create ----
    var payload by mutableStateOf<Payload>(Payload.Url())
    var output by mutableStateOf(Output.QR)
    var style by mutableStateOf(QrStyle())
    /** The library item being edited, if any. */
    var editingId by mutableStateOf<String?>(null)
    var draftName by mutableStateOf("")
    var launchApp by mutableStateOf("")
    /** Null picks the smallest NTAG that fits. */
    var flipperChip by mutableStateOf<UltralightType?>(null)
    var flipperUid by mutableStateOf(FlipperNfcFile.randomUid())

    // ---- NFC ----
    var nfcOp by mutableStateOf<NfcOp?>(null)
    var lastReport by mutableStateOf<TagReport?>(null)
    var lastResult by mutableStateOf<TagResult?>(null)
    var imported by mutableStateOf<ImportedFile?>(null)

    // ---- Flipper ----
    var flipperProgress by mutableStateOf<Float?>(null)

    // ---- Updates ----
    var update by mutableStateOf<AppUpdate?>(null)
    var updateStatus by mutableStateOf<String?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    fun toast(msg: String) { _messages.trySend(msg) }

    init {
        if (settings.checkUpdates) checkForUpdate(manual = false)
    }

    fun draftTitle(): String = draftName.ifBlank { payload.summary().take(40).ifBlank { payload.kind.label.lowercase() } }

    fun newDraft(p: Payload) {
        payload = p
        editingId = null
        draftName = ""
        flipperUid = FlipperNfcFile.randomUid()
    }

    fun edit(item: SavedItem) {
        payload = item.payload
        style = item.style
        editingId = item.id
        draftName = item.name
        tab = Tab.CREATE
    }

    fun saveDraft() {
        payload.problem()?.let { toast(it); return }
        val existing = library.byId(editingId)
        val item = existing?.copy(name = draftTitle(), payload = payload, style = style)
            ?: SavedItem(name = draftTitle(), payload = payload, style = style)
        library.upsert(item)
        editingId = item.id
        toast(if (existing != null) "Updated in library" else "Saved to library")
    }

    // ---- NFC ----

    fun ndefFor(p: Payload): NdefMessage = NdefCodec.build(p, launchApp.takeIf { it.isNotBlank() })

    fun startWrite(p: Payload = payload) {
        p.problem()?.let { toast(it); return }
        nfcOp = NfcOp.Write(ndefFor(p), p.kind.label.lowercase())
        lastResult = null
    }

    fun startWrite(msg: NdefMessage, label: String) {
        nfcOp = NfcOp.Write(msg, label)
        lastResult = null
    }

    /** Called from the NFC reader thread whenever a tag is tapped. */
    fun onTag(tag: Tag) {
        val op = nfcOp
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (op) {
                    is NfcOp.Write -> TagOps.write(tag, op.message)
                    is NfcOp.CopyWrite -> TagOps.write(tag, op.message)
                    NfcOp.Erase -> TagOps.erase(tag)
                    NfcOp.Lock -> TagOps.lock(tag)
                    NfcOp.CopyRead, null -> TagOps.read(tag)
                }
            }
            buzz(result !is TagResult.Failed)
            lastResult = result
            when {
                op == NfcOp.CopyRead && result is TagResult.Read -> {
                    val msg = result.report.message
                    if (msg == null) {
                        nfcOp = null
                        lastResult = TagResult.Failed("That tag has nothing to copy.")
                    } else {
                        lastReport = result.report
                        nfcOp = NfcOp.CopyWrite(msg)
                    }
                }
                result is TagResult.Read -> {
                    lastReport = result.report
                    nfcOp = null
                    if (op == null && tab != Tab.TAGS && !settingsOpen) tab = Tab.TAGS
                }
                result is TagResult.Failed -> Unit // keep the op so the user can retry
                else -> nfcOp = null
            }
        }
    }

    private fun buzz(ok: Boolean) {
        if (!settings.haptics) return
        val v = getApplication<Application>().getSystemService(Vibrator::class.java) ?: return
        runCatching {
            v.vibrate(if (ok) VibrationEffect.createOneShot(40, 180) else VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1))
        }
    }

    // ---- Flipper ----

    fun flipperFile(p: Payload = payload, name: String = draftTitle()): Result<Pair<String, String>> = runCatching {
        p.problem()?.let { error(it) }
        val msg = ndefFor(p)
        val chip = flipperChip ?: FlipperNfcFile.smallestFit(msg) ?: error("Too big for an NTAG216 (872 bytes)")
        if (FlipperNfcFile.footprint(msg) > FlipperNfcFile.capacity(chip)) error("Too big for ${chip.flipperName}. Pick a bigger chip.")
        FlipperNfcFile.safeName(name) + ".nfc" to FlipperNfcFile.fromNdef(msg, chip, flipperUid)
    }

    fun sendToFlipper(fileName: String, content: String) {
        if (!FlipperLink.isReady()) {
            toast("Connect your Flipper first")
            tab = Tab.FLIPPER
            return
        }
        val path = "${FlipperNfcFile.NFC_DIR}/$fileName"
        viewModelScope.launch {
            flipperProgress = 0f
            runCatching { FlipperLink.writeFile(path, content.toByteArray()) { flipperProgress = it } }
                .onSuccess { toast("Sent to Flipper: $path"); buzz(true) }
                .onFailure { toast("Couldn't send: ${it.message}"); buzz(false) }
            flipperProgress = null
        }
    }

    // ---- Updates ----

    fun checkForUpdate(manual: Boolean) {
        viewModelScope.launch {
            if (manual) updateStatus = "Checking…"
            when (val r = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                is UpdateResult.Available -> {
                    update = r.update
                    updateStatus = "Version ${r.update.version} is available"
                    UpdateWorker.notifyOnce(getApplication(), r.update)
                }
                UpdateResult.UpToDate -> updateStatus = "You're on the latest version"
                is UpdateResult.Failed -> updateStatus = r.reason
            }
        }
    }
}
