package yos.music.player.data.discovery

import yos.music.player.code.ListenStatsManager
import yos.music.player.data.libraries.*
import java.util.Calendar

object DiscoveryLibrary {
    fun year(song: YosMediaItem): Int = song.releaseYear?.takeIf { it > 0 } ?: song.recordingYear?.takeIf { it > 0 } ?: 0
    fun tracks(songs: List<YosMediaItem>): List<DiscoveryTrack> = songs.mapNotNull { song ->
        song.uri?.toString()?.let { DiscoveryTrack(it, song.title ?: defaultTitle, song.artistsName ?: defaultArtistsName,
            song.album ?: defaultAlbum, song.genre.orEmpty(), year(song),
            song.addDate ?: 0, song.duration) }
    }
    fun engagements(): Map<String, TrackEngagement> = ListenStatsManager.statsEvents.value.groupBy { it.uri.orEmpty() }.mapValues { (_, events) ->
        TrackEngagement(events.count { it.countsAsPlay }, events.sumOf { it.listenedMs }, events.maxOf { it.timestampMs })
    }
    fun day(): Long = ListenStatsLibrary.dayStartMs(System.currentTimeMillis())
    fun seed(): Long = day() xor DiscoveryPreferences.mixRevision.toLong().shl(32)
    @Synchronized
    fun mixes(songs: List<YosMediaItem>): List<DiscoveryMix> {
        val tracks = tracks(songs); val favorites = FavPlayListLibrary.favPlayList.mapNotNull { it.uri?.toString() }.toSet()
        val day = day(); val revision = DiscoveryPreferences.mixRevision; val mixSize = DiscoveryPreferences.mixSize
        val excluded = DiscoveryPreferences.excluded()
        val key = "${ListenStatsManager.statsEvents.value.isNotEmpty()}:$day:$revision:$mixSize:${tracks.map { it.id }.sorted().hashCode()}:${favorites.sorted().hashCode()}:${excluded.sorted().hashCode()}"
        DiscoveryPreferences.cachedMixes(key)?.let { return it }
        return DiscoveryEngine.generate(tracks, engagements(), favorites, day xor revision.toLong().shl(32), excluded, mixSize).also { DiscoveryPreferences.cacheMixes(key, it) }
    }
    fun resolve(ids: List<String>, songs: List<YosMediaItem>): List<YosMediaItem> {
        val index = songs.associateBy { it.uri?.toString() }; return ids.mapNotNull { index[it] }
    }
    fun evaluate(rule: SmartMixRule, songs: List<YosMediaItem>): List<YosMediaItem> = resolve(
        DiscoveryEngine.smart(rule, tracks(songs), engagements(), FavPlayListLibrary.favPlayList.mapNotNull { it.uri?.toString() }.toSet(), seed()), songs)
}
