package yos.music.player.ui.widgets.basic

internal data class PlayerDismissVisuals(val fraction: Float, val controlsAlpha: Float, val pageAlpha: Float)

/** The title/controls leave before the page is revealed; only the cover finishes docking. */
internal fun playerDismissVisuals(progress: Float, startProgress: Float): PlayerDismissVisuals {
    val fraction = if (progress.isFinite() && startProgress.isFinite() && startProgress > .01f)
        (progress / startProgress).coerceIn(0f, 1f) else 0f
    return PlayerDismissVisuals(fraction, ((fraction - .65f) / .35f).coerceIn(0f, 1f), 1f - fraction)
}
