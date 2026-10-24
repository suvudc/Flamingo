package yos.music.player.code

import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class SessionArtworkBitmapLoaderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val loader = SessionArtworkBitmapLoader(context)

    @After fun tearDown() = loader.close()

    @Test fun legacyAlbumUriUsesTypedThumbnailEndpointOnlyOnAndroid10AndLater() {
        val legacy = Uri.parse("content://media/external/audio/albumart/42")
        assertEquals(legacy, SessionArtworkBitmapLoader.thumbnailUri(legacy, 28))
        for (sdk in listOf(29, 34, 37)) {
            assertEquals(
                Uri.parse("content://media/external/audio/albums/42"),
                SessionArtworkBitmapLoader.thumbnailUri(legacy, sdk)
            )
        }
        val modern = Uri.parse("content://media/external_primary/audio/albums/42")
        assertEquals(modern, SessionArtworkBitmapLoader.thumbnailUri(modern, 37))
        for (value in listOf("file:///tmp/cover.jpg", "content://other/external/audio/albumart/42")) {
            val uri = Uri.parse(value)
            assertEquals(uri, SessionArtworkBitmapLoader.thumbnailUri(uri, 37))
        }
    }

    @Test fun embeddedArtworkIsBoundedAndUsesSoftwareBitmap() {
        val metadata = MediaMetadata.Builder()
            .setArtworkData(png(2048, android.graphics.Color.RED), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        val bitmap = loader.loadBitmapFromMetadata(metadata)!!.get(10, TimeUnit.SECONDS)
        assertTrue(bitmap.width <= 512 && bitmap.height <= 512)
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(0, 0))
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            assertNotEquals(Bitmap.Config.HARDWARE, bitmap.config)
        }
    }

    @Test fun localImageAndTrackChangesDoNotReusePreviousArtwork() {
        val file = File.createTempFile("session-artwork", ".png", context.cacheDir)
        try {
            file.writeBytes(png(64, android.graphics.Color.BLUE))
            val first = loader.loadBitmap(Uri.fromFile(file)).get(10, TimeUnit.SECONDS)
            val second = loader.decodeBitmap(png(64, android.graphics.Color.GREEN)).get(10, TimeUnit.SECONDS)
            assertEquals(android.graphics.Color.BLUE, first.getPixel(0, 0))
            assertEquals(android.graphics.Color.GREEN, second.getPixel(0, 0))
        } finally {
            file.delete()
        }
    }

    @Test fun absentOrInvalidArtworkDoesNotReturnStaleBitmap() {
        assertNull(loader.loadBitmapFromMetadata(MediaMetadata.EMPTY))
        val file = File.createTempFile("invalid-artwork", ".txt", context.cacheDir)
        try {
            file.writeText("not an image")
            try {
                loader.loadBitmap(Uri.fromFile(file)).get(10, TimeUnit.SECONDS)
                fail("Invalid artwork should complete with a failed future")
            } catch (_: ExecutionException) {
                // Media3 handles failed futures by omitting the picture.
            }
        } finally {
            file.delete()
        }
    }

    private fun png(size: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                it.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
