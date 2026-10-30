package com.emitt.widget

import androidx.compose.ui.res.stringResource
import com.emitt.R
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emitt.data.Library
import com.emitt.data.summary
import com.emitt.ui.Dim
import com.emitt.ui.TermPanel
import com.emitt.ui.theme.EmittBackground
import com.emitt.ui.theme.EmittTheme
import com.emitt.ui.theme.LocalLook
import com.emitt.ui.theme.ThemeRepository

/** Lets the user pick which saved code a new (or reconfigured) widget shows. */
class WidgetConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val items = Library.get(this).items.value
        setContent {
            EmittTheme(ThemeRepository.get(this).config.value) {
                Box(Modifier.fillMaxSize()) {
                    EmittBackground()
                    Column(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.widget_pick_title).uppercase(), style = MaterialTheme.typography.titleLarge, color = LocalLook.current.accent)
                        if (items.isEmpty()) {
                            TermPanel { Dim(stringResource(R.string.widget_empty)) }
                        }
                        items.forEach { item ->
                            TermPanel(Modifier.clickable {
                                CodeWidget.assign(this@WidgetConfigActivity, widgetId, item.id)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                finish()
                            }) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Dim("${stringResource(item.payload.kind.label)} · ${item.payload.summary()}", Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}
