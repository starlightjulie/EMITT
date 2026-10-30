package com.emitt.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** EMITT-specific look settings that Material's theme has no slot for. */
@Immutable
data class EmittLook(
    val config: ThemeConfig,
    val accent: Color,
    val signal: Color,
    val ink: Color,
    val dim: Color,
    val bg: Color,
    val panel: Color,
    val dark: Boolean,
    val panelShape: Shape,
    val controlShape: Shape,
    val mono: FontFamily,
) {
    val ok get() = if (dark) Color(0xFF5CFF9D) else Color(0xFF0B7A3E)
    val warn get() = if (dark) Color(0xFFFFC94D) else Color(0xFF9A6200)
    val fail get() = if (dark) Color(0xFFFF5C6C) else Color(0xFFC1121F)
}

val LocalLook = staticCompositionLocalOf<EmittLook> { error("No EmittTheme") }

private fun readableOn(c: Color) = if (c.luminance() > 0.45f) Color(0xFF0B0B0F) else Color.White

fun Palette.toColorScheme(): ColorScheme {
    val a = Color(accent)
    val s = Color(accent2)
    val bgc = Color(bg)
    val surf = Color(surface)
    val inkc = Color(ink)
    val dark = bgc.luminance() < 0.5f
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = a,
        onPrimary = readableOn(a),
        primaryContainer = lerp(a, surf, 0.7f),
        onPrimaryContainer = inkc,
        secondary = s,
        onSecondary = readableOn(s),
        secondaryContainer = lerp(s, surf, 0.75f),
        onSecondaryContainer = inkc,
        tertiary = s,
        onTertiary = readableOn(s),
        background = bgc,
        onBackground = inkc,
        surface = surf,
        onSurface = inkc,
        surfaceVariant = lerp(surf, inkc, 0.08f),
        onSurfaceVariant = lerp(inkc, bgc, 0.35f),
        surfaceContainerLowest = bgc,
        surfaceContainerLow = lerp(bgc, surf, 0.5f),
        surfaceContainer = surf,
        surfaceContainerHigh = lerp(surf, inkc, 0.05f),
        surfaceContainerHighest = lerp(surf, inkc, 0.1f),
        surfaceTint = Color.Transparent,
        outline = lerp(a, bgc, 0.45f),
        outlineVariant = lerp(inkc, bgc, 0.75f),
        inverseSurface = inkc,
        inverseOnSurface = bgc,
        error = if (dark) Color(0xFFFF5C6C) else Color(0xFFC1121F),
    )
}

@Composable
fun EmittTheme(config: ThemeConfig, content: @Composable () -> Unit) {
    val p = config.palette
    val scheme = p.toColorScheme()
    val dark = Color(p.bg).luminance() < 0.5f
    val mono = FontFamily.Monospace
    val body = if (config.font == FontStyle.MONO) mono else FontFamily.Default

    val (panel, control) = when (config.corners) {
        Corners.CUT -> CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp) to CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
        Corners.SHARP -> CutCornerShape(0.dp) to CutCornerShape(0.dp)
        Corners.SOFT -> RoundedCornerShape(10.dp) to RoundedCornerShape(6.dp)
    }

    val look = EmittLook(
        config = config,
        accent = Color(p.accent),
        signal = Color(p.accent2),
        ink = Color(p.ink),
        dim = scheme.onSurfaceVariant,
        bg = Color(p.bg),
        panel = Color(p.surface).copy(alpha = config.panelOpacity.coerceIn(0.2f, 1f)),
        dark = dark,
        panelShape = panel,
        controlShape = control,
        mono = mono,
    )

    val type = Typography(
        displaySmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 2.sp),
        headlineSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.5.sp),
        titleLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp),
        titleMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.5.sp),
        titleSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp),
        bodyLarge = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp),
        labelMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.2.sp),
        labelSmall = TextStyle(fontFamily = mono, fontSize = 10.sp, letterSpacing = 1.sp),
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalLook provides look,
        LocalDensity provides Density(density.density, density.fontScale * config.fontScale),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = type,
            shapes = Shapes(control, control, control, panel, panel),
        ) {
            // EMITT draws on its own backdrop rather than Material surfaces, so set the default text colour here
            CompositionLocalProvider(LocalContentColor provides look.ink, content = content)
        }
    }
}
