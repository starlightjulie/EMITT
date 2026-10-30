package com.emitt.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.emitt.BuildConfig
import com.emitt.LauncherIcons
import com.emitt.UpdateWorker
import com.emitt.data.OfficialBuild
import com.emitt.data.UpdateChecker
import com.emitt.ui.theme.BgColour
import com.emitt.ui.theme.BgStyle
import com.emitt.ui.theme.Colourway
import com.emitt.ui.theme.Colourways
import com.emitt.ui.theme.Corners
import com.emitt.ui.theme.FontStyle
import com.emitt.ui.theme.LauncherIcon
import com.emitt.ui.theme.LocalLook
import com.emitt.ui.theme.PaletteSlot

const val CREATOR_GITHUB = "https://github.com/starlightjulie"
const val CREATOR_KOFI = "https://ko-fi.com/vvanadium"
const val LICENSE_URL = "${UpdateChecker.REPO_URL}/blob/main/ADDITIONAL-TERMS.md"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val cfg by vm.themes.config.collectAsState()
    val look = LocalLook.current
    val context = LocalContext.current
    fun set(t: (com.emitt.ui.theme.ThemeConfig) -> com.emitt.ui.theme.ThemeConfig) = vm.themes.update(t)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TermPanel(title = "Colourway") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Colourways.all.forEach { cw ->
                    ColourwayTile(cw, selected = cfg.custom == null && cfg.colourway == cw.id) { set { it.copy(colourway = cw.id, custom = null) } }
                }
            }
            SectionLabel("Custom colours")
            SwitchRow("Use my own colours", cfg.custom != null, { on -> set { it.copy(custom = if (on) it.palette else null) } }, "Starts from the current colourway")
            cfg.custom?.let { p ->
                PaletteSlot.entries.forEach { slot ->
                    ColorRow(slot.label, slot.get(p), { v -> set { it.copy(custom = slot.set(p, v)) } }, slot.hint)
                }
            }
        }

        TermPanel(title = "Background") {
            ChipGroup(BgStyle.entries, cfg.background, { it.label }, { s -> set { it.copy(background = s) } })
            if (cfg.background != BgStyle.NONE) {
                SectionLabel("Motion")
                SliderRow("Speed", cfg.bgSpeed, 0.1f..3f, { v -> set { it.copy(bgSpeed = v) } }, { "%.1f×".format(it) })
                SliderRow("Density", cfg.bgDensity, 0.25f..2f, { v -> set { it.copy(bgDensity = v) } }, { "%.2f×".format(it) })
                SliderRow("Intensity", cfg.bgIntensity, 0.05f..1f, { v -> set { it.copy(bgIntensity = v) } }, { "${(it * 100).toInt()}%" })
                SectionLabel("Colour")
                ChipGroup(BgColour.entries, cfg.bgColour, { it.label }, { c -> set { it.copy(bgColour = c) } })
            }
            SectionLabel("CRT")
            SwitchRow("Scanlines", cfg.scanlines, { v -> set { it.copy(scanlines = v) } })
            if (cfg.scanlines) SliderRow("Scanline strength", cfg.scanlineStrength, 0.05f..1f, { v -> set { it.copy(scanlineStrength = v) } }, { "${(it * 100).toInt()}%" })
            SwitchRow("Vignette", cfg.vignette, { v -> set { it.copy(vignette = v) } }, "Darkens the screen edges")
            SwitchRow("Flicker", cfg.flicker, { v -> set { it.copy(flicker = v) } }, "Faint CRT shimmer")
        }

        TermPanel(title = "Interface") {
            SliderRow("Panel opacity", cfg.panelOpacity, 0.2f..1f, { v -> set { it.copy(panelOpacity = v) } }, { "${(it * 100).toInt()}%" })
            SwitchRow("Panel outlines", cfg.outlines, { v -> set { it.copy(outlines = v) } })
            SwitchRow("Glow", cfg.glow, { v -> set { it.copy(glow = v) } }, "Neon glow on titles (dark colourways)")
            SwitchRow("Blinking cursor", cfg.cursor, { v -> set { it.copy(cursor = v) } })
            SectionLabel("Corners")
            ChipGroup(Corners.entries, cfg.corners, { it.label }, { c -> set { it.copy(corners = c) } })
            SectionLabel("Font")
            ChipGroup(FontStyle.entries, cfg.font, { it.label }, { f -> set { it.copy(font = f) } })
            SliderRow("Text size", cfg.fontScale, 0.85f..1.3f, { v -> set { it.copy(fontScale = v) } }, { "${(it * 100).toInt()}%" }, applyOnRelease = true)
            ButtonRow { TermButton("Reset look", { set { com.emitt.ui.theme.ThemeConfig(icon = it.icon) } }, Modifier.weight(1f)) }
        }

        TermPanel(title = "App icon") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LauncherIcon.entries.forEach { ic ->
                    Column(
                        Modifier.weight(1f).clickable {
                            set { it.copy(icon = ic) }
                            LauncherIcons.apply(context, ic)
                            vm.toast("Icon changed. Your launcher may take a moment to update.")
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconPreview(ic, cfg.icon == ic)
                        Text(ic.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (cfg.icon == ic) look.accent else look.dim, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        UpdatesPanel(vm)

        TermPanel(title = "Behaviour") {
            var haptics by remember { mutableStateOf(vm.settings.haptics) }
            SwitchRow("Vibrate on tag read/write", haptics, { haptics = it; vm.settings.haptics = it })
        }

        AboutPanel()
    }
}

@Composable
private fun ColourwayTile(cw: Colourway, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    val p = cw.palette
    Column(
        Modifier
            .width(96.dp)
            .background(Color(p.bg), look.controlShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) look.accent else look.dim.copy(alpha = 0.4f), look.controlShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(p.accent, p.accent2, p.surface, p.ink).forEach { Box(Modifier.size(16.dp).background(Color(it))) }
        }
        Spacer(Modifier.height(6.dp))
        Text(cw.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(p.ink), maxLines = 1)
    }
}

@Composable
private fun IconPreview(ic: LauncherIcon, selected: Boolean) {
    val look = LocalLook.current
    Canvas(
        Modifier.size(56.dp)
            .border(if (selected) 2.dp else 0.dp, if (selected) look.accent else Color.Transparent, look.controlShape)
            .padding(3.dp),
    ) {
        val k = size.width / 120f
        drawRoundRect(Color(ic.bg), cornerRadius = androidx.compose.ui.geometry.CornerRadius(26 * k))
        drawRect(Color(ic.core), Offset(50 * k, 50 * k), Size(20 * k, 20 * k))
        val rings = listOf(
            floatArrayOf(42f, 48f, 42f, 42f, 48f, 42f) to 1f,
            floatArrayOf(32f, 42f, 32f, 32f, 42f, 32f) to 0.6f,
            floatArrayOf(22f, 36f, 22f, 22f, 36f, 22f) to 0.3f,
        )
        for ((pt, alpha) in rings) {
            val path = Path()
            for (rot in 0 until 4) {
                fun p(x: Float, y: Float) = when (rot) { 0 -> Offset(x, y); 1 -> Offset(120 - y, x); 2 -> Offset(120 - x, 120 - y); else -> Offset(y, 120 - x) } * k
                val a = p(pt[0], pt[1]); val b = p(pt[2], pt[3]); val c = p(pt[4], pt[5])
                path.moveTo(a.x, a.y); path.lineTo(b.x, b.y); path.lineTo(c.x, c.y)
            }
            drawPath(path, Color(ic.ring).copy(alpha = alpha), style = Stroke(5 * k))
        }
    }
}

@Composable
private fun UpdatesPanel(vm: MainViewModel) {
    val context = LocalContext.current
    var check by remember { mutableStateOf(vm.settings.checkUpdates) }
    var hours by remember { mutableStateOf(vm.settings.updateIntervalHours) }
    fun notifGranted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notif by remember { mutableStateOf(notifGranted()) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notif = it }

    TermPanel(title = "Updates") {
        SwitchRow("Check for new releases", check, {
            check = it; vm.settings.checkUpdates = it; UpdateWorker.schedule(context, replace = true)
        }, "Asks GitHub's public API. Nothing about you is sent.")
        if (check) {
            SectionLabel("Check every")
            ChipGroup(listOf(6, 12, 24, 72), hours, { if (it < 24) "${it}h" else "${it / 24}d" }, {
                hours = it; vm.settings.updateIntervalHours = it; UpdateWorker.schedule(context, replace = true)
            })
            if (!notif) {
                StatusLine(Status.WARN, "Notifications are off, so you'll only see updates inside the app.")
                ButtonRow { TermButton("Allow notifications", { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.weight(1f)) }
            }
        }
        vm.updateStatus?.let { StatusLine(if (vm.update != null) Status.OK else Status.INFO, it) }
        ButtonRow {
            TermButton("Check now", { vm.checkForUpdate(manual = true) }, Modifier.weight(1f), Icons.Rounded.SystemUpdate)
            if (vm.update != null) TermButton("Get it", { context.openUrl(UpdateChecker.LATEST_RELEASE_URL) }, Modifier.weight(1f), primary = true)
        }
    }
}

@Composable
private fun AboutPanel() {
    val context = LocalContext.current
    val look = LocalLook.current
    val official = remember { OfficialBuild.isOfficial(context) }
    TermPanel(title = "About") {
        Text("EMITT", style = glowing(MaterialTheme.typography.headlineSmall), color = look.accent)
        Dim("QR codes, NFC tags and Flipper Zero files from one app.")
        Spacer(Modifier.height(8.dp))
        KvRow("version", BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) " (debug)" else "")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Icon(if (official) Icons.Rounded.Verified else Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = if (official) look.ok else look.warn)
            Spacer(Modifier.width(8.dp))
            Text(
                if (official) "Official build" else "Unofficial build: not signed by StarlightJulie",
                style = MaterialTheme.typography.bodySmall, color = if (official) look.ok else look.warn,
            )
        }
        Dim("Made by StarlightJulie. Free and open source under GPLv3.", Modifier.padding(top = 4.dp))
        ButtonRow {
            TermButton("GitHub", { context.openUrl(CREATOR_GITHUB) }, Modifier.weight(1f), Icons.Rounded.Person)
            TermButton("Ko-fi", { context.openUrl(CREATOR_KOFI) }, Modifier.weight(1f), Icons.Rounded.LocalCafe, primary = true)
        }
        ButtonRow {
            TermButton("Source", { context.openUrl(UpdateChecker.REPO_URL) }, Modifier.weight(1f), Icons.Rounded.Code)
            TermButton("License", { context.openUrl(LICENSE_URL) }, Modifier.weight(1f), Icons.Rounded.Gavel)
        }
        Dim("Flipper Zero is a trademark of Flipper Devices Inc. EMITT isn't affiliated with or endorsed by them.", Modifier.padding(top = 10.dp))
    }
}
