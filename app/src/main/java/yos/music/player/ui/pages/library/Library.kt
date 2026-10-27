package yos.music.player.ui.pages.library

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch
import yos.music.player.R
import yos.music.player.code.ListenHistoryManager
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.*
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.*

@Composable
fun Library(navController: NavController) {
    val haze = remember { HazeState() }
    var search by rememberSaveable { mutableStateOf(false) }
    var create by rememberSaveable { mutableStateOf(false) }
    var shared by rememberSaveable { mutableStateOf(false) }
    val songs = MusicLibrary.songs
    val recent = if (SettingsLibrary.ListenHistory) ListenHistoryManager.recentlyPlayedSongs.value else emptyList()
    val added = remember(songs) { songs.sortedByDescending { it.addDate ?: 0L }.take(24) }
    fun open(title: String, tracks: List<YosMediaItem>) {
        LibraryObject.setTargetListWithTitle(title, tracks)
        navController.toUI(UI.NormalMusic)
    }
    CompositionLocalProvider(LocalGlassBackdrop provides haze) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().haze(haze),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 104.dp, bottom = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item("liked", span = { GridItemSpan(2) }) {
                    Column(Modifier.padding(top = 30.dp, bottom = 16.dp).width(112.dp), horizontalAlignment = Alignment.Start) {
                        Column(Modifier.width(112.dp).clickable { open("Liked Songs", FavPlayListLibrary.favPlayList) },
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(112.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(
                                listOf(Color(0xFFFF2399), Color(0xFFFF6B64), Color(0xFF9557FF), Color(0xFF00DCE5)))),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Favorite, null, Modifier.size(54.dp), tint = Color.White)
                            }
                            Text("Liked Songs", Modifier.padding(top = 8.dp), fontSize = 14.sp)
                        }
                    }
                }
                item("categories", span = { GridItemSpan(2) }) {
                    Column {
                        LibraryRow(Icons.Rounded.QueueMusic, stringResource(R.string.page_library_playlists)) { navController.toUI(UI.PlayLists) }
                        LibraryRow(Icons.Rounded.Mic, stringResource(R.string.page_library_artists)) { navController.toUI(UI.LocalArtists) }
                        LibraryRow(Icons.Rounded.Album, stringResource(R.string.page_library_albums)) { navController.toUI(UI.LocalAlbums) }
                        LibraryRow(Icons.Rounded.MusicNote, stringResource(R.string.page_library_songs)) { open("Songs", songs) }
                        LibraryRow(Icons.Rounded.History, "Recently Played") { open("Recently Played", recent) }
                        LibraryRow(Icons.Rounded.People, "Shared") { shared = true }
                    }
                }
                item("added-title", span = { GridItemSpan(2) }) {
                    Text("Recently Added", Modifier.padding(top = 18.dp, bottom = 2.dp), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                if (added.isEmpty()) item("empty", span = { GridItemSpan(2) }) { Text("Songs you add to your library will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(added, key = { it.uri.toString() }) { song -> LibraryCover(song, added) }
            }
            LibraryHeader(stringResource(R.string.page_library_title), haze,
                onSearch = { search = true }, onCreate = { create = true }, onSettings = { navController.toUI(UI.Settings.Main) })
        }
    }
    if (search) GlassDockSearch { search = false }
    if (create) CreateLibraryPlaylist { create = false }
    if (shared) SharedPlaylists { shared = false }
}

@Composable
private fun LibraryRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp), tint = MusicGlassAccent)
            Text(title, Modifier.padding(start = 18.dp), fontSize = 20.sp)
        }
        HorizontalDivider(Modifier.padding(start = 46.dp), thickness = .5.dp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .18f))
    }
}

@Composable
private fun LibraryHeader(title: String, haze: HazeState, onSearch: (() -> Unit)? = null,
    onCreate: (() -> Unit)? = null, onSettings: () -> Unit) {
    // Toolbar stays outside the blur source: the glass never samples itself.
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Spacer(Modifier.statusBarsPadding())
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            onSearch?.let { LiquidGlassIconButton(Icons.Rounded.Search, "Search library", it, haze = haze) }
            onCreate?.let { LiquidGlassIconButton(Icons.Rounded.PlaylistAdd, "Create playlist", it, haze = haze) }
            LiquidGlassIconButton(Icons.Rounded.MoreVert, "Settings", onSettings, haze = haze)
        }
    }
}

@Composable
internal fun LibraryCover(song: YosMediaItem, queue: List<YosMediaItem>) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().clickable { scope.launch { MediaController.prepare(song, queue) } }) {
        AsyncImage(song.thumb, song.title, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit)
        Text(song.title ?: defaultTitle, Modifier.padding(top = 6.dp), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artistsName ?: defaultArtistsName, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CreateLibraryPlaylist(onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val exists = PlayListLibrary.playList.any { it.name.equals(trimmed, ignoreCase = true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New Playlist") }, text = {
        Column {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Playlist name") }, isError = exists)
            if (exists) Text("A playlist with that name already exists.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = trimmed.isNotEmpty() && !exists,
        onClick = { PlayListLibrary.create(trimmed); onDismiss() }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SharedPlaylists(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val playlists = PlayListLibrary.playList
    val songs = MusicLibrary.songs
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Share a playlist") }, text = {
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            item { Text("Send a playlist's song list to someone. Shared listening rooms require an online service.",
                Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (playlists.isEmpty()) item { Text("Create a playlist to share it here.") }
            items(playlists, key = { it.listID }) { playlist ->
                TextButton(onClick = {
                    val uris = playlist.songDataList.toSet()
                    val tracks = songs.filter { it.uri in uris }
                    val text = playlist.name + "\n\n" + tracks.joinToString("\n") { "${it.title ?: defaultTitle} — ${it.artistsName ?: defaultArtistsName}" }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    }, "Share playlist"))
                }) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(12.dp)); Text(playlist.name) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun NewMusic(navController: NavController) {
    val haze = remember { HazeState() }
    val songs = MusicLibrary.songs
    val added = remember(songs) { songs.sortedByDescending { it.addDate ?: 0L } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().haze(haze),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 136.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item(span = { GridItemSpan(2) }) { Text("Recently Added", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            if (added.isEmpty()) item(span = { GridItemSpan(2) }) { Text("Songs you add to your library will appear here.") }
            items(added, key = { it.uri.toString() }) { LibraryCover(it, added) }
        }
        LibraryHeader("New", haze, onSettings = { navController.toUI(UI.Settings.Main) })
    }
}
