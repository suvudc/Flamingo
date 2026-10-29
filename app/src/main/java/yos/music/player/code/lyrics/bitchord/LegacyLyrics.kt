package yos.music.player.code.lyrics.bitchord

/** Adapter for local/line-synced sources; TTML uses BitChord's parser directly. */
internal fun fromLegacyLyrics(entries: List<List<Pair<Float, String>>>, ends: List<Float>, sides: List<Boolean>): List<LyricLine> =
    yos.music.player.code.lyrics.lyricTimeline(entries, ends).map { line ->
        val words = if (!line.wordTimed) emptyList() else Regex("\\S+").findAll(line.text).mapNotNull { token ->
            val begin = token.range.first
            val until = token.range.last + 1
            val parts = line.words.filter { it.until > begin && it.from < until }
            if (parts.isEmpty()) null else LyricWord(
                parts.first().start.toLong(), parts.last().end.toLong(), token.value,
                LyricSyllable.normalized(parts.map { part -> LyricSyllable(part.start.toLong(), part.end.toLong(),
                    (part.from - begin).coerceAtLeast(0), (part.until - begin).coerceAtMost(token.value.length)) }, token.value.length))
        }.toList()
        LyricLine(line.start.toLong(), line.text, words, line.end.toLong(),
            alignment = if (sides.getOrNull(line.sourceIndex) == true) LyricAlignment.End else LyricAlignment.Start)
    }
