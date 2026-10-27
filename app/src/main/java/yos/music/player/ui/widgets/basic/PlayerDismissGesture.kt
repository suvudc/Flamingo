package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
fun Modifier.playerDismissGesture(enabled: Boolean, onDelta: (Float) -> Unit, onRelease: (Float) -> Unit): Modifier = composed {
    val delta by rememberUpdatedState(onDelta)
    val release by rememberUpdatedState(onRelease)
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            var travel = Offset.Zero
            var dragging = false
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    travel += change.position - change.previousPosition
                    velocity.addPosition(change.uptimeMillis, down.position + travel)
                    if (!change.pressed) {
                        if (dragging) { change.consume(); release(-velocity.calculateVelocity().y); released = true }
                        break
                    }
                    val total = travel
                    if (!dragging) {
                        when (playerDragAxis(total.x, total.y, viewConfiguration.touchSlop)) {
                            PlayerDragAxis.Horizontal -> break
                            PlayerDragAxis.Vertical -> {
                                dragging = true
                                change.consume()
                                delta(-(total.y - sign(total.y) * viewConfiguration.touchSlop))
                            }
                            PlayerDragAxis.Pending -> Unit
                        }
                    } else {
                        val dy = change.position.y - change.previousPosition.y
                        change.consume()
                        delta(-dy)
                    }
                }
            } finally {
                if (dragging && !released) release(0f)
            }
        }
    }
}
