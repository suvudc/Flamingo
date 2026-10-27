package yos.music.player.code.lyrics

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import yos.music.player.code.utils.lrc.YosParsedLyricData
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsName
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Uses the public lrc.red, BiniLyrics, BetterLyrics and LRCLIB routes used by BitChord.
 * Endpoint/query compatibility checked against suvudc/BitChord (GPL-3.0).
 * Existing local TTML/LRC takes precedence. Network results retain their genuine timings.
 */
object LyricsDownloads {
    data class Download(val parsed: YosParsedLyricData, val source: String, val saved: Boolean)
    private val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS).build()
    private fun store(context: Context) = OfflineLyricsStore(File(context.filesDir, "downloaded-lyrics"))
    private fun key(store: OfflineLyricsStore, song: YosMediaItem, provider: LyricsProvider) =
        store.key(song.title.orEmpty(), song.artistsName.orEmpty(), song.album.orEmpty(), song.duration, provider.id)

    suspend fun load(context: Context, song: YosMediaItem, refresh: Boolean = false, provider: LyricsProvider = LyricsProvider.Auto): Download? = withContext(Dispatchers.IO) {
        if (provider == LyricsProvider.Local) return@withContext null
        val disk = store(context)
        val key = key(disk, song, provider)
        val cached = disk.read(key)?.let { raw ->
            runCatching {
                val obj = JsonParser.parseString(raw).asJsonObject
                OnlineLyrics.parse(obj["lyrics"].asString)?.let { Download(it, obj["source"].asString, true) }
            }.getOrNull()
        }
        if (!refresh && cached != null) return@withContext cached
        val title = song.title?.takeIf { it.isNotBlank() } ?: return@withContext cached
        val artist = song.artistsName?.takeIf { it.isNotBlank() } ?: return@withContext cached
        val album = song.album.orEmpty()
        val candidates = supervisorScope {
            val sources: List<suspend () -> Pair<String, String>?> = when (provider) {
                LyricsProvider.Auto -> listOf({ lrcRed(title, artist, song.duration) },
                    { bini(title, artist, album, song.duration) },
                    { better(title, artist, album, song.duration) },
                    { lrcLib(title, artist, album, song.duration) })
                LyricsProvider.LrcRed -> listOf({ lrcRed(title, artist, song.duration) })
                LyricsProvider.Bini -> listOf({ bini(title, artist, album, song.duration) })
                LyricsProvider.Better -> listOf({ better(title, artist, album, song.duration) })
                LyricsProvider.LrcLib -> listOf({ lrcLib(title, artist, album, song.duration) })
                LyricsProvider.Local -> emptyList()
            }
            sources.map { source -> async {
                runCatching { source() }.getOrNull()?.let { (name, raw) ->
                    OnlineLyrics.parse(raw)?.let { Triple(name, raw, it) }
                }
            } }.awaitAll().filterNotNull()
        }
        coroutineContext.ensureActive()
        val best = candidates.firstOrNull { OnlineLyrics.wordTimed(it.third) } ?: candidates.firstOrNull()
            ?: return@withContext cached
        val saved = runCatching {
            val document = JsonObject().apply { addProperty("source", best.first); addProperty("lyrics", best.second) }
            disk.write(key, document.toString())
        }.isSuccess
        Download(best.third, best.first, saved)
    }

    private fun url(base: String, vararg parameters: Pair<String, String>) = base.toHttpUrl().newBuilder()
        .apply { parameters.forEach { (k, v) -> if (v.isNotBlank()) addQueryParameter(k, v) } }.build().toString()
    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "Music-by-Suvu/2.1 (lyrics)").build())
            .execute().use { r ->
                if (!r.isSuccessful || (r.body?.contentLength() ?: 0) > OfflineLyricsStore.MAX_BYTES) return null
                r.body?.byteStream()?.use { stream ->
                    val data = stream.readBytesBounded(OfflineLyricsStore.MAX_BYTES.toInt()) ?: return null
                    data.toString(Charsets.UTF_8)
                }
            }
    }.getOrNull()
    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = read(buffer); if (n < 0) break
            if (output.size() + n > limit) return null
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }
    private fun JsonObject.text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.seconds(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0
    private fun lrcRed(title: String, artist: String, duration: Long): Pair<String, String>? {
        val raw = get(url("https://lrc.red/search.json", "q" to "$title $artist")) ?: return null
        val hits = JsonParser.parseString(raw).asJsonObject.getAsJsonArray("hits") ?: return null
        for (element in hits) {
            val hit = element.asJsonObject
            if (!OnlineLyrics.matches(title, artist, duration, hit.text("title"), hit.text("artist"), hit.seconds("duration"))) continue
            val isrc = hit.text("isrc").uppercase()
            if (!isrc.matches(Regex("[A-Z]{2}[A-Z0-9]{3}\\d{7}"))) continue
            get("https://lrc.red/s/$isrc.ttml")?.let { return "lrc.red" to it }
        }
        return null
    }
    private fun bini(title: String, artist: String, album: String, duration: Long): Pair<String, String>? {
        val raw = get(url("https://lyrics-api.binimum.org/", "track" to title, "artist" to artist,
            "album" to album, "duration" to (duration / 1000).toString())) ?: return null
        val hits = JsonParser.parseString(raw).asJsonObject.getAsJsonArray("results") ?: return null
        for (element in hits) {
            val hit = element.asJsonObject
            if (!OnlineLyrics.matches(title, artist, duration, hit.text("track_name"), hit.text("artist_name"), hit.seconds("duration"))) continue
            val uri = runCatching { hit.text("lyricsUrl").toHttpUrl() }.getOrNull() ?: continue
            if (uri.scheme != "https" || uri.host != "lrc.red") continue
            get(uri.toString())?.let { return "BiniLyrics" to it }
        }
        return null
    }
    private fun better(title: String, artist: String, album: String, duration: Long): Pair<String, String>? =
        get(url("https://lyrics-api.boidu.dev/getLyrics", "s" to title, "a" to artist,
            "d" to (duration / 1000).toString(), "al" to album))?.let { "BetterLyrics" to it }
    private fun lrcLib(title: String, artist: String, album: String, duration: Long): Pair<String, String>? =
        get(url("https://lrclib.net/api/get", "track_name" to title, "artist_name" to artist,
            "album_name" to album, "duration" to (duration / 1000).toString()))?.let { "LRCLIB" to it }
}
