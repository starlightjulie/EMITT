package com.emitt.qr

import androidx.annotation.StringRes
import com.emitt.R
import kotlinx.serialization.Serializable

@Serializable
enum class ModuleShape(@StringRes val label: Int) {
    SQUARE(R.string.shape_square),
    ROUNDED(R.string.shape_rounded),
    DOTS(R.string.shape_dots),
    FLUID(R.string.shape_fluid),
    BARS(R.string.shape_bars),
    DIAMOND(R.string.shape_diamond),
}

@Serializable
enum class EyeShape(@StringRes val label: Int) { SQUARE(R.string.shape_square), ROUNDED(R.string.shape_rounded), CIRCLE(R.string.shape_circle), LEAF(R.string.shape_leaf) }

@Serializable
enum class Gradient(@StringRes val label: Int) { NONE(R.string.grad_flat), LINEAR(R.string.grad_linear), RADIAL(R.string.grad_radial) }

@Serializable
enum class Badge(@StringRes val label: Int) { NONE(R.string.wifi_open), GLYPH(R.string.app_name), TEXT(R.string.kind_text), IMAGE(R.string.badge_image) }

/** Error correction: how much of the code can be covered or damaged and still scan. */
@Serializable
enum class Ec(@StringRes val label: Int, val percent: Int) { L(R.string.ec_low, 7), M(R.string.ec_medium, 15), Q(R.string.ec_quartile, 25), H(R.string.ec_high, 30) }

/** Everything about how a QR code looks. Colours are ARGB. */
@Serializable
data class QrStyle(
    val fg: Long = 0xFF12081F,
    val bg: Long = 0xFFFFFFFF,
    val gradient: Gradient = Gradient.NONE,
    val fg2: Long = 0xFFFF3DCB,
    /** Finder "eye" colours; null follows [fg]. */
    val eyeOuter: Long? = null,
    val eyeInner: Long? = null,
    val transparentBg: Boolean = false,
    val modules: ModuleShape = ModuleShape.SQUARE,
    /** 0.6..1: module size inside its cell. Below 1 leaves small gaps. */
    val moduleScale: Float = 1f,
    val eyes: EyeShape = EyeShape.SQUARE,
    val ec: Ec = Ec.M,
    /** Blank border in modules. 4 is the spec; 2 is usually fine. */
    val quietZone: Int = 4,
    val badge: Badge = Badge.NONE,
    val badgeText: String = "",
    /** Path inside app storage for [Badge.IMAGE]. */
    val badgeImage: String? = null,
    /** Badge width as a fraction of the code, 0.12..0.3. */
    val badgeSize: Float = 0.22f,
)
