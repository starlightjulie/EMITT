package com.emitt.ui.theme

import androidx.annotation.StringRes
import com.emitt.R
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

enum class PaletteSlot(@StringRes val label: Int, @StringRes val hint: Int) {
    ACCENT(R.string.slot_accent, R.string.slot_accent_hint),
    ACCENT2(R.string.slot_signal, R.string.slot_signal_hint),
    BG(R.string.slot_bg, R.string.slot_bg_hint),
    SURFACE(R.string.slot_panels, R.string.slot_panels_hint),
    INK(R.string.kind_text, R.string.slot_text_hint);

    fun get(p: Palette) = when (this) {
        ACCENT -> p.accent; ACCENT2 -> p.accent2; BG -> p.bg; SURFACE -> p.surface; INK -> p.ink
    }

    fun set(p: Palette, v: Long) = when (this) {
        ACCENT -> p.copy(accent = v); ACCENT2 -> p.copy(accent2 = v); BG -> p.copy(bg = v)
        SURFACE -> p.copy(surface = v); INK -> p.copy(ink = v)
    }
}

class Colourway(val id: String, val name: String, val palette: Palette)

/** A custom palette the user saved under a name, so they can switch between their own sets. */
@Serializable
data class SavedColours(val name: String, val palette: Palette)

object Colourways {
    val all = listOf(
        Colourway("terminal", "Mono", Palette(0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000, 0xFF111111, 0xFFEDEDED)),
        Colourway("synthwave", "Synthwave", Palette(0xFFFF3DCB, 0xFF3DF0FF, 0xFF12081F, 0xFF1D0F33, 0xFFF4E9FF)),
        Colourway("amber", "Amber CRT", Palette(0xFFFFB000, 0xFFFF6A00, 0xFF0E0A04, 0xFF1A1309, 0xFFFFE2A8)),
        Colourway("phosphor", "Phosphor", Palette(0xFF3DFF8F, 0xFFB6FF3D, 0xFF050A06, 0xFF0C160F, 0xFFC9FFD9)),
        Colourway("ice", "Ice", Palette(0xFF4DF3FF, 0xFF7A9CFF, 0xFF03121A, 0xFF0A1F2B, 0xFFD6FBFF)),
        Colourway("ultraviolet", "Ultraviolet", Palette(0xFFB47CFF, 0xFFFF5C8A, 0xFF0B0614, 0xFF170E26, 0xFFEBDDFF)),
        Colourway("redline", "Redline", Palette(0xFFFF3B3B, 0xFFFFB13B, 0xFF0D0505, 0xFF1A0B0B, 0xFFFFD9D9)),
        Colourway("vapor", "Vapor", Palette(0xFF7DF9FF, 0xFFFF9DE6, 0xFF1B1035, 0xFF2A1A4F, 0xFFFFF0FB)),
        Colourway("paper", "Paper", Palette(0xFF1B4DFF, 0xFFE0245E, 0xFFEDE9DE, 0xFFFAF8F2, 0xFF1A1A1A)),
        Colourway("solar", "Solar", Palette(0xFFB35C00, 0xFF00796B, 0xFFF6EEDB, 0xFFFFFCF4, 0xFF2A1F0F)),
        Colourway("midnight", "Midnight", Palette(0xFF7AA2FF, 0xFFFFD166, 0xFF0A0F1F, 0xFF121A33, 0xFFE3E9FF)),
        Colourway("sakura", "Sakura", Palette(0xFFFF8FB1, 0xFFFFD6E0, 0xFF1A0F14, 0xFF2A1820, 0xFFFFE8EF)),
        Colourway("forest", "Forest", Palette(0xFF7BD389, 0xFFE3C567, 0xFF08140D, 0xFF10241A, 0xFFDDF5E3)),
        Colourway("ember", "Ember", Palette(0xFFFF7A1A, 0xFFFFD23F, 0xFF120804, 0xFF22110A, 0xFFFFE6D1)),
        Colourway("coffee", "Coffee", Palette(0xFFD4A373, 0xFFE9C46A, 0xFF1B140F, 0xFF2A1F17, 0xFFF3E6D8)),
        Colourway("glacier", "Glacier", Palette(0xFF0077B6, 0xFF00A5C8, 0xFFE4EEF2, 0xFFFFFFFF, 0xFF0B2533)),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

@Serializable
enum class BgStyle(@StringRes val label: Int) {
    COMETS(R.string.bg_comets),
    SIGNAL(R.string.slot_signal),
    CODE_RAIN(R.string.bg_rain),
    SCOPE(R.string.bg_scope),
    RADAR(R.string.bg_radar),
    DOT_MATRIX(R.string.bg_dots),
    CIRCUIT(R.string.bg_circuit),
    WARP(R.string.bg_warp),
    SPECTRUM(R.string.bg_spectrum),
    PULSAR(R.string.bg_pulsar),
    NONE(R.string.pat_off),
    RANDOM(R.string.bg_random),
}

@Serializable
enum class BgColour(@StringRes val label: Int) { DUO(R.string.bgc_duo), ACCENT(R.string.slot_accent), SIGNAL(R.string.slot_signal), CYCLE(R.string.bgc_cycle), INK(R.string.bgc_ink) }

@Serializable
enum class Corners(@StringRes val label: Int) { CUT(R.string.corner_cut), SHARP(R.string.corner_sharp), SOFT(R.string.corner_soft) }

@Serializable
enum class FontStyle(@StringRes val label: Int) { MIXED(R.string.font_mixed), MONO(R.string.font_mono) }

/** Launcher icon variants: each is an activity-alias in the manifest. */
@Serializable
enum class LauncherIcon(val alias: String, val label: String, val bg: Long, val core: Long, val ring: Long) {
    AMBER(".IconAmber", "Amber", 0xFF140D02, 0xFFFFB000, 0xFFFFB000),
    SYNTHWAVE(".IconSynthwave", "Synthwave", 0xFF1A0B2E, 0xFFFF3DCB, 0xFF3DF0FF),
    PHOSPHOR(".IconPhosphor", "Phosphor", 0xFF07110B, 0xFF3DFF8F, 0xFF3DFF8F),
    CYAN(".IconCyan", "Cyan", 0xFF03121A, 0xFF4DF3FF, 0xFF4DF3FF),
    MONO(".IconMono", "Mono", 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF),
    SAKURA(".IconSakura", "Sakura", 0xFF1A0F14, 0xFFFF8FB1, 0xFFFFD6E0),
    REDLINE(".IconRedline", "Redline", 0xFF0D0505, 0xFFFF3B3B, 0xFFFF3B3B),
    ULTRAVIOLET(".IconUltraviolet", "Ultraviolet", 0xFF0B0614, 0xFFB47CFF, 0xFFFF5C8A),
    PAPER(".IconPaper", "Paper", 0xFFEDE9DE, 0xFF1B4DFF, 0xFF1A1A1A),
    GLACIER(".IconGlacier", "Glacier", 0xFFEAF4F8, 0xFF0077B6, 0xFF00B4D8),
}

@Serializable
data class ThemeConfig(
    /** Mono for new installs: calm and readable. Every colourway is one tap away in Appearance. */
    val colourway: String = "terminal",
    /** When set, replaces the colourway. */
    val custom: Palette? = null,
    /** The user's saved custom sets. Kept by Reset look. */
    val savedColours: List<SavedColours> = emptyList(),
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

private val themeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Reads a saved look. 1.3.1 swapped two backgrounds, so old names are mapped and nobody's look resets on update. */
internal fun decodeTheme(saved: String): ThemeConfig = themeJson.decodeFromString(
    saved
        .replace("\"background\":\"SYNTH_GRID\"", "\"background\":\"COMETS\"")
        .replace("\"background\":\"STATIC\"", "\"background\":\"PULSAR\""),
)

class ThemeRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    private val json = themeJson
    private val _config = MutableStateFlow(load())
    val config: StateFlow<ThemeConfig> = _config.asStateFlow()

    private fun load(): ThemeConfig = runCatching {
        prefs.getString("config", null)?.let { decodeTheme(it) }
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
