package yos.music.player.code.lyrics

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LyricTimelineTest {
    private fun words() = lyricTimeline(listOf(listOf(1000f to "", 1500f to "Hello", 2000f to "", 3000f to " world", 1000f to "")), listOf(3000f)).single()
    @Test fun genuineWordGapsHoldTheHighlight() {
        val line = words()
        assertTrue(line.wordTimed)
        assertEquals(5f, line.sungCharacters(1750f), .001f)
        assertEquals(8f, line.sungCharacters(2500f), .001f)
    }
    @Test fun pauseAndSeekAreReadDirectlyFromTheClock() {
        val line = words()
        assertEquals(11f, line.sungCharacters(3500f), .001f)
        assertEquals(2.5f, line.sungCharacters(1250f), .001f)
        assertEquals(0f, line.sungCharacters(0f), .001f)
    }
    @Test fun wholeLineLrcDoesNotInventWordTiming() {
        val line = lyricTimeline(listOf(listOf(1000f to "", 1000f to "One whole line", 1000f to "")), listOf(5000f)).single()
        assertFalse(line.wordTimed)
        assertEquals(14f, line.sungCharacters(1100f), .001f)
    }
    @Test fun singleWordWithRealStartAndEndStillAnimates() {
        val line = lyricTimeline(listOf(listOf(1000f to "", 3000f to "Hello", 1000f to "")), listOf(3000f)).single()
        assertTrue(line.wordTimed)
        assertEquals(2.5f, line.sungCharacters(2000f), .001f)
    }
    @Test fun noPreviousLineDuringInstrumentalGap() {
        val line = words()
        assertEquals(-1, activeLyricIndex(listOf(line), 999f))
        assertEquals(0, activeLyricIndex(listOf(line), 2000f))
        assertEquals(-1, activeLyricIndex(listOf(line), 3000f))
    }
    @Test fun compactStripRetainsLyricsAcrossIntroGapAndOutro() {
        val first = words()
        val second = first.copy(start = 6000f, end = 8000f, text = "Next line")
        val lines = listOf(first, second)
        assertEquals(0, displayLyricIndex(lines, 0f))
        assertEquals(0, displayLyricIndex(lines, 4000f))
        assertEquals(1, displayLyricIndex(lines, 6500f))
        assertEquals(1, displayLyricIndex(lines, 9000f))
        assertEquals(0, displayLyricIndex(lines, 2000f)) // seeking back restores the earlier lyric
        assertEquals(-1, displayLyricIndex(emptyList(), 2000f))
    }
    @Test fun emptyLinesDoNotShiftTranslationMetadata() {
        val lines = lyricTimeline(listOf(listOf(0f to "", 0f to ""), listOf(1000f to "", 1500f to "Hi", 1000f to "")), listOf(1000f, 3000f))
        assertEquals(1, lines.single().sourceIndex)
    }
    @Test fun providerDownloadsRemainIndependentOffline() {
        val root = Files.createTempDirectory("lyrics-providers").toFile()
        try {
            val store = OfflineLyricsStore(root)
            val auto = store.key("Song", "Artist", "Album", 3000)
            val selected = store.key("Song", "Artist", "Album", 3000, "lrclib")
            store.write(auto, "word lyrics"); store.write(selected, "line lyrics")
            assertNotEquals(auto, selected)
            assertEquals("word lyrics", store.read(auto))
            assertEquals("line lyrics", store.read(selected))
        } finally { root.deleteRecursively() }
    }
    @Test fun unknownSavedProviderFallsBackToAutomatic() {
        assertEquals(LyricsProvider.Auto, LyricsProvider.fromId("removed-provider"))
        assertEquals(LyricsProvider.Bini, LyricsProvider.fromId("bini"))
    }
}
