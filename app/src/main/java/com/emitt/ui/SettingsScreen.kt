package com.emitt.ui

import com.emitt.ui.theme.SavedColours
import com.emitt.ui.theme.Palette
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.emitt.BuildConfig
import com.emitt.HapticEvent
import com.emitt.HapticPattern
import com.emitt.Haptics
import com.emitt.LauncherIcons
import com.emitt.R
import com.emitt.UpdateWorker
import com.emitt.data.Languages
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
import com.emitt.ui.theme.ThemeConfig

const val CREATOR_GITHUB = "https://github.com/starlightjulie"
const val CREATOR_KOFI = "https://ko-fi.com/vvanadium"
const val LICENSE_URL = "${UpdateChecker.REPO_URL}/blob/main/ADDITIONAL-TERMS.md"

private fun pct(v: Float) = "${(v * 100).toInt()}%"

/** Everything about how EMITT looks: colours, background, interface and the launcher icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(vm: MainViewModel) {
    val cfg by vm.themes.config.collectAsState()
    val look = LocalLook.current
    val context = LocalContext.current
    fun set(t: (ThemeConfig) -> ThemeConfig) = vm.themes.update(t)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TermPanel(title = stringResource(R.string.colourway)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Colourways.all.forEach { cw ->
                    ColourwayTile(cw.name, cw.palette, selected = cfg.custom == null && cfg.colourway == cw.id) { set { it.copy(colourway = cw.id, custom = null) } }
                }
            }
            SectionLabel(stringResource(R.string.custom_colours))
            // Own colours and saved sets live on their own page
            Row(
                Modifier.fillMaxWidth().clickable { vm.page = Page.CUSTOM_COLOURS }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.use_my_colours), style = MaterialTheme.typography.bodyLarge)
                    Dim(stringResource(if (cfg.custom != null) R.string.custom_status_on else R.string.custom_status_off, cfg.savedColours.size))
                }
                cfg.custom?.let { p ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(end = 8.dp)) {
                        listOf(p.accent, p.accent2, p.bg, p.ink).forEach { Box(Modifier.size(14.dp).background(Color(it)).border(1.dp, look.dim.copy(alpha = 0.4f))) }
                    }
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = look.accent)
            }
        }

        TermPanel(title = stringResource(R.string.slot_bg)) {
            ChipGroup(BgStyle.entries, cfg.background, { stringResource(it.label) }, { s -> set { it.copy(background = s) } })
            if (cfg.background != BgStyle.NONE) {
                SectionLabel(stringResource(R.string.motion))
                SliderRow(stringResource(R.string.speed), cfg.bgSpeed, 0.1f..3f, { v -> set { it.copy(bgSpeed = v) } }, { "%.1f×".format(it) })
                SliderRow(stringResource(R.string.density), cfg.bgDensity, 0.25f..2f, { v -> set { it.copy(bgDensity = v) } }, { "%.2f×".format(it) })
                SliderRow(stringResource(R.string.intensity), cfg.bgIntensity, 0.05f..1f, { v -> set { it.copy(bgIntensity = v) } }, ::pct)
                SectionLabel(stringResource(R.string.colour))
                ChipGroup(BgColour.entries, cfg.bgColour, { stringResource(it.label) }, { c -> set { it.copy(bgColour = c) } })
            }
            SectionLabel("CRT")
            SwitchRow(stringResource(R.string.scanlines), cfg.scanlines, { v -> set { it.copy(scanlines = v) } })
            if (cfg.scanlines) {
                SliderRow(stringResource(R.string.scanline_strength), cfg.scanlineStrength, 0.05f..1f, { v -> set { it.copy(scanlineStrength = v) } }, ::pct)
            }
            SwitchRow(stringResource(R.string.vignette), cfg.vignette, { v -> set { it.copy(vignette = v) } }, stringResource(R.string.vignette_hint))
            SwitchRow(stringResource(R.string.flicker), cfg.flicker, { v -> set { it.copy(flicker = v) } }, stringResource(R.string.flicker_hint))
        }

        TermPanel(title = stringResource(R.string.interface_)) {
            SliderRow(stringResource(R.string.panel_opacity), cfg.panelOpacity, 0.2f..1f, { v -> set { it.copy(panelOpacity = v) } }, ::pct)
            SwitchRow(stringResource(R.string.panel_outlines), cfg.outlines, { v -> set { it.copy(outlines = v) } })
            SwitchRow(stringResource(R.string.glow), cfg.glow, { v -> set { it.copy(glow = v) } }, stringResource(R.string.glow_hint))
            SwitchRow(stringResource(R.string.blinking_cursor), cfg.cursor, { v -> set { it.copy(cursor = v) } })
            SectionLabel(stringResource(R.string.corners))
            ChipGroup(Corners.entries, cfg.corners, { stringResource(it.label) }, { c -> set { it.copy(corners = c) } })
            SectionLabel(stringResource(R.string.font))
            ChipGroup(FontStyle.entries, cfg.font, { stringResource(it.label) }, { f -> set { it.copy(font = f) } })
            SliderRow(stringResource(R.string.text_size), cfg.fontScale, 0.85f..1.3f, { v -> set { it.copy(fontScale = v) } }, ::pct, applyOnRelease = true)
            ButtonRow { TermButton(stringResource(R.string.reset_look), { set { ThemeConfig(icon = it.icon, savedColours = it.savedColours) } }, Modifier.weight(1f)) }
        }

        TermPanel(title = stringResource(R.string.app_icon)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 5,
            ) {
                LauncherIcon.entries.forEach { ic ->
                    Column(
                        Modifier.weight(1f).clickable {
                            set { it.copy(icon = ic) }
                            LauncherIcons.apply(context, ic)
                            vm.toast(R.string.icon_changed)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconPreview(ic, cfg.icon == ic)
                        Text(
                            ic.label.uppercase(), maxLines = 1,
                            style = MaterialTheme.typography.labelSmall.let { s -> if (ic.label.length > 9) s.copy(fontSize = s.fontSize * 0.85f, letterSpacing = 0.sp) else s },
                            color = if (cfg.icon == ic) look.accent else look.dim, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Your own five colours, plus named sets you can save, switch between and delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomColoursScreen(vm: MainViewModel) {
    val cfg by vm.themes.config.collectAsState()
    val look = LocalLook.current
    fun set(t: (ThemeConfig) -> ThemeConfig) = vm.themes.update(t)
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedColours?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TermPanel(title = stringResource(R.string.custom_colours)) {
            SwitchRow(
                stringResource(R.string.use_my_colours), cfg.custom != null,
                { on -> set { it.copy(custom = if (on) it.palette else null) } },
                stringResource(R.string.use_my_colours_hint),
            )
            cfg.custom?.let { p ->
                PaletteSlot.entries.forEach { slot ->
                    ColorRow(stringResource(slot.label), slot.get(p), { v -> set { it.copy(custom = slot.set(p, v)) } }, stringResource(slot.hint))
                }
                ButtonRow { TermButton(stringResource(R.string.save_set), { saving = true }, Modifier.weight(1f), icon = Icons.Rounded.Add) }
            }
        }

        TermPanel(title = stringResource(R.string.saved_sets)) {
            if (cfg.savedColours.isEmpty()) {
                Dim(stringResource(R.string.no_sets))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cfg.savedColours.forEach { s ->
                        ColourwayTile(s.name, s.palette, selected = cfg.custom == s.palette, width = 150.dp, onDelete = { deleting = s }) {
                            set { it.copy(custom = s.palette) }
                        }
                    }
                }
                Dim(stringResource(R.string.sets_hint), Modifier.padding(top = 8.dp))
            }
        }
    }

    if (saving) {
        val suggested = stringResource(R.string.set_default_name, cfg.savedColours.size + 1)
        var name by remember { mutableStateOf(suggested) }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text(stringResource(R.string.save_set).uppercase(), style = MaterialTheme.typography.titleMedium) },
            text = { TermField(name, { name = it.take(24) }, stringResource(R.string.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim().ifEmpty { suggested }
                    cfg.custom?.let { p ->
                        // Saving under an existing name replaces that set
                        set { c -> c.copy(savedColours = c.savedColours.filterNot { it.name.equals(n, ignoreCase = true) } + SavedColours(n, p)) }
                        vm.toast(R.string.set_saved, n)
                    }
                    saving = false
                }) { Text(stringResource(R.string.save).uppercase(), color = look.accent) }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text(stringResource(R.string.cancel).uppercase()) } },
        )
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_q).uppercase(), style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(R.string.delete_set_confirm, s.name)) },
            confirmButton = {
                TextButton(onClick = {
                    set { c -> c.copy(savedColours = c.savedColours - s) }
                    deleting = null
                }) { Text(stringResource(R.string.delete).uppercase(), color = look.fail) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel).uppercase()) } },
        )
    }
}

/** App behaviour: language, updates, vibration, and about. */
@Composable
fun SettingsScreen(vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LanguagePanel()
        UpdatesPanel(vm)
        VibrationPanel(vm)
        AboutPanel()
    }
}

@Composable
private fun LanguagePanel() {
    val look = LocalLook.current
    var open by rememberSaveable { mutableStateOf(false) }
    val current = Languages.currentLanguage()
    TermPanel(title = stringResource(R.string.language), trailing = {
        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.clickable { open = !open }, tint = look.accent)
    }) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(current?.nativeName ?: stringResource(R.string.language_system), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Dim(current?.tag.orEmpty())
        }
        if (open) {
            LanguageRow(stringResource(R.string.language_system), null, current == null) { Languages.apply("") }
            Languages.all.forEach { lang -> LanguageRow(lang.nativeName, lang.tag, current?.tag == lang.tag) { Languages.apply(lang.tag) } }
        }
        Dim(stringResource(R.string.language_hint), Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LanguageRow(name: String, tag: String?, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) look.accent else look.ink)
            if (tag != null) Dim(tag)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = look.accent)
    }
}

@Composable
private fun ColourwayTile(name: String, p: Palette, selected: Boolean, width: Dp = 96.dp, onDelete: (() -> Unit)? = null, onClick: () -> Unit) {
    val look = LocalLook.current
    Box(
        Modifier
            .width(width)
            .background(Color(p.bg), look.controlShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) look.accent else look.dim.copy(alpha = 0.4f), look.controlShape)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(p.accent, p.accent2, p.surface, p.ink).forEach { Box(Modifier.size(16.dp).background(Color(it))) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                name.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(p.ink), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = if (onDelete != null) 18.dp else 0.dp),
            )
        }
        if (onDelete != null) {
            Icon(
                Icons.Rounded.Close, stringResource(R.string.delete),
                Modifier.align(Alignment.BottomEnd).clickable(onClick = onDelete).padding(4.dp).size(16.dp),
                tint = Color(p.ink),
            )
        }
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
private fun VibrationPanel(vm: MainViewModel) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(vm.settings.haptics) }
    var strength by remember { mutableStateOf(vm.settings.hapticStrength) }
    TermPanel(title = stringResource(R.string.vibration)) {
        SwitchRow(stringResource(R.string.vibration), on, { on = it; vm.settings.haptics = it }, stringResource(R.string.vibration_hint))
        if (on) {
            SliderRow(stringResource(R.string.strength), strength, 0.1f..1f, { strength = it; vm.settings.hapticStrength = it }, ::pct)
            HapticEvent.entries.forEach { e ->
                var pattern by remember { mutableStateOf(vm.settings.hapticPattern(e)) }
                SectionLabel(stringResource(e.label))
                Dim(stringResource(e.hint), Modifier.padding(bottom = 6.dp))
                ChipStrip(HapticPattern.entries, pattern, { stringResource(it.label) }, {
                    pattern = it
                    vm.settings.setHapticPattern(e, it)
                    Haptics.play(context, it, strength)
                })
            }
            Dim(stringResource(R.string.tap_pattern), Modifier.padding(top = 8.dp))
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

    TermPanel(title = stringResource(R.string.updates)) {
        SwitchRow(stringResource(R.string.check_releases), check, {
            check = it; vm.settings.checkUpdates = it; UpdateWorker.schedule(context, replace = true)
        }, stringResource(R.string.check_releases_hint))
        if (check) {
            SectionLabel(stringResource(R.string.check_every))
            ChipGroup(listOf(6, 12, 24, 72), hours, {
                if (it < 24) stringResource(R.string.n_hours, it) else stringResource(R.string.n_days, it / 24)
            }, {
                hours = it; vm.settings.updateIntervalHours = it; UpdateWorker.schedule(context, replace = true)
            })
            if (!notif) {
                StatusLine(Status.WARN, stringResource(R.string.notifications_off))
                ButtonRow {
                    TermButton(stringResource(R.string.allow_notifications), { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.weight(1f))
                }
            }
        }
        vm.updateStatus?.let { StatusLine(if (vm.update != null) Status.OK else Status.INFO, it.text()) }
        ButtonRow {
            TermButton(stringResource(R.string.check_now), { vm.checkForUpdate(manual = true) }, Modifier.weight(1f), Icons.Rounded.SystemUpdate)
            if (vm.update != null) {
                TermButton(stringResource(R.string.get_it), { context.openUrl(UpdateChecker.LATEST_RELEASE_URL) }, Modifier.weight(1f), primary = true)
            }
        }
    }
}

@Composable
private fun AboutPanel() {
    val context = LocalContext.current
    val look = LocalLook.current
    val official = remember { OfficialBuild.isOfficial(context) }
    TermPanel(title = stringResource(R.string.about)) {
        Text("EMITT", style = glowing(MaterialTheme.typography.headlineSmall), color = look.accent)
        Dim(stringResource(R.string.tagline))
        Spacer(Modifier.height(8.dp))
        KvRow(stringResource(R.string.version), BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) " (debug)" else "")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Icon(if (official) Icons.Rounded.Verified else Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = if (official) look.ok else look.warn)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (official) R.string.official_build else R.string.unofficial_build),
                style = MaterialTheme.typography.bodySmall, color = if (official) look.ok else look.warn,
            )
        }
        Dim(stringResource(R.string.made_by_line), Modifier.padding(top = 4.dp))
        ButtonRow {
            TermButton("GitHub", { context.openUrl(CREATOR_GITHUB) }, Modifier.weight(1f), Icons.Rounded.Person)
            TermButton("Ko-fi", { context.openUrl(CREATOR_KOFI) }, Modifier.weight(1f), Icons.Rounded.LocalCafe, primary = true)
        }
        ButtonRow {
            TermButton(stringResource(R.string.source), { context.openUrl(UpdateChecker.REPO_URL) }, Modifier.weight(1f), Icons.Rounded.Code)
            TermButton(stringResource(R.string.license), { context.openUrl(LICENSE_URL) }, Modifier.weight(1f), Icons.Rounded.Gavel)
        }
        Dim(stringResource(R.string.flipper_trademark), Modifier.padding(top = 10.dp))
    }
}
