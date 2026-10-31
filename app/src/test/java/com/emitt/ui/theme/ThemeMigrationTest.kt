package com.emitt.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Looks saved by 1.3.0 and earlier must still load after backgrounds were renamed. */
class ThemeMigrationTest {
    @Test
    fun oldBackgroundNamesMapToTheirReplacements() {
        assertEquals(BgStyle.COMETS, decodeTheme("""{"colourway":"sakura","background":"SYNTH_GRID"}""").background)
        assertEquals(BgStyle.PULSAR, decodeTheme("""{"colourway":"sakura","background":"STATIC"}""").background)
    }

    @Test
    fun restOfTheLookSurvives() {
        val old = decodeTheme("""{"colourway":"sakura","background":"STATIC","glow":false,"icon":"SAKURA"}""")
        assertEquals("sakura", old.colourway)
        assertEquals(false, old.glow)
        assertEquals(LauncherIcon.SAKURA, old.icon)
        assertEquals(emptyList<SavedColours>(), old.savedColours)
    }

    @Test
    fun oldLooksKeepClassicControlsWhileNewInstallsGetBlade() {
        assertEquals(ControlStyle.CLASSIC, decodeTheme("""{"colourway":"sakura"}""").controls)
        assertEquals(ControlStyle.SEGMENT, decodeTheme("""{"colourway":"sakura","controls":"SEGMENT"}""").controls)
        assertEquals(ControlStyle.BLADE, ThemeConfig().controls)
    }
}
