package yos.music.player.ui.pages

/** Five stable cover positions, or no collage while the library is empty/loading. */
internal fun <T> homeCollageSlots(covers: List<T>): List<T> {
    if (covers.isEmpty()) return emptyList()
    return List(5) { covers[it % covers.size] }
}
