package yos.music.player.ui.pages

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.ListenHistoryManager
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.*
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.theme.isFlamingoInDarkMode
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Home-only presentation. Playback, navigation and artwork services remain Music's own. */
@Composable
fun PixelStyleHome(navController: NavController) {
    val context = LocalContext.current
    val dark = isFlamingoInDarkMode()
    val palette = remember(context, dark) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = palette) {
        HomeContent(navController)
    }
}

private fun localDay(): Int = Calendar.getInstance().run {
    get(Calendar.YEAR) * 400 + get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun HomeContent(nav: NavController) {
    val songs = MusicLibrary.songs
    var day by remember { mutableIntStateOf(localDay()) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); day = localDay() }
    }
    // Work once per library/day change, never during scrolling or player position updates.
    val selectionState = produceState(emptyList<YosMediaItem>(), songs, day) {
        value = withContext(Dispatchers.Default) {
            songs.distinctBy { it.uri }.sortedBy { it.uri.toString() }
                .shuffled(Random(day)).take(50)
        }
    }
    // Freeze this composition's value. Lazy item lambdas execute later, so reading the
    // State again inside them can pair a loaded selection with an earlier empty cover list.
    val selection = selectionState.value
    val covers = remember(selection) { selection.distinctBy { it.album to it.artists }.take(5) }
    val daily = remember(selection) { selection.drop(1).ifEmpty { selection } }
    val recent = if (SettingsLibrary.ListenHistory) ListenHistoryManager.recentlyPlayedSongs.value else emptyList()
    val scope = rememberCoroutineScope()
    fun play(song: YosMediaItem, queue: List<YosMediaItem>) {
        scope.launch { MediaController.prepare(song, queue) }
    }
    fun open(title: String, queue: List<YosMediaItem>) {
        LibraryObject.setTargetListWithTitle(title, queue)
        nav.toUI(UI.NormalMusic)
    }
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { nav.toUI(UI.Settings.About) }, shape = CircleShape) {
                Icon(Icons.Rounded.Newspaper, stringResource(R.string.home_music_info))
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = { nav.toUI(UI.Settings.Main) }, shape = CircleShape) {
                Icon(Icons.Rounded.Settings, stringResource(R.string.home_settings))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item("mix-heading") {
                Box(Modifier.fillMaxWidth().heightIn(min = 256.dp).padding(16.dp)) {
                    Column(Modifier.padding(top = 48.dp, start = 12.dp, bottom = 32.dp)) {
                        Text("Your\nMix", fontFamily = FontFamily(Font(R.font.music_home_display)),
                            fontSize = 64.sp, lineHeight = 62.sp, color = c.onBackground)
                        Text(stringResource(R.string.home_mix_subtitle), Modifier.padding(start = 8.dp, top = 10.dp),
                            color = c.onSurfaceVariant, fontSize = 14.sp)
                    }
                    FilledIconButton(onClick = { selection.firstOrNull()?.let { play(it, selection) } },
                        enabled = selection.isNotEmpty(), modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 8.dp).size(68.dp), shape = CircleShape) {
                        Icon(Icons.Rounded.Shuffle, stringResource(R.string.home_play_mix), Modifier.size(36.dp))
                    }
                }
            }
            if (selection.isEmpty()) {
                item("empty") {
                    Text(stringResource(R.string.home_no_songs), Modifier.padding(horizontal = 28.dp), color = c.onSurfaceVariant)
                }
            } else {
                item("collage") { HomeCollage(covers) { play(it, selection) } }
                item("daily-mix") {
                    Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(30.dp))
                        .background(c.surfaceContainer)) {
                        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(c.primary, c.tertiary)))
                            .clickable { open("Daily Mix", daily) }.padding(horizontal = 22.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Daily Mix", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.onPrimary)
                                Text(stringResource(R.string.home_daily_subtitle), fontSize = 13.sp, color = c.onPrimary.copy(alpha = .8f))
                            }
                            daily.take(3).forEachIndexed { i, song ->
                                HomeCover(song, Modifier.size(44.dp).rotate((i - 1) * 12f).clip(CircleShape))
                            }
                        }
                        daily.take(4).forEach { song ->
                            Row(Modifier.fillMaxWidth().clickable { play(song, daily) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                HomeCover(song, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(song.title ?: defaultTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text(song.artistsName ?: defaultArtistsName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = c.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.PlayArrow, null, tint = c.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { open("Daily Mix", daily) }, Modifier.fillMaxWidth().padding(6.dp)) {
                            Text(stringResource(R.string.home_view_mix)); Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.ArrowForward, null)
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item("recent") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.home_recent), Modifier.padding(horizontal = 24.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recent.take(12)) { song ->
                                    Column(Modifier.width(144.dp).clickable { play(song, recent) }) {
                                        HomeCover(song, Modifier.size(144.dp).clip(RoundedCornerShape(24.dp)))
                                        Text(song.title ?: defaultTitle, Modifier.padding(top = 8.dp, start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                        Text(song.artistsName ?: defaultArtistsName, Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                item("overview") { HomeOverview(nav) }
            }
            item("system-inset") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun HomeCollage(songs: List<YosMediaItem>, onPlay: (YosMediaItem) -> Unit) {
    // Resolve before BoxWithConstraints' deferred subcomposition; an empty/loading
    // library must never reach the collage's repeating album positions.
    val slots = homeCollageSlots(songs)
    if (slots.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth().height(400.dp).padding(horizontal = 14.dp)) {
        val unit = minOf(maxWidth, 300.dp)
        fun song(index: Int) = slots[index]
        HomeCover(song(0), Modifier.align(Alignment.TopCenter).offset(y = 4.dp)
            .size(width = unit * .48f, height = unit * .8f).rotate(45f).clip(RoundedCornerShape(50))
            .clickable { onPlay(song(0)) })
        HomeCover(song(1), Modifier.align(Alignment.TopStart).offset(x = 15.dp, y = 20.dp)
            .size(unit * .24f).clip(CircleShape).clickable { onPlay(song(1)) })
        HomeCover(song(2), Modifier.align(Alignment.TopEnd).offset(x = (-15).dp, y = 155.dp)
            .size(unit * .24f).clip(CircleShape).clickable { onPlay(song(2)) })
        HomeCover(song(3), Modifier.align(Alignment.TopStart).offset(x = 30.dp, y = 275.dp)
            .size(unit * .35f).rotate(-20f).clip(RoundedCornerShape(20.dp)).clickable { onPlay(song(3)) })
        HomeCover(song(4), Modifier.align(Alignment.BottomEnd).offset(x = 30.dp, y = 10.dp)
            .size(unit * .72f).clip(HomeFlower).clickable { onPlay(song(4)) })
    }
}

/** Rounded six-lobed silhouette, drawn locally rather than importing reference-app code. */
private val HomeFlower = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val points = (0 until 24).map { i ->
            val a = i * Math.PI / 12 - Math.PI / 2
            val radius = if (i % 4 == 0) .50 else if (i % 4 == 2) .34 else .47
            androidx.compose.ui.geometry.Offset((size.width * (.5 + cos(a) * radius)).toFloat(),
                (size.height * (.5 + sin(a) * radius)).toFloat())
        }
        val path = Path()
        val first = (points.last() + points.first()) / 2f
        path.moveTo(first.x, first.y)
        points.forEachIndexed { i, p ->
            val end = (p + points[(i + 1) % points.size]) / 2f
            path.quadraticBezierTo(p.x, p.y, end.x, end.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
private fun HomeCover(song: YosMediaItem, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(song.thumb).size(512).build(),
            contentDescription = song.album, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun HomeOverview(nav: NavController) {
    val snapshot = rememberStatsSnapshot(StatsPeriod.Today)
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(c.surfaceContainer).clickable { nav.toUI(UI.StatsTracks) }.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.home_overview), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.home_minutes, snapshot.summary.totalListenedMs / 60_000), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.home_plays, snapshot.summary.playCount), color = c.onSurfaceVariant)
            Text(stringResource(R.string.home_albums, snapshot.summary.uniqueAlbumCount), color = c.onSurfaceVariant)
        }
    }
}
