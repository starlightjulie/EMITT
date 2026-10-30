package com.emitt.data

import android.content.Context
import androidx.core.content.edit

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

    var askedNotifications: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(v) = prefs.edit { putBoolean("asked_notifications", v) }

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit { putBoolean("haptics", v) }

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
