package yos.music.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.*
import yos.music.player.MainActivity
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.artistsName

/** Optional home-screen controls; does not replace or modify Music's player/session. */
class MusicWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { WidgetUpdates.start(context); WidgetUpdates.render(context) }
    override fun onEnabled(context: Context) { WidgetUpdates.start(context) }
    override fun onDisabled(context: Context) { WidgetUpdates.stop() }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                withTimeout(8500) {
                    if (!MusicLibrary.hasAudioPermission(context)) {
                        android.widget.Toast.makeText(context, "Open Music and allow access to your audio files.", android.widget.Toast.LENGTH_LONG).show()
                        return@withTimeout
                    }
                    MediaController.ensureInitialized(context.applicationContext)
                    repeat(25) { if (MediaController.mediaControl?.currentMediaItem == null) delay(100) }
                    MediaController.mediaControl?.let { player ->
                        when (intent.getStringExtra("command")) {
                            "previous" -> player.seekToPreviousMediaItem()
                            "next" -> player.seekToNextMediaItem()
                            else -> if (player.isPlaying) player.pause() else if (player.currentMediaItem != null) player.play()
                                else MusicLibrary.songs.firstOrNull()?.let { MediaController.prepare(it, MusicLibrary.songs) }
                        }
                    }
                    WidgetUpdates.start(context)
                    WidgetUpdates.render(context)
                }
            } catch (_: Exception) { WidgetUpdates.render(context) }
            finally { pending.finish() }
        }
    }
    companion object { const val ACTION = "yos.music.player.widget.CONTROL" }
}

object WidgetUpdates {
    private var watcher: Job? = null
    private var previousKey: String? = null
    private var artworkKey: String? = null
    private var artwork: Bitmap? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private fun ids(context: Context) = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MusicWidget::class.java))
    fun start(context: Context) {
        val app = context.applicationContext
        if (watcher?.isActive == true || ids(app).isEmpty()) return
        watcher = scope.launch {
            while (isActive && ids(app).isNotEmpty()) {
                val song = MediaController.musicPlaying.value
                val playing = MediaController.mediaControl?.isPlaying == true
                val key = "${song?.uri}|${song?.title}|$playing"
                if (key != previousKey) {
                    previousKey = key
                    val uri = song?.thumb?.toString()
                    if (uri != artworkKey) {
                        artworkKey = uri
                        artwork = withContext(Dispatchers.IO) {
                            runCatching { app.imageLoader.execute(ImageRequest.Builder(app).data(song?.thumb).size(128).allowHardware(false).build()).drawable?.toBitmap(128, 128) }.getOrNull()
                        }
                    }
                    render(app)
                }
                delay(1800)
            }
        }
    }
    fun stop() { watcher?.cancel(); watcher = null; previousKey = null; artwork = null; artworkKey = null }
    fun render(context: Context) {
        val app = context.applicationContext
        val ids = ids(app); if (ids.isEmpty()) return
        val song = MediaController.musicPlaying.value
        val preferences = app.getSharedPreferences("music_widget", Context.MODE_PRIVATE)
        if (song != null) preferences.edit().putString("title", song.title).putString("artist", song.artistsName).apply()
        val views = RemoteViews(app.packageName, R.layout.music_widget)
        views.setTextViewText(R.id.widget_title, song?.title ?: preferences.getString("title", "Music"))
        views.setTextViewText(R.id.widget_artist, song?.artistsName ?: preferences.getString("artist", "Your local music"))
        val playing = MediaController.mediaControl?.isPlaying == true
        views.setTextViewText(R.id.widget_play, if (playing) "Ⅱ" else "▶")
        views.setContentDescription(R.id.widget_play, if (playing) "Pause" else "Play")
        artwork?.let { views.setImageViewBitmap(R.id.widget_art, it) }
        val open = PendingIntent.getActivity(app, 90, Intent(app, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_info, open)
        listOf(R.id.widget_previous to "previous", R.id.widget_play to "play", R.id.widget_next to "next").forEachIndexed { index, (view, command) ->
            val intent = Intent(app, MusicWidget::class.java).setAction(MusicWidget.ACTION).putExtra("command", command)
            views.setOnClickPendingIntent(view, PendingIntent.getBroadcast(app, 100 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        AppWidgetManager.getInstance(app).updateAppWidget(ids, views)
    }
}
