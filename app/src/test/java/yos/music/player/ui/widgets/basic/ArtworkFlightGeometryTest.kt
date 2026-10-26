package yos.music.player.ui.widgets.basic

import org.junit.Assert.*
import org.junit.Test

class ArtworkFlightGeometryTest {
    private val full = ArtworkFlightRect(40f, 120f, 600f, 600f)
    private val mini = ArtworkFlightRect(30f, 1200f, 80f, 80f)
    @Test fun fullEndpointMatchesThePlayerArtwork() {
        assertEquals(full, artworkFlightFrame(full, mini, 1f))
    }
    @Test fun minimizedEndpointLandsExactlyOnTheThumbnail() {
        assertEquals(mini, artworkFlightFrame(full, mini, 0f))
    }
    @Test fun halfwayFrameMovesAndShrinksTogether() {
        assertEquals(ArtworkFlightRect(35f, 660f, 340f, 340f), artworkFlightFrame(full, mini, .5f))
    }
    @Test fun animationCannotOvershootOrUseInvalidProgress() {
        assertEquals(mini, artworkFlightFrame(full, mini, -1f))
        assertEquals(full, artworkFlightFrame(full, mini, 2f))
        assertEquals(mini, artworkFlightFrame(full, mini, Float.NaN))
    }
}
