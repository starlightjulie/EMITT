package com.emitt.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun Context.copyText(label: String, text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun Context.stage(name: String, write: (File) -> Unit): Uri {
    val dir = File(cacheDir, "share").apply { mkdirs() }
    val f = File(dir, name)
    write(f)
    return FileProvider.getUriForFile(this, "$packageName.files", f)
}

fun Context.shareFile(name: String, mime: String, write: (File) -> Unit) {
    val uri = stage(name, write)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.sharePng(name: String, bmp: Bitmap) =
    shareFile("$name.png", "image/png") { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }

fun Context.shareText(name: String, mime: String, text: String) = shareFile(name, mime) { it.writeText(text) }

/** Saves into Pictures/EMITT. Returns false if the gallery refused it. */
fun Context.savePngToGallery(name: String, bmp: Bitmap): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EMITT")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
    } else {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "EMITT").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    true
}.getOrDefault(false)

fun Context.writeUri(uri: Uri, text: String): Boolean =
    runCatching { contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)

fun Context.readUri(uri: Uri, maxBytes: Int = 256 * 1024): String? =
    runCatching { contentResolver.openInputStream(uri)?.use { String(it.readNBytesCompat(maxBytes)) } }.getOrNull()

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
