// Ported from suvudc/BitChord, GPL-3.0. See docs/third-party/BitChord-lyrics.md.
package yos.music.player.ui.widgets.bitchord

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * How far a finger may wander inside a lower-half tap and still be a tap, as a
 * multiple of the platform's own slop.
 *
 * The list underneath starts scrolling at exactly one slop, and it consumes the
 * gesture when it does — so a tap that shifted by a hair more than that was not
 * merely ignored, it was taken by the list and became a browse. Every shortfall
 * here is a tap somebody meant and did not get.
 */
private const val TAP_SLOP_FACTOR = 2.5f

/** Reveal controls on a lower-half tap using normal Compose tap arbitration.
 * Never consume movement at the Initial pass: doing so cancels LazyColumn's
 * drag detector before it can cross touch slop, even if we later let go.
 */
@Composable
internal fun Modifier.revealLyricsControlsOnTap(
    enabled: Boolean,
    onReveal: () -> Unit,
): Modifier {
    val currentOnReveal = rememberUpdatedState(onReveal)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.y < size.height / 2f || down.type == PointerType.Mouse) {
                return@awaitEachGesture
            }
            down.consume()
            val up = waitForUpOrCancellation()
            if (up != null) {
                up.consume()
                currentOnReveal.value()
            }
        }
    }
}

/**
 * Toggle Spotify Canvas controls from an unhandled video tap.
 *
 * Unlike [revealLyricsControlsOnTap], this observes at the final pointer pass
 * and never consumes the gesture. The compact title/artist row remains on
 * screen while the rest of the player is hidden, so its heart, overflow menu,
 * album and artist targets must receive taps normally. Any empty video space
 * that no child claimed can show or hide the full control deck.
 */
@Composable
internal fun Modifier.toggleSpotifyCanvasControlsOnTap(
    enabled: Boolean,
    onToggle: () -> Unit,
): Modifier {
    val currentOnToggle = rememberUpdatedState(onToggle)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val tapSlop = viewConfiguration.touchSlop * TAP_SLOP_FACTOR
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dragged = false
            var claimed = down.isConsumed
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                claimed = claimed || change.isConsumed
                if ((change.position - down.position).getDistance() > tapSlop ||
                    event.changes.size > 1
                ) {
                    dragged = true
                }
                if (!change.pressed) {
                    if (!dragged && !claimed) currentOnToggle.value()
                    break
                }
            } while (true)
        }
    }
}
