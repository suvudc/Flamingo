package yos.music.player.code.lyrics

import kotlin.math.max

data class LyricWord(val start: Float, val end: Float, val from: Int, val until: Int)
data class LyricLine(val start: Float, val end: Float, val text: String, val words: List<LyricWord>, val sourceIndex: Int = 0) {
    val wordTimed: Boolean get() = words.any { it.end > it.start }
    /** Character position comes only from real word boundaries; silence holds the previous position. */
    fun sungCharacters(now: Float): Float {
        if (!wordTimed) return if (now >= start) text.length.toFloat() else 0f
        var sung = 0f
        for (word in words) {
            if (now < word.start) break
            if (now >= word.end) sung = word.until.toFloat()
            else return word.from + (word.until - word.from) *
                ((now - word.start) / (word.end - word.start).coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
        return sung
    }
}

fun lyricTimeline(entries: List<List<Pair<Float, String>>>, endTimes: List<Float>): List<LyricLine> =
    entries.mapIndexedNotNull { index, pairs ->
        val start = pairs.firstOrNull()?.first?.takeIf { it.isFinite() } ?: return@mapIndexedNotNull null
        val main = pairs.dropLast(1)
        val text = StringBuilder()
        val words = mutableListOf<LyricWord>()
        var previous = start
        for ((time, word) in main) {
            if (!time.isFinite()) continue
            if (word.isNotEmpty()) {
                val from = text.length
                text.append(word)
                if (word.isNotBlank()) words += LyricWord(previous, max(time, previous), from, text.length)
            }
            previous = max(previous, time)
        }
        if (text.isBlank()) return@mapIndexedNotNull null
        val nextStart = entries.getOrNull(index + 1)?.firstOrNull()?.first
        val end = endTimes.getOrNull(index)?.takeIf { it.isFinite() && it > start }
            ?: nextStart?.takeIf { it > start } ?: max(start + 5000f, previous)
        LyricLine(start, max(end, previous), text.toString(), words, index)
    }

fun activeLyricIndex(lines: List<LyricLine>, now: Float): Int =
    lines.indexOfLast { now >= it.start && now < it.end }
