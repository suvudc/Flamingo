# Music: local discovery and library features

This implementation recreates local-music feature behavior independently in Music's
existing Compose design. It does not copy or redistribute Pixel Player's proprietary
source. The current Music player UI, playback service, Quick Settings artwork,
animated-cover lookup and complete offline artwork storage are retained.

## Added

- Home with Your Mix, daily mixes, recent plays, recently added music, albums,
  artists and today's listening overview. Sections can be hidden and reordered.
- Local recommendations using favorite tracks, artist familiarity and recorded
  listening history, with daily stable selections, manual refresh, artist variety,
  replay/discovery/fresh mixes and artist/genre mixes. Mix size is configurable from 10 to 100 songs. No cloud AI is needed.
- Per-song mix exclusions and a reset action; save any mix as a normal playlist.
- Smart playlists combining artist, genre, year range, favorites and unplayed
  filters, sorting and a song limit. Rules are saved and evaluated against the
  current library on opening; they are not static exported playlists.
- Debounced global search over songs, artists, albums, genres, years and playlists,
  folder search, recent-search history and paginated results. Native song menus
  retain queue, playlist and favorite actions.
- Hierarchical folder browsing, genre/year collections and listening history with
  time-period filters, alongside existing album/artist/song/playlist views.
- M3U/M3U8 local playlist import/export. Imported paths must identify one local
  song; ambiguous, unavailable and network stream entries are reported/skipped.
- Additive backups for playlists, favorites, listening events, smart rules, mix
  exclusions and Home layout. Audio files and animated-cover downloads are not
  copied into backups. Backup imports/exports are bounded to 10 MB. Existing playlists are retained when restoring.
- Local tag and embedded lyrics editing with the existing TagLib dependency,
  Android-mediated write permission, LRC import/export, rescan, duplicate review
  and missing-metadata review. Only explicit Save edits the selected audio file.
- A resizable home-screen playback widget with bounded album artwork and native
  previous/play/pause/next actions; no second audio engine is added.

## Reused from Music

Playlist create/edit/pin/reorder/cover/undo, favorites, library hiding and rescan,
artist splitting, detailed listening statistics, sleep timer, lyrics display,
light/dark appearance, queue controls and existing audio/playback preferences.

## Scope

The user chose local phone features. Cloud accounts, AI-provider integrations,
streaming services and separate Wear OS/TV applications are not included. The
request also preserves Music's player: this change does not replace its playback
engine, introduce Pixel Player's mixer/equalizer/casting engine, or redesign the
player and animated covers. These are functional equivalents, not identical
implementations of every Pixel Player setting or platform integration.

## Validation

Meaningful JVM tests cover mix determinism, diversity, valid/excluded IDs, empty
libraries, history-driven selections, smart filters, multilingual search,
folder path boundaries and M3U matching. Android compilation and tests run in
GitHub Actions. Physical-device verification is still required for navigation,
large-library scrolling, audio write-permission flows and widget interactions.
