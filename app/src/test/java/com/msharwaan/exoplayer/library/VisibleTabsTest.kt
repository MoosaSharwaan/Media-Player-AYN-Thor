package com.msharwaan.exoplayer.library

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only tabs with something in them show (D53). */
class VisibleTabsTest {

    private fun card(name: String) = Card("k:$name", "MOVIE", name, null, "", null, null, null, playFileId = 1)

    @Test
    fun emptyTabsAreHidden() {
        val tabs = mapOf(Tab.CONTINUE to emptyList(), Tab.MOVIES to emptyList(), Tab.SERIES to listOf(card("A")), Tab.ANIME to listOf(card("B")), Tab.OTHERS to emptyList())
        assertEquals(listOf(Tab.SERIES, Tab.ANIME), Library.visibleTabs(tabs))
    }

    @Test
    fun anEmptyLibraryKeepsEveryTab() {
        assertEquals(Tab.entries, Library.visibleTabs(emptyMap()))
        assertEquals(Tab.entries, Library.visibleTabs(Tab.entries.associateWith { emptyList() }))
    }
}
