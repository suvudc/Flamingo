package yos.music.player.ui.widgets.bitchord

import android.os.Build
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import yos.music.player.code.MediaController

internal val PLAYER_GUTTER = 30.dp
internal val renderEffectBlurSupported: Boolean get() = Build.VERSION.SDK_INT >= 31
internal val LocalPlayerTint = staticCompositionLocalOf { Color.White }

@Stable
internal class LyricPlayhead(private val position: PlaybackPosition) {
    val positionMs get() = position.positionMs
    val sampledAtNanos get() = position.sampledAtNanos
    val discontinuity get() = position.seeks.toLong()
}

@Composable
internal fun rememberIsForeground(): Boolean =
    LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState().value.isAtLeast(Lifecycle.State.STARTED)

/** Media3 adapter only. The frame interpolation engine itself is BitChord's. */
@Composable
internal fun rememberMusicLyricPlayhead(trackKey: String): Pair<LyricPlayhead, Boolean> {
    val position = remember(trackKey) { PlaybackPosition() }
    val head = remember(position) { LyricPlayhead(position) }
    val foreground = rememberIsForeground()
    LaunchedEffect(position, foreground) {
        if (!foreground) return@LaunchedEffect
        var attached: Player? = null
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo, reason: Int) {
                position.seeks++
                position.report(newPosition.positionMs.coerceAtLeast(0L))
            }
        }
        try {
            while (true) {
                val player = MediaController.mediaControl
                if (player !== attached) {
                    attached?.removeListener(listener)
                    attached = player
                    player?.addListener(listener)
                    position.seeks++
                }
                position.report(player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
                position.advancing = player?.isPlaying == true
                // No player/binder reads from a draw pass; intermediate frames use LyricClock.
                delay(100L)
            }
        } finally { attached?.removeListener(listener) }
    }
    return head to (foreground && position.advancing)
}

// Ported list containment and fade from BitChord PlayerQueue.kt (GPL-3.0).
internal fun keepScrollInList(listState: LazyListState) = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPreFling(available: Velocity) =
        if (available.y > 0f && !listState.canScrollBackward) available else Velocity.Zero
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}
// Keep this local to the lyric viewport: fading the player would wash out the
// seek bar and translation button too. Cache the masks until its size changes.
internal val LYRICS_BOTTOM_REVEAL = 56.dp
internal fun Modifier.fadingEdges(): Modifier = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithCache {
        val topFade = 28.dp.toPx().coerceAtMost(size.height / 2f)
        val bottomFade = LYRICS_BOTTOM_REVEAL.toPx().coerceAtMost(size.height * .42f)
        val topMask = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, topFade)
        // Keep the upcoming words visible until the final part of the panel.
        // The long build-30 ramp dimmed already-muted words too early.
        val bottomMask = Brush.verticalGradient(
            0f to Color.Black, .25f to Color.Black.copy(alpha = .96f),
            .5f to Color.Black.copy(alpha = .75f), .75f to Color.Black.copy(alpha = .4f),
            1f to Color.Transparent,
            startY = size.height - bottomFade, endY = size.height,
        )
        onDrawWithContent {
            drawContent()
            drawRect(topMask, blendMode = BlendMode.DstIn)
            drawRect(bottomMask, blendMode = BlendMode.DstIn)
        }
    }

/** Reuse GPU blur effects; scrolling changes only a row's layer properties. */
@Composable
internal fun rememberLyricsEdgeBlurEffects(): List<RenderEffect?> {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(density) {
        List(25) { step ->
            if (step == 0 || !renderEffectBlurSupported) null
            else {
                // A faint feather at the edge, not a second strong blur on
                // top of the renderer's existing line treatment.
                val radius = with(density) { (step * .125f).dp.toPx() }
                android.graphics.RenderEffect.createBlurEffect(radius, radius,
                    android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect()
            }
        }
    }
}

// BitChord's Android implementation (PlayerPlatformUi.android.kt), GPL-3.0.
internal fun DrawScope.clipShiftedDown(left: Float, top: Float, right: Float, bottom: Float,
    dy: Float, block: DrawScope.() -> Unit) {
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        translate(top = dy) { block() }
    }
}
