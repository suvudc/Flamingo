package yos.music.player.ui.widgets.basic

import org.junit.Assert.*
import org.junit.Test

class PlayerSheetStateTest {
    @Test fun closedPlayerNeverScalesHomeEvenWithStaleDragOffset() {
        for (offset in listOf(-20f, 0f, 1f, 500f, 1200f)) {
            assertEquals(0f, playerSheetProgress(false, offset, 1000), 0f)
        }
    }
    @Test fun negativeDragCannotReopenOrScaleThePlayer() {
        assertEquals(0f, playerSheetProgress(true, -15f, 1000), 0f)
    }
    @Test fun openPlayerProgressIsBounded() {
        assertEquals(.5f, playerSheetProgress(true, 500f, 1000), 0f)
        assertEquals(1f, playerSheetProgress(true, 1500f, 1000), 0f)
    }
    @Test fun initialLayoutAndInvalidOffsetsStayClosed() {
        assertEquals(0f, playerSheetProgress(true, 500f, 0), 0f)
        assertEquals(0f, playerSheetProgress(true, Float.NaN, 1000), 0f)
    }
    @Test fun slowReleaseSettlesAtNearestEndpoint() {
        assertFalse(playerSheetTargetOpen(.1f, 0f))
        assertTrue(playerSheetTargetOpen(.8f, 0f))
    }
    @Test fun DeliberateFlickUsesItsDirection() {
        assertFalse(playerSheetTargetOpen(.8f, -1000f))
        assertTrue(playerSheetTargetOpen(.1f, 1000f))
    }
}
