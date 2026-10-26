package yos.music.player.code.lyrics

import org.junit.Test
import org.junit.Assert.*

class OnlineLyricsTest {
    @Test fun betterLyricsEnvelopePreservesWordTimes() {
        val json = """{"ttml":"<tt xmlns='http://www.w3.org/ns/ttml'><body><div><p begin='1.0' end='3.0'><span begin='1.0' end='1.5'>Hello</span> <span begin='2.0' end='3.0'>world</span></p></div></body></tt>"}"""
        val parsed = OnlineLyrics.parse(json)!!
        assertTrue(parsed.isTtml)
        assertTrue(OnlineLyrics.wordTimed(parsed))
        assertEquals(3000f, parsed.endTimes.first())
        assertTrue(parsed.entries.first().any { it.first == 2000f && it.second.isEmpty() })
        assertEquals(listOf(1500f, 3000f), parsed.entries.first().filter { it.second.isNotBlank() }.map { it.first })
    }
    @Test fun lineFallbackDoesNotPretendToBeWordTimed() {
        val parsed = OnlineLyrics.parse("""{"syncedLyrics":"[00:01.500]One line\n[00:04.000]Next line"}""")!!
        assertEquals(2, parsed.entries.size)
        assertFalse(OnlineLyrics.wordTimed(parsed))
        assertEquals(1500f, parsed.entries.first().first().first)
    }
    @Test fun rejectsProviderErrorsAndUntimedHtml() {
        assertNull(OnlineLyrics.parse("""{"error":"not found","lyrics":"wrong"}"""))
        assertNull(OnlineLyrics.parse("<html>Not available</html>"))
        assertNull(OnlineLyrics.parse("Lyrics not found"))
    }
    @Test fun recordingMatchIgnoresRemasterButRejectsLiveAndWrongLength() {
        assertTrue(OnlineLyrics.matches("Let Down", "Radiohead", 299000, "Let Down (Remastered)", "Radiohead", 300.0))
        assertFalse(OnlineLyrics.matches("Let Down", "Radiohead", 299000, "Let Down (Live)", "Radiohead", 300.0))
        assertFalse(OnlineLyrics.matches("Let Down", "Radiohead", 299000, "Let Down", "Radiohead", 350.0))
        assertFalse(OnlineLyrics.matches("Let Down", "Radiohead", 299000, "Let Down", "Other Artist", 299.0))
    }
    @Test fun rawLrcRemainsReadable() {
        assertEquals("Hello", OnlineLyrics.parse("[00:01.00]Hello")!!.entries.first()[1].second)
    }
    @Test fun savedEnvelopeRoundTripsOffline() {
        val raw = """{"source":"lrc.red","lyrics":"[00:01.00]Hello"}"""
        assertEquals("Hello", OnlineLyrics.parse(raw)!!.entries.first()[1].second)
    }
}
