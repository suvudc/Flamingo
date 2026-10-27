package yos.music.player.ui.widgets.basic

import org.junit.Assert.*
import org.junit.Test

class PlayerDismissGestureTest {
    @Test fun tapMotionDoesNotMinimize() {
        assertEquals(PlayerDragAxis.Pending, playerDragAxis(2f, 5f, 12f))
    }
    @Test fun horizontalSeekRemainsWithSlider() {
        assertEquals(PlayerDragAxis.Horizontal, playerDragAxis(40f, 9f, 12f))
    }
    @Test fun verticalMotionFromContentBelongsToPlayer() {
        assertEquals(PlayerDragAxis.Vertical, playerDragAxis(6f, 70f, 12f))
        assertEquals(PlayerDragAxis.Vertical, playerDragAxis(6f, -70f, 12f))
    }
}
