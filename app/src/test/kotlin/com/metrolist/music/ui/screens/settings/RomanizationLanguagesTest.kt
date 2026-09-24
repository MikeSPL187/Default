package com.metrolist.music.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizationLanguagesTest {
    @Test
    fun `empty setting uses the defaults`() {
        assertEquals(defaultList, parseRomanizationLanguages(""))
    }

    @Test
    fun `saved choices override defaults`() {
        val parsed = parseRomanizationLanguages("Japanese:false,Korean:true").toMap()
        assertFalse(parsed.getValue("Japanese"))
        assertTrue(parsed.getValue("Korean"))
    }

    @Test
    fun `malformed entries are skipped instead of crashing`() {
        val enabled = enabledRomanizationLanguages("Japanese:false,broken,:true,Korean:true:extra")
        assertFalse("Japanese" in enabled)
        // Korean's entry is malformed, so it keeps its default.
        assertEquals(defaultList.toMap().getValue("Korean"), "Korean" in enabled)
    }
}
