package yos.music.player.ui.pages.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import yos.music.player.R
import yos.music.player.data.libraries.MusicLibrary.songs
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.ProfileButton
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosWrapper

@Composable
fun Library(navController: NavController) {
    Column(
        Modifier
            .fillMaxSize()
        /*.statusBarsPadding()*/
    ) {
        Title(
            title = stringResource(id = R.string.page_library_title),
            rightIconContent = {
                ProfileButton { navController.toUI(UI.Settings.Main) }
            }
        ) {
            item("Library") {
                Column(
                    Modifier
                        .fillMaxSize(),
                ) {
                    yos.music.player.ui.pages.discovery.NavigationRow("Search", "Find songs, albums, artists and playlists", androidx.compose.material.icons.Icons.Rounded.Search) {
                        yos.music.player.ui.pages.discovery.DiscoveryRoutes.open(navController, "search")
                    }
                    yos.music.player.ui.pages.discovery.NavigationRow("Favorites", "Your liked songs", androidx.compose.material.icons.Icons.Rounded.Favorite) {
                        yos.music.player.ui.pages.discovery.DiscoveryRoutes.collection(navController, "Favorites", yos.music.player.data.libraries.FavPlayListLibrary.favPlayList)
                    }
                    listOf("Your Mix & Daily Mixes" to "mixes", "Smart Playlists" to "smart", "Genres" to "genres", "Years" to "years", "Folders" to "folders", "Library Tools" to "tools").forEach { (name, page) ->
                        yos.music.player.ui.pages.discovery.NavigationRow(name) { yos.music.player.ui.pages.discovery.DiscoveryRoutes.open(navController, page) }
                    }
                    LibraryDivider()
                    SmallLabelItem(
                        icon = painterResource(id = R.drawable.ic_library_link_icon_playlists),
                        label = stringResource(
                            id = R.string.page_library_playlists
                        )
                    ) {
                        navController.toUI(UI.PlayLists)
                    }
                    LibraryDivider()
                    SmallLabelItem(
                        icon = painterResource(id = R.drawable.ic_library_link_icon_artists),
                        label = stringResource(
                            id = R.string.page_library_artists
                        )
                    ) {
                        navController.toUI(UI.LocalArtists)
                    }
                    LibraryDivider()
                    SmallLabelItem(
                        icon = painterResource(id = R.drawable.ic_library_link_icon_album),
                        label = stringResource(
                            id = R.string.page_library_albums
                        )
                    ) {
                        navController.toUI(UI.LocalAlbums)
                    }
                    LibraryDivider()

                    YosWrapper {
                        val targetTitle = stringResource(
                            id = R.string.page_library_songs
                        )
                        val targetList = songs
                        SmallLabelItem(
                            icon = painterResource(id = R.drawable.ic_library_link_icon_songs),
                            label = targetTitle
                        ) {
                            LibraryObject.setTargetListWithTitle(targetTitle, targetList)
                            navController.toUI(UI.NormalMusic)
                        }
                    }

                    LibraryDivider()
                }
            }
        }
    }
}
