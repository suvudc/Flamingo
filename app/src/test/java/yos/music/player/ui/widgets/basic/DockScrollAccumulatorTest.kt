package yos.music.player.ui.widgets.basic

import org.junit.Assert.*
import org.junit.Test

class DockScrollAccumulatorTest {
    @Test fun shortMovementDoesNotCollapseDock() {
        val scroll = DockScrollAccumulator(24f)
        assertNull(scroll.scroll(-10f)); assertNull(scroll.scroll(-10f))
        assertEquals(true, scroll.scroll(-4f))
    }
    @Test fun scrollingBackExpandsDock() {
        val scroll = DockScrollAccumulator(24f)
        assertEquals(true, scroll.scroll(-30f))
        assertEquals(false, scroll.scroll(30f))
    }
    @Test fun oppositeMovementDoesNotCombineIntoFalseCollapse() {
        val scroll = DockScrollAccumulator(24f)
        repeat(10) { assertNull(scroll.scroll(-10f)); assertNull(scroll.scroll(10f)) }
    }
    @Test fun resetAndInvalidEventsDoNotChangeDock() {
        val scroll = DockScrollAccumulator(24f)
        assertNull(scroll.scroll(-20f)); scroll.reset()
        assertNull(scroll.scroll(Float.NaN)); assertNull(scroll.scroll(Float.POSITIVE_INFINITY))
        assertNull(scroll.scroll(0f)); assertNull(scroll.scroll(-10f))
    }
}
