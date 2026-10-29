package yos.music.player.ui.widgets.basic

internal fun playerSheetProgress(open: Boolean, offset: Float, height: Int): Float {
    if (!open || height <= 0 || !offset.isFinite()) return 0f
    return (offset / height).coerceIn(0f, 1f)
}

internal fun playerSheetTargetOpen(progress: Float, velocity: Float): Boolean =
    if (kotlin.math.abs(velocity) >= 400f) velocity > 0f else progress >= .5f
