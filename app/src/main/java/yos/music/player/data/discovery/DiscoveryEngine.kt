package yos.music.player.data.discovery

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Independent local recommendation/search engine; no remote service or Pixel Player code. */
data class DiscoveryTrack(
    val id: String, val title: String, val artist: String, val album: String,
    val genre: String = "", val year: Int = 0, val addedAt: Long = 0, val duration: Long = 0,
)
data class DiscoverySearchEntry(val id: String, val title: String, val artist: String, val all: String)
data class TrackEngagement(val plays: Int = 0, val listenedMs: Long = 0, val lastPlayed: Long = 0)
data class DiscoveryMix(val id: String, val title: String, val subtitle: String, val trackIds: List<String>)
data class SmartMixRule(
    val id: String = "", val name: String = "", val artist: String = "", val genre: String = "",
    val fromYear: Int = 0, val toYear: Int = 0, val favoritesOnly: Boolean = false,
    val unplayedOnly: Boolean = false, val order: String = "mix", val limit: Int = 50,
)

object DiscoveryEngine {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ").trim()

    fun generate(
        input: List<DiscoveryTrack>, stats: Map<String, TrackEngagement>, favorites: Set<String>,
        seed: Long, excluded: Set<String> = emptySet(), maximumTracks: Int = 50,
    ): List<DiscoveryMix> {
        val songs = input.distinctBy { it.id }.filter { it.id.isNotBlank() && it.id !in excluded }.sortedBy { it.id }
        if (songs.isEmpty()) return emptyList()
        val limit = maximumTracks.coerceIn(10, 100)
        val artistAffinity = songs.groupBy { normalize(it.artist) }.mapValues { (_, items) ->
            items.sumOf { sqrt((stats[it.id]?.plays ?: 0).toDouble()) + if (it.id in favorites) 2.0 else 0.0 }
        }
        fun select(list: List<DiscoveryTrack>, salt: Long, discovery: Boolean = false): List<String> {
            val random = Random(seed xor salt)
            val ranked = list.map { track ->
                val engagement = stats[track.id]
                val affinity = artistAffinity[normalize(track.artist)] ?: 0.0
                val familiarity = sqrt((engagement?.plays ?: 0).toDouble())
                val weight = if (discovery) 1.0 + affinity / (1.0 + familiarity)
                    else 1.0 + (if (track.id in favorites) 3.0 else 0.0) + sqrt(affinity) + familiarity
                track to (-ln(random.nextDouble().coerceAtLeast(0.000001)) / weight)
            }.sortedBy { it.second }.map { it.first }
            // Round-robin the ranking by artist, so a large discography cannot fill the mix.
            val buckets = ranked.groupBy { normalize(it.artist) }.values.map { it.toMutableList() }
            return buildList {
                while (size < minOf(limit, ranked.size)) {
                    var added = false
                    for (bucket in buckets) if (bucket.isNotEmpty() && size < limit) {
                        add(bucket.removeAt(0).id); added = true
                    }
                    if (!added) break
                }
            }
        }
        return buildList {
            add(DiscoveryMix("your-mix", "Your Mix", "Favorites, familiar artists and fresh finds", select(songs, 11)))
            add(DiscoveryMix("daily-mix", "Daily Mix", "A new local selection every day", select(songs, 29)))
            val replay = songs.filter { it.id in favorites || (stats[it.id]?.plays ?: 0) > 0 }
            if (replay.isNotEmpty()) add(DiscoveryMix("replay", "On Repeat", "The music you keep coming back to", select(replay, 41)))
            val lowPlays = songs.filter { (stats[it.id]?.plays ?: 0) <= 1 && it.id !in favorites }
            if (lowPlays.isNotEmpty()) add(DiscoveryMix("discovery", "Rediscover", "Give overlooked songs a turn", select(lowPlays, 67, true)))
            val fresh = songs.sortedByDescending { it.addedAt }.take(100)
            add(DiscoveryMix("fresh", "Fresh Rotation", "Recently added to your library", select(fresh, 83)))
            songs.groupBy { it.artist }.filter { it.key.isNotBlank() && it.value.size >= 3 }
                .entries.sortedByDescending { artistAffinity[normalize(it.key)] ?: 0.0 }.take(3).forEach { (artist, items) ->
                    add(DiscoveryMix("artist:$artist", artist, "Artist mix · ${items.size} songs", select(items, artist.hashCode().toLong())))
                }
            songs.filter { it.genre.isNotBlank() }.groupBy { it.genre }.entries.sortedByDescending { it.value.size }.take(3)
                .forEach { (genre, items) -> add(DiscoveryMix("genre:$genre", "$genre Mix", "From your local collection", select(items, genre.hashCode().toLong()))) }
        }
    }

    fun smart(rule: SmartMixRule, songs: List<DiscoveryTrack>, stats: Map<String, TrackEngagement>, favorites: Set<String>, seed: Long): List<String> {
        val filtered = songs.distinctBy { it.id }.filter { song ->
            (rule.artist.isBlank() || normalize(song.artist).contains(normalize(rule.artist))) &&
            (rule.genre.isBlank() || normalize(song.genre).contains(normalize(rule.genre))) &&
            (rule.fromYear <= 0 || song.year >= rule.fromYear) && (rule.toYear <= 0 || song.year in 1..rule.toYear) &&
            (!rule.favoritesOnly || song.id in favorites) && (!rule.unplayedOnly || (stats[song.id]?.plays ?: 0) == 0)
        }
        val ordered = when (rule.order) {
            "recent" -> filtered.sortedByDescending { it.addedAt }
            "plays" -> filtered.sortedByDescending { stats[it.id]?.plays ?: 0 }
            "title" -> filtered.sortedBy { normalize(it.title) }
            else -> filtered.sortedBy { it.id }.shuffled(Random(seed))
        }
        return ordered.take(rule.limit.coerceIn(1, 500)).map { it.id }
    }

    fun searchIndex(songs: List<DiscoveryTrack>): List<DiscoverySearchEntry> = songs.map { song ->
        val title = normalize(song.title); val artist = normalize(song.artist)
        DiscoverySearchEntry(song.id, title, artist, "$title $artist ${normalize(song.album)} ${normalize(song.genre)} ${song.year}")
    }

    fun search(query: String, songs: List<DiscoveryTrack>): List<String> = searchIndexed(query, searchIndex(songs))

    fun searchIndexed(query: String, index: List<DiscoverySearchEntry>): List<String> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }.take(12)
        if (tokens.isEmpty()) return emptyList()
        return index.mapNotNull { song ->
            if (!tokens.all { it in song.all }) return@mapNotNull null
            val score: Int = tokens.fold(0) { total, token -> total + if (token == song.title) 10 else if (song.title.startsWith(token)) 7 else if (token in song.title) 5 else if (token in song.artist) 3 else 1 }
            Triple(song.id, score, song.title)
        }.sortedWith(compareByDescending<Triple<String, Int, String>> { it.second }.thenBy { it.third }).map { it.first }.distinct()
    }

    fun embeddedLyrics(properties: Map<String, Array<String>>): String {
        val keys = listOf("LYRICS", "LYRICSENG", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "USLT", "SYLT")
        return keys.firstNotNullOfOrNull { key -> properties[key]?.firstOrNull { it.isNotBlank() } }
            ?: properties.entries.firstOrNull { it.key.contains("LYRIC", true) }?.value?.firstOrNull().orEmpty()
    }

    fun mergeTags(original: Map<String, Array<String>>, changes: Map<String, String>): HashMap<String, Array<String>> {
        val result = HashMap(original)
        changes.forEach { (key, value) ->
            if (key == "LYRICS" && value.isBlank()) result.keys.filter { it.contains("LYRIC", true) || it in listOf("USLT", "SYLT") }.forEach { result.remove(it) }
            if (value.isBlank()) result.remove(key) else result[key] = arrayOf(value)
        }
        return result
    }

    fun commonFolderRoot(paths: List<String>): String {
        if (paths.isEmpty()) return ""
        val pieces = paths.map { it.trim('/').split('/').filter { part -> part.isNotEmpty() } }
        val common = pieces.first().takeWhileIndexed { index, part -> pieces.all { it.getOrNull(index) == part } }
        return if (paths.first().startsWith('/')) "/" + common.joinToString("/") else common.joinToString("/")
    }

    private fun <T> List<T>.takeWhileIndexed(test: (Int, T) -> Boolean): List<T> {
        val result = mutableListOf<T>(); forEachIndexed { index, value -> if (!test(index, value)) return result; result.add(value) }; return result
    }

    fun folderChildren(paths: List<String>, parent: String): List<String> {
        val prefix = parent.trimEnd('/') + "/"
        return paths.filter { it.startsWith(prefix) }.mapNotNull { path ->
            path.removePrefix(prefix).substringBefore('/').takeIf { it.isNotBlank() }?.let { prefix + it }
        }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /** Match a playlist path only when it identifies one library file. Never guess between duplicates. */
    fun importM3u(text: String, paths: Map<String, String>): Pair<List<String>, Int> {
        val ids = mutableListOf<String>(); var missing = 0
        val normalized = paths.mapValues { it.value.replace('\\', '/') }
        val fullPaths = normalized.entries.groupBy({ it.value }, { it.key })
        val basenames = normalized.entries.groupBy { it.value.substringAfterLast('/') }
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim(); if (line.isBlank() || line.startsWith('#')) continue
            if (Regex("https?://", RegexOption.IGNORE_CASE).containsMatchIn(line)) { missing++; continue }
            if (line in paths) { ids.add(line); continue }
            val path = runCatching { if (line.startsWith("file:", true)) java.net.URI(line).path else line }.getOrDefault(line).replace('\\', '/')
            val matches = fullPaths[path] ?: basenames[path.substringAfterLast('/')].orEmpty().filter { it.value.endsWith("/$path") }.map { it.key }
            if (matches.size == 1) ids.add(matches.single()) else missing++
        }
        return ids to missing
    }
}
