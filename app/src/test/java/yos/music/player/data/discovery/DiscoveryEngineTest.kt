package yos.music.player.data.discovery

import org.junit.Assert.*
import org.junit.Test

class DiscoveryEngineTest {
    private fun tracks(count: Int = 120) = (1..count).map { i -> DiscoveryTrack("song-$i", "Song $i", "Artist ${i % 12}", "Album ${i % 8}", if (i % 2 == 0) "Rock" else "Jazz", 1980 + i % 40, i.toLong(), 180_000) }

    @Test fun mixesAreDeterministicForDayAndIndependentOfScanOrder() {
        val first = DiscoveryEngine.generate(tracks(), emptyMap(), emptySet(), 1234)
        assertEquals(first, DiscoveryEngine.generate(tracks().reversed(), emptyMap(), emptySet(), 1234))
        assertNotEquals(first.first().trackIds, DiscoveryEngine.generate(tracks(), emptyMap(), emptySet(), 5678).first().trackIds)
    }
    @Test fun mixesUseUniqueExistingIdsAndNeverIncludeExcludedSongs() {
        val input = tracks() + tracks().take(10)
        val mixes = DiscoveryEngine.generate(input, emptyMap(), emptySet(), 7, setOf("song-1", "song-2"))
        mixes.forEach { mix ->
            assertEquals(mix.trackIds.distinct(), mix.trackIds)
            assertTrue(mix.trackIds.size <= 50)
            assertTrue(mix.trackIds.all { id -> input.any { it.id == id } })
            assertFalse("song-1" in mix.trackIds); assertFalse("song-2" in mix.trackIds)
        }
    }
    @Test fun smallAndEmptyLibrariesWorkWithoutDuplicatesOrFakeRecommendations() {
        assertTrue(DiscoveryEngine.generate(emptyList(), emptyMap(), emptySet(), 0).isEmpty())
        val mixes = DiscoveryEngine.generate(tracks(1), emptyMap(), emptySet(), 0)
        assertTrue(mixes.all { it.trackIds == listOf("song-1") })
    }
    @Test fun replayContainsOnlyPlayedOrFavoriteSongs() {
        val mixes = DiscoveryEngine.generate(tracks(), mapOf("song-3" to TrackEngagement(2)), setOf("song-9"), 1)
        assertEquals(setOf("song-3", "song-9"), mixes.first { it.id == "replay" }.trackIds.toSet())
    }
    @Test fun mainMixDiversifiesArtists() {
        val input = tracks(90).map { it.copy(artist = "Large Artist") } + tracks(20).map { it.copy(id = "other-${it.id}", artist = "Other ${it.id}") }
        val result = DiscoveryEngine.generate(input, emptyMap(), emptySet(), 2).first().trackIds
        val index = input.associateBy { it.id }
        assertTrue(result.take(10).map { index[it]!!.artist }.distinct().size > 1)
    }
    @Test fun smartRulesCombineArtistGenreYearsAndFavoritesAndRespectLimit() {
        val songs = tracks()
        val favorites = songs.filter { it.genre == "Rock" && it.artist == "Artist 2" && it.year >= 1990 }.map { it.id }.toSet()
        val rule = SmartMixRule(artist = "Artist 2", genre = "rock", fromYear = 1990, toYear = 2019, favoritesOnly = true, limit = 2)
        val result = DiscoveryEngine.smart(rule, songs, emptyMap(), favorites, 4)
        assertEquals(2, result.size); assertTrue(result.all { it in favorites })
    }
    @Test fun unplayedAndPlayCountOrderingFollowActualHistory() {
        val history = mapOf("song-1" to TrackEngagement(7), "song-2" to TrackEngagement(2))
        assertFalse("song-1" in DiscoveryEngine.smart(SmartMixRule(unplayedOnly = true), tracks(10), history, emptySet(), 1))
        assertEquals(listOf("song-1", "song-2"), DiscoveryEngine.smart(SmartMixRule(order = "plays", limit = 2), tracks(10), history, emptySet(), 1))
    }
    @Test fun searchUsesAllTokensAcrossMetadataAndIgnoresAccents() {
        val songs = listOf(DiscoveryTrack("a", "Déjà Vu", "Beyoncé", "Album", "Pop", 2006), DiscoveryTrack("b", "Other", "Other", "Other"))
        assertEquals(listOf("a"), DiscoveryEngine.search("deja beyonce 2006", songs))
        assertTrue(DiscoveryEngine.search("deja missing", songs).isEmpty())
    }
    @Test fun nonLatinSearchDoesNotTreatDifferentNamesAsIdentical() {
        val songs = listOf(DiscoveryTrack("a", "नमस्ते", "कलाकार", "एल्बम"), DiscoveryTrack("b", "अर्को", "कलाकार", "एल्बम"))
        assertEquals(listOf("a"), DiscoveryEngine.search("नमस्ते", songs))
    }
    @Test fun m3uPreservesOrderAndDuplicatesButSkipsAmbiguousAndRemoteEntries() {
        val paths = mapOf("a" to "/Music/First.mp3", "b" to "/Music/Album/Same.mp3", "c" to "/Music/Other/Same.mp3")
        val (ids, missing) = DiscoveryEngine.importM3u("\uFEFF#EXTM3U\nFirst.mp3\nSame.mp3\nfile:///Music/First.mp3\nhttps://example.com/stream\n", paths)
        assertEquals(listOf("a", "a"), ids); assertEquals(2, missing)
    }
    @Test fun m3uUsesExactFullPathToResolveDuplicateBasenames() {
        val (ids, missing) = DiscoveryEngine.importM3u("/Music/A/Same.mp3\n", mapOf("a" to "/Music/A/Same.mp3", "b" to "/Music/B/Same.mp3"))
        assertEquals(listOf("a"), ids); assertEquals(0, missing)
    }
    @Test fun folderTreeUsesPathBoundariesAndHandlesSingleFolder() {
        val paths = listOf("/Music/A/First", "/Music/A/Second", "/Music/B", "/Music2/C")
        assertEquals("/", DiscoveryEngine.commonFolderRoot(paths))
        assertEquals(listOf("/Music/A", "/Music/B"), DiscoveryEngine.folderChildren(paths, "/Music"))
        assertEquals("/Music/B", DiscoveryEngine.commonFolderRoot(listOf("/Music/B")))
        assertTrue(DiscoveryEngine.folderChildren(listOf("/Music/B"), "/Music/B").isEmpty())
    }
}
