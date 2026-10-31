package com.emitt.data

import android.content.Context
import androidx.core.content.edit
import com.emitt.HapticEvent
import com.emitt.HapticPattern

class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Look for a newer EMITT release on GitHub. */
    var checkUpdates: Boolean
        get() = prefs.getBoolean("check_updates", true)
        set(v) = prefs.edit { putBoolean("check_updates", v) }

    /** Hours between background update checks. */
    var updateIntervalHours: Int
        get() = prefs.getInt("update_interval", 12)
        set(v) = prefs.edit { putInt("update_interval", v) }

    /** Newest version we've already sent a notification about, so each release notifies once. */
    var notifiedVersion: String
        get() = prefs.getString("notified_version", "").orEmpty()
        set(v) = prefs.edit { putString("notified_version", v) }

    /** "Remind me later": the update pop-up for this version stays away until [snoozedUntil]. */
    var snoozedVersion: String
        get() = prefs.getString("snoozed_version", "").orEmpty()
        set(v) = prefs.edit { putString("snoozed_version", v) }

    var snoozedUntil: Long
        get() = prefs.getLong("snoozed_until", 0L)
        set(v) = prefs.edit { putLong("snoozed_until", v) }

    var askedNotifications: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(v) = prefs.edit { putBoolean("asked_notifications", v) }

    /** Master switch for all vibration. */
    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit { putBoolean("haptics", v) }

    fun hapticPattern(e: HapticEvent): HapticPattern = HapticPattern.of(prefs.getString("haptic_${e.name}", null), e.default)
    fun setHapticPattern(e: HapticEvent, p: HapticPattern) = prefs.edit { putString("haptic_${e.name}", p.name) }

    /** 0.1..1: vibration strength on phones that support it. */
    var hapticStrength: Float
        get() = prefs.getFloat("haptic_strength", 0.85f)
        set(v) = prefs.edit { putFloat("haptic_strength", v) }

    /** Full brightness while a code is shown full screen, so cameras read it from further away. */
    var boostBrightness: Boolean
        get() = prefs.getBoolean("boost_brightness", true)
        set(v) = prefs.edit { putBoolean("boost_brightness", v) }

    /** Saved item the Quick Settings tile opens. */
    var tileItemId: String?
        get() = prefs.getString("tile_item", null)
        set(v) = prefs.edit { putString("tile_item", v) }

    /** Last Flipper we connected to, for one-tap reconnect. */
    var flipperAddress: String?
        get() = prefs.getString("flipper_address", null)
        set(v) = prefs.edit { putString("flipper_address", v) }

    var flipperName: String?
        get() = prefs.getString("flipper_name", null)
        set(v) = prefs.edit { putString("flipper_name", v) }
}
