# Music build 19: player motion and offline lyrics

Motion reference: suvudc/BitChord commit 1189e6a566c9181111205f26a983f7984f9393ea,
`PlayerSheetMotion.kt`, `PlayerDock.kt`, and `NowPlayingScreen.kt` (GPL-3.0).
Music uses the same critically damped 195 stiffness spring, 0.5px stopping
threshold, and 0.15 fade lead. Artwork position follows the spring directly;
there is no second easing curve. The cover layer is recorded while visible and
reused for the close, avoiding an image request/decode and cover replacement at
that moment. The stationary page behind it avoids per-frame glass recapture
from page scaling. Video preparation starts after opening settles; a closing
player pauses the decorative video and keeps its player alive until unmount.
The native player layout and offline animated artwork lookup remain unchanged.
Device frame pacing and Android TextureView capture need checking on the Pixel;
unit tests alone cannot prove visual or performance equivalence to BitChord.

Lyrics endpoints and query conventions were checked against the same BitChord
revision (`LrcRed.kt`, `BiniLyrics.kt`, `BetterLyrics.kt`, `LrcLib.kt`). This is
an independent adapter to Music's existing TTML and lyric view, not a port of
BitChord's entire lyrics UI or all its providers. lrc.red, BiniLyrics and
BetterLyrics supply genuine word/syllable TTML when available; LRCLIB supplies
the whole-line fallback. Live/remix and artist/duration matching prevent obvious
wrong recordings. Existing local word-timed lyrics take precedence; local
whole-line lyrics remain visible while a word-timed upgrade is sought.

Successful network lyrics are saved automatically in filesDir/downloaded-lyrics,
not cacheDir. The lyric view shows provider/save status and offers a tap to
retry or refresh. Downloads are atomic and synced before rename, keyed by
track title, artists, album and duration. Cached data loads before any network
request; clearing app data/uninstalling removes it. Requests run off the main
thread, are bounded by response size/deadlines and cannot apply a stale result
after skipping. These are public third-party providers; availability and word
coverage vary. No provider credentials are embedded.

Validation: JVM tests cover word timing, line fallback, invalid responses,
recording matching, durable reload, failed replacement and key isolation. The
Android build and all unit tests are run by the optimized APK workflow.
