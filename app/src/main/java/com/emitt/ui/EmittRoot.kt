package com.emitt.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.alpha
import com.emitt.ui.theme.reduceMotion
import kotlinx.coroutines.delay
import kotlin.math.sign
import androidx.compose.ui.res.stringResource
import com.emitt.R
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import com.emitt.data.Encoding
import com.emitt.data.UpdateChecker
import com.emitt.nfc.Emulator
import kotlin.math.roundToInt
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
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(it.get(context)) } }

    val emulating = Emulator.state.collectAsState().value != null
    BackHandler(enabled = vm.fullscreen != null || vm.nfcOp != null || vm.settingsOpen || vm.tab != Tab.CREATE || (vm.tagModeOpen && emulating)) {
        when {
            vm.fullscreen != null -> vm.fullscreen = null
            vm.tagModeOpen && emulating -> vm.tagModeOpen = false
            vm.nfcOp != null -> vm.nfcOp = null
            vm.settingsOpen -> vm.page = vm.page?.parent
            else -> vm.tab = Tab.CREATE
        }
    }

    Box(Modifier.fillMaxSize()) {
        EmittBackground()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            TopBar(vm)
            val scroll = rememberScrollState()
            LaunchedEffect(vm.tab, vm.page) { scroll.scrollTo(0) }
            Column(
                Modifier.weight(1f).fillMaxWidth().imePadding().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                vm.update?.let { UpdateBanner(it.version) { vm.update = null } }
                val still = reduceMotion()
                AnimatedContent(
                    targetState = vm.page to vm.tab,
                    transitionSpec = {
                        if (still) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            // Slide a little in the direction of travel: along the tabs, or into a sub-page
                            val dir = (screenRank(targetState.first, targetState.second) - screenRank(initialState.first, initialState.second)).sign
                            (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(260)) { it / 12 * dir }) togetherWith
                                (fadeOut(tween(120)) + slideOutHorizontally(tween(260)) { -it / 12 * dir }) using
                                SizeTransform(clip = false) { _, _ -> snap() }
                        }
                    },
                    label = "screen",
                ) { (page, tab) ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (page) {
                            Page.APPEARANCE -> AppearanceScreen(vm)
                            Page.CUSTOM_COLOURS -> CustomColoursScreen(vm)
                            Page.SETTINGS -> SettingsScreen(vm)
                            null -> when (tab) {
                                Tab.CREATE -> CreateScreen(vm)
                                Tab.SCAN -> ScanScreen(vm)
                                Tab.TAGS -> TagsScreen(vm)
                                Tab.FLIPPER -> FlipperScreen(vm)
                                Tab.LIBRARY -> LibraryScreen(vm)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            NfcPrompt(vm)
            EmulationBar(vm)
            BottomBar(vm)
        }
        FloatingQr(vm, Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(top = 60.dp, end = 12.dp))
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
        CrtOverlay()
        if (vm.tagModeOpen) TagMode(vm)
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
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), Modifier.clickable { vm.page = vm.page?.parent }.padding(end = 10.dp), tint = look.accent)
        }
        // The path is a URL-like name, so it reads left to right even in Arabic and Urdu.
        // Takes whatever width the icons leave, so a long path clips instead of pushing them off screen
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = if (rtl) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("EMITT", style = glowing(MaterialTheme.typography.titleLarge), color = look.accent)
                Text(
                    "://" + typed(vm.page?.path ?: vm.tab.path),
                    style = MaterialTheme.typography.titleLarge,
                    color = look.signal,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f, fill = false),
                )
                BlinkCursor()
            }
        }
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
        Icon(
            Icons.Rounded.Palette, stringResource(R.string.appearance),
            Modifier.clickable { vm.page = if (vm.page == Page.APPEARANCE) null else Page.APPEARANCE }.padding(10.dp),
            tint = if (vm.page == Page.APPEARANCE || vm.page == Page.CUSTOM_COLOURS) look.signal else look.accent,
        )
        Icon(
            Icons.Rounded.Settings, stringResource(R.string.settings),
            Modifier.clickable { vm.page = if (vm.page == Page.SETTINGS) null else Page.SETTINGS }.padding(10.dp),
            tint = if (vm.page == Page.SETTINGS) look.signal else look.accent,
        )
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
            Dim(stringResource(R.string.made_by) + " ")
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
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val slot = maxWidth / Tab.entries.size
            val still = reduceMotion()
            val x by animateDpAsState(
                slot * vm.tab.ordinal + (slot - 28.dp) / 2,
                if (still) snap() else spring(dampingRatio = 0.75f, stiffness = 420f),
                label = "tabIndicator",
            )
            val shown by animateFloatAsState(if (vm.settingsOpen) 0f else 1f, tween(if (still) 0 else 180), label = "tabIndicatorAlpha")
            Row(Modifier.fillMaxWidth()) {
                Tab.entries.forEach { t ->
                    val sel = t == vm.tab && !vm.settingsOpen
                    val tint by animateColorAsState(if (sel) look.accent else look.dim, tween(if (still) 0 else 200), label = "tabTint")
                    Column(
                        Modifier.weight(1f).clickable { vm.settingsOpen = false; vm.tab = t }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Icon(t.icon(), stringResource(t.label), tint = tint)
                        Text(
                            stringResource(t.label).uppercase(),
                            style = if (sel) glowing(MaterialTheme.typography.labelSmall) else MaterialTheme.typography.labelSmall,
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(Modifier.offset(x = x, y = 8.dp).width(28.dp).height(2.dp).alpha(shown).background(look.accent))
        }
    }
}

/** The bracket mark pulsing outward, like it's transmitting. */
@Composable
private fun EmitPulse(ring: androidx.compose.ui.graphics.Color, core: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val t = rememberInfiniteTransition(label = "emit")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "p")
    val k = size / 44.dp
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val half = 4.dp.toPx() * k
        drawRect(core, Offset(c.x - half, c.y - half), androidx.compose.ui.geometry.Size(half * 2, half * 2))
        for (k2 in 0 until 3) {
            val f = (p + k2 / 3f) % 1f
            val s = 6.dp.toPx() * k + f * this.size.width / 2
            val arm = s * 0.4f
            val path = Path().apply {
                moveTo(c.x - s, c.y - s + arm); lineTo(c.x - s, c.y - s); lineTo(c.x - s + arm, c.y - s)
                moveTo(c.x + s - arm, c.y - s); lineTo(c.x + s, c.y - s); lineTo(c.x + s, c.y - s + arm)
                moveTo(c.x + s, c.y + s - arm); lineTo(c.x + s, c.y + s); lineTo(c.x + s - arm, c.y + s)
                moveTo(c.x - s + arm, c.y + s); lineTo(c.x - s, c.y + s); lineTo(c.x - s, c.y + s - arm)
            }
            drawPath(path, ring.copy(alpha = 1f - f), style = Stroke(2.dp.toPx() * k.coerceAtMost(3f)))
        }
    }
}

/**
 * Full-screen "you are a tag" view. Keeping EMITT in front matters: on phones with a built-in
 * tag service (Samsung's "Embedded tag") EMITT only wins NFC readers while it's on screen, and
 * readers that probe for payment cards can pop Google Wallet over a backgrounded app.
 */
@Composable
private fun TagMode(vm: MainViewModel) {
    val look = LocalLook.current
    val emu by Emulator.state.collectAsState()
    val e = emu ?: return
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    Box(
        Modifier.fillMaxSize().background(look.bg).pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            EmitPulse(look.ok, look.accent, 220.dp)
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.phone_is_tag).uppercase(), style = glowing(MaterialTheme.typography.headlineSmall, look.ok), color = look.ok)
            Spacer(Modifier.height(8.dp))
            Text(e.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            StatusLine(
                if (e.reads > 0) Status.OK else Status.INFO,
                if (e.reads == 0) stringResource(R.string.waiting_reader) else stringResource(R.string.reads, e.reads),
            )
            Spacer(Modifier.height(22.dp))
            Dim(stringResource(R.string.tagmode_hold), Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(6.dp))
            Dim(stringResource(R.string.tagmode_keep_open), Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(6.dp))
            Dim(stringResource(R.string.tagmode_builtin), Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(6.dp))
            Dim(stringResource(R.string.tagmode_flipper), Modifier.padding(horizontal = 12.dp))
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TermButton(stringResource(R.string.minimise), { vm.tagModeOpen = false }, Modifier.weight(1f))
            TermButton(stringResource(R.string.stop), { vm.stopEmulating() }, Modifier.weight(1f), danger = true, primary = true)
        }
    }
}

/** A bottom bar with the pulse, a title, details and one action. */
@Composable
private fun PulseBar(
    title: String,
    color: androidx.compose.ui.graphics.Color,
    action: String,
    onAction: () -> Unit,
    onOpen: (() -> Unit)? = null,
    details: @Composable () -> Unit,
) {
    val look = LocalLook.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .background(look.panel.copy(alpha = 0.97f), look.panelShape)
            .border(1.dp, color, look.panelShape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmitPulse(color, look.accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = color)
            details()
        }
        TermButton(action, onAction)
    }
}

/** Shown while EMITT is waiting for a tag. */
@Composable
private fun NfcPrompt(vm: MainViewModel) {
    val look = LocalLook.current
    val op = vm.nfcOp
    AnimatedVisibility(op != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        PulseBar(stringResource(R.string.awaiting_tag).uppercase(), look.signal, stringResource(R.string.cancel), { vm.nfcOp = null }) {
            Text(op?.prompt?.text().orEmpty(), style = MaterialTheme.typography.bodySmall)
            (vm.lastResult as? com.emitt.nfc.TagResult.Failed)?.let { StatusLine(Status.FAIL, it.reason.text()) }
        }
    }
}

/** Shown while the phone itself is acting as a tag. */
@Composable
private fun EmulationBar(vm: MainViewModel) {
    val look = LocalLook.current
    val emu by Emulator.state.collectAsState()
    AnimatedVisibility(emu != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val e = emu ?: return@AnimatedVisibility
        PulseBar(stringResource(R.string.phone_is_tag).uppercase(), look.ok, stringResource(R.string.stop), vm::stopEmulating, onOpen = { vm.tagModeOpen = true }) {
            Text(e.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Dim(if (e.reads == 0) stringResource(R.string.bar_hold) else stringResource(R.string.reads, e.reads))
        }
    }
}

/**
 * When the QR preview scrolls out of view while styling, a small copy swoops down into the corner
 * so changes stay visible. Drag it anywhere; tap it for full screen.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FloatingQr(vm: MainViewModel, modifier: Modifier) {
    val look = LocalLook.current
    val health = vm.liveQr
    // Only while styling: the Style panel is on screen and nobody is typing
    val typing = WindowInsets.isImeVisible
    val show = vm.previewOffscreen && vm.styleVisible && !typing && !vm.settingsOpen && vm.tab == Tab.CREATE &&
        vm.output == Output.QR && health?.bitmap != null && vm.fullscreen == null
    var drag by remember { mutableStateOf(Offset.Zero) }
    val swoop = spring<IntOffset>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    AnimatedVisibility(
        show,
        modifier = modifier.offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) },
        enter = slideInVertically(swoop) { -it * 2 } +
            scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.35f, transformOrigin = TransformOrigin(1f, 0f)) +
            fadeIn(tween(150)),
        exit = slideOutVertically(tween(220)) { -it * 2 } + scaleOut(tween(220), targetScale = 0.35f, transformOrigin = TransformOrigin(1f, 0f)) + fadeOut(tween(220)),
    ) {
        Column(
            Modifier
                .width(144.dp)
                .background(look.panel.copy(alpha = 0.97f), look.controlShape)
                .border(1.dp, look.accent, look.controlShape)
                .pointerInput(Unit) { detectDragGestures { change, d -> change.consume(); drag += d } }
                .clickable { vm.fullscreen = Encoding.toQrText(vm.payload) to vm.style }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Crossfade(health?.bitmap, animationSpec = tween(160), label = "qr") { bmp ->
                if (bmp != null) Image(bmp.asImageBitmap(), stringResource(R.string.out_qr), Modifier.size(128.dp))
            }
            val ok = health?.readable == true
            Text(
                if (ok) "[ OK ] " + stringResource(R.string.scans).uppercase() else "[FAIL] " + stringResource(R.string.check).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) look.ok else look.fail,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onDismiss: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    TermPanel(title = stringResource(R.string.update_available), trailing = {
        Text("✕", color = look.dim, modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
    }) {
        StatusLine(Status.OK, stringResource(R.string.upd_notif_title, version))
        ButtonRow { TermButton(stringResource(R.string.open_release), { context.openUrl(UpdateChecker.LATEST_RELEASE_URL) }, Modifier.weight(1f), primary = true) }
    }
}

/** Orders screens so transitions slide the way you're going: tabs left to right, then pages, then sub-pages. */
private fun screenRank(page: Page?, tab: Tab): Int = when (page) {
    null -> tab.ordinal
    else -> 10 + generateSequence(page.parent) { it.parent }.count()
}

/** Terminal-style path change: backspace to what the old and new paths share, then type the rest. */
@Composable
private fun typed(target: String): String {
    var shown by remember { mutableStateOf(target) }
    val still = reduceMotion()
    LaunchedEffect(target) {
        if (still) { shown = target; return@LaunchedEffect }
        val keep = shown.commonPrefixWith(target).length
        while (shown.length > keep) { shown = shown.dropLast(1); delay(16) }
        while (shown.length < target.length) { shown = target.take(shown.length + 1); delay(28) }
    }
    return shown
}
