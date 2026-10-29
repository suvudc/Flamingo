package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.sign

internal enum class PlayerDragAxis { Pending, Horizontal, Vertical }
internal fun playerDragAxis(x: Float, y: Float, slop: Float): PlayerDragAxis = when {
    abs(x) > slop && abs(x) >= abs(y) -> PlayerDragAxis.Horizontal
    abs(y) > slop && abs(y) > abs(x) * 1.15f -> PlayerDragAxis.Vertical
    else -> PlayerDragAxis.Pending
}

/** Observe before child click handlers; intercept only an intentional vertical drag.
 * Horizontal seek/volume gestures and ordinary taps continue to the original controls. */
fun Modifier.playerDismissGesture(enabled: Boolean, onDelta: (Float) -> Unit, onRelease: (Float, Boolean) -> Unit): Modifier = composed {
    val delta by rememberUpdatedState(onDelta)
    val release by rememberUpdatedState(onRelease)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onGloballyPositioned { coordinates[0] = it }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            fun rootPoint(point: Offset): Offset = coordinates[0]?.takeIf { it.isAttached }?.localToRoot(point) ?: point
            val origin = rootPoint(down.position)
            var previousRoot = origin
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, origin)
            var travel = Offset.Zero
            var dragging = false
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val root = rootPoint(change.position)
                    val movement = root - previousRoot
                    previousRoot = root
                    travel = root - origin
                    velocity.addPosition(change.uptimeMillis, root)
                    if (!change.pressed) {
                        if (dragging) {
                            change.consume()
                            val speed = -velocity.calculateVelocity().y
                            val dismiss = travel.y >= 64.dp.toPx()
                            Log.d("MusicPlayerDrag", "Release distance=${travel.y} velocity=$speed dismiss=$dismiss")
                            release(speed, dismiss)
                            released = true
                        }
                        break
                    }
                    val total = travel
                    if (!dragging) {
                        when (playerDragAxis(total.x, total.y, viewConfiguration.touchSlop)) {
                            PlayerDragAxis.Horizontal -> break
                            PlayerDragAxis.Vertical -> {
                                dragging = true
                                Log.d("MusicPlayerDrag", "Vertical drag started")
                                change.consume()
                                delta(-(total.y - sign(total.y) * viewConfiguration.touchSlop))
                            }
                            PlayerDragAxis.Pending -> Unit
                        }
                    } else {
                        val dy = movement.y
                        change.consume()
                        delta(-dy)
                    }
                }
            } finally {
                if (dragging && !released) release(0f, false)
            }
        }
    }
}
