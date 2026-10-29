package yos.music.player.ui.widgets.basic

internal data class ArtworkFit(val scaleX: Float, val scaleY: Float)

/** TextureView starts stretched to its box. Scale the longer axis down to letterbox it. */
internal fun artworkFit(viewWidth: Float, viewHeight: Float, videoWidth: Float, videoHeight: Float): ArtworkFit {
    if (listOf(viewWidth, viewHeight, videoWidth, videoHeight).any { !it.isFinite() || it <= 0f })
        return ArtworkFit(1f, 1f)
    val videoAspect = videoWidth / videoHeight
    return if (viewWidth / viewHeight > videoAspect) ArtworkFit((viewHeight * videoAspect / viewWidth).coerceIn(0f, 1f), 1f)
    else ArtworkFit(1f, (viewWidth / videoAspect / viewHeight).coerceIn(0f, 1f))
}
