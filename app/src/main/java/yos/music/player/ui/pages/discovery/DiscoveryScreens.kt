package yos.music.player.ui.pages.discovery

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.ListenHistoryManager
import yos.music.player.code.MediaController
import yos.music.player.code.ListenStatsManager
import yos.music.player.data.discovery.*
import yos.music.player.data.libraries.*
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.pages.library.MusicList
import yos.music.player.ui.pages.rememberStatsSnapshot
import yos.music.player.ui.widgets.basic.ProfileButton
import yos.music.player.ui.widgets.basic.Title
import java.util.Calendar
import java.util.UUID
import kotlin.math.roundToInt

object DiscoveryRoutes {
    const val pattern = "Discovery/{page}?key={key}"
    fun open(nav: NavController, page: String, key: String = "") = nav.navigate("Discovery/$page?key=${Uri.encode(key)}")
    fun collection(nav: NavController, title: String, songs: List<YosMediaItem>) {
        LibraryObject.setTargetListWithTitle(title, songs)
        nav.toUI(UI.NormalMusic)
    }
}

@Composable
internal fun rememberDiscoveryMixes(): List<DiscoveryMix> {
    val songs = MusicLibrary.songs
    val favorites = FavPlayListLibrary.favPlayList
    val revision = DiscoveryPreferences.mixRevision
    val mixSize = DiscoveryPreferences.mixSize
    val excluded = DiscoveryPreferences.excludedJson
    val historyReady = ListenStatsManager.statsEvents.value.isNotEmpty()
    var day by remember { mutableLongStateOf(DiscoveryLibrary.day()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); day = DiscoveryLibrary.day() } }
    return produceState(emptyList<DiscoveryMix>(), songs, favorites, revision, mixSize, excluded, day, historyReady) {
        value = withContext(Dispatchers.Default) { DiscoveryLibrary.mixes(songs) }
    }.value
}

@Composable
private fun rememberDiscoveryFolders(): Map<String, List<YosMediaItem>> {
    val context = LocalContext.current
    val songs = MusicLibrary.songs
    return produceState(emptyMap<String, List<YosMediaItem>>(), songs) { value = DiscoveryFolders.groups(context.applicationContext, songs) }.value
}

@Composable
fun DiscoveryHome(nav: NavController) {
    val songs = MusicLibrary.songs
    val mixes = rememberDiscoveryMixes()
    val sections = DiscoveryPreferences.sections()
    val recents = ListenHistoryManager.recentlyPlayedSongs.value
    val stats = rememberStatsSnapshot(StatsPeriod.Today)
    val scope = rememberCoroutineScope()
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    val favorites = FavPlayListLibrary.favPlayList
    val currentSongsByUri = remember(songs) { songs.associateBy { it.uri.toString() } }
    val recentSongs = remember(recents, currentSongsByUri) { recents.mapNotNull { currentSongsByUri[it.uri.toString()] }.distinctBy { it.uri } }
    Title(title = "Home", rightIconContent = {
        ProfileButton { nav.toUI(UI.Settings.Main) }
    }) {
        item("greeting") {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 10.dp)) {
                Text(greeting, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                Text("Made for your music", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${songs.size} songs · ${favorites.size} favorites", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("shortcuts") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Search" to "search", "Mixes" to "mixes", "Smart Playlists" to "smart", "Folders" to "folders", "Genres" to "genres", "Tools" to "tools", "Customize" to "home-options")) { (label, route) ->
                    AssistChip(onClick = { DiscoveryRoutes.open(nav, route) }, label = { Text(label) })
                }
            }
        }
        if (songs.isEmpty()) item("empty") { EmptyLibrary(nav) }
        sections.forEach { section ->
            when (section) {
                "mix" -> if (songs.isNotEmpty()) item("your-mix") {
                    val mix = mixes.firstOrNull()
                    SectionHeading("Your Mix", "Refresh") { DiscoveryPreferences.mixRevision++ }
                    if (mix == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 22.dp))
                    else MixCard(mix, mix.trackIds.mapNotNull { currentSongsByUri[it] }, Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { DiscoveryRoutes.open(nav, "mix", mix.id) }
                }
                "daily" -> if (mixes.size > 1) item("daily-mixes") {
                    SectionHeading("Made for You", "See all") { DiscoveryRoutes.open(nav, "mixes") }
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(mixes.drop(1), key = { it.id }) { mix ->
                            MixCard(mix, mix.trackIds.mapNotNull { currentSongsByUri[it] }, Modifier.width(230.dp)) { DiscoveryRoutes.open(nav, "mix", mix.id) }
                        }
                    }
                }
                "recent" -> if (SettingsLibrary.ListenHistory && recentSongs.isNotEmpty()) item("recent") {
                    SectionHeading("Recently Played", "See all") { DiscoveryRoutes.open(nav, "history") }
                    SongCarousel(recentSongs.take(12)) { song -> scope.launch { MediaController.prepare(song, recentSongs) } }
                }
                "added" -> if (songs.isNotEmpty()) item("added") {
                    val added = remember(songs) { songs.sortedByDescending { it.addDate ?: 0L }.take(50) }
                    SectionHeading("Recently Added", "See all") { DiscoveryRoutes.collection(nav, "Recently Added", added) }
                    SongCarousel(added.take(12)) { song -> scope.launch { MediaController.prepare(song, added) } }
                }
                "albums" -> if (songs.isNotEmpty()) item("albums") {
                    val albums = remember(songs) { songs.groupBy { it.album.orEmpty() }.entries.filter { it.key.isNotBlank() }.sortedByDescending { it.value.maxOf { song -> song.addDate ?: 0 } }.take(12) }
                    SectionHeading("Your Albums", "See all") { nav.toUI(UI.LocalAlbums) }
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(albums, key = { it.key }) { entry -> ArtworkTile(entry.key, entry.value.first().albumArtists ?: entry.value.first().artistsName.orEmpty(), entry.value.first().thumb) {
                            LibraryObject.setTargetAlbumName(entry.key); nav.toUI(UI.AlbumInfo)
                        } }
                    }
                }
                "artists" -> if (songs.isNotEmpty()) item("artists") {
                    val followed = SettingsLibrary.FollowedArtists.toSet()
                    val artists = remember(songs, favorites, followed) {
                        val favoriteArtists = favorites.flatMap { it.artistsList.orEmpty() }.groupingBy { it }.eachCount()
                        songs.flatMap { song -> song.artistsList.orEmpty().map { it to song } }.groupBy({ it.first }, { it.second }).entries
                            .sortedByDescending { (favoriteArtists[it.key] ?: 0) + if (it.key in followed) 10 else 0 }.take(12)
                    }
                    SectionHeading("Your Artists", "See all") { nav.toUI(UI.LocalArtists) }
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(artists, key = { it.key }) { entry -> ArtworkTile(entry.key, "${entry.value.size} songs", entry.value.first().thumb) { LibraryObject.setTargetArtistName(entry.key); nav.toUI(UI.ArtistInfo) } }
                    }
                }
                "stats" -> item("stats") {
                    SectionHeading("Listening Today") {}
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable {
                        SettingsLibrary.StatsSelectedPeriod = StatsPeriod.Today.ordinal; nav.toUI(UI.StatsTracks)
                    }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatNumber("${stats.summary.totalListenedMs / 60_000}", "minutes")
                            StatNumber("${stats.summary.playCount}", "plays")
                            StatNumber("${stats.summary.uniqueAlbumCount}", "albums")
                        }
                    }
                }
            }
        }
        item("bottom-space") { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StatNumber(value: String, label: String) = Column {
    Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SectionHeading(title: String, action: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 18.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick) { Text(action) }
    }
}

@Composable
private fun MixCard(mix: DiscoveryMix, songs: List<YosMediaItem>, modifier: Modifier, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    Box(modifier.height(242.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxSize()) { for (column in 0..1) Column(Modifier.weight(1f)) { for (row in 0..1) {
            AsyncImage(songs.getOrNull(column * 2 + row)?.thumb ?: songs.firstOrNull()?.thumb ?: R.drawable.placeholder_music_default_artwork, null, Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Crop)
        } } }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .05f), Color.Black.copy(alpha = .85f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp).padding(end = 40.dp)) {
            Text(mix.title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(mix.subtitle, color = Color.White.copy(alpha = .80f), fontSize = 13.sp, maxLines = 2)
            Text("${songs.size} songs · saved on your phone", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
        }
        FilledIconButton(onClick = { songs.firstOrNull()?.let { first -> scope.launch { MediaController.prepare(first, songs) } } }, enabled = songs.isNotEmpty(), modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp)) { Icon(Icons.Rounded.PlayArrow, "Play ${mix.title}") }
    }
}

@Composable
private fun SongCarousel(songs: List<YosMediaItem>, onPlay: (YosMediaItem) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(songs, key = { it.uri.toString() }) { song -> ArtworkTile(song.title ?: defaultTitle, song.artistsName ?: defaultArtistsName, song.thumb) { onPlay(song) } }
    }
}

@Composable
private fun ArtworkTile(title: String, subtitle: String, uri: Uri?, onClick: () -> Unit) {
    Column(Modifier.width(142.dp).clickable(onClick = onClick)) {
        AsyncImage(uri ?: R.drawable.placeholder_music_default_artwork, null, Modifier.size(142.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
        Text(title, Modifier.padding(top = 7.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun NavigationRow(title: String, subtitle: String = "", icon: ImageVector = Icons.Rounded.MusicNote, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 2)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyLibrary(nav: NavController) {
    Column(Modifier.padding(22.dp)) {
        Text("Your music starts here", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Add audio files to your phone, then scan your library. Mixes will use your local songs and listening history.")
        TextButton({ nav.toUI(UI.Settings.LibraryOverview) }) { Text("Open library settings") }
    }
}

@Composable
fun DiscoveryPage(nav: NavController, page: String, key: String) {
    when (page) {
        "search" -> GlobalSearch(nav)
        "home-options" -> HomeOptions(nav)
        "smart" -> SmartPlaylists(nav)
        "smart-edit" -> SmartEditor(nav, key)
        "tools" -> LibraryTools(nav)
        "editor" -> LocalTrackEditor(nav, key)
        "history" -> ListeningHistory(nav)
        "mix", "mixes" -> MixBrowser(nav, if (page == "mix") key else "")
        else -> CollectionBrowser(nav, page, key)
    }
}

@Composable
private fun MixBrowser(nav: NavController, id: String) {
    val songs = MusicLibrary.songs; val mixes = rememberDiscoveryMixes()
    val songsByUri = remember(songs) { songs.associateBy { it.uri.toString() } }
    val mix = mixes.firstOrNull { it.id == id }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    Title(title = if (id.isBlank()) "Made for You" else mix?.title ?: "Your Mix", onBack = { nav.popBackStack() }, rightIcon = Icons.Rounded.Refresh, onRightIcon = { DiscoveryPreferences.mixRevision++ }) {
        if (id.isBlank()) items(mixes, key = { it.id }) { item ->
            MixCard(item, item.trackIds.mapNotNull { songsByUri[it] }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { DiscoveryRoutes.open(nav, "mix", item.id) }
        } else if (mix != null) {
            val queue = mix.trackIds.mapNotNull { songsByUri[it] }
            item("actions") {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text(mix.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ queue.firstOrNull()?.let { scope.launch { MediaController.prepare(it, queue) } } }, enabled = queue.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text("Play") }
                        OutlinedButton({ queue.firstOrNull()?.let { scope.launch { MediaController.prepare(it, queue, shuffleModeEnabled = true) } } }, enabled = queue.isNotEmpty()) { Text("Shuffle") }
                        TextButton({ val name = DiscoveryActions.savePlaylist(mix.title, queue); Toast.makeText(context, "Saved $name", Toast.LENGTH_SHORT).show() }) { Text("Save") }
                    }
                    Text("Selections refresh daily. Refresh to make a new mix now.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(queue, key = { index, song -> "$index:${song.uri}" }) { _, song ->
                MusicList(song, navController = nav, trailingContent = {
                    IconButton({ DiscoveryPreferences.exclude(song.uri.toString()) }) { Icon(Icons.Rounded.RemoveCircleOutline, "Exclude ${song.title} from mixes", Modifier.size(20.dp)) }
                }) { scope.launch { MediaController.prepare(song, queue) } }
            }
        } else item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(22.dp)) }
    }
}

@Composable
private fun HomeOptions(nav: NavController) {
    val order = (DiscoveryPreferences.sectionOrder.split(',') + DiscoveryPreferences.sectionTitles.keys).distinct().filter { it in DiscoveryPreferences.sectionTitles }
    val hidden = DiscoveryPreferences.hiddenSections.split(',').filter { it.isNotBlank() }.toSet()
    var mixSize by remember(DiscoveryPreferences.mixSize) { mutableFloatStateOf(DiscoveryPreferences.mixSize.toFloat()) }
    Title("Customize Home", onBack = { nav.popBackStack() }) {
        item("intro") { Text("Choose and arrange your Home sections.", Modifier.padding(22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(order, key = { _, id -> id }) { index, id ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(id !in hidden, { enabled -> DiscoveryPreferences.hiddenSections = (if (enabled) hidden - id else hidden + id).joinToString(",") })
                Text(DiscoveryPreferences.sectionTitles.getValue(id), Modifier.weight(1f).padding(10.dp))
                IconButton({ val changed = order.toMutableList(); java.util.Collections.swap(changed, index, index - 1); DiscoveryPreferences.sectionOrder = changed.joinToString(",") }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                IconButton({ val changed = order.toMutableList(); java.util.Collections.swap(changed, index, index + 1); DiscoveryPreferences.sectionOrder = changed.joinToString(",") }, enabled = index < order.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
            }
        }
        item("mix-size") {
            Column(Modifier.padding(22.dp)) {
                Text("Songs per Mix: ${mixSize.roundToInt()}", fontWeight = FontWeight.SemiBold)
                Slider(value = mixSize, onValueChange = { mixSize = it }, valueRange = 10f..100f, steps = 8,
                    onValueChangeFinished = { DiscoveryPreferences.mixSize = mixSize.roundToInt(); DiscoveryPreferences.mixRevision++ })
            }
        }
        item("reset") {
            TextButton({ DiscoveryPreferences.sectionOrder = DiscoveryPreferences.sectionTitles.keys.joinToString(","); DiscoveryPreferences.hiddenSections = "" }, Modifier.padding(horizontal = 16.dp)) { Text("Reset Home layout") }
            TextButton({ DiscoveryPreferences.excludedJson = "[]"; DiscoveryPreferences.mixRevision++ }, Modifier.padding(horizontal = 16.dp)) { Text("Restore excluded mix songs") }
        }
    }
}

@Composable
private fun CollectionBrowser(nav: NavController, page: String, folderPath: String) {
    val songs = MusicLibrary.songs
    val folders = if (page == "folders") rememberDiscoveryFolders() else emptyMap()
    val root = remember(folders.keys) { DiscoveryEngine.commonFolderRoot(folders.keys.toList()) }
    val current = folderPath.ifBlank { root }
    val inFolder = remember(folders, current) { folders.filterKeys { it == current || it.startsWith("${current.trimEnd('/')}/") }.values.flatten().distinctBy { it.uri } }
    val children = remember(folders, current) { DiscoveryEngine.folderChildren(folders.keys.toList(), current) }
    val groups = remember(songs, page) {
        when (page) {
            "genres" -> songs.groupBy { it.genre?.takeIf { it.isNotBlank() } ?: "Unknown Genre" }
            "years" -> songs.groupBy { DiscoveryLibrary.year(it).takeIf { it > 0 }?.toString() ?: "Unknown Year" }
            else -> emptyMap()
        }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val scope = rememberCoroutineScope()
    Title(when (page) { "genres" -> "Genres"; "years" -> "Years"; else -> if (folderPath.isBlank()) "Folders" else current.substringAfterLast('/') }, onBack = { nav.popBackStack() }) {
        if (page == "folders") {
            item("folder-actions") {
                Text(current.ifBlank { "/" }, Modifier.padding(horizontal = 22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                NavigationRow("All songs here", "${inFolder.size} songs including subfolders", Icons.Rounded.PlayArrow) { DiscoveryRoutes.collection(nav, current.substringAfterLast('/').ifBlank { "Folders" }, inFolder) }
            }
            items(children, key = { it }) { path ->
                val count = folders.filterKeys { it == path || it.startsWith("$path/") }.values.sumOf { it.size }
                NavigationRow(path.substringAfterLast('/'), "$count songs", Icons.Rounded.Folder) { DiscoveryRoutes.open(nav, "folders", path) }
            }
            itemsIndexed(folders[current].orEmpty(), key = { i, song -> "$i:${song.uri}" }) { _, song -> MusicList(song, navController = nav) { scope.launch { MediaController.prepare(song, folders[current].orEmpty()) } } }
        } else items(groups.entries.toList(), key = { it.key }) { (name, items) ->
            NavigationRow(name, "${items.size} songs", Icons.Rounded.LibraryMusic) { DiscoveryRoutes.collection(nav, name, items) }
        }
        if (songs.isEmpty()) item("empty") { Text("No music in this category yet.", Modifier.padding(22.dp)) }
    }
}

@Composable
private fun GlobalSearch(nav: NavController) {
    val songs = MusicLibrary.songs; val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }; var kind by rememberSaveable { mutableStateOf("Songs") }
    var results by remember { mutableStateOf<List<YosMediaItem>>(emptyList()) }; var loading by remember { mutableStateOf(false) }
    var shown by remember(query, kind) { mutableIntStateOf(100) }
    val index = produceState(emptyList<DiscoverySearchEntry>(), songs) {
        value = withContext(Dispatchers.Default) { DiscoveryEngine.searchIndex(DiscoveryLibrary.tracks(songs)) }
    }.value
    LaunchedEffect(query, index) {
        loading = query.isNotBlank(); delay(180)
        results = if (query.isBlank()) emptyList() else withContext(Dispatchers.Default) { DiscoveryLibrary.resolve(DiscoveryEngine.searchIndexed(query, index), songs) }
        loading = false
    }
    val groups = remember(results, kind, query) { when (kind) {
        "Albums" -> results.groupBy { it.album ?: defaultAlbum }; "Artists" -> results.flatMap { song -> song.artistsList.orEmpty().map { it to song } }.groupBy({ it.first }, { it.second })
            .filterKeys { name -> DiscoveryEngine.normalize(query).split(' ').all { it in DiscoveryEngine.normalize(name) } }
        "Genres" -> results.groupBy { it.genre ?: "Unknown Genre" }; else -> emptyMap()
    } }
    val folders = if (kind == "Folders") rememberDiscoveryFolders() else emptyMap()
    val folderMatches = remember(query, folders) { folders.entries.filter { DiscoveryEngine.normalize(it.key).contains(DiscoveryEngine.normalize(query)) } }
    val playlists = remember(query, PlayListLibrary.playList) { PlayListLibrary.playList.filter { DiscoveryEngine.normalize(it.name).contains(DiscoveryEngine.normalize(query)) } }
    Title("Search", onBack = { nav.popBackStack() }) {
        item("search") {
            OutlinedTextField(query, { query = it.take(250) }, Modifier.fillMaxWidth().padding(horizontal = 22.dp), placeholder = { Text("Songs, artists, albums, genres…") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }, shape = RoundedCornerShape(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Songs", "Albums", "Artists", "Genres", "Playlists", "Folders").forEach { label -> FilterChip(kind == label, { kind = label }, label = { Text(label) }) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (query.isBlank()) {
            item("history-header") { SectionHeading("Recent Searches", "Clear") { DiscoveryPreferences.searchJson = "[]" } }
            items(DiscoveryPreferences.searchHistory(), key = { it }) { term -> NavigationRow(term, icon = Icons.Rounded.History) { query = term } }
        } else if (kind == "Songs") {
            itemsIndexed(results.take(shown), key = { i, song -> "$i:${song.uri}" }) { _, song ->
                MusicList(song, navController = nav, trailingContent = { IconButton({ DiscoveryPreferences.rememberSearch(query); DiscoveryRoutes.open(nav, "editor", song.uri.toString()) }) { Icon(Icons.Rounded.Edit, "Edit track details", Modifier.size(19.dp)) } }) {
                    DiscoveryPreferences.rememberSearch(query); scope.launch { MediaController.prepare(song, results) }
                }
            }
            if (shown < results.size) item("more") { TextButton({ shown += 100 }, Modifier.padding(16.dp)) { Text("Show more (${results.size} matches)") } }
        } else if (kind == "Folders") items(folderMatches, key = { it.key }) { folder ->
            NavigationRow(folder.key.substringAfterLast('/'), "${folder.key} · ${folder.value.size} songs", Icons.Rounded.Folder) { DiscoveryPreferences.rememberSearch(query); DiscoveryRoutes.open(nav, "folders", folder.key) }
        } else if (kind == "Playlists") items(playlists, key = { it.listID }) { playlist ->
            NavigationRow(playlist.name, "${playlist.songDataList.size} songs", Icons.Rounded.QueueMusic) {
                DiscoveryPreferences.rememberSearch(query)
                val resolved = playlist.songDataList.mapNotNull { uri -> songs.firstOrNull { it.uri == uri } }
                LibraryObject.setTargetListWithTitle(playlist.name, resolved, playlist.listID); nav.toUI(UI.NormalMusic)
            }
        } else items(groups.entries.toList(), key = { it.key }) { (name, entries) -> NavigationRow(name, "${entries.size} matching songs", Icons.Rounded.LibraryMusic) {
            DiscoveryPreferences.rememberSearch(query)
            if (kind == "Albums") { LibraryObject.setTargetAlbumName(name); nav.toUI(UI.AlbumInfo) }
            else if (kind == "Artists") { LibraryObject.setTargetArtistName(name); nav.toUI(UI.ArtistInfo) }
            else DiscoveryRoutes.collection(nav, name, entries)
        } }
        if (query.isNotBlank() && !loading && (if (kind == "Playlists") playlists.isEmpty() else if (kind == "Folders") folderMatches.isEmpty() else if (kind == "Songs") results.isEmpty() else groups.isEmpty())) item("no-results") {
            Text("No matches in your local library.", Modifier.padding(22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmartPlaylists(nav: NavController) {
    val songs = MusicLibrary.songs
    val rules = DiscoveryPreferences.rules()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    fun openRule(rule: SmartMixRule) {
        if (loading) return
        scope.launch {
            loading = true
            try {
                val result = withContext(Dispatchers.Default) { DiscoveryLibrary.evaluate(rule, songs) }
                DiscoveryRoutes.collection(nav, rule.name, result)
            } finally { loading = false }
        }
    }
    Title("Smart Playlists", onBack = { nav.popBackStack() }, rightIcon = Icons.Rounded.Add, onRightIcon = { DiscoveryRoutes.open(nav, "smart-edit") }) {
        if (loading) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item("intro") { Text("Playlists that update from your library, favorites and listening history whenever you open them.", Modifier.padding(22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val presets = listOf(
            SmartMixRule(name = "Favorites", favoritesOnly = true), SmartMixRule(name = "Never Played", unplayedOnly = true),
            SmartMixRule(name = "Most Played", order = "plays"), SmartMixRule(name = "Recently Added", order = "recent"),
        )
        items(presets, key = { it.name }) { rule -> NavigationRow(rule.name, icon = Icons.Rounded.AutoAwesome) { openRule(rule) } }
        item("saved") { SectionHeading("Your Rules", "Create") { DiscoveryRoutes.open(nav, "smart-edit") } }
        items(rules, key = { it.id }) { rule ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { NavigationRow(rule.name, "Up to ${rule.limit} songs", Icons.Rounded.AutoAwesome) { openRule(rule) } }
                IconButton({ DiscoveryRoutes.open(nav, "smart-edit", rule.id) }) { Icon(Icons.Rounded.Edit, "Edit ${rule.name}") }
            }
        }
    }
}

@Composable
private fun SmartEditor(nav: NavController, id: String) {
    val original = remember(id) { DiscoveryPreferences.rules().firstOrNull { it.id == id } ?: SmartMixRule(id = UUID.randomUUID().toString()) }
    var name by rememberSaveable { mutableStateOf(original.name) }; var artist by rememberSaveable { mutableStateOf(original.artist) }
    var genre by rememberSaveable { mutableStateOf(original.genre) }; var from by rememberSaveable { mutableStateOf(original.fromYear.takeIf { it > 0 }?.toString().orEmpty()) }
    var to by rememberSaveable { mutableStateOf(original.toYear.takeIf { it > 0 }?.toString().orEmpty()) }; var limit by rememberSaveable { mutableStateOf(original.limit.toString()) }
    var favorite by rememberSaveable { mutableStateOf(original.favoritesOnly) }; var unplayed by rememberSaveable { mutableStateOf(original.unplayedOnly) }
    var order by rememberSaveable { mutableStateOf(original.order) }; var error by remember { mutableStateOf("") }
    Title(if (id.isBlank()) "Create Smart Playlist" else "Edit Smart Playlist", onBack = { nav.popBackStack() }) {
        item("fields") { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DiscoveryField("Name", name) { name = it }; DiscoveryField("Artist contains (optional)", artist) { artist = it }; DiscoveryField("Genre contains (optional)", genre) { genre = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Box(Modifier.weight(1f)) { DiscoveryField("From year", from) { from = it } }; Box(Modifier.weight(1f)) { DiscoveryField("To year", to) { to = it } } }
            DiscoveryField("Maximum songs (1–500)", limit) { limit = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(favorite, { favorite = it }); Text("Favorites only") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(unplayed, { unplayed = it }); Text("Never played only") }
            Text("Order", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("mix" to "Mixed", "recent" to "Newest", "plays" to "Most played", "title" to "Title").forEach { (value, label) -> FilterChip(order == value, { order = value }, label = { Text(label) }) } }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Button({
                val start = from.toIntOrNull(); val end = to.toIntOrNull(); val count = limit.toIntOrNull()
                error = when { name.trim().isEmpty() -> "Choose a name."; count == null || count !in 1..500 -> "Enter a song limit from 1 to 500."; from.isNotBlank() && (start == null || start !in 1..9999) -> "Enter a valid starting year."; to.isNotBlank() && (end == null || end !in 1..9999) -> "Enter a valid ending year."; start != null && end != null && start > end -> "Starting year must come first."; else -> "" }
                if (error.isBlank()) { DiscoveryPreferences.saveRule(SmartMixRule(original.id, name.trim(), artist.trim(), genre.trim(), start ?: 0, end ?: 0, favorite, unplayed, order, count!!)); nav.popBackStack() }
            }, Modifier.fillMaxWidth()) { Text("Save smart playlist") }
            if (id.isNotBlank()) TextButton({ DiscoveryPreferences.removeRule(id); nav.popBackStack() }) { Text("Delete rule", color = MaterialTheme.colorScheme.error) }
        } }
    }
}

@Composable
internal fun DiscoveryField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(14.dp))


@Composable
private fun ListeningHistory(nav: NavController) {
    val songs = MusicLibrary.songs
    val events = ListenStatsManager.statsEvents.value
    var period by rememberSaveable { mutableStateOf(StatsPeriod.AllTime.name) }
    var shown by remember(period) { mutableIntStateOf(100) }
    val selected = StatsPeriod.valueOf(period)
    val scope = rememberCoroutineScope()
    val records = produceState(emptyList<Pair<Long, YosMediaItem>>(), songs, events, selected) {
        value = withContext(Dispatchers.Default) {
            val index = songs.associateBy { it.uri.toString() }
            val start = ListenStatsLibrary.periodStartMs(selected)
            events.asSequence().filter { it.countsAsPlay && it.timestampMs >= start }.sortedByDescending { it.timestampMs }
                .mapNotNull { event -> index[event.uri]?.let { event.timestampMs to it } }.toList()
                .ifEmpty { if (selected == StatsPeriod.AllTime) ListenHistoryManager.recentlyPlayedSongs.value.filter { it.uri.toString() in index }.map { 0L to it } else emptyList() }
        }
    }.value
    Title("Listening History", onBack = { nav.popBackStack() }) {
        item("periods") {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(StatsPeriod.Today to "Today", StatsPeriod.ThisWeek to "This Week", StatsPeriod.ThisMonth to "This Month", StatsPeriod.AllTime to "All Time").forEach { (value, label) ->
                    FilterChip(selected == value, { period = value.name }, label = { Text(label) })
                }
            }
        }
        itemsIndexed(records.take(shown), key = { index, record -> "$index:${record.first}:${record.second.uri}" }) { _, record ->
            Column {
                if (record.first > 0) Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(record.first)), Modifier.padding(start = 22.dp, top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                MusicList(record.second, navController = nav) { scope.launch { MediaController.prepare(record.second, listOf(record.second)) } }
            }
        }
        if (shown < records.size) item("more") { TextButton({ shown += 100 }, Modifier.padding(20.dp)) { Text("Show more history") } }
        if (records.isEmpty()) item("empty") { Text("No recorded plays in this period yet.", Modifier.padding(22.dp)) }
    }
}
