package yos.music.player.ui.widgets.basic

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Stable
class GlassDockScrollState(thresholdPx: Float) {
    val compact = mutableStateOf(false)
    private val movement = DockScrollAccumulator(thresholdPx)
    fun expand() { movement.reset(); compact.value = false }
    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
                movement.scroll(consumed.y)?.let { compact.value = it }
            }
            return Offset.Zero // Observe scrolling without consuming or changing the list's fling.
        }
    }
}

@Composable
fun rememberGlassDockScrollState(): GlassDockScrollState {
    val density = LocalDensity.current
    return remember(density) { GlassDockScrollState(with(density) { 24.dp.toPx() }) }
}
