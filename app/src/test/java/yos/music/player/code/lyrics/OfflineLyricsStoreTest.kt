package yos.music.player.code.lyrics

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class OfflineLyricsStoreTest {
    @Test fun downloadSurvivesStoreRecreation() {
        val dir = Files.createTempDirectory("lyrics").toFile()
        try {
            val store = OfflineLyricsStore(dir)
            val key = store.key("Song", "Artist", "Album", 123000)
            store.write(key, "[00:01.00]Sung words")
            assertEquals("[00:01.00]Sung words", OfflineLyricsStore(dir).read(key))
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
        } finally { dir.deleteRecursively() }
    }
    @Test fun differentRecordingsDoNotShareDownloads() {
        val store = OfflineLyricsStore(Files.createTempDirectory("lyrics").toFile())
        assertNotEquals(store.key("Song", "A", "Album", 1000), store.key("Song", "B", "Album", 1000))
        assertNotEquals(store.key("Song", "A", "Album", 1000), store.key("Song", "A", "Album", 2000))
    }
    @Test fun failedReplacementKeepsOriginal() {
        val dir = Files.createTempDirectory("lyrics").toFile()
        try {
            val store = OfflineLyricsStore(dir); val key = store.key("Song", "A", "Album", 1)
            store.write(key, "good")
            assertTrue(runCatching { store.write(key, "") }.isFailure)
            assertEquals("good", store.read(key))
        } finally { dir.deleteRecursively() }
    }
    @Test fun invalidKeyCannotEscapeDirectory() {
        val store = OfflineLyricsStore(Files.createTempDirectory("lyrics").toFile())
        assertTrue(runCatching { store.write("../outside", "lyrics") }.isFailure)
    }
}
