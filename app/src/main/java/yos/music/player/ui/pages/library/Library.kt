package yos.music.player.ui.pages.library

import yos.music.player.ui.widgets.basic.HapticTextButton as TextButton
import yos.music.player.ui.widgets.basic.hapticClickable as clickable
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.code.ListenHistoryManager
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.*
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.pages.MusicCollectionCover
import yos.music.player.ui.pages.MusicScreenHeader
import yos.music.player.ui.widgets.basic.GlassDockSearch
import yos.music.player.ui.widgets.basic.MusicGlassButton

@Composable
fun Library(navController: NavController) {
    val songs = MusicLibrary.songs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val recent by produceState(emptyList<YosMediaItem>(), songs) {
        value = withContext(Dispatchers.Default) { songs.sortedByDescending { it.addDate ?: 0L }.take(12) }
    }
    fun open(title: String, queue: List<YosMediaItem>) {
        LibraryObject.setTargetListWithTitle(title, queue); navController.toUI(UI.NormalMusic)
    }
    val accent = Color(0xFFFF0065)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 170.dp)) {
        item("header") {
            MusicScreenHeader("Library", navController) {
                MusicGlassButton(Icons.Rounded.Search, "Search songs", onClick = { search = true })
                MusicGlassButton(Icons.Rounded.PlaylistAdd, "Create playlist", onClick = { name = ""; create = true })
            }
        }
        item("liked") {
            Column(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 28.dp).width(132.dp)
                .clickable { open("Liked Songs", FavPlayListLibrary.favPlayList) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(132.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF267F), Color(0xFFAD4BFD), Color(0xFF00D8ED)))),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
                Text("Liked Songs", Modifier.padding(top = 8.dp), fontSize = 15.sp)
            }
        }
        item("links") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                LibraryLink(Icons.Rounded.QueueMusic, "Playlists", accent) { navController.toUI(UI.PlayLists) }
                LibraryLink(Icons.Rounded.Mic, "Artists", accent) { navController.toUI(UI.LocalArtists) }
                LibraryLink(Icons.Rounded.Album, "Albums", accent) { navController.toUI(UI.LocalAlbums) }
                LibraryLink(Icons.Rounded.MusicNote, "Songs", accent) { open("Songs", songs) }
                LibraryLink(Icons.Rounded.History, "Recently Played", accent) {
                    open("Recently Played", ListenHistoryManager.recentlyPlayedSongs.value)
                }
                LibraryLink(Icons.Rounded.People, "Shared", accent) { sharing = true }
            }
        }
        item("recent-heading") {
            Text("Recently Added", Modifier.padding(start = 20.dp, top = 34.dp, bottom = 18.dp),
                fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        if (recent.isEmpty()) item("empty") { Text("Songs you add will appear here.", Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        recent.chunked(2).forEachIndexed { index, row ->
            item("recent-$index") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { song ->
                        Column(Modifier.weight(1f).clickable { scope.launch { MediaController.prepare(song, recent) } }) {
                            MusicCollectionCover(song, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
                            Text(song.title ?: defaultTitle, Modifier.padding(top = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artistsName ?: defaultArtistsName, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (search) GlassDockSearch { search = false }
    if (create) {
        val valid = name.trim().isNotEmpty() && PlayListLibrary.playList.none { it.name.equals(name.trim(), true) }
        AlertDialog(onDismissRequest = { create = false }, title = { Text("New Playlist") },
            text = { OutlinedTextField(name, { name = it.take(100) }, singleLine = true, label = { Text("Playlist name") },
                isError = name.isNotBlank() && !valid) },
            confirmButton = { TextButton(enabled = valid, onClick = {
                PlayListLibrary.create(name.trim()); create = false; navController.toUI(UI.PlayLists)
            }) { Text("Create") } }, dismissButton = { TextButton(onClick = { create = false }) { Text("Cancel") } })
    }
    if (sharing) AlertDialog(onDismissRequest = { sharing = false }, title = { Text("Shared playlists") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("Choose a playlist to share its track list.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (PlayListLibrary.playList.isEmpty()) Text("Create a playlist to share it.", Modifier.padding(top = 16.dp))
                PlayListLibrary.playList.forEach { playlist ->
                    TextButton(onClick = {
                        val lookup = songs.associateBy { it.uri }
                        val text = buildString {
                            appendLine(playlist.name)
                            playlist.songDataList.mapNotNull { lookup[it] }.forEach { song ->
                                appendLine("${song.title.orEmpty()} — ${song.artistsName.orEmpty()}")
                            }
                        }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, playlist.name); putExtra(Intent.EXTRA_TEXT, text)
                        }, "Share playlist"))
                    }) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text(playlist.name) }
                }
            }
        }, confirmButton = { TextButton(onClick = { sharing = false }) { Text("Done") } })
}

@Composable
private fun LibraryLink(icon: ImageVector, title: String, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.padding(start = 7.dp).size(29.dp))
        Text(title, Modifier.padding(start = 18.dp), fontSize = 21.sp)
    }
    HorizontalDivider(Modifier.padding(start = 54.dp), thickness = .5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f))
}
