package com.emitt.data

import android.content.Context

enum class NoteKind { NEW, FIXED, IMPROVED }

/** One heading's worth of release notes, cut down to short headlines. */
data class NoteSection(val kind: NoteKind, val items: List<String>)

/**
 * Turns EMITT's Markdown release notes (the CHANGELOG format: "### New", "### Fixed", "### Changed",
 * then "- **Headline.** Details") into a short list for the update and what's-new pop-ups.
 * Anything under other headings (like "Install") is left out.
 */
object ReleaseNotes {

    fun parse(md: String): List<NoteSection> {
        val found = linkedMapOf<NoteKind, MutableList<String>>()
        var kind: NoteKind? = null
        for (raw in md.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#") -> kind = kindOf(line.trimStart('#').trim())
                kind != null && (line.startsWith("- ") || line.startsWith("* ")) ->
                    found.getOrPut(kind) { mutableListOf() } += headline(line.drop(2))
            }
        }
        return NoteKind.entries.mapNotNull { k -> found[k]?.let { NoteSection(k, it) } }
    }

    private fun kindOf(heading: String) = when (heading.lowercase()) {
        "new", "added" -> NoteKind.NEW
        "fixed", "fixes" -> NoteKind.FIXED
        "changed", "improved", "improvements" -> NoteKind.IMPROVED
        else -> null
    }

    /**
     * "**Saved colour sets.** Long details" -> "Saved colour sets";
     * "**Comets** background: details" -> "Comets background".
     */
    fun headline(item: String): String {
        Regex("^\\*\\*(.+?)\\*\\*").find(item)?.groupValues?.get(1)?.let { bold ->
            if (bold.endsWith(".") || bold.endsWith(":")) return bold.trimEnd('.', ':')
        }
        val plain = item
            .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            .replace("**", "")
            .replace("`", "")
        val cut = listOf(plain.indexOf(": "), plain.indexOf(". ")).filter { it > 0 }.minOrNull() ?: plain.length
        val text = plain.take(cut).trimEnd('.', ':')
        return if (text.length > 110) text.take(107).trimEnd() + "…" else text
    }

    /** The "## [version]" section of a CHANGELOG, without its heading, or null if it isn't there. */
    fun section(changelog: String, version: String): String? {
        val start = Regex("(?m)^## ${Regex.escape(version)}\\s*$").find(changelog) ?: return null
        val rest = changelog.substring(start.range.last + 1)
        val end = Regex("(?m)^## ").find(rest)?.range?.first ?: rest.length
        return rest.substring(0, end)
    }

    /** Notes for [version] from the CHANGELOG bundled into the app at build time. */
    fun bundled(context: Context, version: String): List<NoteSection> = runCatching {
        val changelog = context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        section(changelog, version)?.let(::parse).orEmpty()
    }.getOrDefault(emptyList())
}
