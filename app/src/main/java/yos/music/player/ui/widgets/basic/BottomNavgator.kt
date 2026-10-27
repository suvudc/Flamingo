package yos.music.player.ui.widgets.basic

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.insets.navigationBarsHeight
import dev.chrisbanes.haze.*
import kotlinx.coroutines.launch
import yos.music.player.code.MediaController
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.*
import yos.music.player.data.objects.MediaViewModelObject
import yos.music.player.ui.theme.isFlamingoInDarkMode

@Stable
data class NavItem(val label: String, val iconResId: Int)

/** One dock morphs between stacked pills and the scroll-collapsed three-button row. */
@Composable
fun BottomNavigator(
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit,
    items: List<NavItem>,
    modifier: Modifier,
    hazeState: HazeState? = null,
    dockState: GlassDockScrollState,
    opacity: () -> Float,
    onExpandPlayer: () -> Unit
) {
    if (items.isEmpty()) return
    val dark = isFlamingoInDarkMode()
    val ink = if (dark) Color.White else Color(0xFF16161B)
    val base = if (dark) Color(0xFF18181C) else Color(0xFFF4F4F7)
    val state = dockState.compact.value
    val morph by animateFloatAsState(if (state) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 400f), label = "Dock collapse")
    var search by remember { mutableStateOf(false) }
    val dockItems = items + NavItem("Search", yos.music.player.R.drawable.ic_uitabbar_search)
    val selected = items.indexOfFirst { it.label == nowLabel() }.coerceAtLeast(0)
    Box(modifier.fillMaxWidth().graphicsLayer { alpha = opacity() }) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            .height((132f - 76f * morph).dp)) {
            val miniX = 52.dp * morph
            val miniWidth = (maxWidth - 104.dp * morph).coerceAtLeast(80.dp)
            val buttonSize = (64f - 16f * morph).dp
            val bottomY = 68.dp * (1f - morph)
            val navWidth = maxWidth * (1f - morph) + 48.dp * morph
            val miniHeight = (56f - 8f * morph).dp
            GlassPill(Modifier.offset(x = miniX).width(miniWidth).height(miniHeight), hazeState, base, dark) {
                MiniSongPill(morph, ink, onExpandPlayer)
            }
            GlassPill(Modifier.offset(y = bottomY).width(navWidth).height(buttonSize), hazeState, base, dark) {
                if (morph < .99f) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - morph }) {
                        GlassTabs(dockItems, if (search) dockItems.lastIndex else selected, ink) {
                            dockState.expand()
                            if (it == "Search") search = true else onLabelChange(it)
                        }
                    }
                }
                if (morph > .5f) {
                    IconButton(onClick = { dockState.expand() }, modifier = Modifier.fillMaxSize()
                        .graphicsLayer { alpha = morph }) {
                        Icon(Icons.Rounded.GridView, "Show navigation tabs", tint = ink, modifier = Modifier.size(25.dp))
                    }
                }
            }
            if (morph > .01f) GlassPill(Modifier.align(Alignment.TopEnd).offset(y = bottomY).size(buttonSize)
                .graphicsLayer { alpha = morph }, hazeState, base, dark) {
                IconButton(onClick = { search = true }, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.Search, "Search music", tint = ink.copy(alpha = .8f), modifier = Modifier.size(29.dp))
                }
            }
        }
    }
    if (search) GlassDockSearch(onDismiss = { search = false })
}

@Composable
private fun GlassPill(modifier: Modifier, haze: HazeState?, base: Color, dark: Boolean,
    content: @Composable BoxScope.() -> Unit) = MusicGlass(modifier, haze, content)

@Composable
private fun GlassTabs(items: List<NavItem>, selected: Int, ink: Color, onSelect: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
        // Draw one selection marker. Per-tab ripples would add a second capsule
        // under the finger while this marker travels to the selected tab.
        val position = animateFloatAsState(selected.toFloat(),
            spring(dampingRatio = 1f, stiffness = 420f), label = "Glass tab selection")
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Canvas(Modifier.fillMaxSize()) {
            val tabWidth = size.width / items.size
            val index = if (rtl) items.lastIndex - position.value else position.value
            drawRoundRect(ink.copy(alpha = .13f),
                topLeft = Offset(index * tabWidth, 0f),
                size = Size(tabWidth, size.height),
                cornerRadius = CornerRadius(size.height / 2f))
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            items.forEachIndexed { index, item ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .selectable(selected = index == selected,
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = Role.Tab, onClick = { onSelect(item.label) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val tint = if (index == selected) Color(0xFFFF0065) else ink.copy(alpha = .90f)
                    when (index) {
                        0 -> Icon(Icons.Rounded.Home, null, tint = tint, modifier = Modifier.size(25.dp))
                        1 -> Icon(Icons.Rounded.GridView, null, tint = tint, modifier = Modifier.size(25.dp))
                        else -> Icon(painterResource(item.iconResId), null, tint = tint, modifier = Modifier.size(25.dp))
                    }
                    Text(item.label, color = tint, fontSize = 11.sp, lineHeight = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun MiniSongPill(morph: Float, ink: Color, onExpand: () -> Unit) {
    val artworkMotion = LocalPlayerArtworkMotion.current
    val track = MediaController.musicPlaying.value
    val playing = MediaViewModelObject.isPlaying.value
    val scope = rememberCoroutineScope()
    var horizontal = remember { 0f }
    var vertical = remember { 0f }
    val context = LocalContext.current
    val art = MediaViewModelObject.bitmap.value ?: track?.thumb
    val request = remember(context, art) { ImageRequest.Builder(context).data(art).size(128).crossfade(false).build() }
    Row(Modifier.fillMaxSize().clickable(onClick = onExpand)
        .pointerInput(track?.uri) {
            detectHorizontalDragGestures(onDragStart = { horizontal = 0f }, onHorizontalDrag = { change, amount ->
                change.consume(); horizontal += amount
            }, onDragEnd = {
                if (horizontal < -48.dp.toPx()) MediaController.mediaControl?.seekToNextMediaItem()
                else if (horizontal > 48.dp.toPx()) MediaController.mediaControl?.seekToPreviousMediaItem()
            })
        }.pointerInput(Unit) {
            detectVerticalDragGestures(onDragStart = { vertical = 0f }, onVerticalDrag = { change, amount ->
                change.consume(); vertical += amount
            }, onDragEnd = { if (vertical < -24.dp.toPx()) onExpand() })
        }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((40f - 4f * morph).dp)
            .onGloballyPositioned { artworkMotion?.captureThumbnail(it, 10f + 10f * morph) }
            .graphicsLayer { alpha = if (artworkMotion?.hasFlight == true) 0f else 1f }.clip(RoundedCornerShape((10f + 10f * morph).dp))
            .background(ink.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = ink.copy(alpha = .7f))
            AsyncImage(request, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp).height((38f - 18f * morph).dp), verticalArrangement = Arrangement.Center) {
            Text(track?.title ?: "Music", color = ink, fontSize = 16.sp, lineHeight = 20.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            if (morph < .99f) Text(track?.artistsName ?: "Choose a song", color = ink.copy(alpha = .62f),
                fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = 1f - morph })
        }
        IconButton(onClick = {
            val wasPlaying = MediaViewModelObject.isPlaying.value
            MediaViewModelObject.isPlaying.value = !wasPlaying
            scope.launch {
                if (wasPlaying) MediaController.mediaControl?.fadePause()
                else MediaController.mediaControl?.fadePlay()
            }
        }, Modifier.size(40.dp), enabled = MediaController.mediaControl != null) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pause" else "Play", tint = ink, modifier = Modifier.size(30.dp))
        }
        if (morph < .99f) IconButton(onClick = { MediaController.mediaControl?.seekToNextMediaItem() },
            Modifier.width(40.dp * (1f - morph)).height(40.dp).graphicsLayer { alpha = 1f - morph }) {
            Icon(Icons.Rounded.SkipNext, "Next track", tint = ink, modifier = Modifier.size(30.dp))
        }
    }
}

