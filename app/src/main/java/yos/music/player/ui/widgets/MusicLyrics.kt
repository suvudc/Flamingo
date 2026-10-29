package yos.music.player.ui.widgets

import yos.music.player.ui.widgets.basic.HapticTextButton as TextButton
import yos.music.player.ui.widgets.basic.hapticClickable as clickable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import yos.music.player.code.MediaController
import yos.music.player.code.lyrics.*
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.objects.MediaViewModelObject
import kotlin.math.*

/** Host integration; drawing and animation live in the ported BitChord sources. */
@Composable
private fun rememberBitChordLines(): List<yos.music.player.code.lyrics.bitchord.LyricLine> {
    val entries = MediaViewModelObject.lrcEntries.value
    val raw = MediaViewModelObject.bitChordLines.value
    val ends = MediaViewModelObject.lyricLineEndTimes.toList()
    val sides = MediaViewModelObject.otherSideForLines.toList()
    return remember(entries, raw, ends, sides) {
        if (entries.isEmpty()) emptyList()
        else raw?.takeIf { it.isNotEmpty() }
            ?: yos.music.player.code.lyrics.bitchord.fromLegacyLyrics(entries, ends, sides)
    }
}

@Composable
fun MainPlayerLyricStrip(modifier: Modifier = Modifier, onOpenLyrics: () -> Unit) = yos.music.player.ui.widgets.bitchord.BitChordLyricsTypography {
    val lines = rememberBitChordLines()
    val key = MediaController.musicPlaying.value?.uri.toString()
    val (head, advancing) = yos.music.player.ui.widgets.bitchord.rememberMusicLyricPlayhead(key)
    Box(modifier.fillMaxWidth().height(34.dp).clipToBounds().semantics { contentDescription = "Open lyrics" },
        contentAlignment = Alignment.CenterStart) {
        if (lines.isNotEmpty()) yos.music.player.ui.widgets.bitchord.CurrentLyricLine(
            lines, key, head, MediaViewModelObject.isPlaying.value && advancing,
            0L, onOpenLyrics, Modifier.fillMaxWidth())
        else Box(Modifier.fillMaxSize().clickable(onClick = onOpenLyrics))
    }
}

@Composable
fun MusicLyricsPanel(modifier: Modifier = Modifier, showTranslation: Boolean = true,
    controlsOpen: Boolean = true, onRevealControls: () -> Unit = {}, onHideControls: () -> Unit = {},
    onSeek: (Long) -> Unit) = yos.music.player.ui.widgets.bitchord.BitChordLyricsTypography {
    val lines = rememberBitChordLines()
    val key = MediaController.musicPlaying.value?.uri.toString()
    val (head, advancing) = yos.music.player.ui.widgets.bitchord.rememberMusicLyricPlayhead(key)
    val subtitles = MediaViewModelObject.lyricLineSubtitles.toList()
    val transliterations = MediaViewModelObject.lyricLineTransliterations.toList()
    val entries = MediaViewModelObject.lrcEntries.value
    val secondary = remember(lines, subtitles, transliterations, entries, showTranslation) {
        val texts = lines.map { line ->
            val source = entries.indexOfFirst { it.firstOrNull()?.first?.toLong() == line.timeMs }
            val text = if (showTranslation) subtitles.getOrNull(source)
                ?: entries.getOrNull(source)?.lastOrNull()?.second?.takeIf { it.isNotBlank() }
                else transliterations.getOrNull(source)
            text?.takeIf { it.isNotBlank() }
        }
        if (texts.none { it != null }) null else lines.mapIndexed { i, line ->
            line.copy(text = texts[i] ?: line.text, words = emptyList(), background = null, timingSource = line)
        }
    }
    yos.music.player.ui.widgets.bitchord.LyricsPanel(
        lines = lines, subLines = secondary, trackKey = key, playhead = head,
        looking = MediaViewModelObject.lyricsDownloading.value,
        isPlaying = MediaViewModelObject.isPlaying.value && advancing,
        onSeekToLine = onSeek, controlsOpen = controlsOpen, onRevealControls = onRevealControls, onHideControls = onHideControls,
        modifier = modifier.clipToBounds())
}

@Composable
fun LyricsProviderContent(onDone: () -> Unit) {
    val selected = LyricsProvider.fromId(SettingsLibrary.LyricsProvider)
    Text("Lyrics provider", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("Downloads are saved for offline listening. Word animation needs a source with word timings.",
        Modifier.padding(top = 8.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), fontSize = 13.sp)
    LyricsProvider.entries.forEach { provider ->
        Row(Modifier.fillMaxWidth().clickable {
            SettingsLibrary.LyricsProvider = provider.id
            MediaController.downloadLyrics?.invoke()
            onDone()
        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected == provider, onClick = null)
            Column(Modifier.padding(start = 10.dp)) {
                Text(provider.title, fontWeight = FontWeight.SemiBold)
                Text(provider.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            }
        }
    }
    TextButton(onClick = { MediaController.downloadLyrics?.invoke(); onDone() }, enabled = !MediaViewModelObject.lyricsDownloading.value) {
        Text("Download / refresh lyrics")
    }
    Text(MediaViewModelObject.lyricsDownloadStatus.value, fontSize = 12.sp)
}
