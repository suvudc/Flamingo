package yos.music.player.code

import org.junit.Assert.*
import org.junit.Test
import yos.music.player.code.canvas.CanvasArtwork
import yos.music.player.code.canvas.CanvasLookup

class CanvasLookupTest {
    @Test fun musicArtistSeparatorMatchesCatalogueCollaborations() {
        val queries = CanvasLookup.queries("Song", "Artist One、Artist Two", null, null)
        assertEquals("Artist One, Artist Two", queries.single().artist)
        assertTrue(queries.single().accepts(CanvasArtwork("video", title = "Song", artist = "Artist One & Artist Two")))
        assertFalse(queries.single().accepts(CanvasArtwork("video", title = "Song", artist = "Artist One")))
    }

    @Test fun absentAlbumStillSearchesByExactSongAndArtist() {
        val queries = CanvasLookup.queries("21 Guns", "Green Day", null, null)
        assertEquals(1, queries.size)
        assertTrue(queries.single().accepts(CanvasArtwork("video", title = "21 Guns", artist = "Green Day", album = "21st Century Breakdown")))
        assertFalse(queries.single().accepts(CanvasArtwork("video", title = "Holiday", artist = "Green Day")))
    }

    @Test fun deluxeTagsFindBaseReleaseWithoutDiscardingArtistCheck() {
        val cover = CanvasArtwork("video", title = "21st Century Breakdown", artist = "Green Day", album = "21st Century Breakdown")
        val queries = CanvasLookup.queries("21 Guns", "Green Day", null, "21st Century Breakdown (Deluxe Edition)")
        assertTrue(queries.any { it.albumSearch && it.accepts(cover) })
        assertFalse(queries.any { it.accepts(cover.copy(artist = "Other Artist")) })
    }

    @Test fun localCompilationTagCanFallBackToOriginalReleasePerTrack() {
        val queries = CanvasLookup.queries("21 Guns", "Green Day", "Various Artists", "My Rock Collection")
        val cover = CanvasArtwork("video", title = "21 Guns", artist = "Green Day", album = "21st Century Breakdown")
        val accepted = queries.filter { it.accepts(cover) }
        assertTrue(accepted.isNotEmpty())
        assertTrue(accepted.all { it.trackSpecific })
        assertTrue(queries.all { it.artist == "Green Day" })
    }

    @Test fun videoPackagingIsCleanedButLiveAndRemixRecordingsStayDistinct() {
        assertEquals("21 Guns", CanvasLookup.clean("21 Guns (Official Music Video)"))
        assertEquals("21 Guns", CanvasLookup.clean("21 Guns [Official Audio]"))
        val cover = CanvasArtwork("video", title = "21 Guns", artist = "Green Day")
        for (title in listOf("21 Guns (Live)", "21 Guns [Remix]")) {
            assertFalse(CanvasLookup.queries(title, "Green Day", null, null).any { it.accepts(cover) })
        }
    }

    @Test fun unknownAlbumIsNotRequiredAndCacheSeparatesSongs() {
        assertTrue(CanvasLookup.queries("Song", "Artist", "<unknown>", "Unknown Album").any { !it.albumSearch })
        assertNotEquals(CanvasLookup.cacheAlbum(null, "Song One"), CanvasLookup.cacheAlbum(null, "Song Two"))
        assertTrue(CanvasLookup.queries("Song", "<unknown>", null, "Album").isEmpty())
    }
}
