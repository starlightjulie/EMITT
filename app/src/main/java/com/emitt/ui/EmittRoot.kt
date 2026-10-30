package com.emitt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emitt.data.UpdateChecker
import com.emitt.flipper.FlipperLink
import com.emitt.flipper.FlipperState
import com.emitt.ui.theme.CrtOverlay
import com.emitt.ui.theme.EmittBackground
import com.emitt.ui.theme.LocalLook

private fun Tab.icon(): ImageVector = when (this) {
    Tab.CREATE -> Icons.Rounded.Edit
    Tab.SCAN -> Icons.Rounded.QrCodeScanner
    Tab.TAGS -> Icons.Rounded.Nfc
    Tab.FLIPPER -> Icons.Rounded.SdCard
    Tab.LIBRARY -> Icons.Rounded.Bookmarks
}

@Composable
fun EmittRoot(vm: MainViewModel) {
    val look = LocalLook.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(it) } }

    BackHandler(enabled = vm.fullscreen != null || vm.nfcOp != null || vm.settingsOpen || vm.tab != Tab.CREATE) {
        when {
            vm.fullscreen != null -> vm.fullscreen = null
            vm.nfcOp != null -> vm.nfcOp = null
            vm.settingsOpen -> vm.settingsOpen = false
            else -> vm.tab = Tab.CREATE
        }
    }

    Box(Modifier.fillMaxSize()) {
        EmittBackground()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            TopBar(vm)
            val scroll = rememberScrollState()
            LaunchedEffect(vm.tab, vm.settingsOpen) { scroll.scrollTo(0) }
            Column(
                Modifier.weight(1f).fillMaxWidth().imePadding().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                vm.update?.let { UpdateBanner(it.version) { vm.update = null } }
                if (vm.settingsOpen) {
                    SettingsScreen(vm)
                } else {
                    when (vm.tab) {
                        Tab.CREATE -> CreateScreen(vm)
                        Tab.SCAN -> ScanScreen(vm)
                        Tab.TAGS -> TagsScreen(vm)
                        Tab.FLIPPER -> FlipperScreen(vm)
                        Tab.LIBRARY -> LibraryScreen(vm)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            NfcPrompt(vm)
            BottomBar(vm)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
        CrtOverlay()
        vm.fullscreen?.let { (text, style) ->
            FullscreenCode(text, style, null, vm.settings.boostBrightness) { vm.fullscreen = null }
        }
    }
}

@Composable
private fun TopBar(vm: MainViewModel) {
    val look = LocalLook.current
    val link by FlipperLink.state.collectAsState()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (vm.settingsOpen) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clickable { vm.settingsOpen = false }.padding(end = 10.dp), tint = look.accent)
        }
        Text("EMITT", style = glowing(MaterialTheme.typography.titleLarge), color = look.accent)
        Text(
            "://" + if (vm.settingsOpen) "settings" else vm.tab.path,
            style = MaterialTheme.typography.titleLarge,
            color = look.signal,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        BlinkCursor()
        Spacer(Modifier.weight(1f))
        if (link is FlipperState.Ready) {
            Row(
                Modifier.clickable { vm.settingsOpen = false; vm.tab = Tab.FLIPPER }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(look.ok))
                Spacer(Modifier.width(6.dp))
                Text("FLP", style = MaterialTheme.typography.labelSmall, color = look.ok)
            }
        }
        if (!vm.settingsOpen) {
            Icon(Icons.Rounded.Settings, "Settings", Modifier.clickable { vm.settingsOpen = true }.padding(10.dp), tint = look.accent)
        }
    }
}

@Composable
private fun BottomBar(vm: MainViewModel) {
    val look = LocalLook.current
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(look.panel.copy(alpha = (look.config.panelOpacity + 0.1f).coerceAtMost(1f)))
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(look.accent.copy(alpha = 0.5f)))
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dim("made by ")
            Text(
                "StarlightJulie",
                style = MaterialTheme.typography.labelSmall,
                color = look.accent,
                modifier = Modifier.clickable { context.openUrl(CREATOR_GITHUB) }.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Dim("·")
            Row(
                Modifier.clickable { context.openUrl(CREATOR_KOFI) }.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.LocalCafe, null, Modifier.size(12.dp), tint = look.accent)
                Spacer(Modifier.width(3.dp))
                Text("Ko-fi", style = MaterialTheme.typography.labelSmall, color = look.accent)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Tab.entries.forEach { t ->
                val sel = t == vm.tab && !vm.settingsOpen
                Column(
                    Modifier.weight(1f).clickable { vm.settingsOpen = false; vm.tab = t }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(28.dp).height(2.dp).background(if (sel) look.accent else androidx.compose.ui.graphics.Color.Transparent))
                    Spacer(Modifier.height(6.dp))
                    Icon(t.icon(), t.label, tint = if (sel) look.accent else look.dim)
                    Text(
                        t.label,
                        style = if (sel) glowing(MaterialTheme.typography.labelSmall) else MaterialTheme.typography.labelSmall,
                        color = if (sel) look.accent else look.dim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Shown while EMITT is waiting for a tag. The bracket mark pulses outward like it's transmitting. */
@Composable
private fun NfcPrompt(vm: MainViewModel) {
    val look = LocalLook.current
    val op = vm.nfcOp
    AnimatedVisibility(op != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val t = rememberInfiniteTransition(label = "emit")
        val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "p")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .background(look.panel.copy(alpha = 0.97f), look.panelShape)
                .border(1.dp, look.signal, look.panelShape)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(44.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                drawRect(look.accent, Offset(c.x - 4.dp.toPx(), c.y - 4.dp.toPx()), androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()))
                for (k in 0 until 3) {
                    val f = (p + k / 3f) % 1f
                    val s = 6.dp.toPx() + f * size.width / 2
                    val arm = s * 0.4f
                    val path = Path().apply {
                        moveTo(c.x - s, c.y - s + arm); lineTo(c.x - s, c.y - s); lineTo(c.x - s + arm, c.y - s)
                        moveTo(c.x + s - arm, c.y - s); lineTo(c.x + s, c.y - s); lineTo(c.x + s, c.y - s + arm)
                        moveTo(c.x + s, c.y + s - arm); lineTo(c.x + s, c.y + s); lineTo(c.x + s - arm, c.y + s)
                        moveTo(c.x - s + arm, c.y + s); lineTo(c.x - s, c.y + s); lineTo(c.x - s, c.y + s - arm)
                    }
                    drawPath(path, look.signal.copy(alpha = 1f - f), style = Stroke(2.dp.toPx()))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("AWAITING TAG", style = MaterialTheme.typography.labelLarge, color = look.signal)
                Text(op?.prompt.orEmpty(), style = MaterialTheme.typography.bodySmall)
                (vm.lastResult as? com.emitt.nfc.TagResult.Failed)?.let { StatusLine(Status.FAIL, it.reason) }
            }
            TermButton("Cancel", { vm.nfcOp = null })
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onDismiss: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    TermPanel(title = "Update available", trailing = {
        Text("✕", color = look.dim, modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
    }) {
        StatusLine(Status.OK, "EMITT $version is out.")
        ButtonRow { TermButton("Open release", { context.openUrl(UpdateChecker.LATEST_RELEASE_URL) }, Modifier.weight(1f), primary = true) }
    }
}
