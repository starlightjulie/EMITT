package com.emitt.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.edit
import com.emitt.R
import com.emitt.data.Encoding
import com.emitt.data.Library
import com.emitt.qr.QrRenderer

/** Home-screen widget showing one saved code. Tapping it opens the code full screen. */
class CodeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit { ids.forEach { remove(key(it)) } }
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        private fun key(id: Int) = "item_$id"

        fun assign(context: Context, widgetId: Int, itemId: String) {
            prefs(context).edit { putString(key(widgetId), itemId) }
            update(context, AppWidgetManager.getInstance(context), widgetId)
        }

        fun refreshAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            m.getAppWidgetIds(ComponentName(context, CodeWidget::class.java)).forEach { update(context, m, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_code)
            val item = Library.get(context).byId(prefs(context).getString(key(widgetId), null))
            if (item == null) {
                views.setImageViewResource(R.id.widget_code, R.drawable.ic_launcher_monochrome)
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_pick))
                val pick = Intent(context, WidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, widgetId, pick, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                val bmp = runCatching { QrRenderer.render(Encoding.toQrText(item.payload), item.style, 512).bitmap }.getOrNull()
                if (bmp != null) views.setImageViewBitmap(R.id.widget_code, bmp)
                views.setTextViewText(R.id.widget_title, item.name)
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context, widgetId, ShowCodeActivity.intent(context, item.id),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
