package com.emitt

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.emitt.ui.theme.LauncherIcon

/** Swaps the home-screen icon by enabling one launcher activity-alias and disabling the rest. */
object LauncherIcons {
    fun apply(context: Context, icon: LauncherIcon) {
        val pm = context.packageManager
        // Enable the new one first so the app never has zero launcher entries
        LauncherIcon.entries.sortedByDescending { it == icon }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(context, context.packageName + it.alias),
                if (it == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
