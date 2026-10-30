package com.emitt.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emitt.qr.Badge
import com.emitt.qr.Ec
import com.emitt.qr.QrDecoder
import com.emitt.qr.QrRenderer
import com.emitt.qr.QrStyle
import com.emitt.ui.theme.LocalLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How likely a code is to scan, with reasons. */
class QrHealth(
    val bitmap: Bitmap?,
    val error: String?,
    val version: Int = 0,
    val modules: Int = 0,
    val checks: List<Pair<Status, String>> = emptyList(),
) {
    val readable get() = checks.none { it.first == Status.FAIL } && bitmap != null
}

fun qrChecks(text: String, style: QrStyle, bmp: Bitmap, version: Int, modules: Int): List<Pair<Status, String>> {
    val out = mutableListOf<Pair<Status, String>>()
    val decoded = QrDecoder.selfTest(bmp, text)
    out += if (decoded) Status.OK to "Self-test scan passed (version $version, ${modules}×$modules)"
    else Status.FAIL to "Self-test couldn't read it. Raise contrast, use square modules or less badge."

    val bg = if (style.transparentBg) 0xFFFFFFFF else style.bg
    val worst = listOfNotNull(style.fg, style.fg2.takeIf { style.gradient != com.emitt.qr.Gradient.NONE }, style.eyeOuter, style.eyeInner)
        .minOf { QrRenderer.contrast(it, bg) }
    out += when {
        worst < 2.5 -> Status.FAIL to "Contrast %.1f:1 is too low. Aim for 4:1 or more.".format(worst)
        worst < 4.0 -> Status.WARN to "Contrast %.1f:1 is borderline in poor light.".format(worst)
        else -> Status.OK to "Contrast %.1f:1".format(worst)
    }
    if (QrRenderer.luminance(style.fg) > QrRenderer.luminance(bg)) {
        out += Status.WARN to "Light-on-dark (inverted) codes don't scan on some older readers."
    }
    if (style.transparentBg) out += Status.INFO to "Transparent background: make sure what's behind it is light and plain."
    if (style.quietZone < 2) out += Status.WARN to "Border under 2 modules: give it space when printing."
    if (style.badge != Badge.NONE && QrRenderer.effectiveEc(style) != style.ec) {
        out += Status.INFO to "Error correction raised to ${QrRenderer.effectiveEc(style).label} to make room for the badge."
    }
    if (style.badge != Badge.NONE && style.badgeSize > 0.26f && style.ec != Ec.H) {
        out += Status.WARN to "Large badge: switch error correction to High."
    }
    if (version >= 15) out += Status.WARN to "Dense code (version $version). Shorter content scans faster and from further away."
    return out
}

/** Renders [text] in [style] off the main thread, plus the scannability checks, debounced while typing. */
@Composable
fun rememberQr(text: String, style: QrStyle, sizePx: Int = 900): QrHealth? {
    val state by produceState<QrHealth?>(null, text, style, sizePx) {
        delay(120)
        value = withContext(Dispatchers.Default) {
            if (text.isEmpty()) return@withContext QrHealth(null, "Nothing to encode yet")
            runCatching {
                val r = QrRenderer.render(text, style, sizePx)
                QrHealth(r.bitmap, null, r.version, r.modules, qrChecks(text, style, r.bitmap, r.version, r.modules))
            }.getOrElse { QrHealth(null, if (it is com.google.zxing.WriterException) "Too much data for a QR code" else it.message) }
        }
    }
    return state
}

/** The framed code preview. */
@Composable
fun QrPreview(health: QrHealth?, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(look.controlShape)
            .background(checker())
            .border(1.dp, look.accent.copy(alpha = 0.5f), look.controlShape),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = health?.bitmap
        when {
            health == null -> CircularProgressIndicator(color = look.accent)
            bmp != null -> Image(bmp.asImageBitmap(), "QR code preview", Modifier.fillMaxWidth().aspectRatio(1f))
            else -> Text(
                "> ${health.error ?: "…"}",
                style = MaterialTheme.typography.labelLarge,
                color = look.dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** Checkerboard so transparent codes are visible in the preview. */
@Composable
private fun checker(): androidx.compose.ui.graphics.Brush {
    val look = LocalLook.current
    val a = look.dim.copy(alpha = 0.10f)
    val b = look.dim.copy(alpha = 0.18f)
    return androidx.compose.ui.graphics.Brush.linearGradient(
        0f to a, 0.5f to a, 0.5f to b, 1f to b,
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset(24f, 24f),
        tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
    )
}

@Composable
fun HealthReport(health: QrHealth?) {
    if (health == null) return
    Column(Modifier.padding(top = 10.dp)) {
        health.checks.forEach { (s, t) -> StatusLine(s, t) }
    }
}
