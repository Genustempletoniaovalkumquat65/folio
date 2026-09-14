package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Folio has to lay out on any Android window, not just the Galaxy Z Fold it's developed on. */
class ScreenMatrixTest {
    private data class Screen(val name: String, val width: Float, val height: Float)

    private val screens = listOf(
        Screen("small phone", 320f, 568f), Screen("phone", 360f, 640f), Screen("Pixel", 412f, 915f),
        Screen("phone landscape", 915f, 412f), Screen("small landscape", 640f, 360f),
        Screen("flip cover", 388f, 384f), Screen("split-screen phone half", 412f, 450f),
        Screen("Fold cover", 390f, 910f), Screen("Fold cover landscape", 910f, 390f),
        Screen("Fold inner", 932f, 704f), Screen("Fold inner portrait", 704f, 932f), Screen("Fold split half", 460f, 704f),
        Screen("small tablet portrait", 600f, 960f), Screen("tablet portrait", 800f, 1280f), Screen("tablet landscape", 1280f, 800f),
        Screen("big tablet", 1366f, 1024f), Screen("tablet split half", 640f, 800f), Screen("desktop window", 1920f, 1080f),
        Screen("big desktop", 2560f, 1440f), Screen("freeform window", 500f, 700f),
    )

    @Test fun `phones and foldables draw at the system density`() {
        for (s in listOf(screens[0], screens[1], screens[2], screens[3], screens[5], screens[7], screens[9], screens[10])) {
            assertEquals(s.name, 1f, uiScale(s.width, s.height))
        }
    }

    @Test fun `big screens scale up but never past the cap`() {
        val tablet = uiScale(1280f, 800f)
        assertTrue(tablet > 1.05f)
        assertEquals(tablet, uiScale(800f, 1280f))
        assertEquals(1.45f, uiScale(2560f, 1440f))
        for (s in screens) {
            val scale = uiScale(s.width, s.height)
            assertTrue(s.name, scale in 1f..1.45f)
            // Scaling never turns a regular window compact: the layout family matches the real window.
            assertEquals(s.name, isRegularSize(s.width, s.height), isRegularSize(s.width / scale, s.height / scale))
        }
    }

    @Test fun `Home fits every window without cropping or overlapping`() {
        for (s in screens) for (labels in listOf(true, false)) {
            val scale = uiScale(s.width, s.height)
            val w = s.width / scale; val h = s.height / scale
            val status = if (isRegularSize(w, h)) 180f else 0f
            val g = homeGeometry(w, h, LayoutPreset(), labels, statusHeight = status)
            val tag = "${s.name} (${w.toInt()}×${h.toInt()})"
            assertTrue("$tag rows ${g.rowHeight}", g.rowHeight >= 48f)
            assertTrue("$tag dock rows", g.dockRowHeight >= 48f)
            assertTrue("$tag icon ${g.iconSize}", g.iconSize >= 32f)
            assertTrue("$tag home width", g.homeWidth <= w)
            if (!g.horizontalDock) assertTrue("$tag grid ${g.gridWidth} + dock beside it", g.gridWidth + LayoutPreset().sanitized().dockWidth <= g.homeWidth)
            else assertTrue("$tag grid", g.gridWidth <= w)
            assertTrue("$tag dock under the top", g.dockTop >= 8f)
            if (h >= 400f) assertTrue("$tag dock ${g.dockTop}+${g.dockHeight} inside $h", g.dockTop + g.dockHeight <= h)
            // Two Home panels only when there's room for both; tall roomy windows get the bottom dock bar.
            assertFalse("$tag expanded and bar", g.expanded && g.horizontalDock)
            if (g.expanded) assertTrue(tag, w >= 650f && w > h)
        }
    }
}
