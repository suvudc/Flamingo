package yos.music.player.ui.widgets.basic

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.objects.MediaViewModelObject

@Stable
class PlayerArtworkMotion {
    var closing by mutableStateOf(false)
        private set
    var playerOpen = false
    private var source: Rect? = null
    private var thumbnail: Rect? = null
    private var thumbnailCorner = 10f
    internal var start: ArtworkFlightRect? = null
    internal var end: ArtworkFlightRect? = null
    var startProgress = 1f
        private set
    internal var endCorner = 10f
    fun captureSource(coordinates: LayoutCoordinates) {
        if (!closing) source = Rect(coordinates.positionInRoot(), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
    }
    fun captureThumbnail(coordinates: LayoutCoordinates, cornerDp: Float) {
        if (!playerOpen) {
            thumbnail = Rect(coordinates.positionInRoot(), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            thumbnailCorner = cornerDp
        }
    }
    fun begin(progress: Float) {
        val a = source; val b = thumbnail
        if (a == null || b == null || a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0 || progress <= .01f) return
        start = ArtworkFlightRect(a.left, a.top, a.width, a.height)
        end = ArtworkFlightRect(b.left, b.top, b.width, b.height)
        endCorner = thumbnailCorner
        startProgress = progress
        closing = true
    }
    fun reset() { closing = false; source = null; start = null; end = null }
}

val LocalPlayerArtworkMotion = staticCompositionLocalOf<PlayerArtworkMotion?> { null }

@Composable
fun PlayerArtworkFlight(motion: PlayerArtworkMotion, progress: () -> Float) {
    if (!motion.closing) return
    val source = motion.start ?: return
    val target = motion.end ?: return
    val density = LocalDensity.current
    val context = LocalContext.current
    val uri = MediaController.musicPlaying.value?.thumb ?: MediaViewModelObject.bitmap.value
    val request = remember(context, uri) {
        ImageRequest.Builder(context).data(uri).memoryCacheKey(uri.toString()).size(512).crossfade(false)
            .error(R.drawable.placeholder_music_default_artwork).fallback(R.drawable.placeholder_music_default_artwork).build()
    }
    Box(Modifier.fillMaxSize().zIndex(2f)) {
        AsyncImage(request, null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(with(density) { source.width.toDp() }, with(density) { source.height.toDp() })
                .graphicsLayer {
                    val raw = (progress() / motion.startProgress).coerceIn(0f, 1f)
                    val p = FastOutSlowInEasing.transform(raw)
                    val frame = artworkFlightFrame(source, target, p)
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = frame.left
                    translationY = frame.top
                    scaleX = frame.width / source.width
                    scaleY = frame.height / source.height
                    clip = true
                    val radius = motion.endCorner + (8f - motion.endCorner) * p
                    shape = RoundedCornerShape((radius / scaleX.coerceAtLeast(.01f)).dp)
                })
    }
}
