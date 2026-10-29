package yos.music.player.ui.widgets.basic

internal data class ArtworkFlightRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** Progress zero lands exactly on the thumbnail; one preserves the full artwork bounds. */
internal fun artworkFlightFrame(source: ArtworkFlightRect, target: ArtworkFlightRect, progress: Float): ArtworkFlightRect {
    val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    fun blend(a: Float, b: Float) = a + (b - a) * p
    return ArtworkFlightRect(blend(target.left, source.left), blend(target.top, source.top),
        blend(target.width, source.width), blend(target.height, source.height))
}
