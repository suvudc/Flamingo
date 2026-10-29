package yos.music.player.ui.pages

import kotlin.random.Random

internal data class HomeLibraryShuffle<T>(
    val firstSong: T,
    val queue: List<T>,
    val shuffleModeEnabled: Boolean,
)

/** Snapshot the whole visible library for this tap, independently of the daily mix. */
internal fun <T> homeLibraryShuffle(
    library: List<T>,
    random: Random = Random.Default,
): HomeLibraryShuffle<T>? {
    val queue = library.toList()
    val firstSong = queue.randomOrNull(random) ?: return null
    return HomeLibraryShuffle(firstSong, queue, shuffleModeEnabled = true)
}
