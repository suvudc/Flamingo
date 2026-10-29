# BitChord lyrics source

The lyrics drawing, word/syllable growth, held-note glow, rise/sweep geometry, line-focus/scroll choreography and smoothed playhead are ported from https://github.com/suvudc/BitChord (GPL-3.0). The original source comments and GPL license are retained alongside this notice.

Source snapshot: 1189e6a566c9181111205f26a983f7984f9393ea.

Files originate in shared/data/lyrics and sharedUi/ui/player: LyricLine, LyricAlignments, LyricGaps, LyricSections, TtmlLyrics, PlayerLyrics, LyricClock, LyricFocus, LyricsControlsGesture and PlaybackPosition. Music adapts package names, Media3/lifecycle access, icons/strings and settings. Translation-network UI and share-card UI remain Music's existing controls. The list receives Music's already-full-width bounds instead of BitChord's parent-gutter compensation. Intro/Instrumental placeholders remain suppressed at the user's request.

Raw provider documents remain in Music's atomic, persistent offline store. They are parsed by the same TTML parser for online and offline playback, preserving original syllables, backing vocals and duet alignment. Rendering and playhead interpolation do not access the network.

The Android lyric font weights and typography metrics come from BitChord's Android Theme.kt. Gradle's prepareBitChordLyricsFonts task downloads the three original font assets from the immutable source revision and verifies pinned SHA-256 hashes before packaging them into the APK. They are Android resources available entirely offline at runtime. Only the lyrics use this typography; other app screens retain their existing type. A clean source build needs network access to acquire these assets once, alongside its Gradle dependencies.
