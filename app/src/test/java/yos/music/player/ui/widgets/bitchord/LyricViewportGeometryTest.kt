package yos.music.player.ui.widgets.bitchord

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricViewportGeometryTest {
    @Test fun lyricsReachMeasuredSliderInsteadOfStoppingAtControlHostTop() {
        // 154dp header + insets put the list at 534px. The translation
        // action and control padding put the slider well below its host.
        val height = lyricViewportHeightPx(534f, 1500f, 1800f, true, 24f)
        assertEquals(1500f - 24f, 534f + height, .01f)
    }

    @Test fun commonRootTranslationDoesNotChangeAvailableLyricsSpace() {
        val original = lyricViewportHeightPx(534f, 1500f, 1800f, true, 24f)
        val translated = lyricViewportHeightPx(634f, 1600f, 1800f, true, 24f)
        assertEquals(original, translated, .01f)
    }

    @Test fun hiddenControlsGiveLyricsTheWholeViewport() {
        assertEquals(1800f, lyricViewportHeightPx(534f, 1500f, 1800f, false, 24f), .01f)
    }

    @Test fun resizedAndOverlappingControlsNeverLetWordsCrossTheSlider() {
        assertEquals(0f, lyricViewportHeightPx(700f, 600f, 800f, true, 24f), .01f)
        assertEquals(800f, lyricViewportHeightPx(100f, 2000f, 800f, true, 24f), .01f)
    }

    @Test fun firstLayoutHasAFiniteBoundedFallback() {
        assertEquals(560f, lyricViewportHeightPx(Float.NaN, Float.NaN, 1000f, true, 24f), .01f)
        assertEquals(0f, lyricViewportHeightPx(0f, 1000f, 0f, true, 24f), .01f)
    }
}
