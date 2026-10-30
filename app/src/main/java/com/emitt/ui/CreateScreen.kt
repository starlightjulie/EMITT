package com.emitt.ui

import androidx.compose.ui.res.stringResource
import com.emitt.R
import com.emitt.data.UiText
import com.emitt.data.UiTextException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emitt.data.Encoding
import com.emitt.data.Payload
import com.emitt.data.PayloadKind
import com.emitt.data.WifiSecurity
import com.emitt.data.problem
import com.emitt.flipper.FlipperLink
import com.emitt.flipper.FlipperNfcFile
import com.emitt.nfc.NdefCodec
import com.emitt.nfc.hexToBytes
import com.emitt.nfc.toHex
import com.emitt.qr.Badge
import com.emitt.qr.Ec
import com.emitt.qr.EyeShape
import com.emitt.qr.Gradient
import com.emitt.qr.ModuleShape
import com.emitt.qr.QrRenderer
import com.emitt.qr.QrStyle
import com.emitt.ui.theme.LocalLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun CreateScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TermPanel(title = if (vm.editingId != null) stringResource(R.string.edit_title, vm.draftTitle()) else stringResource(R.string.payload)) {
            PayloadEditor(vm.payload, onChange = { vm.payload = it }, onKind = { vm.payload = it.blank() })
            vm.payload.problem()?.let { StatusLine(Status.INFO, it.text()) }
        }
        TermPanel(title = stringResource(R.string.output)) {
            ChipGroup(Output.entries, vm.output, { stringResource(it.label) }, { vm.output = it })
        }
        when (vm.output) {
            Output.QR -> QrOutput(vm)
            Output.NFC -> NfcOutput(vm)
            Output.FLIPPER -> FlipperOutput(vm)
        }
        TermPanel(title = stringResource(R.string.save)) {
            TermField(vm.draftName, { vm.draftName = it }, stringResource(R.string.name), supporting = stringResource(R.string.name_hint))
            ButtonRow {
                TermButton(stringResource(if (vm.editingId != null) R.string.update else R.string.save_to_library), vm::saveDraft, Modifier.weight(1f), Icons.Rounded.Bookmark, primary = true)
                if (vm.editingId != null) TermButton(stringResource(R.string.new_draft), { vm.newDraft(vm.payload.kind.blank()) }, Modifier.weight(0.6f))
            }
        }
    }
}

@Composable
fun PayloadEditor(p: Payload, onChange: (Payload) -> Unit, onKind: (PayloadKind) -> Unit) {
    ChipStrip(PayloadKind.entries, p.kind, { stringResource(it.label) }, onKind)
    Spacer(Modifier.height(8.dp))
    when (p) {
        is Payload.Text -> TermField(p.text, { onChange(p.copy(text = it)) }, stringResource(R.string.kind_text), singleLine = false, minLines = 3)
        is Payload.Url -> TermField(p.url, { onChange(p.copy(url = it)) }, stringResource(R.string.kind_url), keyboard = KeyboardType.Uri, mono = true)
        is Payload.Wifi -> {
            TermField(p.ssid, { onChange(p.copy(ssid = it)) }, stringResource(R.string.ssid))
            ChipGroup(WifiSecurity.entries, p.security, { stringResource(it.label) }, { onChange(p.copy(security = it)) }, Modifier.padding(vertical = 6.dp))
            if (p.security != WifiSecurity.OPEN) TermField(p.password, { onChange(p.copy(password = it)) }, stringResource(R.string.password), mono = true)
            SwitchRow(stringResource(R.string.hidden_network), p.hidden, { onChange(p.copy(hidden = it)) })
        }
        is Payload.Contact -> {
            TermField(p.name, { onChange(p.copy(name = it)) }, stringResource(R.string.name))
            TermField(p.phone, { onChange(p.copy(phone = it)) }, stringResource(R.string.kind_phone), keyboard = KeyboardType.Phone)
            TermField(p.email, { onChange(p.copy(email = it)) }, stringResource(R.string.kind_email), keyboard = KeyboardType.Email)
            TermField(p.org, { onChange(p.copy(org = it)) }, stringResource(R.string.organisation))
            TermField(p.url, { onChange(p.copy(url = it)) }, stringResource(R.string.website), keyboard = KeyboardType.Uri)
        }
        is Payload.Email -> {
            TermField(p.to, { onChange(p.copy(to = it)) }, stringResource(R.string.to), keyboard = KeyboardType.Email)
            TermField(p.subject, { onChange(p.copy(subject = it)) }, stringResource(R.string.subject))
            TermField(p.body, { onChange(p.copy(body = it)) }, stringResource(R.string.message), singleLine = false, minLines = 2)
        }
        is Payload.Sms -> {
            TermField(p.number, { onChange(p.copy(number = it)) }, stringResource(R.string.number), keyboard = KeyboardType.Phone)
            TermField(p.message, { onChange(p.copy(message = it)) }, stringResource(R.string.message), singleLine = false, minLines = 2)
        }
        is Payload.Phone -> TermField(p.number, { onChange(p.copy(number = it)) }, stringResource(R.string.number), keyboard = KeyboardType.Phone)
        is Payload.Geo -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermField(p.lat, { onChange(p.copy(lat = it)) }, stringResource(R.string.latitude), Modifier.weight(1f), keyboard = KeyboardType.Decimal, mono = true)
                TermField(p.lon, { onChange(p.copy(lon = it)) }, stringResource(R.string.longitude), Modifier.weight(1f), keyboard = KeyboardType.Decimal, mono = true)
            }
            TermField(p.label, { onChange(p.copy(label = it)) }, stringResource(R.string.label_optional))
        }
        is Payload.App -> TermField(
            p.packageName, { onChange(p.copy(packageName = it.trim())) }, stringResource(R.string.package_name), mono = true,
            supporting = stringResource(R.string.package_hint),
        )
    }
}

// ---------------- QR ----------------

@Composable
private fun QrOutput(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = remember(vm.payload) { if (vm.payload.problem() == null) Encoding.toQrText(vm.payload) else "" }
    val health = rememberQr(text, vm.style)
    var exportSize by rememberSaveable { mutableIntStateOf(1024) }
    var styleOpen by rememberSaveable { mutableStateOf(true) }

    fun export(action: (Bitmap) -> Unit) {
        if (text.isEmpty()) { vm.toast(vm.payload.problem() ?: UiText(R.string.qr_nothing)); return }
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { runCatching { QrRenderer.render(text, vm.style, exportSize).bitmap }.getOrNull() }
            if (bmp == null) vm.toast(R.string.qr_too_much) else action(bmp)
        }
    }

    LaunchedEffect(health) { vm.liveQr = health }
    DisposableEffect(Unit) { onDispose { vm.previewOffscreen = false; vm.styleVisible = false } }

    TermPanel(title = stringResource(R.string.out_qr), trailing = {
        health?.takeIf { it.bitmap != null }?.let { Dim("V${it.version} · ${it.modules}²") }
    }) {
        QrPreview(
            health,
            Modifier.onGloballyPositioned { c ->
                // boundsInWindow is clipped by the scrolling column, so it shrinks as the preview leaves the screen.
                // Only count it as gone when it scrolled off the top (you're below it, styling).
                val hidden = c.boundsInWindow().height < c.size.height * 0.3f
                val above = c.positionInWindow().y < c.findRootCoordinates().size.height / 2f
                vm.previewOffscreen = hidden && above
            },
        )
        HealthReport(health)
        SectionLabel(stringResource(R.string.export_size))
        ChipGroup(listOf(512, 1024, 2048, 4096), exportSize, { "${it}px" }, { exportSize = it })
        ButtonRow {
            TermButton(stringResource(R.string.full), { if (text.isNotEmpty()) vm.fullscreen = text to vm.style }, Modifier.weight(1f), Icons.Rounded.Fullscreen)
            TermButton(stringResource(R.string.share), { export { context.sharePng(FlipperNfcFile.safeName(vm.draftTitle()), it) } }, Modifier.weight(1f), Icons.Rounded.Share)
            TermButton(stringResource(R.string.save), {
                export { vm.toast(if (context.savePngToGallery(FlipperNfcFile.safeName(vm.draftTitle()) + "_" + System.currentTimeMillis() / 1000, it)) R.string.saved_gallery else R.string.save_image_failed) }
            }, Modifier.weight(1f), Icons.Rounded.Download)
        }
    }
    TermPanel(Modifier.onGloballyPositioned { vm.styleVisible = styleOpen && it.boundsInWindow().height > 0f }, title = stringResource(R.string.style), trailing = {
        Icon(if (styleOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.clickable { styleOpen = !styleOpen }, tint = LocalLook.current.accent)
    }) {
        AnimatedVisibility(styleOpen) {
            Column { QrStyleEditor(vm.style, { vm.style = it }, vm) }
        }
    }
}

private class StylePreset(@androidx.annotation.StringRes val name: Int, val make: (QrStyle, com.emitt.ui.theme.EmittLook) -> QrStyle)

private val stylePresets = listOf(
    StylePreset(R.string.preset_classic) { s, _ -> s.copy(fg = 0xFF000000, bg = 0xFFFFFFFF, gradient = Gradient.NONE, eyeOuter = null, eyeInner = null, modules = ModuleShape.SQUARE, eyes = EyeShape.SQUARE, moduleScale = 1f, transparentBg = false) },
    StylePreset(R.string.preset_theme) { s, l -> s.copy(fg = l.bg.toArgbLong(), bg = 0xFFFFFFFF, gradient = Gradient.NONE, eyeOuter = l.accent.toArgbLong().darkenFor(), eyeInner = l.bg.toArgbLong(), modules = ModuleShape.ROUNDED, eyes = EyeShape.ROUNDED, transparentBg = false) },
    StylePreset(R.string.preset_synth) { s, _ -> s.copy(fg = 0xFF5B0FB0, fg2 = 0xFFD4148F, bg = 0xFFFFFFFF, gradient = Gradient.LINEAR, eyeOuter = null, eyeInner = null, modules = ModuleShape.DOTS, eyes = EyeShape.LEAF, moduleScale = 0.9f, transparentBg = false) },
    StylePreset(R.string.shape_fluid) { s, _ -> s.copy(fg = 0xFF0B2545, bg = 0xFFF4F1E8, gradient = Gradient.NONE, eyeOuter = 0xFF13315C, eyeInner = 0xFF0B2545, modules = ModuleShape.FLUID, eyes = EyeShape.CIRCLE, moduleScale = 1f, transparentBg = false) },
    StylePreset(R.string.preset_terminal) { s, _ -> s.copy(fg = 0xFF0A3D1F, bg = 0xFFE9FFF0, gradient = Gradient.NONE, eyeOuter = null, eyeInner = null, modules = ModuleShape.SQUARE, eyes = EyeShape.SQUARE, moduleScale = 0.82f, transparentBg = false) },
    StylePreset(R.string.preset_inverted) { s, _ -> s.copy(fg = 0xFFFFFFFF, bg = 0xFF000000, gradient = Gradient.NONE, eyeOuter = null, eyeInner = null, transparentBg = false) },
)

private fun androidx.compose.ui.graphics.Color.toArgbLong(): Long = (copy(alpha = 1f).toArgb().toLong()) and 0xFFFFFFFF

/** Bright accents make poor QR eyes on white: pull them darker until they read. */
private fun Long.darkenFor(): Long {
    var c = this
    var k = 0
    while (QrRenderer.contrast(c, 0xFFFFFFFF) < 4.5 && k++ < 12) {
        val r = ((c shr 16) and 0xFF) * 85 / 100
        val g = ((c shr 8) and 0xFF) * 85 / 100
        val b = (c and 0xFF) * 85 / 100
        c = 0xFF000000 or (r shl 16) or (g shl 8) or b
    }
    return c
}

@Composable
fun QrStyleEditor(s: QrStyle, onChange: (QrStyle) -> Unit, vm: MainViewModel) {
    val look = LocalLook.current
    val context = LocalContext.current
    val ctx = context
    val pickBadge = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = runCatching {
            val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            val k = 320f / maxOf(src.width, src.height)
            val small = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true) else src
            val dir = File(context.filesDir, "badges").apply { mkdirs() }
            File(dir, UUID.randomUUID().toString() + ".png").also { f -> f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) } }.absolutePath
        }.getOrNull()
        if (path == null) vm.toast(R.string.image_load_failed) else onChange(s.copy(badge = Badge.IMAGE, badgeImage = path))
    }

    SectionLabel(stringResource(R.string.presets))
    ChipStrip<StylePreset?>(stylePresets, null, { stringResource(it!!.name) }, { onChange(it!!.make(s, look)) })

    SectionLabel(stringResource(R.string.colours))
    ColorRow(stringResource(R.string.modules), s.fg, { onChange(s.copy(fg = it)) })
    ChipGroup(Gradient.entries, s.gradient, { stringResource(it.label) }, { onChange(s.copy(gradient = it)) }, Modifier.padding(vertical = 4.dp))
    if (s.gradient != Gradient.NONE) ColorRow(stringResource(R.string.gradient_end), s.fg2, { onChange(s.copy(fg2 = it)) })
    SwitchRow(stringResource(R.string.transparent_bg), s.transparentBg, { onChange(s.copy(transparentBg = it)) })
    if (!s.transparentBg) ColorRow(stringResource(R.string.slot_bg), s.bg, { onChange(s.copy(bg = it)) })
    SwitchRow(stringResource(R.string.custom_eyes), s.eyeOuter != null || s.eyeInner != null, { on ->
        onChange(if (on) s.copy(eyeOuter = s.fg, eyeInner = s.fg) else s.copy(eyeOuter = null, eyeInner = null))
    })
    if (s.eyeOuter != null || s.eyeInner != null) {
        ColorRow(stringResource(R.string.eye_frame), s.eyeOuter ?: s.fg, { onChange(s.copy(eyeOuter = it)) })
        ColorRow(stringResource(R.string.eye_centre), s.eyeInner ?: s.fg, { onChange(s.copy(eyeInner = it)) })
    }
    ButtonRow {
        TermButton(stringResource(R.string.swap), { onChange(s.copy(fg = s.bg, bg = s.fg)) }, Modifier.weight(1f))
        TermButton(stringResource(R.string.reset), { onChange(QrStyle(badge = s.badge, badgeText = s.badgeText, badgeImage = s.badgeImage)) }, Modifier.weight(1f))
    }

    SectionLabel(stringResource(R.string.shape))
    ChipGroup(ModuleShape.entries, s.modules, { stringResource(it.label) }, { onChange(s.copy(modules = it)) })
    SliderRow(stringResource(R.string.module_size), s.moduleScale, 0.6f..1f, { onChange(s.copy(moduleScale = it)) }, { "${(it * 100).toInt()}%" })
    Dim(stringResource(R.string.eyes), Modifier.padding(top = 4.dp, bottom = 6.dp))
    ChipGroup(EyeShape.entries, s.eyes, { stringResource(it.label) }, { onChange(s.copy(eyes = it)) })

    SectionLabel(stringResource(R.string.centre_badge))
    ChipGroup(Badge.entries, s.badge, { stringResource(it.label) }, { b ->
        if (b == Badge.IMAGE && s.badgeImage == null) pickBadge.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        else onChange(s.copy(badge = b))
    })
    if (s.badge == Badge.TEXT) TermField(s.badgeText, { onChange(s.copy(badgeText = it.take(6))) }, stringResource(R.string.badge_text), mono = true)
    if (s.badge == Badge.IMAGE) TermButton(stringResource(R.string.choose_image), {
        pickBadge.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }, Modifier.fillMaxWidth().padding(top = 6.dp), Icons.Rounded.Image)
    if (s.badge != Badge.NONE) SliderRow(stringResource(R.string.badge_size), s.badgeSize, 0.12f..0.3f, { onChange(s.copy(badgeSize = it)) }, { "${(it * 100).toInt()}%" })

    SectionLabel(stringResource(R.string.visibility))
    Dim(stringResource(R.string.ec_hint), Modifier.padding(bottom = 6.dp))
    ChipGroup(Ec.entries, s.ec, { "${it.name} ${it.percent}%" }, { onChange(s.copy(ec = it)) })
    SliderRow(stringResource(R.string.quiet_zone), s.quietZone.toFloat(), 0f..8f, { onChange(s.copy(quietZone = it.toInt())) }, { ctx.getString(R.string.n_modules, it.toInt()) }, steps = 7)
    var boost by remember { mutableStateOf(vm.settings.boostBrightness) }
    SwitchRow(stringResource(R.string.max_brightness), boost, { boost = it; vm.settings.boostBrightness = it }, stringResource(R.string.max_brightness_hint))
}

// ---------------- NFC ----------------

@Composable
private fun NfcOutput(vm: MainViewModel) {
    val look = LocalLook.current
    val ok = vm.payload.problem() == null
    val msg = remember(vm.payload, vm.launchApp) { if (ok) runCatching { vm.ndefFor(vm.payload) }.getOrNull() else null }
    TermPanel(title = stringResource(R.string.out_nfc)) {
        if (msg == null) {
            StatusLine(Status.INFO, (vm.payload.problem() ?: UiText(R.string.need_text)).text())
        } else {
            NdefCodec.describe(msg).forEach { KvRow(it.type, it.value) }
            val size = msg.byteArrayLength
            Spacer(Modifier.height(6.dp))
            val fits = FlipperNfcFile.ntags.filter { FlipperNfcFile.footprint(msg) <= FlipperNfcFile.capacity(it) }
            StatusLine(
                if (fits.isEmpty()) Status.WARN else Status.OK,
                if (fits.isEmpty()) stringResource(R.string.ndef_too_big, size) else stringResource(R.string.ndef_fits, size, fits.joinToString { it.flipperName }),
            )
        }
        SectionLabel(stringResource(R.string.extras))
        if (vm.payload !is Payload.App) {
            TermField(
                vm.launchApp, { vm.launchApp = it.trim() }, stringResource(R.string.also_open_app), mono = true,
                supporting = stringResource(R.string.also_open_app_hint),
            )
        }
        ButtonRow {
            TermButton(stringResource(R.string.write_to_tag), { vm.startWrite() }, Modifier.weight(1f), Icons.Rounded.Nfc, primary = true, enabled = msg != null)
        }
        ButtonRow {
            TermButton(stringResource(R.string.be_the_tag), { vm.emulate() }, Modifier.weight(1f), Icons.Rounded.Smartphone, enabled = msg != null)
        }
        Dim(
            stringResource(R.string.nfc_modes_hint),
            Modifier.padding(top = 8.dp),
        )
        SectionLabel(stringResource(R.string.with_flipper))
        Dim(stringResource(R.string.flipper_write_tip))
        Dim(
            stringResource(R.string.flipper_read_tip),
            Modifier.padding(top = 4.dp),
        )
        Dim(stringResource(R.string.ntag_tip), Modifier.padding(top = 4.dp))
        if (vm.nfcOp != null) Text("> " + stringResource(R.string.waiting_for_tag), style = MaterialTheme.typography.labelLarge, color = look.signal, modifier = Modifier.padding(top = 8.dp))
    }
}

// ---------------- Flipper ----------------

@Composable
private fun FlipperOutput(vm: MainViewModel) {
    val context = LocalContext.current
    val look = LocalLook.current
    val file = remember(vm.payload, vm.launchApp, vm.flipperChip, vm.flipperUid, vm.draftName) { vm.flipperFile() }
    var preview by rememberSaveable { mutableStateOf(false) }
    var uidText by remember(vm.flipperUid) { mutableStateOf(vm.flipperUid.toHex()) }
    val connected = FlipperLink.state.collectAsState().value is com.emitt.flipper.FlipperState.Ready
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = file.getOrNull() ?: return@rememberLauncherForActivityResult
        if (uri != null) if (context.writeUri(uri, f.second)) vm.toast(R.string.saved_file, f.first) else vm.toast(R.string.save_file_failed)
    }

    TermPanel(title = "Flipper .nfc") {
        Dim(stringResource(R.string.flipper_file_hint))
        SectionLabel(stringResource(R.string.chip))
        ChipGroup(listOf(null) + FlipperNfcFile.ntags, vm.flipperChip, { it?.let { c -> "${c.flipperName} ${FlipperNfcFile.capacity(c)}B" } ?: stringResource(R.string.auto) }, { vm.flipperChip = it })
        SectionLabel("UID")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TermField(
                uidText,
                { t ->
                    uidText = t.uppercase()
                    t.hexToBytes()?.takeIf { it.size == 7 }?.let { vm.flipperUid = it }
                },
                stringResource(R.string.uid_7), Modifier.weight(1f), mono = true,
                isError = uidText.hexToBytes()?.size != 7,
            )
            Spacer(Modifier.width(8.dp))
            TermButton("", { vm.flipperUid = FlipperNfcFile.randomUid() }, icon = Icons.Rounded.Casino)
        }
        Spacer(Modifier.height(4.dp))
        file.fold(
            onSuccess = { (name, text) ->
                StatusLine(Status.OK, stringResource(R.string.file_summary, name, text.length, text.lineSequence().count { it.startsWith("Page ") }))
                Row(Modifier.clickable { preview = !preview }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (preview) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = look.signal)
                    Text(stringResource(if (preview) R.string.hide_file else R.string.show_file), style = MaterialTheme.typography.labelMedium, color = look.signal)
                }
                AnimatedVisibility(preview) {
                    Text(
                        text.lines().take(40).joinToString("\n") + if (text.lines().size > 40) "\n…" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = look.mono),
                        color = look.dim,
                    )
                }
                vm.flipperProgress?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(vertical = 6.dp), color = look.accent) }
                ButtonRow {
                    TermButton(stringResource(if (connected) R.string.send_to_flipper else R.string.connect_flipper), {
                        if (connected) vm.sendToFlipper(name, text) else vm.tab = Tab.FLIPPER
                    }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.Send, primary = true, enabled = vm.flipperProgress == null)
                }
                ButtonRow {
                    TermButton(stringResource(R.string.share), { context.shareText(name, "application/octet-stream", text) }, Modifier.weight(1f), Icons.Rounded.Share)
                    TermButton(stringResource(R.string.save_file), { saveAs.launch(name) }, Modifier.weight(1f), Icons.Rounded.Download)
                }
            },
            onFailure = { StatusLine(Status.FAIL, (it as? UiTextException)?.text?.text() ?: stringResource(R.string.cant_build_file)) },
        )
    }
}
