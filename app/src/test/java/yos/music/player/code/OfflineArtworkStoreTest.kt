package yos.music.player.code

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class OfflineArtworkStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun store(responses: Map<String, String>, requested: MutableList<String> = mutableListOf()) =
        OfflineArtworkStore(
            readText = { url -> requested.add(url); responses[url] ?: throw IOException("Offline: $url") },
            download = { url, destination, budget ->
                requested.add(url)
                val bytes = (responses[url] ?: throw IOException("Offline: $url")).toByteArray()
                if (bytes.size > budget) throw IOException("Over budget")
                destination.writeBytes(bytes)
                bytes.size.toLong()
            },
            validateVideo = { it.readText().startsWith("video") },
        )

    @Test fun downloadedMp4IsReusedAfterRestartWithNoNetworkCalls() = runBlocking {
        val directory = File(temporary.root, "album")
        val online = store(mapOf("https://example.com/cover.mp4" to "video frames"))
        val saved = online.save("https://example.com/cover.mp4", directory)
        assertEquals("video frames", saved?.readText())
        val offlineRequests = mutableListOf<String>()
        val offline = store(emptyMap(), offlineRequests)
        assertEquals(saved, offline.save("https://expired.example/cover.mp4", directory))
        assertTrue(offlineRequests.isEmpty())
    }

    @Test fun segmentedHlsDownloadsEveryMapKeyAndSegmentAndPlaysFromLocalPlaylist() = runBlocking {
        val base = "https://example.com/cover/"
        val requests = mutableListOf<String>()
        val responses = mapOf(
            "${base}master.m3u8" to "#EXTM3U\n#EXT-X-STREAM-INF:CODECS=\"avc1\",RESOLUTION=512x512\nmedia.m3u8",
            "${base}media.m3u8" to """
                #EXTM3U
                #EXT-X-TARGETDURATION:3
                #EXT-X-KEY:METHOD=AES-128,URI="../key.bin"
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:3,
                part1.m4s?token=old
                #EXTINF:3,
                part2.m4s
                #EXT-X-ENDLIST
            """.trimIndent(),
            "https://example.com/key.bin" to "0123456789abcdef",
            "${base}init.mp4" to "initialization",
            "${base}part1.m4s?token=old" to "first frames",
            "${base}part2.m4s" to "last frames",
        )
        val directory = File(temporary.root, "album")
        val cover = store(responses, requests).save("${base}master.m3u8", directory)
        assertNotNull(cover)
        val playlist = cover!!.readText()
        assertFalse(playlist.contains("https://"))
        assertFalse(playlist.contains("token=old"))
        assertEquals(responses.keys, requests.toSet())
        val offlineRequests = mutableListOf<String>()
        assertEquals(cover, store(emptyMap(), offlineRequests).save("${base}master.m3u8", directory))
        assertTrue(offlineRequests.isEmpty())
        assertEquals(4, directory.listFiles()!!.count { it.name.startsWith("asset-") })
    }

    @Test fun byteRangePlaylistSavesSharedFileOnceAndPreservesRanges() = runBlocking {
        val url = "https://example.com/media.m3u8"
        val playlist = """
            #EXTM3U
            #EXT-X-MAP:URI="all.mp4",BYTERANGE="100@0"
            #EXTINF:3,
            #EXT-X-BYTERANGE:200@100
            all.mp4
            #EXTINF:3,
            #EXT-X-BYTERANGE:300@300
            all.mp4
            #EXT-X-ENDLIST
        """.trimIndent()
        val requests = mutableListOf<String>()
        val saved = store(mapOf(url to playlist, "https://example.com/all.mp4" to "all frames"), requests)
            .save(url, File(temporary.root, "album"))
        assertNotNull(saved)
        assertTrue(saved!!.readText().contains("#EXT-X-BYTERANGE:300@300"))
        assertEquals(1, requests.count { it.endsWith("all.mp4") })
    }

    @Test fun missingSegmentNeverPublishesPartialCoverAndRetrySucceeds() = runBlocking {
        val url = "https://example.com/media.m3u8"
        val playlist = "#EXTM3U\n#EXTINF:3,\n1.ts\n#EXTINF:3,\n2.ts\n#EXT-X-ENDLIST"
        val directory = File(temporary.root, "album")
        val partial = mapOf(url to playlist, "https://example.com/1.ts" to "first")
        assertNull(store(partial).save(url, directory))
        assertNull(OfflineArtworkStore.cached(directory))
        assertFalse(File(temporary.root, ".album.download").exists())
        assertNotNull(store(partial + ("https://example.com/2.ts" to "second")).save(url, directory))
    }

    @Test fun cancellationCleansStagingAndDoesNotPublishCover() = runBlocking {
        val directory = File(temporary.root, "album")
        val downloader = OfflineArtworkStore(
            readText = { "" },
            download = { _, file, _ -> file.writeText("partial"); throw CancellationException("Left player") },
            validateVideo = { true },
        )
        try {
            downloader.save("https://example.com/cover.mp4", directory)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertNull(OfflineArtworkStore.cached(directory))
        assertFalse(File(temporary.root, ".album.download").exists())
    }

    @Test fun truncatedDownloadIsNotReused() = runBlocking {
        val directory = File(temporary.root, "album")
        val saved = store(mapOf("https://example.com/cover.mp4" to "video frames"))
            .save("https://example.com/cover.mp4", directory)!!
        saved.writeText("vid")
        assertNull(OfflineArtworkStore.cached(directory))
    }

    @Test fun liveAndDrmPlaylistsAreRejectedInsteadOfLeavingOnlineReferences() = runBlocking {
        val url = "https://example.com/media.m3u8"
        for (playlist in listOf(
            "#EXTM3U\n#EXTINF:3,\nsegment.ts",
            "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\"\n#EXTINF:3,\nsegment.ts\n#EXT-X-ENDLIST",
        )) {
            assertNull(store(mapOf(url to playlist)).save(url, File(temporary.root, "album")))
        }
    }

    @Test fun invalidMp4DoesNotReplaceStillCover() = runBlocking {
        assertNull(store(mapOf("https://example.com/bad.mp4" to "not video"))
            .save("https://example.com/bad.mp4", File(temporary.root, "album")))
    }
}
