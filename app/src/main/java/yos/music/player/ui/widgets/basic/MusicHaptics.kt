package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import yos.music.player.code.utils.others.Vibrator

/** Runs only after a real activation; canceled taps and scrolling never vibrate. */
@Composable
fun rememberHapticAction(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(action)
    return remember(context) { { Vibrator.click(context); latest() } }
}

fun Modifier.hapticClickable(enabled: Boolean = true, onClickLabel: String? = null,
    role: Role? = null, onClick: () -> Unit): Modifier = composed {
    clickable(enabled = enabled, onClickLabel = onClickLabel, role = role,
        onClick = rememberHapticAction(onClick))
}

fun Modifier.hapticClickable(interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = interactionSource, indication = indication, enabled = enabled,
        onClickLabel = onClickLabel, role = role, onClick = rememberHapticAction(onClick))
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hapticCombinedClickable(enabled: Boolean = true, onClickLabel: String? = null,
    role: Role? = null, onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    combinedClickable(enabled = enabled, onClickLabel = onClickLabel, role = role,
        onLongClickLabel = onLongClickLabel, onLongClick = onLongClick,
        onDoubleClick = onDoubleClick?.let { rememberHapticAction(it) }, onClick = rememberHapticAction(onClick))
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hapticCombinedClickable(interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    combinedClickable(interactionSource = interactionSource, indication = indication, enabled = enabled,
        onClickLabel = onClickLabel, role = role, onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick, onDoubleClick = onDoubleClick?.let { rememberHapticAction(it) },
        onClick = rememberHapticAction(onClick))
}

@Composable
fun HapticIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) =
    IconButton(onClick = rememberHapticAction(onClick), modifier = modifier, enabled = enabled,
        colors = colors, interactionSource = interactionSource ?: remember { MutableInteractionSource() }, content = content)

@Composable
fun HapticTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit) =
    TextButton(onClick = rememberHapticAction(onClick), modifier = modifier, enabled = enabled,
        contentPadding = contentPadding, content = content)

@Composable
fun HapticFilledIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape, content: @Composable () -> Unit) =
    FilledIconButton(onClick = rememberHapticAction(onClick), modifier = modifier, enabled = enabled,
        shape = shape, content = content)

@Composable
fun HapticFilledTonalIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape, content: @Composable () -> Unit) =
    FilledTonalIconButton(onClick = rememberHapticAction(onClick), modifier = modifier, enabled = enabled,
        shape = shape, content = content)
