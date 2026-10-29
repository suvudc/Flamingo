package yos.music.player.ui.widgets.basic
import org.junit.Test
import org.junit.Assert.*
class ArtworkFitTest {
    @Test fun squareCoverStaysComplete() { assertEquals(ArtworkFit(1f, 1f), artworkFit(400f, 400f, 512f, 512f)) }
    @Test fun portraitAndLandscapeVideosAreLetterboxed() {
        assertEquals(ArtworkFit(.5f, 1f), artworkFit(400f, 400f, 300f, 600f))
        assertEquals(ArtworkFit(1f, .5f), artworkFit(400f, 400f, 600f, 300f))
    }
    @Test fun resizedViewportDoesNotCrop() { assertEquals(ArtworkFit(.5f, 1f), artworkFit(800f, 400f, 400f, 400f)) }
    @Test fun unavailableOrInvalidVideoSizeHasSafeIdentityTransform() {
        assertEquals(ArtworkFit(1f, 1f), artworkFit(400f, 400f, 0f, 0f))
        assertEquals(ArtworkFit(1f, 1f), artworkFit(Float.NaN, 400f, 512f, 512f))
    }
}
