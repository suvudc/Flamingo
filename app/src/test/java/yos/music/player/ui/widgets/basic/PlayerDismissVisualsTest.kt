package yos.music.player.ui.widgets.basic

import org.junit.Assert.*
import org.junit.Test

class PlayerDismissVisualsTest {
    @Test fun controlsAreGoneWhileCoverStillTravels() {
        val frame = playerDismissVisuals(.5f, 1f)
        assertEquals(0f, frame.controlsAlpha, 0f)
        assertEquals(.5f, frame.pageAlpha, 0f)
        assertEquals(.5f, frame.fraction, 0f)
    }
    @Test fun closeFromPartiallyDraggedPositionStartsContinuous() {
        val frame = playerDismissVisuals(.6f, .6f)
        assertEquals(1f, frame.controlsAlpha, 0.00001f)
        assertEquals(0f, frame.pageAlpha, 0f)
        val end = playerDismissVisuals(0f, .6f)
        assertEquals(0f, end.controlsAlpha, 0f)
        assertEquals(1f, end.pageAlpha, 0f)
    }
    @Test fun invalidProgressNeverLeavesGhostControls() {
        for (frame in listOf(playerDismissVisuals(Float.NaN, 1f), playerDismissVisuals(1f, 0f))) {
            assertEquals(0f, frame.controlsAlpha, 0f)
            assertEquals(1f, frame.pageAlpha, 0f)
        }
    }
    @Test fun missingArtworkStillOwnsTheCloseAndCanRecover() {
        val motion = PlayerArtworkMotion()
        motion.begin(.8f)
        assertTrue(motion.closing)
        assertFalse(motion.hasFlight)
        motion.cancelClose()
        assertFalse(motion.closing)
        assertFalse(motion.hasFlight)
        motion.begin(.5f)
        assertTrue(motion.closing)
        motion.reset()
        assertFalse(motion.closing)
    }
}
