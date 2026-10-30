package com.emitt.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.emitt.data.Encoding
import com.emitt.data.Library
import com.emitt.data.Settings
import com.emitt.ui.FullscreenCode
import com.emitt.ui.theme.EmittTheme
import com.emitt.ui.theme.ThemeRepository

/** Opens one saved code full screen, straight from the widget or Quick Settings tile. */
class ShowCodeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val item = Library.get(this).byId(intent.getStringExtra(EXTRA_ID))
        if (item == null) {
            finish()
            return
        }
        val boost = Settings(this).boostBrightness
        setContent {
            EmittTheme(ThemeRepository.get(this).config.value) {
                FullscreenCode(Encoding.toQrText(item.payload), item.style, item.name, boost) { finish() }
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "item"

        fun intent(context: Context, itemId: String): Intent =
            Intent(context, ShowCodeActivity::class.java).putExtra(EXTRA_ID, itemId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
