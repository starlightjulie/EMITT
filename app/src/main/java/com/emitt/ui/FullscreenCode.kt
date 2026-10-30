package com.emitt.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emitt.qr.QrStyle

/**
 * A code alone on screen, big, at full brightness: what you show someone to scan.
 * Tapping anywhere closes it.
 */
@Composable
fun FullscreenCode(text: String, style: QrStyle, title: String?, boostBrightness: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(boostBrightness) {
        val window = (context as? Activity)?.window
        val before = window?.attributes?.screenBrightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (boostBrightness) window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (before != null) window.attributes = window.attributes.apply { screenBrightness = before }
        }
    }
    val bg = if (style.transparentBg) Color.White else Color(style.bg)
    val ink = if (bg.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val health = rememberQr(text, style, 1200)
    Box(
        Modifier.fillMaxSize().background(bg).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            health?.bitmap?.let { Image(it.asImageBitmap(), "QR code", Modifier.fillMaxWidth().padding(20.dp).aspectRatio(1f)) }
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, color = ink, textAlign = TextAlign.Center)
            Text("TAP TO CLOSE", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.5f))
        }
    }
}
