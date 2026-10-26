# Animated cover providers

The files under `app/src/main/java/yos/music/player/code/canvas/` adapt the
Apple Music, Tidal and community animated artwork providers and metadata matching
from [BitChord](https://github.com/suvudc/BitChord), originally developed in
[kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord).

BitChord is licensed under GNU GPL version 3. This project retains that license;
see LICENSE. Changes here adapt the package, Android logging and bounded network
client for Music. Music adds persistent, complete offline downloads separately.

Spotify Canvas integration is not included: BitChord's implementation requires
the listener's Spotify session and a separate authentication flow.
