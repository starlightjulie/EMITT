package com.emitt.data

import android.content.Context
import com.emitt.qr.QrStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A saved code. [style] is how its QR looks; NFC and Flipper output ignore it. */
@Serializable
data class SavedItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val payload: Payload,
    val style: QrStyle = QrStyle(),
    val favourite: Boolean = false,
    val created: Long = System.currentTimeMillis(),
)

/** The user's saved codes, kept as one small JSON file in app storage. */
class Library private constructor(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SavedItem>> = _items.asStateFlow()

    private fun load(): List<SavedItem> = runCatching {
        if (file.exists()) json.decodeFromString<List<SavedItem>>(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    fun byId(id: String?): SavedItem? = id?.let { i -> _items.value.firstOrNull { it.id == i } }

    fun upsert(item: SavedItem) = mutate { list ->
        val i = list.indexOfFirst { it.id == item.id }
        if (i >= 0) list.toMutableList().also { it[i] = item } else listOf(item) + list
    }

    fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    private fun mutate(transform: (List<SavedItem>) -> List<SavedItem>) = synchronized(this) {
        val next = transform(_items.value)
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(json.encodeToString(next))
        tmp.renameTo(file)
        _items.value = next
    }

    companion object {
        @Volatile private var instance: Library? = null
        fun get(context: Context): Library =
            instance ?: synchronized(this) { instance ?: Library(context.applicationContext).also { instance = it } }
    }
}
