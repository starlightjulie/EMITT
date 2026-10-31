package com.emitt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The update pop-up shows only short headlines, grouped New / Fixed / Improved. */
class ReleaseNotesTest {
    private val md = """
        ### Fixed
        - **Be the tag on phones with a built-in NFC tag.** Some phones have an empty tag.

        ### New
        - **Saved colour sets.** "Use my own colours" now has its own page.
        - **Comets** background: a twinkling starfield. It replaces Synth Grid.
        - **Six new colourways**: Midnight, Sakura, Forest.

        ### Changed
        - New installs start in the **Mono** colourway. Your current look is kept.

        ### Install
        - Download the APK below.
    """.trimIndent()

    @Test
    fun keepsOnlyShortHeadlinesInOrder() {
        val s = ReleaseNotes.parse(md)
        assertEquals(listOf(NoteKind.NEW, NoteKind.FIXED, NoteKind.IMPROVED), s.map { it.kind })
        assertEquals(listOf("Saved colour sets", "Comets background", "Six new colourways"), s[0].items)
        assertEquals(listOf("Be the tag on phones with a built-in NFC tag"), s[1].items)
        assertEquals(listOf("New installs start in the Mono colourway"), s[2].items)
    }

    @Test
    fun findsAVersionSection() {
        val log = "# Changelog\n\n## 1.3.2\n\n### New\n- **A.** x\n\n## 1.3.1\n\n### New\n- **B.** y\n"
        assertEquals(listOf("A"), ReleaseNotes.parse(ReleaseNotes.section(log, "1.3.2")!!).single().items)
        assertEquals(listOf("B"), ReleaseNotes.parse(ReleaseNotes.section(log, "1.3.1")!!).single().items)
        assertNull(ReleaseNotes.section(log, "9.9.9"))
    }
}
