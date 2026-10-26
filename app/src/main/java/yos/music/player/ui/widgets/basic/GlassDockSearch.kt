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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.*
import yos.music.player.ui.theme.isFlamingoInDarkMode

@Composable
internal fun GlassDockSearch(onDismiss: () -> Unit) {
    val songs = MusicLibrary.songs
    var query by remember { mutableStateOf("") }
    val results by produceState(emptyList<YosMediaItem>(), songs, query) {
        delay(150)
        val input = query.trim()
        value = withContext(Dispatchers.Default) {
            songs.asSequence().filter { input.isEmpty() ||
                it.title.orEmpty().contains(input, true) || it.artistsName.orEmpty().contains(input, true) ||
                it.album.orEmpty().contains(input, true) }.distinctBy { it.uri }.take(100).toList()
        }
    }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val dark = isFlamingoInDarkMode()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = if (dark) Color.Black else Color.White,
            contentColor = if (dark) Color.White else Color.Black) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.ArrowBack, "Close search") }
                    OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f).focusRequester(focus),
                        singleLine = true, placeholder = { Text("Search songs, artists, albums") },
                        shape = RoundedCornerShape(28.dp), leadingIcon = { Icon(Icons.Rounded.Search, null) })
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(results, key = { it.uri.toString() }) { song ->
                        ListItem(headlineContent = { Text(song.title ?: defaultTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(song.artistsName ?: defaultArtistsName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable {
                                val queue = results
                                scope.launch { MediaController.prepare(song, queue); onDismiss() }
                            })
                    }
                    if (results.isEmpty()) item { Text(if (query.isBlank()) "No songs in your library" else "No matching songs",
                        Modifier.padding(24.dp)) }
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
