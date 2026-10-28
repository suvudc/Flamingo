package yos.music.player.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import yos.music.player.code.MediaController
import yos.music.player.code.lyrics.bitchord.LyricLine
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.models.MainViewModel
import yos.music.player.data.models.MediaViewModel
import yos.music.player.data.objects.MediaViewModelObject
import yos.music.player.ui.pages.NowPlaying
import yos.music.player.ui.pages.NowPlayingPage
import yos.music.player.ui.widgets.bitchord.*

/** Real pointer input against the production lyric renderer and player layout. */
@RunWith(AndroidJUnit4::class)
class PlayerInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val lines = List(80) { LyricLine(it * 4_000L, "Lyric line %02d".format(it)) }

    private fun panel(
        content: List<LyricLine> = lines,
        onReveal: () -> Unit = {},
        onSeek: (Long) -> Unit = {},
    ) {
        val head = LyricPlayhead(PlaybackPosition().apply { report(0L); advancing = false })
        compose.setContent {
            MaterialTheme {
                BitChordLyricsTypography {
                    LyricsPanel(
                        lines = content, trackKey = "gesture-fixture", playhead = head,
                        looking = false, isPlaying = false, controlsOpen = false,
                        onSeekToLine = onSeek, onRevealControls = onReveal, onHideControls = {},
                        modifier = Modifier.fillMaxSize().testTag("lyrics"),
                    )
                }
            }
        }
    }

    private fun scrollPosition(): Float = compose.onNodeWithTag("lyrics")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun lowerHalfDragScrollsWithoutRevealingOrSeeking() {
        var reveals = 0
        var seeks = 0
        panel(onReveal = { reveals++ }, onSeek = { seeks++ })
        val before = scrollPosition()
        compose.onNodeWithTag("lyrics").performTouchInput {
            swipe(Offset(center.x, height * .88f), Offset(center.x, height * .57f), 500)
        }
        assertTrue("A drag wholly below the midpoint must scroll", scrollPosition() > before)
        compose.runOnIdle { assertEquals(0, reveals); assertEquals(0, seeks) }
        screenshot("lyrics-lower-half-scroll")
    }

    @Test fun upperHalfDragStillScrolls() {
        panel()
        val before = scrollPosition()
        compose.onNodeWithTag("lyrics").performTouchInput {
            swipe(Offset(center.x, height * .45f), Offset(center.x, height * .12f), 500)
        }
        assertTrue("Upper-half drags must continue scrolling", scrollPosition() > before)
    }

    @Test fun lowerHalfLyricTapReachesTheLineExactlyOnce() {
        val seeks = mutableListOf<Long>()
        var reveals = 0
        panel(onReveal = { reveals++ }, onSeek = { seeks += it })
        val viewport = compose.onNodeWithTag("lyrics").fetchSemanticsNode().boundsInRoot
        val rows = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val index = rows.indexOfFirst {
            it.boundsInRoot.center.y > viewport.top + viewport.height * .55f &&
                it.boundsInRoot.bottom < viewport.bottom - 24f
        }
        assertTrue("Fixture must expose a clickable lyric below the midpoint", index >= 0)
        compose.onAllNodes(hasClickAction())[index].performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, seeks.size); assertEquals(0, reveals) }
    }

    @Test fun emptyPanelLowerTapRevealsControls() {
        var reveals = 0
        panel(content = emptyList(), onReveal = { reveals++ })
        compose.onNodeWithTag("lyrics").performTouchInput {
            click(Offset(center.x, height * .8f))
        }
        compose.runOnIdle { assertEquals(1, reveals) }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    private fun player(onMinimize: () -> Unit = {}) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val artwork = File(context.cacheDir, "interaction-cover.png")
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(139, 52, 42))
        artwork.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = Uri.fromFile(artwork)
        compose.runOnIdle {
            MediaController.musicPlaying.value = YosMediaItem(
                uri = Uri.parse("content://music-fixture/1"), mediaId = "fixture", mimeType = "audio/mpeg",
                title = "Layout fixture", writer = null, compilation = null, composer = null,
                artists = "Music by Suvu", album = "Interaction checks", albumArtists = null,
                thumb = uri, trackNumber = null, discNumber = null, genre = null,
                recordingDay = null, recordingMonth = null, recordingYear = null, releaseYear = null,
                artistId = null, albumId = null, genreId = null, author = null, addDate = null,
                duration = 320_000L, modifiedDate = null, cdTrackNumber = null,
            )
            MediaViewModelObject.bitmap.value = uri
            MediaViewModelObject.lrcEntries.value = lines.map { listOf(it.timeMs.toFloat() to it.text, 0f to "") }
            MediaViewModelObject.bitChordLines.value = lines
            MediaViewModelObject.isPlaying.value = false
        }
        compose.setContent {
            val page = androidx.compose.runtime.remember { mutableStateOf(NowPlayingPage.Album) }
            MaterialTheme {
                NowPlaying(
                    mainViewModel = MainViewModel(), mediaViewModel = MediaViewModel(),
                    navController = rememberNavController(), onMinimizeNowPlaying = { onMinimize() },
                    isPlayingStatusLambda = { false }, isPlayingOnChanged = {},
                    nowPageLambda = { page.value }, showNowPlaying = { true }, showMiniPlayer = { false },
                    nowPageOnChanged = { page.value = it },
                )
            }
        }
    }

    @Test fun albumLyricsAreCloseAndOverflowRespondsAtItsExpandedEdge() {
        player()
        val artist = compose.onNodeWithText("Music by Suvu").fetchSemanticsNode().boundsInRoot
        val strip = compose.onNodeWithContentDescription("Open lyrics").fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        val gap = (strip.top - artist.bottom) / density
        assertTrue("Artist/lyric gap should be compact without overlap: $gap dp", gap in 4f..26f)
        compose.onNodeWithContentDescription("More song options").assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Toggle favorite").assertWidthIsAtLeast(48.dp)
        screenshot("album-compact-spacing")
        repeat(3) {
            compose.onNodeWithContentDescription("More song options").performTouchInput {
                click(Offset(3.dp.toPx(), center.y))
            }
            compose.onNodeWithText("Sleep Timer").assertIsDisplayed()
            pressBack()
            compose.waitForIdle()
        }
    }

    @Test fun playerHandleRespondsToTap() {
        var minimizes = 0
        player { minimizes++ }
        compose.onNodeWithContentDescription("Minimize player").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, minimizes) }
    }

    private fun screenshot(name: String) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "interaction-diagnostics").apply { mkdirs() }
        File(root, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
