package yos.music.player.ui.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCollageDataTest {
    @Test fun emptyLoadingLibraryDoesNotCreateCoverSlots() {
        assertTrue(homeCollageSlots(emptyList<String>()).isEmpty())
    }

    @Test fun libraryCanLoadAndClearWithoutDividingByZero() {
        val states = listOf(emptyList(), listOf("album"), emptyList())
        assertEquals(listOf(0, 5, 0), states.map { homeCollageSlots(it).size })
    }

    @Test fun smallLibrariesFillAllFivePositions() {
        assertEquals(List(5) { "one" }, homeCollageSlots(listOf("one")))
        assertEquals(listOf("one", "two", "one", "two", "one"), homeCollageSlots(listOf("one", "two")))
    }

    @Test fun deferredCollageKeepsItsSnapshotWhenLibraryChanges() {
        val source = mutableListOf("one", "two", "three", "four", "five", "six")
        val slots = homeCollageSlots(source)
        source.clear()
        assertEquals(listOf("one", "two", "three", "four", "five"), slots)
        assertTrue(homeCollageSlots(source).isEmpty())
    }
}
