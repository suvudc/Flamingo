package yos.music.player.ui.widgets.basic

/** Accumulate deliberate movement; tiny direction changes must not flicker the dock. */
internal class DockScrollAccumulator(private val thresholdPx: Float) {
    private var distance = 0f
    fun reset() { distance = 0f }
    fun scroll(delta: Float): Boolean? {
        if (!delta.isFinite() || delta == 0f) return null
        if ((delta > 0) != (distance > 0)) distance = 0f
        distance += delta
        if (kotlin.math.abs(distance) < thresholdPx) return null
        val compact = distance < 0
        distance = 0f
        return compact
    }
}
