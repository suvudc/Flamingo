package yos.music.player.ui.widgets.basic

import androidx.compose.runtime.Composable

/** Preserve the call site's Compose identity; lambda identity changes with captured state. */
@Composable
fun YosWrapper(content: @Composable () -> Unit) { content() }
