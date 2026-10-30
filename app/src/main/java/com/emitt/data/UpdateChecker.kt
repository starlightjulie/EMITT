package com.emitt.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** A newer EMITT release found on GitHub. */
data class AppUpdate(val version: String, val title: String, val notes: String)

sealed interface UpdateResult {
    data class Available(val update: AppUpdate) : UpdateResult
    data object UpToDate : UpdateResult
    /** GitHub couldn't be reached, or the repo has no public releases yet. */
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Checks the official GitHub repo for a newer release. It only talks to GitHub's public API, and
 * "Update" always opens [LATEST_RELEASE_URL], never a link taken from the response.
 */
object UpdateChecker {
    const val REPO = "starlightjulie/EMITT"
    const val REPO_URL = "https://github.com/$REPO"
    const val LATEST_RELEASE_URL = "$REPO_URL/releases/latest"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EMITT/$currentVersion")
            }
            try {
                when (conn.responseCode) {
                    200 -> Unit
                    404 -> return@runCatching UpdateResult.Failed("No public releases yet")
                    else -> return@runCatching UpdateResult.Failed("GitHub replied ${conn.responseCode}")
                }
                val json = Json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
                if (json["draft"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching UpdateResult.UpToDate
                if (json["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching UpdateResult.UpToDate
                val tag = json["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v")
                    ?: return@runCatching UpdateResult.Failed("Release has no version tag")
                if (compare(tag, currentVersion) <= 0) return@runCatching UpdateResult.UpToDate
                UpdateResult.Available(
                    AppUpdate(
                        version = tag,
                        title = json["name"]?.jsonPrimitive?.contentOrNull ?: "EMITT $tag",
                        notes = plainText(json["body"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                    ),
                )
            } finally {
                conn.disconnect()
            }
        }.getOrElse { UpdateResult.Failed("Couldn't reach GitHub") }
    }

    /** Compares dotted version names numerically ("1.1.10" > "1.1.9"). */
    fun compare(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    /** Turns the release's Markdown into readable plain text. */
    private fun plainText(md: String): String = md
        .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
