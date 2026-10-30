package com.emitt.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.emitt.MainActivity
import com.emitt.data.Library
import com.emitt.data.Settings

/** Quick Settings tile: one tap shows the pinned code (e.g. your Wi-Fi) full screen. */
class CodeTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        val item = Library.get(this).byId(Settings(this).tileItemId)
        tile.label = item?.name ?: "EMITT code"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (item == null) "Pin one in Library" else "Show code"
        tile.state = if (item == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val item = Library.get(this).byId(Settings(this).tileItemId)
        val intent = if (item != null) ShowCodeActivity.intent(this, item.id)
        else Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, CodeTileService::class.java)) }
        }
    }
}
