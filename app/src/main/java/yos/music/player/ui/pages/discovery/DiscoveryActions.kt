package yos.music.player.ui.pages.discovery

import android.content.Context
import android.net.Uri
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yos.music.player.code.AudioMetadataUtils
import yos.music.player.code.ListenStatsManager
import yos.music.player.data.discovery.*
import yos.music.player.data.libraries.*
import yos.music.player.data.libraries.PlayListLibrary.applyEdits
import yos.music.player.data.libraries.PlayListLibrary.pin
import java.io.InputStream

object DiscoveryActions {
    fun savePlaylist(requestedName: String, songs: List<YosMediaItem>, description: String? = null): String {
        val base = requestedName.trim().ifBlank { "New Playlist" }.take(150)
        var name = base; var suffix = 2
        while (PlayListLibrary.playList.any { it.name == name }) name = "$base (${suffix++})"
        PlayListLibrary.create(name)
        PlayListLibrary.playList.first { it.name == name }.applyEdits(name, description, null, songs.mapNotNull { it.uri })
        return name
    }

    fun readBounded(input: InputStream, maximum: Int = 10 * 1024 * 1024): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            require(output.size() + count <= maximum) { "The selected file is too large." }
            output.write(buffer, 0, count)
        }
        return output.toString("UTF-8")
    }

    suspend fun importPlaylist(context: Context, uri: Uri, name: String): String {
        val songs = MusicLibrary.songs
        val result = withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.use { readBounded(it, 2 * 1024 * 1024) } ?: error("Cannot read playlist")
            val paths = songs.mapNotNull { song -> song.uri?.toString()?.let { id -> id to (AudioMetadataUtils.resolveLocalAudioFilePath(context, song.uri) ?: id) } }.toMap()
            DiscoveryEngine.importM3u(text, paths)
        }
        if (result.first.isEmpty()) return "No matching local songs. ${result.second} unavailable entries were skipped."
        val saved = savePlaylist(name, DiscoveryLibrary.resolve(result.first, songs))
        return "Imported $saved · ${result.first.size} songs · ${result.second} unavailable entries"
    }

    suspend fun exportPlaylist(context: Context, uri: Uri, songs: List<YosMediaItem>) = withContext(Dispatchers.IO) {
        val text = buildString {
            appendLine("#EXTM3U")
            songs.forEach { song ->
                val path = AudioMetadataUtils.resolveLocalAudioFilePath(context, song.uri) ?: song.uri?.toString() ?: return@forEach
                appendLine("#EXTINF:${song.duration / 1000},${song.artistsName.orEmpty().replace('\n', ' ')} - ${song.title.orEmpty().replace('\n', ' ')}")
                appendLine(path.replace("\n", ""))
            }
        }
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } ?: error("Cannot write playlist")
    }

    fun backup(): String = JsonObject().apply {
        addProperty("format", "Music local library backup"); addProperty("version", 1); addProperty("mixSize", DiscoveryPreferences.mixSize)
        add("playlists", JsonArray().apply { PlayListLibrary.playList.forEach { playlist -> add(JsonObject().apply {
            addProperty("name", playlist.name); addProperty("description", playlist.description); addProperty("pinned", playlist.isPinned)
            add("songs", JsonArray().apply { playlist.songDataList.forEach { add(it.toString()) } })
        }) } })
        add("favorites", JsonArray().apply { FavPlayListLibrary.favPlayList.forEach { add(it.uri.toString()) } })
        add("stats", com.google.gson.Gson().toJsonTree((ListenStatsManager.statsEvents.value + ListenStatsManager.liveSessionEvents.value).distinctBy { "${it.uri}|${it.timestampMs}|${it.dayStartMs}" }))
        addProperty("smartRules", DiscoveryPreferences.rulesJson); addProperty("homeOrder", DiscoveryPreferences.sectionOrder)
        addProperty("hiddenSections", DiscoveryPreferences.hiddenSections); addProperty("excludedMixSongs", DiscoveryPreferences.excludedJson)
    }.toString()

    fun restore(text: String): String {
        val root = JsonParser.parseString(text).asJsonObject
        require(root.get("format")?.asString == "Music local library backup" && root.get("version")?.asInt == 1) { "This is not a supported Music backup." }
        val index = MusicLibrary.songs.associateBy { it.uri.toString() }
        // Validate the whole document before changing any persistent collection.
        val playlists = root.getAsJsonArray("playlists")?.map { element ->
            val item = element.asJsonObject
            Triple(item.get("name").asString.take(150), item.getAsJsonArray("songs").map { it.asString }, item)
        }.orEmpty()
        require(playlists.size <= 1000 && playlists.all { it.second.size <= 100_000 }) { "Too many playlist entries." }
        val favorites = root.getAsJsonArray("favorites")?.map { it.asString }.orEmpty()
        val rules = root.get("smartRules")?.asString?.let { com.google.gson.Gson().fromJson(it, Array<SmartMixRule>::class.java).toList() }.orEmpty()
        require(rules.size <= 1000 && rules.all { rule -> listOf<String?>(rule.id, rule.name, rule.artist, rule.genre, rule.order).all { it != null } && rule.id.isNotBlank() && rule.name.isNotBlank() && rule.limit in 1..500 && rule.fromYear in 0..9999 && rule.toYear in 0..9999 && (rule.fromYear == 0 || rule.toYear == 0 || rule.fromYear <= rule.toYear) }) { "Invalid smart playlist rules." }
        val stats = root.getAsJsonArray("stats")?.also { array -> array.forEach { e ->
            val event = e.asJsonObject
            require((event.get("listenedMs")?.asLong ?: 0L) >= 0 && (event.get("timestampMs")?.asLong ?: 0L) >= 0) { "Invalid listening event." }
        } }
        val homeOrder = root.get("homeOrder")?.asString?.split(',')?.filter { it in DiscoveryPreferences.sectionTitles }?.joinToString(",")
        val hiddenSections = root.get("hiddenSections")?.asString?.split(',')?.filter { it in DiscoveryPreferences.sectionTitles }?.joinToString(",")
        val excluded = root.get("excludedMixSongs")?.asString?.let { com.google.gson.Gson().fromJson(it, Array<String>::class.java).toList() }.orEmpty()
        val mixSize = root.get("mixSize")?.asInt ?: 50
        require(mixSize in 10..100) { "Invalid mix size." }
        var created = 0; var missing = 0
        playlists.forEach { (name, ids, data) ->
            val songs = ids.mapNotNull { id -> index[id].also { if (it == null) missing++ } }
            val alreadyExists = PlayListLibrary.playList.any { it.name == name && it.songDataList.map { it.toString() } == songs.map { it.uri.toString() } }
            if (!alreadyExists) {
                val description = data.get("description")?.takeUnless { it.isJsonNull }?.asString
                val newName = savePlaylist(name, songs, description)
                if (data.get("pinned")?.asBoolean == true) PlayListLibrary.playList.first { it.name == newName }.pin()
                created++
            }
        }
        favorites.mapNotNull { index[it] }.forEach { FavPlayListLibrary.addMusic(it) }
        rules.forEach { DiscoveryPreferences.saveRule(it) }
        homeOrder?.let { DiscoveryPreferences.sectionOrder = it }
        hiddenSections?.let { DiscoveryPreferences.hiddenSections = it }
        DiscoveryPreferences.excludedJson = com.google.gson.Gson().toJson(excluded.filter { it in index })
        DiscoveryPreferences.mixSize = mixSize
        DiscoveryPreferences.mixRevision++
        if (stats != null) ListenStatsManager.importEventsFromJson(stats.toString(), false)
        return "Restored $created playlists, favorites and Home preferences. $missing unavailable song entries were skipped."
    }
}
