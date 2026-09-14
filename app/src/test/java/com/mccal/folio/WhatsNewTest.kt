package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewTest {
    @Test fun `parses version sections and ignores other headings`() {
        val md = """
            # Changelog
            Intro text.
            ## [0.4.0] - 2026-09-14
            ### Added
            - Focus
            - Themes
            ### Fixed
            - A bug
            ## [0.3.0] - 2026-09-13
            - Loose item
            ## DuoLauncher history
            ### 0.15.0
            - Not Folio
        """.trimIndent()
        val notes = WhatsNew.parse(md)
        assertEquals(listOf("0.4.0", "0.3.0"), notes.map { it.version })
        assertEquals("2026-09-14", notes[0].date)
        assertEquals(listOf("Added" to listOf("Focus", "Themes"), "Fixed" to listOf("A bug")), notes[0].sections)
        assertEquals(listOf("" to listOf("Loose item")), notes[1].sections)
    }
    @Test fun `the bundled changelog's newest section matches the app version`() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val newest = WhatsNew.parse(java.io.File(root, "CHANGELOG.md").readText()).first()
        val gradle = java.io.File(root, "app/build.gradle.kts").readText()
        assertEquals(Regex("""val folioVersion = "([^"]+)"""").find(gradle)!!.groupValues[1], newest.version)
    }
}
