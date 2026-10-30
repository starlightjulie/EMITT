package com.emitt.ui

import androidx.compose.ui.res.stringResource
import com.emitt.R
import com.emitt.data.UiText
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
    val error: UiText?,
    val version: Int = 0,
    val modules: Int = 0,
    val checks: List<Pair<Status, UiText>> = emptyList(),
) {
    val readable get() = checks.none { it.first == Status.FAIL } && bitmap != null
}

fun qrChecks(text: String, style: QrStyle, bmp: Bitmap, version: Int, modules: Int): List<Pair<Status, UiText>> {
    val out = mutableListOf<Pair<Status, UiText>>()
    val decoded = QrDecoder.selfTest(bmp, text)
    out += if (decoded) Status.OK to UiText(R.string.qr_selftest_ok, version, modules)
    else Status.FAIL to UiText(R.string.qr_selftest_fail)

    val bg = if (style.transparentBg) 0xFFFFFFFF else style.bg
    val worst = listOfNotNull(style.fg, style.fg2.takeIf { style.gradient != com.emitt.qr.Gradient.NONE }, style.eyeOuter, style.eyeInner)
        .minOf { QrRenderer.contrast(it, bg) }
    out += when {
        worst < 2.5 -> Status.FAIL to UiText(R.string.qr_contrast_low, worst)
        worst < 4.0 -> Status.WARN to UiText(R.string.qr_contrast_mid, worst)
        else -> Status.OK to UiText(R.string.qr_contrast_ok, worst)
    }
    if (QrRenderer.luminance(style.fg) > QrRenderer.luminance(bg)) {
        out += Status.WARN to UiText(R.string.qr_inverted)
    }
    if (style.transparentBg) out += Status.INFO to UiText(R.string.qr_transparent)
    if (style.quietZone < 2) out += Status.WARN to UiText(R.string.qr_border)
    if (style.badge != Badge.NONE && QrRenderer.effectiveEc(style) != style.ec) {
        out += Status.INFO to UiText(R.string.qr_ec_raised, UiText(QrRenderer.effectiveEc(style).label))
    }
    if (style.badge != Badge.NONE && style.badgeSize > 0.26f && style.ec != Ec.H) {
        out += Status.WARN to UiText(R.string.qr_big_badge)
    }
    if (version >= 15) out += Status.WARN to UiText(R.string.qr_dense, version)
    return out
}

/** Renders [text] in [style] off the main thread, plus the scannability checks, debounced while typing. */
@Composable
fun rememberQr(text: String, style: QrStyle, sizePx: Int = 900): QrHealth? {
    val state by produceState<QrHealth?>(null, text, style, sizePx) {
        delay(120)
        value = withContext(Dispatchers.Default) {
            if (text.isEmpty()) return@withContext QrHealth(null, UiText(R.string.qr_nothing))
            runCatching {
                val r = QrRenderer.render(text, style, sizePx)
                QrHealth(r.bitmap, null, r.version, r.modules, qrChecks(text, style, r.bitmap, r.version, r.modules))
            }.getOrElse { QrHealth(null, UiText(R.string.qr_too_much)) }
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
            bmp != null -> Image(bmp.asImageBitmap(), stringResource(R.string.out_qr), Modifier.fillMaxWidth().aspectRatio(1f))
            else -> Text(
                "> ${health.error?.text() ?: "…"}",
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
        health.checks.forEach { (s, t) -> StatusLine(s, t.text()) }
    }
}
