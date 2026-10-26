package yos.music.player.code

import org.junit.Assert.*
import org.junit.Test
import yos.music.player.code.canvas.CanvasArtwork

class CanvasMatchingTest {
    @Test fun accentsAndArtistSeparatorsDoNotLoseCorrectCover() {
        assertTrue(CanvasArtwork("clip", title = "Déjà Vu", artist = "Beyoncé & Jay Z", album = "Album")
            .matches("Deja Vu", "Beyonce, Jay Z", "Album"))
    }

    @Test fun wrongAlbumOrArtistIsRejected() {
        val cover = CanvasArtwork("clip", title = "21 Guns", artist = "Green Day", album = "21st Century Breakdown")
        assertFalse(cover.matches("21 Guns", "Other Artist", "21st Century Breakdown"))
        assertFalse(cover.matches("21 Guns", "Green Day", "American Idiot"))
    }

    @Test fun nonLatinTitlesAreNotAllTreatedAsTheSameAlbum() {
        val cover = CanvasArtwork("clip", title = "नमस्ते", artist = "कलाकार", album = "पहिलो")
        assertTrue(cover.matches("नमस्ते", "कलाकार", "पहिलो"))
        assertFalse(cover.matches("अर्को", "कलाकार", "पहिलो"))
    }
}
