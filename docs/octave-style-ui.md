# Music build 22: reference-style glass and library

Reference: user-provided Octave APK, three screenshots and the 19.48s recording.
The APK contains Compose 1.11.4 and an AGSL glass shader. Music retains its
Compose 1.7.1 baseline and uses an independently written, single-capsule shader
adapted to its Haze backdrop. No Octave server, account, app binary, resources,
credentials, catalogue, Radio, Podcasts or Local Files row are integrated.

A shader layer refracts only the backdrop on API 33+, while Haze provides blur.
Text, artwork and controls draw above it and remain sharp. Shader construction
is guarded with the existing frosted renderer as fallback. There is one cached
shader per pill, and size changes update uniforms during layer rendering rather
than reading animation state in composition. No per-frame bitmap captures.

Dock: Home, New, Library, Search. One glass row expands/collapses on consumed
user scroll; a spring lead/tail stretches the tab highlight. Mini player playback,
next/previous and player-opening gestures remain functional. Search works on the
local library. New is recently added local music, not the reference's online
catalogue. Stats remains reachable through the header overflow menu.

Library: Liked Songs gradient tile, Playlists, Artists, Albums, Songs, Recently
Played, Shared and a two-column Recently Added grid. Shared invokes Android's
share sheet with a chosen playlist's track list; no collaborative cloud service
is simulated. Glass header buttons open search, create a playlist with name
validation, and open the settings/stats/about menu. Empty states are supported.

Player: uncropped edge-to-edge square cover in the upper screen, fitted into a
small/landscape viewport if necessary, with a bottom dissolve. TextureView video
receives a contain/letterbox transform. Existing motion/video lookup, durable
offline animation/lyrics files and playback services are retained. The existing
cover-to-mini flight uses the measured hero geometry. The YosWrapper helper no
longer keys composition by a captured lambda's identity, which could recreate
remembered animation/render layers as state changed.

Validation: optimized Android build and JVM suite via GitHub Actions. Added pure
fit-geometry tests for square, portrait, landscape, resize and unavailable video
sizes. Shader appearance, native video capture, gestures and frame pacing need
verification on the user's Pixel 7; no device/emulator rendering is available here.

## Build 23 startup correction and runtime verification

Removed implicit access to the page's HazeState from descendants. In Haze
0.9.0-alpha06 the source records its descendants into a GraphicsLayer, and a
hazeChild draws that same layer. The new in-page header buttons were both
recorded by the source and drawing from it, creating a self-referential native
RenderNode graph. Header/player buttons now use the reflective shader material
without a page backdrop; the dock explicitly receives the page's HazeState as a
sibling outside the source and retains its actual backdrop blur. A runtime
workflow builds the optimized x86_64 variant and launches/navigates it on an
Android emulator, collecting logcat, activity state, screenshot and R8 mapping.
Phone APK ABI defaults and its signing identity are unchanged.

Runtime evidence: baseline run 40446975400 reproduced SIGSEGV on RenderThread
with repeating libhwui RenderNode::prepareTreeImpl frames. Patched run
40447177751 launched and tapped New, Library and Home; its final screenshot
shows Home and its activity dump identifies MainActivity as topResumedActivity.
That run's final shell assertion used the old mResumedActivity field name and
returned a false failure. The assertion now accepts current Android field names,
and was checked against the captured real activity dump. Phone APK run
40447177746 passed all 66 JVM tests; APK integrity and matching signer verified.
