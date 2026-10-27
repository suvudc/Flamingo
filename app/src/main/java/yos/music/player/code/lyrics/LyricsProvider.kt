package yos.music.player.code.lyrics

/** Stable IDs also namespace offline downloads, so switching sources never returns another source's file. */
enum class LyricsProvider(val id: String, val title: String, val detail: String) {
    Auto("auto", "Automatic", "Prefer word timings, then synced lines"),
    Local("local", "Local lyrics", "Embedded lyrics and nearby LRC / TTML files"),
    LrcRed("lrc-red", "lrc.red", "Word-synced TTML when available"),
    Bini("bini", "BiniLyrics", "Word-synced TTML when available"),
    Better("better", "BetterLyrics", "Word-synced lyrics when available"),
    LrcLib("lrclib", "LRCLIB", "Synced lines when available");
    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: Auto
    }
}
