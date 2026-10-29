package yos.music.player.ui.pages

import yos.music.player.ui.widgets.basic.hapticClickable as clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.code.ListenHistoryManager
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.*
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.MusicGlassButton
import java.util.Calendar
import kotlin.random.Random

@Composable
fun MusicScreenHeader(title: String, nav: NavController, actions: @Composable RowScope.() -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
        Box {
            MusicGlassButton(Icons.Rounded.MoreVert, "Settings and more", onClick = { menu = true })
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; nav.toUI(UI.Settings.Main) })
                DropdownMenuItem(text = { Text("Listening stats") }, onClick = { menu = false; nav.toUI("MusicStats") })
                DropdownMenuItem(text = { Text("About Music") }, onClick = { menu = false; nav.toUI(UI.Settings.About) })
            }
        }
    }
}

@Composable
fun OctaveStyleHome(nav: NavController, newMusic: Boolean = false) {
    val songs = MusicLibrary.songs
    val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val picks by produceState(emptyList<YosMediaItem>(), songs, day) {
        value = withContext(Dispatchers.Default) { songs.distinctBy { it.uri }.shuffled(Random(day)).take(40) }
    }
    val newest by produceState(emptyList<YosMediaItem>(), songs) {
        value = withContext(Dispatchers.Default) { songs.sortedByDescending { it.addDate ?: 0L }.take(40) }
    }
    val recent = if (SettingsLibrary.ListenHistory) ListenHistoryManager.recentlyPlayedSongs.value else emptyList()
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    fun open(title: String, queue: List<YosMediaItem>) {
        LibraryObject.setTargetListWithTitle(title, queue); nav.toUI(UI.NormalMusic)
    }
    fun play(song: YosMediaItem, queue: List<YosMediaItem>) { scope.launch { MediaController.prepare(song, queue) } }
    LazyColumn(Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 170.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item("header") { MusicScreenHeader(if (newMusic) "New" else "Home", nav) }
        item("scope") {
            Text(if (newMusic) "New in your library" else "Your music, picked for you",
                Modifier.padding(horizontal = 20.dp), color = c.onSurfaceVariant, fontSize = 14.sp)
        }
        val featured = if (newMusic) newest else picks
        if (songs.isEmpty()) item("empty") { Text("Your music will appear here after the library scan.", Modifier.padding(20.dp)) }
        if (featured.isNotEmpty()) {
            item("featured") {
                MusicShelf(if (newMusic) "Recently Added" else "Top Picks for You", featured.take(6), large = true) {
                    play(it, featured)
                }
            }
            if (recent.isNotEmpty()) item("recent") {
                MusicShelf("Recently Played", recent.take(12)) { play(it, recent) }
            }
            item("mix") {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Made for You", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MixTile("Your Mix", Color(0xFFFF1666), Modifier.weight(1f)) { open("Your Mix", picks) }
                        MixTile("Liked Songs", Color(0xFF7A52FC), Modifier.weight(1f)) {
                            open("Liked Songs", FavPlayListLibrary.favPlayList)
                        }
                    }
                }
            }
            item("artists") {
                MusicShelf("From Your Artists", featured.distinctBy { it.artists }.take(10)) { song ->
                    open(song.artistsName ?: "Artist", songs.filter { it.artists == song.artists })
                }
            }
            if (!newMusic && newest.isNotEmpty()) item("new") {
                MusicShelf("Recently Added", newest.take(12)) { play(it, newest) }
            }
        }
    }
}

@Composable
private fun MixTile(title: String, colour: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(138.dp).clip(RoundedCornerShape(18.dp))
        .background(Brush.linearGradient(listOf(colour, Color(0xFF191926)))).clickable(onClick = onClick).padding(16.dp)) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White.copy(alpha = .75f), modifier = Modifier.size(36.dp))
        Text(title, Modifier.align(Alignment.BottomStart), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MusicShelf(title: String, songs: List<YosMediaItem>, large: Boolean = false, onClick: (YosMediaItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(songs) { song ->
                Column(Modifier.width(if (large) 232.dp else 144.dp).clickable { onClick(song) }) {
                    MusicCollectionCover(song, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(if (large) 18.dp else 10.dp)))
                    Text(song.title ?: defaultTitle, Modifier.padding(top = 8.dp), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = if (large) 17.sp else 14.sp)
                    Text(song.artistsName ?: defaultArtistsName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun MusicCollectionCover(song: YosMediaItem, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(context, song.thumb) { ImageRequest.Builder(context).data(song.thumb).size(512)
        .crossfade(false).error(yos.music.player.R.drawable.placeholder_music_default_artwork)
        .fallback(yos.music.player.R.drawable.placeholder_music_default_artwork).build() }
    AsyncImage(request, null, modifier.background(Color(0xFF19191C)), contentScale = ContentScale.Fit)
}
