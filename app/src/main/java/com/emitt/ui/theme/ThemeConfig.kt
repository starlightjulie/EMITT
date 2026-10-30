package com.emitt.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The five user-editable colours. Values are ARGB. Light or dark follows [bg]. */
@Serializable
data class Palette(
    val accent: Long,
    val accent2: Long,
    val bg: Long,
    val surface: Long,
    val ink: Long,
)

enum class PaletteSlot(val label: String, val hint: String) {
    ACCENT("Accent", "Buttons, highlights, active tab"),
    ACCENT2("Signal", "Second accent: status, links, animation"),
    BG("Background", "Behind everything"),
    SURFACE("Panels", "Cards and sheets"),
    INK("Text", "Main text colour");

    fun get(p: Palette) = when (this) {
        ACCENT -> p.accent; ACCENT2 -> p.accent2; BG -> p.bg; SURFACE -> p.surface; INK -> p.ink
    }

    fun set(p: Palette, v: Long) = when (this) {
        ACCENT -> p.copy(accent = v); ACCENT2 -> p.copy(accent2 = v); BG -> p.copy(bg = v)
        SURFACE -> p.copy(surface = v); INK -> p.copy(ink = v)
    }
}

class Colourway(val id: String, val name: String, val palette: Palette)

object Colourways {
    val all = listOf(
        Colourway("synthwave", "Synthwave", Palette(0xFFFF3DCB, 0xFF3DF0FF, 0xFF12081F, 0xFF1D0F33, 0xFFF4E9FF)),
        Colourway("amber", "Amber CRT", Palette(0xFFFFB000, 0xFFFF6A00, 0xFF0E0A04, 0xFF1A1309, 0xFFFFE2A8)),
        Colourway("phosphor", "Phosphor", Palette(0xFF3DFF8F, 0xFFB6FF3D, 0xFF050A06, 0xFF0C160F, 0xFFC9FFD9)),
        Colourway("ice", "Ice", Palette(0xFF4DF3FF, 0xFF7A9CFF, 0xFF03121A, 0xFF0A1F2B, 0xFFD6FBFF)),
        Colourway("ultraviolet", "Ultraviolet", Palette(0xFFB47CFF, 0xFFFF5C8A, 0xFF0B0614, 0xFF170E26, 0xFFEBDDFF)),
        Colourway("redline", "Redline", Palette(0xFFFF3B3B, 0xFFFFB13B, 0xFF0D0505, 0xFF1A0B0B, 0xFFFFD9D9)),
        Colourway("vapor", "Vapor", Palette(0xFF7DF9FF, 0xFFFF9DE6, 0xFF1B1035, 0xFF2A1A4F, 0xFFFFF0FB)),
        Colourway("terminal", "Mono", Palette(0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000, 0xFF111111, 0xFFEDEDED)),
        Colourway("paper", "Paper", Palette(0xFF1B4DFF, 0xFFE0245E, 0xFFEDE9DE, 0xFFFAF8F2, 0xFF1A1A1A)),
        Colourway("solar", "Solar", Palette(0xFFB35C00, 0xFF00796B, 0xFFF6EEDB, 0xFFFFFCF4, 0xFF2A1F0F)),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

@Serializable
enum class BgStyle(val label: String) {
    SYNTH_GRID("Synth grid"),
    SIGNAL("Signal"),
    CODE_RAIN("Code rain"),
    SCOPE("Oscilloscope"),
    RADAR("Radar"),
    DOT_MATRIX("Dot matrix"),
    CIRCUIT("Circuit"),
    WARP("Warp"),
    SPECTRUM("Spectrum"),
    STATIC("Static"),
    NONE("Off"),
    RANDOM("Random"),
}

@Serializable
enum class BgColour(val label: String) { DUO("Both accents"), ACCENT("Accent"), SIGNAL("Signal"), CYCLE("Hue cycle"), INK("Text colour") }

@Serializable
enum class Corners(val label: String) { CUT("Chamfered"), SHARP("Sharp"), SOFT("Soft") }

@Serializable
enum class FontStyle(val label: String) { MIXED("Mono labels"), MONO("All mono") }

/** Launcher icon variants: each is an activity-alias in the manifest. */
@Serializable
enum class LauncherIcon(val alias: String, val label: String, val bg: Long, val core: Long, val ring: Long) {
    AMBER(".IconAmber", "Amber", 0xFF140D02, 0xFFFFB000, 0xFFFFB000),
    SYNTHWAVE(".IconSynthwave", "Synthwave", 0xFF1A0B2E, 0xFFFF3DCB, 0xFF3DF0FF),
    PHOSPHOR(".IconPhosphor", "Phosphor", 0xFF07110B, 0xFF3DFF8F, 0xFF3DFF8F),
    CYAN(".IconCyan", "Cyan", 0xFF03121A, 0xFF4DF3FF, 0xFF4DF3FF),
}

@Serializable
data class ThemeConfig(
    val colourway: String = "synthwave",
    /** When set, replaces the colourway. */
    val custom: Palette? = null,
    /** Off for new installs: calmer first impression. Every style is one tap away in Settings. */
    val background: BgStyle = BgStyle.NONE,
    val bgSpeed: Float = 1f,
    val bgDensity: Float = 1f,
    /** 0..1: how strongly the animation shows. */
    val bgIntensity: Float = 0.75f,
    val bgColour: BgColour = BgColour.DUO,
    val scanlines: Boolean = true,
    val scanlineStrength: Float = 0.3f,
    val vignette: Boolean = true,
    val flicker: Boolean = false,
    /** Soft glow on titles and the active tab. */
    val glow: Boolean = true,
    /** 0..1: panel opacity. Lower lets the animation show through. */
    val panelOpacity: Float = 0.82f,
    val outlines: Boolean = true,
    val corners: Corners = Corners.CUT,
    val font: FontStyle = FontStyle.MIXED,
    val fontScale: Float = 1f,
    /** Blinking block cursor in screen titles. */
    val cursor: Boolean = true,
    val icon: LauncherIcon = LauncherIcon.AMBER,
) {
    val palette: Palette get() = custom ?: Colourways.byId(colourway).palette
}

class ThemeRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _config = MutableStateFlow(load())
    val config: StateFlow<ThemeConfig> = _config.asStateFlow()

    private fun load(): ThemeConfig = runCatching {
        prefs.getString("config", null)?.let { json.decodeFromString<ThemeConfig>(it) }
    }.getOrNull() ?: ThemeConfig()

    fun update(transform: (ThemeConfig) -> ThemeConfig) {
        val next = transform(_config.value)
        prefs.edit { putString("config", json.encodeToString(next)) }
        _config.value = next
    }

    companion object {
        @Volatile private var instance: ThemeRepository? = null
        fun get(context: Context): ThemeRepository =
            instance ?: synchronized(this) { instance ?: ThemeRepository(context.applicationContext).also { instance = it } }
    }
}
