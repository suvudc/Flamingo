package yos.music.player.code

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.Closeable
import kotlin.math.roundToInt

/** Loads session artwork with the same URI support as the in-app cover images. */
@OptIn(UnstableApi::class)
class MediaArtworkBitmapLoader(context: Context) : BitmapLoader, Closeable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = load(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = load(uri)

    private fun load(data: Any): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch {
            try {
                val request = ImageRequest.Builder(appContext)
                    .data(data)
                    // SystemUI needs a transferable software bitmap, not the GPU-backed
                    // images used by the app UI. Bound its size to limit IPC/memory cost.
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .size(MAX_ARTWORK_SIZE)
                    .scale(Scale.FIT)
                    .build()
                when (val result = appContext.imageLoader.execute(request)) {
                    is SuccessResult -> {
                        val drawable = result.drawable
                        val width = drawable.intrinsicWidth.coerceAtLeast(1)
                        val height = drawable.intrinsicHeight.coerceAtLeast(1)
                        val scale = minOf(1f, MAX_ARTWORK_SIZE.toFloat() / maxOf(width, height))
                        val bitmap = drawable.toBitmap(
                            (width * scale).roundToInt().coerceAtLeast(1),
                            (height * scale).roundToInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        future.set(bitmap)
                    }
                    is ErrorResult -> throw result.throwable
                }
            } catch (cancelled: CancellationException) {
                future.cancel(false)
                throw cancelled
            } catch (error: Exception) {
                Log.w("FlamingoArtwork", "Unable to load media-session artwork", error)
                future.setException(error)
            }
        }
        // Also complete requests submitted after the service has been destroyed.
        job.invokeOnCompletion { cause ->
            if (cause != null) future.cancel(false)
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return future
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val MAX_ARTWORK_SIZE = 512
    }
}
