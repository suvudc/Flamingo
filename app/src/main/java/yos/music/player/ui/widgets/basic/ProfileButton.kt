package yos.music.player.ui.widgets.basic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable

@Composable
fun ProfileButton(onClick: () -> Unit) {
    LiquidGlassIconButton(Icons.Rounded.MoreVert, "Settings", onClick)
}
