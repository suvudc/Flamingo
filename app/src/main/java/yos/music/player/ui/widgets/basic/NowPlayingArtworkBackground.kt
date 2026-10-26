package yos.music.player.ui.widgets.basic

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/** Full-width sleeve or canvas, dissolving into the existing album-coloured background. */
@Composable
fun NowPlayingArtworkBackground(artwork: Uri?, animation: AnimatedAlbumCoverState) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // The decoded video determines its own shape: square covers stay square, portrait
        // canvases extend down the screen. Never stretch a square into a phone-sized image.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .aspectRatio(animation.videoAspectRatio)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val fade = Brush.verticalGradient(
                        0f to Color.White,
                        0.60f to Color.White,
                        1f to Color.Transparent,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(fade, blendMode = BlendMode.DstIn)
                    }
                },
        ) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedAlbumCoverOverlay(animation)
        }
        // Keep the title, transport and status bar readable across bright and dark artwork.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.12f),
                    0.30f to Color.Transparent,
                    0.52f to Color.Black.copy(alpha = 0.24f),
                    1f to Color.Black.copy(alpha = 0.48f),
                )
            )
        )
    }
}
