package com.emitt.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text to show the user, kept as a string resource plus arguments until it reaches the screen, so
 * it's always rendered in the language the user picked. Arguments may themselves be [UiText].
 */
class UiText(@StringRes val id: Int, vararg val args: Any) {
    fun get(context: Context): String =
        context.getString(id, *args.map { if (it is UiText) it.get(context) else it }.toTypedArray())

    @Composable
    fun text(): String = get(LocalContext.current)
}

/** An error whose message is [UiText], so it can be shown in the user's language. */
class UiTextException(val text: UiText) : Exception("UiText ${text.id}")
