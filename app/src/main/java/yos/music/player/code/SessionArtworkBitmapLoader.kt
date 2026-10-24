package yos.music.player.code

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Loads session artwork in the app process, including MediaStore's typed album thumbnails. */
@UnstableApi
internal class SessionArtworkBitmapLoader(context: Context) : BitmapLoader {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean =
        Util.isBitmapFactorySupportedMimeType(mimeType)

    // ExoPlayer can supply embedded pictures as artworkData instead of an artworkUri.
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = load(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        load(thumbnailUri(uri, Build.VERSION.SDK_INT))

    private fun load(data: Any): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch {
            try {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(data)
                        .size(512)
                        // Hardware bitmaps cannot safely be sent to legacy/system media clients.
                        .allowHardware(false)
                        .build()
                )
                val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                    ?: throw IOException("Unable to load session artwork")
                future.set(bitmap)
            } catch (cancelled: CancellationException) {
                future.cancel(false)
                throw cancelled
            } catch (exception: Exception) {
                // A failed future lets Media3 omit the picture; never reuse another track's art.
                future.setException(exception)
            }
        }
        job.invokeOnCompletion { if (job.isCancelled) future.cancel(false) }
        future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return future
    }

    fun close() {
        scope.cancel()
    }

    internal companion object {
        fun thumbnailUri(uri: Uri, sdk: Int): Uri {
            val segments = uri.pathSegments
            // Coil uses openTypedAssetFile("image/*") for /audio/albums/<id> on API 29+.
            // A raw DataSource read of an album/albumart URI need not return image bytes.
            return if (sdk >= 29 && uri.scheme == "content" && uri.authority == "media" &&
                segments.size == 4 && segments[1] == "audio" && segments[2] == "albumart"
            ) {
                uri.buildUpon().path("/${segments[0]}/audio/albums/${segments[3]}").build()
            } else {
                uri
            }
        }
    }
}
