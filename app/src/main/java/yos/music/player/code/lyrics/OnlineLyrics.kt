package yos.music.player.code.lyrics

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import yos.music.player.code.utils.lrc.YosParsedLyricData
import yos.music.player.code.utils.lrc.YosTtmlFactory
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Parsing and matching are kept independent of Android and the network. */
object OnlineLyrics {
    fun unwrap(raw: String): String? {
        val text = raw.trim().removePrefix("\uFEFF")
        if (Regex("^\\[\\d{1,3}:").containsMatchIn(text)) return text
        if (!text.startsWith("{") && !text.startsWith("[") && !text.startsWith("\"")) return text.takeIf { it.isNotBlank() }
        fun extract(e: JsonElement): String? {
            if (e.isJsonPrimitive) return if (e.asJsonPrimitive.isString) e.asString else null
            if (e.isJsonArray) return e.asJsonArray.firstNotNullOfOrNull(::extract)
            if (!e.isJsonObject) return null
            val o = e.asJsonObject
            if (o.has("error") && !o["error"].isJsonNull && o["error"].toString() !in listOf("false", "\"\"")) return null
            if (o["ok"]?.toString() == "false" || o["isError"]?.toString() == "true") return null
            return listOf("ttml", "ttmlContent", "syncedLyrics", "lyrics", "lrc", "content", "data", "result")
                .firstNotNullOfOrNull { key -> o[key]?.let(::extract) }
        }
        return runCatching { extract(JsonParser.parseString(text)) }.getOrNull()
    }

    fun parse(raw: String): YosParsedLyricData? {
        val content = unwrap(raw) ?: return null
        if (YosTtmlFactory.isTtmlText(content)) return YosTtmlFactory().formatTtmlEntries(content)
        // Whole-line LRC fallback. Never invent word timestamps from plain text.
        val stamp = Regex("\\[(\\d{1,3}):(\\d{2}(?:\\.\\d+)?)\\]")
        val lines = content.lineSequence().flatMap { line ->
            val times = stamp.findAll(line).toList()
            val words = line.substringAfterLast(']').trim()
            if (words.isBlank()) emptySequence() else times.asSequence().map { m ->
                (m.groupValues[1].toFloat() * 60_000f + m.groupValues[2].toFloat() * 1000f) to words
            }
        }.sortedBy { it.first }.toList()
        if (lines.isEmpty()) return null
        return YosParsedLyricData(lines.map { listOf(it.first to "", it, it.first to "") },
            lines.mapIndexed { i, v -> lines.getOrNull(i + 1)?.first ?: v.first + 5000f },
            List(lines.size) { false }, List(lines.size) { null }, List(lines.size) { null }, false)
    }

    fun wordTimed(data: YosParsedLyricData): Boolean =
        data.entries.any { line -> line.count { it.second.isNotBlank() } > 1 }

    fun matches(title: String, artist: String, durationMs: Long,
                candidateTitle: String, candidateArtist: String, candidateSeconds: Double): Boolean {
        fun normalized(v: String) = Normalizer.normalize(v, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        fun core(v: String) = normalized(v.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ").substringBefore(" - "))
        val versions = setOf("live", "remix", "acoustic", "instrumental", "karaoke", "demo", "cover", "sped", "slowed", "reverb", "nightcore")
        fun version(v: String) = normalized(v).split(' ').filter { it in versions }.toSet()
        val artists = Regex("(?i)\\s*(?:,|&|;|/| feat\\.? | ft\\.? | and )\\s*")
        val wantedArtists = artist.split(artists).map(::normalized).filter { it.isNotEmpty() }
        val foundArtists = candidateArtist.split(artists).map(::normalized)
        return core(title).isNotBlank() && core(title) == core(candidateTitle) && version(title) == version(candidateTitle) &&
            wantedArtists.any { it in foundArtists } &&
            (durationMs <= 0 || candidateSeconds <= 0 || abs(durationMs / 1000.0 - candidateSeconds) <= 4.0)
    }
}
