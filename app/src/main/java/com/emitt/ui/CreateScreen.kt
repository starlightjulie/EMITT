package com.emitt.ui

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
import androidx.compose.material.icons.rounded.Send
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
        TermPanel(title = if (vm.editingId != null) "Edit · ${vm.draftTitle()}" else "Payload") {
            PayloadEditor(vm.payload, onChange = { vm.payload = it }, onKind = { vm.payload = it.blank() })
            vm.payload.problem()?.let { StatusLine(Status.INFO, it) }
        }
        TermPanel(title = "Output") {
            ChipGroup(Output.entries, vm.output, { it.label }, { vm.output = it })
        }
        when (vm.output) {
            Output.QR -> QrOutput(vm)
            Output.NFC -> NfcOutput(vm)
            Output.FLIPPER -> FlipperOutput(vm)
        }
        TermPanel(title = "Save") {
            TermField(vm.draftName, { vm.draftName = it }, "Name", supporting = "Shown in your library, widget and file names")
            ButtonRow {
                TermButton(if (vm.editingId != null) "Update" else "Save to library", vm::saveDraft, Modifier.weight(1f), Icons.Rounded.Bookmark, primary = true)
                if (vm.editingId != null) TermButton("New", { vm.newDraft(vm.payload.kind.blank()) }, Modifier.weight(0.6f))
            }
        }
    }
}

@Composable
fun PayloadEditor(p: Payload, onChange: (Payload) -> Unit, onKind: (PayloadKind) -> Unit) {
    ChipStrip(PayloadKind.entries, p.kind, { it.label }, onKind)
    Spacer(Modifier.height(8.dp))
    when (p) {
        is Payload.Text -> TermField(p.text, { onChange(p.copy(text = it)) }, "Text", singleLine = false, minLines = 3)
        is Payload.Url -> TermField(p.url, { onChange(p.copy(url = it)) }, "Link", keyboard = KeyboardType.Uri, mono = true)
        is Payload.Wifi -> {
            TermField(p.ssid, { onChange(p.copy(ssid = it)) }, "Network name (SSID)")
            ChipGroup(WifiSecurity.entries, p.security, { it.label }, { onChange(p.copy(security = it)) }, Modifier.padding(vertical = 6.dp))
            if (p.security != WifiSecurity.OPEN) TermField(p.password, { onChange(p.copy(password = it)) }, "Password", mono = true)
            SwitchRow("Hidden network", p.hidden, { onChange(p.copy(hidden = it)) })
        }
        is Payload.Contact -> {
            TermField(p.name, { onChange(p.copy(name = it)) }, "Name")
            TermField(p.phone, { onChange(p.copy(phone = it)) }, "Phone", keyboard = KeyboardType.Phone)
            TermField(p.email, { onChange(p.copy(email = it)) }, "Email", keyboard = KeyboardType.Email)
            TermField(p.org, { onChange(p.copy(org = it)) }, "Organisation")
            TermField(p.url, { onChange(p.copy(url = it)) }, "Website", keyboard = KeyboardType.Uri)
        }
        is Payload.Email -> {
            TermField(p.to, { onChange(p.copy(to = it)) }, "To", keyboard = KeyboardType.Email)
            TermField(p.subject, { onChange(p.copy(subject = it)) }, "Subject")
            TermField(p.body, { onChange(p.copy(body = it)) }, "Message", singleLine = false, minLines = 2)
        }
        is Payload.Sms -> {
            TermField(p.number, { onChange(p.copy(number = it)) }, "Number", keyboard = KeyboardType.Phone)
            TermField(p.message, { onChange(p.copy(message = it)) }, "Message", singleLine = false, minLines = 2)
        }
        is Payload.Phone -> TermField(p.number, { onChange(p.copy(number = it)) }, "Number", keyboard = KeyboardType.Phone)
        is Payload.Geo -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermField(p.lat, { onChange(p.copy(lat = it)) }, "Latitude", Modifier.weight(1f), keyboard = KeyboardType.Decimal, mono = true)
                TermField(p.lon, { onChange(p.copy(lon = it)) }, "Longitude", Modifier.weight(1f), keyboard = KeyboardType.Decimal, mono = true)
            }
            TermField(p.label, { onChange(p.copy(label = it)) }, "Label (optional)")
        }
        is Payload.App -> TermField(
            p.packageName, { onChange(p.copy(packageName = it.trim())) }, "Package name", mono = true,
            supporting = "NFC opens the app directly; QR links to its store page",
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
        if (text.isEmpty()) { vm.toast(vm.payload.problem() ?: "Nothing to export"); return }
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { runCatching { QrRenderer.render(text, vm.style, exportSize).bitmap }.getOrNull() }
            if (bmp == null) vm.toast("Too much data for a QR code") else action(bmp)
        }
    }

    LaunchedEffect(health) { vm.liveQr = health }
    DisposableEffect(Unit) { onDispose { vm.previewOffscreen = false } }

    TermPanel(title = "QR code", trailing = {
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
        SectionLabel("Export size")
        ChipGroup(listOf(512, 1024, 2048, 4096), exportSize, { "${it}px" }, { exportSize = it })
        ButtonRow {
            TermButton("Full", { if (text.isNotEmpty()) vm.fullscreen = text to vm.style }, Modifier.weight(1f), Icons.Rounded.Fullscreen)
            TermButton("Share", { export { context.sharePng(FlipperNfcFile.safeName(vm.draftTitle()), it) } }, Modifier.weight(1f), Icons.Rounded.Share)
            TermButton("Save", {
                export { vm.toast(if (context.savePngToGallery(FlipperNfcFile.safeName(vm.draftTitle()) + "_" + System.currentTimeMillis() / 1000, it)) "Saved to Pictures/EMITT" else "Couldn't save the image") }
            }, Modifier.weight(1f), Icons.Rounded.Download)
        }
    }
    TermPanel(title = "Style", trailing = {
        Icon(if (styleOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.clickable { styleOpen = !styleOpen }, tint = LocalLook.current.accent)
    }) {
        AnimatedVisibility(styleOpen) {
            Column { QrStyleEditor(vm.style, { vm.style = it }, vm) }
        }
    }
}

private class StylePreset(val name: String, val make: (QrStyle, com.emitt.ui.theme.EmittLook) -> QrStyle)

private val stylePresets = listOf(
    StylePreset("Classic") { s, _ -> s.copy(fg = 0xFF000000, bg = 0xFFFFFFFF, gradient = Gradient.NONE, eyeOuter = null, eyeInner = null, modules = ModuleShape.SQUARE, eyes = EyeShape.SQUARE, moduleScale = 1f, transparentBg = false) },
    StylePreset("Theme") { s, l -> s.copy(fg = l.bg.toArgbLong(), bg = 0xFFFFFFFF, gradient = Gradient.NONE, eyeOuter = l.accent.toArgbLong().darkenFor(), eyeInner = l.bg.toArgbLong(), modules = ModuleShape.ROUNDED, eyes = EyeShape.ROUNDED, transparentBg = false) },
    StylePreset("Synth") { s, _ -> s.copy(fg = 0xFF5B0FB0, fg2 = 0xFFD4148F, bg = 0xFFFFFFFF, gradient = Gradient.LINEAR, eyeOuter = null, eyeInner = null, modules = ModuleShape.DOTS, eyes = EyeShape.LEAF, moduleScale = 0.9f, transparentBg = false) },
    StylePreset("Fluid") { s, _ -> s.copy(fg = 0xFF0B2545, bg = 0xFFF4F1E8, gradient = Gradient.NONE, eyeOuter = 0xFF13315C, eyeInner = 0xFF0B2545, modules = ModuleShape.FLUID, eyes = EyeShape.CIRCLE, moduleScale = 1f, transparentBg = false) },
    StylePreset("Terminal") { s, _ -> s.copy(fg = 0xFF0A3D1F, bg = 0xFFE9FFF0, gradient = Gradient.NONE, eyeOuter = null, eyeInner = null, modules = ModuleShape.SQUARE, eyes = EyeShape.SQUARE, moduleScale = 0.82f, transparentBg = false) },
    StylePreset("Inverted") { s, _ -> s.copy(fg = 0xFFFFFFFF, bg = 0xFF000000, gradient = Gradient.NONE, eyeOuter = null, eyeInner = null, transparentBg = false) },
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
    val pickBadge = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = runCatching {
            val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            val k = 320f / maxOf(src.width, src.height)
            val small = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true) else src
            val dir = File(context.filesDir, "badges").apply { mkdirs() }
            File(dir, UUID.randomUUID().toString() + ".png").also { f -> f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) } }.absolutePath
        }.getOrNull()
        if (path == null) vm.toast("Couldn't load that image") else onChange(s.copy(badge = Badge.IMAGE, badgeImage = path))
    }

    SectionLabel("Presets")
    ChipStrip<StylePreset?>(stylePresets, null, { it!!.name }, { onChange(it!!.make(s, look)) })

    SectionLabel("Colours")
    ColorRow("Modules", s.fg, { onChange(s.copy(fg = it)) })
    ChipGroup(Gradient.entries, s.gradient, { it.label }, { onChange(s.copy(gradient = it)) }, Modifier.padding(vertical = 4.dp))
    if (s.gradient != Gradient.NONE) ColorRow("Gradient end", s.fg2, { onChange(s.copy(fg2 = it)) })
    SwitchRow("Transparent background", s.transparentBg, { onChange(s.copy(transparentBg = it)) })
    if (!s.transparentBg) ColorRow("Background", s.bg, { onChange(s.copy(bg = it)) })
    SwitchRow("Custom eye colours", s.eyeOuter != null || s.eyeInner != null, { on ->
        onChange(if (on) s.copy(eyeOuter = s.fg, eyeInner = s.fg) else s.copy(eyeOuter = null, eyeInner = null))
    })
    if (s.eyeOuter != null || s.eyeInner != null) {
        ColorRow("Eye frame", s.eyeOuter ?: s.fg, { onChange(s.copy(eyeOuter = it)) })
        ColorRow("Eye centre", s.eyeInner ?: s.fg, { onChange(s.copy(eyeInner = it)) })
    }
    ButtonRow {
        TermButton("Swap", { onChange(s.copy(fg = s.bg, bg = s.fg)) }, Modifier.weight(1f))
        TermButton("Reset", { onChange(QrStyle(badge = s.badge, badgeText = s.badgeText, badgeImage = s.badgeImage)) }, Modifier.weight(1f))
    }

    SectionLabel("Shape")
    ChipGroup(ModuleShape.entries, s.modules, { it.label }, { onChange(s.copy(modules = it)) })
    SliderRow("Module size", s.moduleScale, 0.6f..1f, { onChange(s.copy(moduleScale = it)) }, { "${(it * 100).toInt()}%" })
    Dim("Eyes", Modifier.padding(top = 4.dp, bottom = 6.dp))
    ChipGroup(EyeShape.entries, s.eyes, { it.label }, { onChange(s.copy(eyes = it)) })

    SectionLabel("Centre badge")
    ChipGroup(Badge.entries, s.badge, { it.label }, { b ->
        if (b == Badge.IMAGE && s.badgeImage == null) pickBadge.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        else onChange(s.copy(badge = b))
    })
    if (s.badge == Badge.TEXT) TermField(s.badgeText, { onChange(s.copy(badgeText = it.take(6))) }, "Badge text (up to 6)", mono = true)
    if (s.badge == Badge.IMAGE) TermButton("Choose image", {
        pickBadge.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }, Modifier.fillMaxWidth().padding(top = 6.dp), Icons.Rounded.Image)
    if (s.badge != Badge.NONE) SliderRow("Badge size", s.badgeSize, 0.12f..0.3f, { onChange(s.copy(badgeSize = it)) }, { "${(it * 100).toInt()}%" })

    SectionLabel("Visibility")
    Dim("Error correction: higher survives scratches, glare and badges, but makes a denser code.", Modifier.padding(bottom = 6.dp))
    ChipGroup(Ec.entries, s.ec, { "${it.name} ${it.percent}%" }, { onChange(s.copy(ec = it)) })
    SliderRow("Border (quiet zone)", s.quietZone.toFloat(), 0f..8f, { onChange(s.copy(quietZone = it.toInt())) }, { "${it.toInt()} modules" }, steps = 7)
    var boost by remember { mutableStateOf(vm.settings.boostBrightness) }
    SwitchRow("Max brightness in full screen", boost, { boost = it; vm.settings.boostBrightness = it }, "Helps other phones read it from further away")
}

// ---------------- NFC ----------------

@Composable
private fun NfcOutput(vm: MainViewModel) {
    val look = LocalLook.current
    val ok = vm.payload.problem() == null
    val msg = remember(vm.payload, vm.launchApp) { if (ok) runCatching { vm.ndefFor(vm.payload) }.getOrNull() else null }
    TermPanel(title = "NFC tag") {
        if (msg == null) {
            StatusLine(Status.INFO, vm.payload.problem() ?: "Invalid payload")
        } else {
            NdefCodec.describe(msg).forEach { KvRow(it.type, it.value) }
            val size = msg.byteArrayLength
            Spacer(Modifier.height(6.dp))
            val fits = FlipperNfcFile.ntags.filter { FlipperNfcFile.footprint(msg) <= FlipperNfcFile.capacity(it) }
            StatusLine(
                if (fits.isEmpty()) Status.WARN else Status.OK,
                "$size bytes · " + if (fits.isEmpty()) "needs a tag bigger than NTAG216" else "fits ${fits.joinToString { it.flipperName }}",
            )
        }
        SectionLabel("Extras")
        if (vm.payload !is Payload.App) {
            TermField(
                vm.launchApp, { vm.launchApp = it.trim() }, "Also open app (package)", mono = true,
                supporting = "Optional Android Application Record. Leave empty for none.",
            )
        }
        ButtonRow {
            TermButton("Write to tag", { vm.startWrite() }, Modifier.weight(1f), Icons.Rounded.Nfc, primary = true, enabled = msg != null)
        }
        ButtonRow {
            TermButton("Be the tag", { vm.emulate() }, Modifier.weight(1f), Icons.Rounded.Smartphone, enabled = msg != null)
        }
        Dim(
            "Write to tag puts it on a sticker or card. Be the tag makes this phone act as one: other phones tap your phone to read it while your screen is on.",
            Modifier.padding(top = 8.dp),
        )
        Dim("Tip: NTAG213 holds about 130 characters of link, NTAG215/216 hold much more.", Modifier.padding(top = 4.dp))
        if (vm.nfcOp != null) Text("> waiting for tag…", style = MaterialTheme.typography.labelLarge, color = look.signal, modifier = Modifier.padding(top = 8.dp))
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
        if (uri != null) vm.toast(if (context.writeUri(uri, f.second)) "Saved ${f.first}" else "Couldn't save the file")
    }

    TermPanel(title = "Flipper .nfc") {
        Dim("Makes an NTAG file the Flipper can emulate: NFC → Saved → Emulate.")
        SectionLabel("Chip")
        ChipGroup(listOf(null) + FlipperNfcFile.ntags, vm.flipperChip, { it?.let { c -> "${c.flipperName} ${FlipperNfcFile.capacity(c)}B" } ?: "Auto" }, { vm.flipperChip = it })
        SectionLabel("UID")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TermField(
                uidText,
                { t ->
                    uidText = t.uppercase()
                    t.hexToBytes()?.takeIf { it.size == 7 }?.let { vm.flipperUid = it }
                },
                "7-byte UID", Modifier.weight(1f), mono = true,
                isError = uidText.hexToBytes()?.size != 7,
            )
            Spacer(Modifier.width(8.dp))
            TermButton("", { vm.flipperUid = FlipperNfcFile.randomUid() }, icon = Icons.Rounded.Casino)
        }
        Spacer(Modifier.height(4.dp))
        file.fold(
            onSuccess = { (name, text) ->
                StatusLine(Status.OK, "$name · ${text.length} bytes · ${text.lineSequence().count { it.startsWith("Page ") }} pages")
                Row(Modifier.clickable { preview = !preview }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (preview) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = look.signal)
                    Text(if (preview) "Hide file" else "Show file", style = MaterialTheme.typography.labelMedium, color = look.signal)
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
                    TermButton(if (connected) "Send to Flipper" else "Connect Flipper", {
                        if (connected) vm.sendToFlipper(name, text) else vm.tab = Tab.FLIPPER
                    }, Modifier.weight(1f), Icons.Rounded.Send, primary = true, enabled = vm.flipperProgress == null)
                }
                ButtonRow {
                    TermButton("Share", { context.shareText(name, "application/octet-stream", text) }, Modifier.weight(1f), Icons.Rounded.Share)
                    TermButton("Save file", { saveAs.launch(name) }, Modifier.weight(1f), Icons.Rounded.Download)
                }
            },
            onFailure = { StatusLine(Status.FAIL, it.message ?: "Can't build the file") },
        )
    }
}
