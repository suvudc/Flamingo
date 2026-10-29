package yos.music.player.code.lyrics

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import yos.music.player.code.lyrics.bitchord.LyricAlignment
import yos.music.player.code.lyrics.bitchord.fromLegacyLyrics

class BitChordOfflineLyricsTest {
    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><head><metadata><ttm:agent xml:id="v1" type="person"/><ttm:agent xml:id="v2" type="person"/></metadata></head><body><div><p begin="0s" end="4s" ttm:agent="v1"><span begin="0s" end="0.5s">e</span><span begin="0.5s" end="3s">nough</span> <span ttm:role="x-bg"><span begin="1s" end="4s">echo</span></span></p><p begin="5s" end="7s" ttm:agent="v2"><span begin="5s" end="7s">again</span></p></div></body></tt>"""

    @Test fun persistedDocumentKeepsOriginalSyllablesVoicesAndGrowthWithoutNetwork() {
        val folder = Files.createTempDirectory("bitchord-offline").toFile()
        try {
            val disk = OfflineLyricsStore(folder)
            val key = disk.key("Song", "Artist", "Album", 7000, "lrc-red")
            // This is the same raw document a provider download retains, not flattened display text.
            disk.write(key, ttml)
            val online = OnlineLyrics.parse(ttml)!!.bitChordLines!!
            val offline = OnlineLyrics.parse(disk.read(key)!!)!!.bitChordLines!!
            assertEquals(online, offline)
            val first = offline.first { !it.isGap }
            assertEquals("enough", first.text)
            assertEquals(2, first.words.single().syllables.size)
            assertEquals(500L, first.words.single().syllables.first().endMs)
            assertEquals("echo", first.background!!.text)
            assertEquals(4000L, first.background!!.endMs)
            assertTrue(first.growingWords.isNotEmpty())
            assertEquals(LyricAlignment.End, offline.last { !it.isGap }.alignment)
        } finally { folder.deleteRecursively() }
    }

    @Test fun lineSyncedOfflineSourcesDoNotAcquireFakeWordTimings() {
        val parsed = OnlineLyrics.parse("[00:01.00]A whole lyric line\n[00:05.00]Another line")!!
        val lines = fromLegacyLyrics(parsed.entries, parsed.endTimes, parsed.otherSideForLines)
        assertEquals(2, lines.size)
        assertFalse(lines.first().isWordSynced)
        assertEquals(1000L, lines.first().timeMs)
    }

    @Test fun legacySyllablesRemainOneWordWithSeparateTimings() {
        val lines = fromLegacyLyrics(listOf(listOf(0f to "", 500f to "e", 2000f to "nough", 0f to "")), listOf(2000f), listOf(false))
        assertEquals("enough", lines.single().words.single().text)
        assertEquals(2, lines.single().words.single().syllables.size)
    }
}
