package com.emitt.ui

import androidx.annotation.StringRes
import com.emitt.R
import com.emitt.data.UiText
import com.emitt.data.UiTextException
import android.app.Application
import android.nfc.NdefMessage
import android.nfc.Tag
import com.emitt.HapticEvent
import com.emitt.Haptics
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
import com.emitt.nfc.Emulator
import com.emitt.nfc.NdefCodec
import com.emitt.nfc.TagOps
import com.emitt.nfc.TagReport
import com.emitt.nfc.TagResult
import com.emitt.nfc.UltralightType
import com.emitt.nfc.toHex
import com.emitt.qr.QrStyle
import com.emitt.ui.theme.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(@StringRes val label: Int, val path: String) {
    CREATE(R.string.tab_create, "create"),
    SCAN(R.string.tab_scan, "scan"),
    TAGS(R.string.tab_tags, "nfc"),
    FLIPPER(R.string.tab_flipper, "flipper"),
    LIBRARY(R.string.tab_library, "lib"),
}

/** A Flipper .nfc file opened from storage: [text] is kept so it can be re-sent unchanged. */
class ImportedFile(val name: String, val text: String, val file: FlipperNfc)

enum class Output(@StringRes val label: Int) { QR(R.string.out_qr), NFC(R.string.out_nfc), FLIPPER(R.string.tab_flipper) }

/** Full-screen pages opened from the top bar. */
enum class Page(val path: String, val parent: Page? = null) {
    SETTINGS("settings"),
    APPEARANCE("appearance"),
    CUSTOM_COLOURS("themes", APPEARANCE),
}

/** What the next tag tapped against the phone should be used for. */
sealed interface NfcOp {
    val prompt: UiText
    data class Write(val message: NdefMessage, val label: UiText) : NfcOp { override val prompt get() = UiText(R.string.prompt_write, label) }
    data object Erase : NfcOp { override val prompt get() = UiText(R.string.prompt_erase) }
    data object Lock : NfcOp { override val prompt get() = UiText(R.string.prompt_lock) }
    data object CopyRead : NfcOp { override val prompt get() = UiText(R.string.prompt_copy_from) }
    data class CopyWrite(val message: NdefMessage) : NfcOp { override val prompt get() = UiText(R.string.prompt_copy_to) }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val themes = ThemeRepository.get(app)
    val library = Library.get(app)
    val settings = Settings(app)

    var tab by mutableStateOf(Tab.CREATE)
    /** Settings or Appearance, when one is open over the tabs. */
    var page by mutableStateOf<Page?>(null)
    var settingsOpen: Boolean
        get() = page != null
        set(v) { page = if (v) Page.SETTINGS else null }
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
    /** The latest rendered QR, shared with the floating preview. */
    var liveQr by mutableStateOf<QrHealth?>(null)
    /** True while the QR preview is scrolled out of view, so the floating copy shows. */
    var previewOffscreen by mutableStateOf(false)
    /** True while any part of the QR Style panel is on screen. */
    var styleVisible by mutableStateOf(false)

    // ---- NFC ----
    var nfcOp by mutableStateOf<NfcOp?>(null)
    var lastReport by mutableStateOf<TagReport?>(null)
    var lastResult by mutableStateOf<TagResult?>(null)
    var imported by mutableStateOf<ImportedFile?>(null)

    // ---- Flipper ----
    var flipperProgress by mutableStateOf<Float?>(null)

    // ---- Updates ----
    var update by mutableStateOf<AppUpdate?>(null)
    var updateStatus by mutableStateOf<UiText?>(null)
    /** The update pop-up, shown after a check finds a newer release. */
    var updateDialog by mutableStateOf<AppUpdate?>(null)

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    fun toast(msg: UiText) { _messages.trySend(msg) }
    fun toast(@StringRes id: Int, vararg args: Any) = toast(UiText(id, *args))

    init {
        if (settings.checkUpdates) checkForUpdate(manual = false)
    }

    fun draftTitle(): String = draftName.ifBlank { payload.summary().take(40).ifBlank { payload.kind.name.lowercase() } }

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
        toast(if (existing != null) R.string.lib_updated else R.string.lib_saved)
    }

    // ---- NFC ----

    fun ndefFor(p: Payload): NdefMessage = NdefCodec.build(p, launchApp.takeIf { it.isNotBlank() })

    fun startWrite(p: Payload = payload) {
        p.problem()?.let { toast(it); return }
        nfcOp = NfcOp.Write(ndefFor(p), UiText(p.kind.label))
        lastResult = null
    }

    fun startWrite(msg: NdefMessage, label: UiText) {
        nfcOp = NfcOp.Write(msg, label)
        lastResult = null
    }

    /** Makes this phone act as a tag carrying [p], until stopped. */
    fun emulate(p: Payload = payload, label: String = draftTitle()) {
        p.problem()?.let { toast(it); return }
        emulate(ndefFor(p), label)
    }

    fun emulate(msg: NdefMessage, label: String) {
        val app = getApplication<Application>()
        val adapter = android.nfc.NfcAdapter.getDefaultAdapter(app)
        when {
            adapter == null || !Emulator.supported(app) -> toast(R.string.emu_unsupported)
            !adapter.isEnabled -> { toast(R.string.nfc_turn_on_first); tab = Tab.TAGS }
            else -> { nfcOp = null; Emulator.start(app, msg, label); tagModeOpen = true }
        }
    }

    /** Full-screen tag mode while emulating; false when minimised to the bottom bar. */
    var tagModeOpen by mutableStateOf(false)

    fun stopEmulating() {
        Emulator.stop(getApplication())
        tagModeOpen = false
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
            android.util.Log.i("EMITT/NFC", "tag ${tag.id.toHex("")} techs=${tag.techList.map { it.substringAfterLast('.') }} op=${op?.javaClass?.simpleName} -> ${result.javaClass.simpleName}")
            buzz(result !is TagResult.Failed)
            lastResult = result
            when {
                op == NfcOp.CopyRead && result is TagResult.Read -> {
                    val msg = result.report.message
                    if (msg == null) {
                        nfcOp = null
                        lastResult = TagResult.Failed(UiText(R.string.copy_empty))
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

    private fun buzz(ok: Boolean) = Haptics.play(getApplication(), if (ok) HapticEvent.WRITE else HapticEvent.FAIL)

    // ---- Flipper ----

    fun flipperFile(p: Payload = payload, name: String = draftTitle()): Result<Pair<String, String>> = runCatching {
        p.problem()?.let { throw UiTextException(it) }
        val msg = ndefFor(p)
        val chip = flipperChip ?: FlipperNfcFile.smallestFit(msg) ?: throw UiTextException(UiText(R.string.fl_too_big))
        if (FlipperNfcFile.footprint(msg) > FlipperNfcFile.capacity(chip)) throw UiTextException(UiText(R.string.fl_too_big_chip, chip.flipperName))
        FlipperNfcFile.safeName(name) + ".nfc" to FlipperNfcFile.fromNdef(msg, chip, flipperUid)
    }

    fun sendToFlipper(fileName: String, content: String) {
        if (!FlipperLink.isReady()) {
            toast(R.string.fl_connect_first)
            tab = Tab.FLIPPER
            return
        }
        val path = "${FlipperNfcFile.NFC_DIR}/$fileName"
        viewModelScope.launch {
            flipperProgress = 0f
            runCatching { FlipperLink.writeFile(path, content.toByteArray()) { flipperProgress = it } }
                .onSuccess { toast(R.string.fl_sent, path); buzz(true) }
                .onFailure { toast(UiText(R.string.fl_send_failed, (it as? com.emitt.flipper.FlipperException)?.text ?: UiText(R.string.fl_failed))); buzz(false) }
            flipperProgress = null
        }
    }

    // ---- Updates ----

    /** Hide the update pop-up for this version for a day. Check now still shows it. */
    fun remindLater() {
        updateDialog?.let {
            settings.snoozedVersion = it.version
            settings.snoozedUntil = System.currentTimeMillis() + 24L * 60 * 60 * 1000
        }
        updateDialog = null
    }

    fun checkForUpdate(manual: Boolean) {
        viewModelScope.launch {
            if (manual) updateStatus = UiText(R.string.upd_checking)
            when (val r = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                is UpdateResult.Available -> {
                    update = r.update
                    updateStatus = UiText(R.string.upd_available, r.update.version)
                    val snoozed = settings.snoozedVersion == r.update.version && System.currentTimeMillis() < settings.snoozedUntil
                    if (manual || !snoozed) updateDialog = r.update
                    if (!manual) UpdateWorker.notifyOnce(getApplication(), r.update)
                }
                UpdateResult.UpToDate -> updateStatus = UiText(R.string.upd_latest)
                is UpdateResult.Failed -> updateStatus = r.reason
            }
        }
    }
}
