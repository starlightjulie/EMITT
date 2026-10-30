package com.emitt.qr

import kotlinx.serialization.Serializable

@Serializable
enum class ModuleShape(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    DOTS("Dots"),
    FLUID("Fluid"),
    BARS("Bars"),
    DIAMOND("Diamond"),
}

@Serializable
enum class EyeShape(val label: String) { SQUARE("Square"), ROUNDED("Rounded"), CIRCLE("Circle"), LEAF("Leaf") }

@Serializable
enum class Gradient(val label: String) { NONE("Flat"), LINEAR("Linear"), RADIAL("Radial") }

@Serializable
enum class Badge(val label: String) { NONE("None"), GLYPH("EMITT"), TEXT("Text"), IMAGE("Image") }

/** Error correction: how much of the code can be covered or damaged and still scan. */
@Serializable
enum class Ec(val label: String, val percent: Int) { L("Low", 7), M("Medium", 15), Q("Quartile", 25), H("High", 30) }

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
