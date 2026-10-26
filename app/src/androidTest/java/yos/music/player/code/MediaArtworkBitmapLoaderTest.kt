package yos.music.player.code

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MediaArtworkBitmapLoaderTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun embeddedArtworkIsBoundedAndCanBeDrawnOnSoftwareCanvas() {
        MediaArtworkBitmapLoader(context).use { loader ->
            val result = loader.decodeBitmap(png(2048, 1024)).get(10, TimeUnit.SECONDS)
            assertTrue(result.width <= 512)
            assertTrue(result.height <= 512)
            assertEquals(2f, result.width.toFloat() / result.height, 0.02f)
            val canvasBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(canvasBitmap).drawBitmap(result, 0f, 0f, null)
            assertEquals(Color.RED, canvasBitmap.getPixel(0, 0))
        }
    }

    @Test
    fun uriArtworkLoadsWithoutChangingTheOriginalFile() {
        val file = File.createTempFile("session-cover", ".png", context.cacheDir)
        try {
            val data = png(64, 32)
            file.writeBytes(data)
            MediaArtworkBitmapLoader(context).use { loader ->
                val metadata = MediaMetadata.Builder().setArtworkUri(Uri.fromFile(file)).build()
                val result = loader.loadBitmapFromMetadata(metadata)!!.get(10, TimeUnit.SECONDS)
                assertEquals(Color.RED, result.getPixel(0, 0))
                assertArrayEquals(data, file.readBytes())
                assertNull(loader.loadBitmapFromMetadata(MediaMetadata.EMPTY))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun corruptArtworkFailsTheFutureInsteadOfHanging() {
        MediaArtworkBitmapLoader(context).use { loader ->
            try {
                loader.decodeBitmap(byteArrayOf(1, 2, 3)).get(10, TimeUnit.SECONDS)
                fail("Invalid image data must fail")
            } catch (expected: ExecutionException) {
                assertNotNull(expected.cause)
            }
        }
    }

    @Test
    fun requestsAfterCloseAreCancelled() {
        val loader = MediaArtworkBitmapLoader(context)
        loader.close()
        assertTrue(loader.decodeBitmap(byteArrayOf(1)).isCancelled)
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
