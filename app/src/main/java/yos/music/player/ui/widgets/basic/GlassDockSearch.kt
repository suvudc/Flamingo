package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import yos.music.player.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.*
import yos.music.player.ui.theme.isFlamingoInDarkMode

private data class LibrarySearchResult(
    val query: String,
    val source: List<YosMediaItem>,
    val songs: List<YosMediaItem>
)

@Composable
internal fun GlassDockSearch(onDismiss: () -> Unit) {
    val songs = MusicLibrary.songs
    var query by remember { mutableStateOf("") }
    val input = query.trim()
    val search by produceState<LibrarySearchResult?>(null, songs, input) {
        if (input.isNotEmpty()) delay(150)
        val matching = withContext(Dispatchers.Default) {
            songs.asSequence().filter { input.isEmpty() ||
                it.title.orEmpty().contains(input, true) || it.artistsName.orEmpty().contains(input, true) ||
                it.album.orEmpty().contains(input, true) }.distinctBy { it.uri }.take(100).toList()
        }
        value = LibrarySearchResult(input, songs, matching)
    }
    // produceState retains its previous value until the replacement search completes.
    // Do not let the previous query's rows stay visible or clickable in that interval.
    val currentSearch = search?.takeIf { it.query == input && it.source == songs }
    val searching = currentSearch == null
    val results = currentSearch?.songs.orEmpty()
    var preparing by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val dark = isFlamingoInDarkMode()
    val context = LocalContext.current
    Dialog(onDismissRequest = { if (!preparing) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = if (dark) Color.Black else Color.White,
            contentColor = if (dark) Color.White else Color.Black) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    IconButton(onClick = onDismiss, enabled = !preparing) { Icon(Icons.Rounded.ArrowBack, "Close search") }
                    OutlinedTextField(query, { query = it; playbackError = null }, enabled = !preparing, modifier = Modifier.weight(1f).focusRequester(focus),
                        singleLine = true, placeholder = { Text("Search songs, artists, albums") },
                        shape = RoundedCornerShape(28.dp), leadingIcon = { Icon(Icons.Rounded.Search, null) })
                }
                if (preparing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                LazyColumn(Modifier.weight(1f)) {
                    items(results, key = { it.uri.toString() }) { song ->
                        ListItem(leadingContent = {
                            val artwork = remember(context, song.thumb) {
                                ImageRequest.Builder(context).data(song.thumb).size(128)
                                    .placeholder(R.drawable.placeholder_music_default_artwork)
                                    .error(R.drawable.placeholder_music_default_artwork)
                                    .fallback(R.drawable.placeholder_music_default_artwork).build()
                            }
                            AsyncImage(artwork, contentDescription = "Album cover for ${song.title ?: defaultTitle}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(7.dp)))
                        }, headlineContent = { Text(song.title ?: defaultTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(song.artistsName ?: defaultArtistsName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable(enabled = !preparing && !searching) {
                                if (!preparing) {
                                    val queue = results
                                    preparing = true
                                    playbackError = null
                                    scope.launch {
                                        try {
                                            MediaController.prepare(song, queue)
                                            onDismiss()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            playbackError = "Could not play this song. Please try again."
                                        } finally {
                                            preparing = false
                                        }
                                    }
                                }
                            })
                    }
                    if (results.isEmpty()) item { Text(if (searching) "Searching…" else if (input.isEmpty()) "No songs in your library" else "No matching songs",
                        Modifier.padding(24.dp)) }
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
