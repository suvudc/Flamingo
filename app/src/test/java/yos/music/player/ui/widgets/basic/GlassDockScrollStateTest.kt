package yos.music.player.ui.widgets.basic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test

class GlassDockScrollStateTest {
    @Test fun overscrollWithoutListMovementDoesNotFoldDock() {
        val state = GlassDockScrollState(24f)
        state.connection.onPostScroll(Offset.Zero, Offset(0f, -100f), NestedScrollSource.UserInput)
        assertFalse(state.compact.value)
    }
    @Test fun onlyUserScrollingControlsDockAndNeverConsumesScroll() {
        val state = GlassDockScrollState(24f)
        val consumed = state.connection.onPostScroll(Offset(0f, -30f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(state.compact.value)
        assertEquals(Offset.Zero, consumed)
        state.connection.onPostScroll(Offset(0f, 30f), Offset.Zero, NestedScrollSource.SideEffect)
        assertTrue(state.compact.value)
        state.connection.onPostScroll(Offset(0f, 30f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(state.compact.value)
    }
}
