package yos.music.player.ui.pages

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class HomeLibraryShuffleTest {
    @Test fun wholeLibraryIsQueuedBeyondTheDailyMixLimit() {
        val library = (1..1_000).toList()
        val request = homeLibraryShuffle(library, Random(7))!!

        assertEquals(library, request.queue)
        assertEquals(library.size, request.queue.size)
        assertTrue(request.queue.contains(library.last()))
        assertTrue(request.shuffleModeEnabled)
    }

    @Test fun theStartingSongCanComeFromBeyondTheFirstFifty() {
        val library = (0 until 500).toList()
        val startingSongs = (0 until 30).map { seed ->
            homeLibraryShuffle(library, Random(seed))!!.firstSong
        }

        assertTrue(startingSongs.all { it in library })
        assertTrue(startingSongs.any { it >= 50 })
    }

    @Test fun emptyLibraryDoesNotStartPlayback() {
        assertNull(homeLibraryShuffle(emptyList<String>()))
    }

    @Test fun smallLibrariesKeepEverySongAndEnableShuffle() {
        for (size in listOf(1, 2, 49, 50, 51)) {
            val library = (0 until size).toList()
            val request = homeLibraryShuffle(library, Random(size))!!

            assertEquals(library, request.queue)
            assertTrue(request.firstSong in library)
            assertTrue(request.shuffleModeEnabled)
        }
    }

    @Test fun queueSurvivesARefreshOfTheLibrary() {
        val library = (0 until 600).toMutableList()
        val request = homeLibraryShuffle(library, Random(5))!!
        library.clear()
        library.add(-1)

        assertEquals((0 until 600).toList(), request.queue)
        assertTrue(request.firstSong in request.queue)
    }

    @Test fun theNextTapUsesSongsAddedSinceThePreviousTap() {
        val library = (0 until 49).toMutableList()
        val firstRequest = homeLibraryShuffle(library, Random(1))!!
        library.addAll(49 until 350)
        val nextRequest = homeLibraryShuffle(library, Random(2))!!

        assertEquals(49, firstRequest.queue.size)
        assertEquals(350, nextRequest.queue.size)
        assertTrue(nextRequest.queue.contains(349))
    }
}
