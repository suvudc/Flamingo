package yos.music.player.ui.pages.discovery

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import yos.music.player.code.AudioMetadataUtils
import yos.music.player.data.discovery.DiscoveryEngine
import yos.music.player.data.libraries.*
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.Title
import java.io.File

@Composable
fun LibraryTools(nav: NavController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var exportId by rememberSaveable { mutableStateOf("") }
    var chooseExport by remember { mutableStateOf(false) }
    fun runOperation(block: suspend () -> String) { scope.launch { busy = true; message = runCatching { block() }.getOrElse { it.message ?: "Operation failed." }; busy = false } }
    val importM3u = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) runOperation { DiscoveryActions.importPlaylist(context, uri, "Imported Playlist") } }
    val exportM3u = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) runOperation {
            val playlist = PlayListLibrary.playList.firstOrNull { it.listID == exportId } ?: error("Playlist no longer exists")
            val index = MusicLibrary.songs.associateBy { it.uri }
            DiscoveryActions.exportPlaylist(context, uri, playlist.songDataList.mapNotNull { index[it] }); "Playlist exported."
        }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runOperation {
            val json = DiscoveryActions.backup()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) } ?: error("Cannot write backup") }
            "Backup exported. Your audio files are not copied."
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runOperation {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { DiscoveryActions.readBounded(it) } ?: error("Cannot read backup") }
            withContext(Dispatchers.Default) { DiscoveryActions.restore(text) }
        }
    }
    Title("Library Tools", onBack = { nav.popBackStack() }) {
        item("actions") {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotBlank()) Text(message, Modifier.padding(22.dp), color = MaterialTheme.colorScheme.primary)
            NavigationRow("Edit Tags & Lyrics", "Search for a song, then tap its pencil icon", Icons.Rounded.Edit) { DiscoveryRoutes.open(nav, "search") }
            NavigationRow("Import M3U / M3U8", "Match playlist paths to audio already on this phone", Icons.Rounded.FileDownload) { if (!busy) importM3u.launch(arrayOf("audio/*", "text/*", "application/octet-stream")) }
            NavigationRow("Export a Playlist", "Save a portable M3U file", Icons.Rounded.FileUpload) { if (!busy) chooseExport = true }
            NavigationRow("Export Music Backup", "Playlists, favorites, listening stats, smart rules and Home layout", Icons.Rounded.Backup) { if (!busy) exportBackup.launch("Music-backup.json") }
            NavigationRow("Restore Music Backup", "Merge a backup with your library; existing playlists are kept", Icons.Rounded.Restore) { if (!busy) importBackup.launch(arrayOf("application/json", "text/plain")) }
            NavigationRow("Scan Library", "Refresh local files and metadata", Icons.Rounded.Refresh) { if (!busy) runOperation { MusicLibrary.scanMedia(context); "Library refreshed: ${MusicLibrary.songs.size} songs." } }
            NavigationRow("Folder Filtering", "Choose the folders and short audio included in your library", Icons.Rounded.Folder) { nav.toUI(UI.Settings.LibraryOverview) }
            NavigationRow("Artist Parsing", "Set separators and artist-name exceptions", Icons.Rounded.Person) { nav.toUI(UI.Settings.ArtistSplit) }
            NavigationRow("Review Duplicate Titles", "Review possible duplicates without deleting files", Icons.Rounded.ContentCopy) {
                val groups = MusicLibrary.songs.groupBy { "${DiscoveryEngine.normalize(it.title.orEmpty())}|${DiscoveryEngine.normalize(it.artistsName.orEmpty())}" }
                DiscoveryRoutes.collection(nav, "Possible Duplicates", groups.values.filter { it.size > 1 }.flatten())
            }
            NavigationRow("Missing Metadata", "Tracks without a title, artist or album", Icons.Rounded.Label) {
                DiscoveryRoutes.collection(nav, "Missing Metadata", MusicLibrary.songs.filter { it.title.isNullOrBlank() || it.artists.isNullOrBlank() || it.album.isNullOrBlank() })
            }
        }
    }
    if (chooseExport) AlertDialog(onDismissRequest = { chooseExport = false }, title = { Text("Export playlist") }, text = {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(PlayListLibrary.playList.size) { index ->
                val playlist = PlayListLibrary.playList[index]
                TextButton({ exportId = playlist.listID; chooseExport = false; exportM3u.launch("${playlist.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.m3u") }) { Text(playlist.name) }
            }
            if (PlayListLibrary.playList.isEmpty()) item { Text("Create or save a playlist first.") }
        }
    }, confirmButton = { TextButton({ chooseExport = false }) { Text("Close") } })
}

private fun editableMediaUri(context: Context, song: YosMediaItem): Uri {
    val uri = song.uri ?: error("Missing audio location")
    if (uri.scheme == "content") return uri
    val path = AudioMetadataUtils.resolveLocalAudioFilePath(context, uri) ?: error("Cannot locate audio file")
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    return context.contentResolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), "${MediaStore.Audio.Media.DATA} = ?", arrayOf(path), null)?.use { cursor ->
        if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
    } ?: Uri.fromFile(File(path))
}

@Composable
fun LocalTrackEditor(nav: NavController, id: String) {
    val song = MusicLibrary.songs.firstOrNull { it.uri.toString() == id }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    if (song == null) { Title("Track Details", onBack = { nav.popBackStack() }) { item { Text("This song is no longer in the library.", Modifier.padding(22.dp)) } }; return }
    var title by rememberSaveable(id) { mutableStateOf(song.title.orEmpty()) }; var artist by rememberSaveable(id) { mutableStateOf(song.artists.orEmpty()) }
    var album by rememberSaveable(id) { mutableStateOf(song.album.orEmpty()) }; var albumArtist by rememberSaveable(id) { mutableStateOf(song.albumArtists.orEmpty()) }
    var genre by rememberSaveable(id) { mutableStateOf(song.genre.orEmpty()) }; var year by rememberSaveable(id) { mutableStateOf((song.releaseYear ?: song.recordingYear)?.toString().orEmpty()) }
    var lyrics by rememberSaveable(id) { mutableStateOf("") }; var originalLyrics by remember(id) { mutableStateOf("") }
    var loaded by remember(id) { mutableStateOf(false) }; var saving by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }; var pendingValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    suspend fun save(uri: Uri, values: Map<String, String>): String {
        withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                val metadata = TagLib.getMetadata(descriptor.dup().detachFd(), false) ?: error("This audio format cannot be edited.")
                val properties = metadata.propertyMap.toMutableMap()
                values.forEach { (key, value) -> if (value.isBlank()) properties.remove(key) else properties[key] = arrayOf(value) }
                check(TagLib.savePropertyMap(descriptor.dup().detachFd(), properties)) { "The audio format rejected these changes." }
            } ?: error("Cannot open audio file for writing.")
            val path = AudioMetadataUtils.resolveLocalAudioFilePath(context, song.uri)
            if (path != null) suspendCancellableCoroutine<Unit> { continuation ->
                MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> if (continuation.isActive) continuation.resume(Unit) }
            }
            MusicLibrary.scanMedia(context)
        }
        return "Saved tags and embedded lyrics. Reopen the song to reload its lyrics."
    }
    val grantWrite = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && pendingUri != null) scope.launch {
            saving = true; message = runCatching { save(pendingUri!!, pendingValues) }.getOrElse { it.message ?: "Could not save changes." }; saving = false
        } else { saving = false; message = "Write permission was not granted." }
    }
    LaunchedEffect(id) {
        message = runCatching {
            originalLyrics = withContext(Dispatchers.IO) { context.contentResolver.openFileDescriptor(editableMediaUri(context, song), "r")?.use { TagLib.getMetadata(it.dup().detachFd(), false)?.propertyMap?.get("LYRICS")?.firstOrNull().orEmpty() }.orEmpty() }
            lyrics = originalLyrics; ""
        }.getOrElse { "Metadata is shown below. Embedded lyrics could not be read: ${it.message}" }
        loaded = true
    }
    val importLyrics = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch {
        message = runCatching { lyrics = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { DiscoveryActions.readBounded(it, 512 * 1024) } ?: error("Cannot read lyrics") }; "Lyrics loaded. Tap Save to embed them." }.getOrElse { it.message.orEmpty() }
    } }
    val exportLyrics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> if (uri != null) scope.launch {
        message = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(lyrics) } ?: error("Cannot export lyrics") }; "Lyrics exported." }.getOrElse { it.message.orEmpty() }
    } }
    Title("Tags & Lyrics", onBack = { nav.popBackStack() }) {
        item("editor") { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Changes are saved to this audio file only when you tap Save.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            DiscoveryField("Title", title) { title = it }; DiscoveryField("Artist", artist) { artist = it }; DiscoveryField("Album", album) { album = it }
            DiscoveryField("Album artist", albumArtist) { albumArtist = it }; DiscoveryField("Genre", genre) { genre = it }; DiscoveryField("Year", year) { year = it }
            Text("Embedded Lyrics", fontWeight = FontWeight.Bold)
            OutlinedTextField(lyrics, { lyrics = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp), label = { Text("Plain text or timestamped LRC") })
            Row { TextButton({ importLyrics.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Import LRC") }; TextButton({ exportLyrics.launch("${title.ifBlank { "Lyrics" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.lrc") }) { Text("Export LRC") } }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            if (saving || !loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button({
                if (title.isBlank() || (year.isNotBlank() && (year.toIntOrNull() ?: -1) !in 1..9999)) { message = "Enter a title and a valid year (or leave year blank)."; return@Button }
                if (yos.music.player.code.MediaController.musicPlaying.value?.uri == song.uri && yos.music.player.code.MediaController.mediaControl?.isPlaying == true) {
                    message = "Pause this song before saving changes to its file."; return@Button
                }
                val values = buildMap {
                    if (title != song.title.orEmpty()) put("TITLE", title.trim())
                    if (artist != song.artists.orEmpty()) put("ARTIST", artist.trim())
                    if (album != song.album.orEmpty()) put("ALBUM", album.trim())
                    if (albumArtist != song.albumArtists.orEmpty()) put("ALBUMARTIST", albumArtist.trim())
                    if (genre != song.genre.orEmpty()) put("GENRE", genre.trim())
                    if (year != (song.releaseYear ?: song.recordingYear)?.toString().orEmpty()) put("DATE", year.trim())
                    if (lyrics != originalLyrics) put("LYRICS", lyrics)
                }
                if (values.isEmpty()) { message = "No changes to save."; return@Button }
                scope.launch {
                    saving = true
                    try { val uri = withContext(Dispatchers.IO) { editableMediaUri(context, song) }; pendingUri = uri; pendingValues = values; message = save(uri, values); saving = false }
                    catch (security: SecurityException) {
                        try {
                            val sender = if (Build.VERSION.SDK_INT >= 30 && pendingUri?.scheme == "content") MediaStore.createWriteRequest(context.contentResolver, listOf(pendingUri!!)).intentSender
                                else if (Build.VERSION.SDK_INT == 29 && security is android.app.RecoverableSecurityException) security.userAction.actionIntent.intentSender
                                else throw security
                            grantWrite.launch(IntentSenderRequest.Builder(sender).build())
                        } catch (failure: Exception) { message = "Cannot obtain write access: ${failure.message}"; saving = false }
                    } catch (failure: Exception) { message = failure.message ?: "Could not save changes."; saving = false }
                }
            }, Modifier.fillMaxWidth(), enabled = loaded && !saving) { Text("Save to audio file") }
        } }
    }
}
