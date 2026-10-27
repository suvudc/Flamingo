package yos.music.player.ui.widgets.bitchord

/** Root-space measurements keep the lyric fade immediately above the real seek bar. */
internal fun lyricViewportHeightPx(
    panelTop: Float,
    seekBarTop: Float,
    availableHeight: Float,
    controlsVisible: Boolean,
    clearance: Float,
): Float {
    if (!availableHeight.isFinite() || availableHeight <= 0f) return 0f
    if (!controlsVisible) return availableHeight
    // One layout pass is needed before both sibling positions are available.
    if (!panelTop.isFinite() || !seekBarTop.isFinite()) return availableHeight * .56f
    return (seekBarTop - panelTop - clearance.coerceAtLeast(0f))
        .coerceIn(0f, availableHeight)
}
