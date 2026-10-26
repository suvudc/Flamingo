package yos.music.player

import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import yos.music.player.code.ArtworkDiagnostics
import yos.music.player.code.MediaController

/** Reads our own session only. No notification-listener permission or external logs needed. */
@OptIn(UnstableApi::class)
class ArtworkDiagnosticsActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var report: TextView
    private lateinit var preview: ImageView
    private val refresh = object : Runnable {
        override fun run() {
            showReport()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(android.graphics.Color.rgb(24, 24, 24))
        }
        report = TextView(this).apply {
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setTextIsSelectable(true)
        }
        preview = ImageView(this).apply { adjustViewBounds = true }
        content.addView(report)
        content.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (240 * resources.displayMetrics.density).toInt()
        ))
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun showReport() {
        var platformArtwork: Bitmap? = null
        report.text = buildString {
            appendLine("ARTWORK REPORT — fix build 3 / Media3 1.8.0")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            val session = MediaController.mediaSession
            if (session == null) {
                appendLine("No playback session. Go back and play a song first.")
            } else {
                val metadata = session.player.mediaMetadata
                appendLine("Track: ${metadata.title}")
                appendLine("Playing: ${session.player.isPlaying}")
                appendLine("Image URI: ${metadata.artworkUri?.scheme ?: "MISSING"}")
                appendLine("Embedded image: ${metadata.artworkData?.size ?: 0} bytes")
                appendLine()
                appendLine("Android platform metadata:")
                runCatching {
                    val platform = android.media.session.MediaController(this@ArtworkDiagnosticsActivity, session.platformToken)
                    val platformMetadata = platform.metadata
                    appendLine("Session package: ${platform.packageName}")
                    for ((name, key) in listOf(
                        "ART" to MediaMetadata.METADATA_KEY_ART,
                        "ALBUM_ART" to MediaMetadata.METADATA_KEY_ALBUM_ART,
                        "DISPLAY_ICON" to MediaMetadata.METADATA_KEY_DISPLAY_ICON
                    )) {
                        val bitmap = platformMetadata?.getBitmap(key)
                        appendLine("$name: ${bitmap?.let { "${it.width}x${it.height} ${it.config}" } ?: "MISSING"}")
                        if (platformArtwork == null) platformArtwork = bitmap
                    }
                    appendLine("Description image: ${platformMetadata?.description?.iconBitmap?.let { "${it.width}x${it.height}" } ?: "MISSING"}")
                }.onFailure { appendLine("Read failed: ${it.javaClass.simpleName}") }
            }
            appendLine()
            appendLine("Recent cover-loading events:")
            appendLine(ArtworkDiagnostics.snapshot())
            appendLine()
            appendLine("Screenshot this report after the Quick Settings cover fails.")
        }
        preview.setImageBitmap(platformArtwork)
    }
}
