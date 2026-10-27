package yos.music.player.ui.widgets

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

// Adapted motion values from suvudc/BitChord PlayerLyrics.kt (GPL-3.0):
// soft 30dp sweep, 2dp word lift, 400ms handover, 340ms strip slide.
private val lyricEasing = CubicBezierEasing(.41f, 0f, .12f, .99f)

@Composable
private fun rememberLyricsClock(): State<Float> {
    val clock = remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        while (true) {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                if (MediaViewModelObject.isPlaying.value) withFrameNanos {
                    clock.floatValue = (MediaController.mediaControl?.currentPosition ?: 0).toFloat()
                } else {
                    clock.floatValue = (MediaController.mediaControl?.currentPosition ?: 0).toFloat()
                    delay(80)
                }
            } else delay(250)
        }
    }
    return clock
}

@Composable
private fun rememberTimeline(): List<LyricLine> {
    val entries = MediaViewModelObject.lrcEntries.value
    val ends = MediaViewModelObject.lyricLineEndTimes.toList()
    return remember(entries, ends) { lyricTimeline(entries, ends) }
}

/** Text layout is cached; only the active line reads the playback clock during drawing. */
@Composable
private fun SweptLine(line: LyricLine, clock: State<Float>, active: Boolean,
    modifier: Modifier = Modifier, compact: Boolean = false, alignRight: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = remember(compact) { TextStyle(fontSize = if (compact) 16.sp else 32.sp,
        lineHeight = if (compact) 23.sp else 39.sp, fontWeight = FontWeight.Bold,
        color = Color.White) }
    BoxWithConstraints(modifier) {
        val maxPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val layout = remember(line.text, maxPx, style, density) {
            measurer.measure(AnnotatedString(line.text), style, maxLines = if (compact) 1 else Int.MAX_VALUE,
                overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
                constraints = Constraints(maxWidth = maxPx))
        }
        val height = with(density) { layout.size.height.toDp() + 8.dp }
        Spacer(Modifier.fillMaxWidth().height(height).drawWithCache {
            val inset = 4.dp.toPx()
            val shiftX = if (alignRight) (size.width - layout.size.width).coerceAtLeast(0f) else 0f
            val feather = (if (compact) 16.dp else 30.dp).toPx()
            onDrawBehind {
                translate(shiftX, inset) {
                    val now = if (active) clock.value else line.start - 1f
                    val sung = if (active) line.sungCharacters(now) else 0f
                    val moving = if (active && line.wordTimed && !compact)
                        line.words.firstOrNull { now >= it.start && now < it.end } else null
                    val lift = moving?.let { word ->
                        val p = ((now - word.start) / (word.end - word.start).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        2.dp.toPx() * sin(PI.toFloat() * p)
                    } ?: 0f
                    for (visual in 0 until layout.lineCount) {
                        val start = layout.getLineStart(visual)
                        val end = layout.getLineEnd(visual, visibleEnd = true)
                        if (end <= start) continue
                        val top = layout.getLineTop(visual)
                        val bottom = layout.getLineBottom(visual)
                        val rtl = layout.getParagraphDirection(start) == ResolvedTextDirection.Rtl
                        val character = floor(sung).toInt().coerceIn(start, end - 1)
                        val box = layout.getBoundingBox(character)
                        val fraction = sung - floor(sung)
                        val boundary = when {
                            sung >= end -> if (rtl) layout.getLineLeft(visual) - feather else layout.getLineRight(visual) + feather
                            sung <= start -> if (rtl) layout.getLineRight(visual) + feather else layout.getLineLeft(visual) - feather
                            rtl -> box.right - box.width * fraction
                            else -> box.left + box.width * fraction
                        }
                        val wipe = Brush.horizontalGradient(
                            if (rtl) listOf(Color.Transparent, Color.White) else listOf(Color.White, Color.Transparent),
                            startX = boundary - feather / 2, endX = boundary + feather / 2)
                        fun androidx.compose.ui.graphics.drawscope.DrawScope.segment(left: Float, right: Float, rise: Float) {
                            if (right <= left) return
                            clipRect(left, top - inset, right, bottom + inset) {
                                translate(0f, -rise) {
                                    drawText(layout, color = Color.White.copy(alpha = if (compact) .55f else .45f))
                                    if (active) {
                                        if (!line.wordTimed) drawText(layout, color = Color.White)
                                        else drawText(layout, brush = wipe)
                                    }
                                }
                            }
                        }
                        val first = moving?.from?.coerceAtLeast(start)
                        val last = moving?.until?.coerceAtMost(end)
                        if (first != null && last != null && last > first) {
                            val a = layout.getBoundingBox(first)
                            val b = layout.getBoundingBox(last - 1)
                            val left = min(a.left, b.left); val right = max(a.right, b.right)
                            segment(0f, left, 0f); segment(left, right, lift); segment(right, layout.size.width.toFloat(), 0f)
                        } else segment(0f, layout.size.width.toFloat(), 0f)
                    }
                }
            }
        })
    }
}

@Composable
fun MainPlayerLyricStrip(onOpenLyrics: () -> Unit) {
    val lines = rememberTimeline()
    val clock = rememberLyricsClock()
    val index by remember(lines, clock) { derivedStateOf { activeLyricIndex(lines, clock.value) } }
    val line = lines.getOrNull(index)
    Row(Modifier.fillMaxWidth().height(34.dp).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenLyrics),
        verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(targetState = index, label = "Current lyric", modifier = Modifier.weight(1f),
            transitionSpec = { (fadeIn(tween(340)) + slideInVertically(tween(340, easing = lyricEasing)) { it / 3 }) togetherWith
                (fadeOut(tween(260)) + slideOutVertically(tween(340, easing = lyricEasing)) { -it / 3 }) }) { shown ->
            val shownLine = lines.getOrNull(shown)
            if (shownLine != null) SweptLine(shownLine, clock, active = shown == index, compact = true)
            else Text(if (MediaViewModelObject.lyricsDownloading.value) "Finding lyrics…" else if (lines.isEmpty()) "Lyrics & providers" else "♪ Instrumental",
                color = Color.White.copy(alpha = .55f), fontSize = 15.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.ChevronRight, "Open lyrics", tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MusicLyricsPanel(modifier: Modifier = Modifier, showTranslation: Boolean = true, onSeek: (Long) -> Unit) {
    val lines = rememberTimeline()
    val clock = rememberLyricsClock()
    val index by remember(lines, clock) { derivedStateOf { activeLyricIndex(lines, clock.value) } }
    val list = rememberLazyListState()
    var viewport by remember { mutableIntStateOf(0) }
    val browsing by remember { derivedStateOf { list.isScrollInProgress } }
    val sides = MediaViewModelObject.otherSideForLines
    val subtitles = MediaViewModelObject.lyricLineSubtitles
    val transliterations = MediaViewModelObject.lyricLineTransliterations
    LaunchedEffect(index, lines) {
        if (index < 0 || list.isScrollInProgress) return@LaunchedEffect
        val target = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (target == null) list.scrollToItem((index - 1).coerceAtLeast(0))
        withFrameNanos { }
        list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let {
            list.animateScrollBy(it.offset - viewport * .32f, tween(400, easing = lyricEasing))
        }
    }
    if (lines.isEmpty()) Box(modifier, contentAlignment = Alignment.Center) {
        Text(if (MediaViewModelObject.lyricsDownloading.value) "Finding lyrics…" else "No lyrics yet. Choose a provider above.",
            color = Color.White.copy(alpha = .6f), modifier = Modifier.padding(24.dp))
    } else LazyColumn(modifier.onSizeChanged { viewport = it.height }, state = list,
        contentPadding = PaddingValues(top = 42.dp, bottom = 180.dp, start = 26.dp, end = 26.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        itemsIndexed(lines, key = { i, line -> "$i:${line.start}:${line.text.hashCode()}" }) { i, line ->
            val active by remember(line, clock) { derivedStateOf {
                clock.value >= line.start && clock.value < line.end
            } }
            val distance = abs(i - index)
            val alpha by animateFloatAsState(if (active) 1f else if (browsing) .8f else when(distance) { 1 -> .8f; 2 -> .7f; 3 -> .58f; else -> .46f },
                tween(400, easing = lyricEasing), label = "Lyric focus")
            val scale by animateFloatAsState(if (active) 1f else .98f, tween(400, easing = lyricEasing), label = "Lyric scale")
            Column(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, .5f) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSeek(line.start.toLong()) }) {
                SweptLine(line, clock, active, alignRight = sides.getOrNull(line.sourceIndex) == true)
                val secondary = if (showTranslation) subtitles.getOrNull(line.sourceIndex)
                    ?: MediaViewModelObject.lrcEntries.value.getOrNull(line.sourceIndex)?.lastOrNull()?.second?.takeIf { it.isNotBlank() }
                    else transliterations.getOrNull(line.sourceIndex)
                if (!secondary.isNullOrBlank()) Text(secondary, color = Color.White.copy(alpha = .65f), fontSize = 18.sp)
            }
        }
    }
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
