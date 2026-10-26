package yos.music.player.ui.widgets.basic

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.insets.navigationBarsHeight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import yos.music.player.ui.theme.isFlamingoInDarkMode

@Stable
data class NavItem(val label: String, val iconResId: Int)

/** Floating navigation only; the mini-player and full player use their existing views. */
@Composable
fun BottomNavigator(
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit,
    items: List<NavItem>,
    modifier: Modifier,
    hazeState: HazeState? = null
) {
    if (items.isEmpty()) return
    val dark = isFlamingoInDarkMode()
    val base = if (dark) Color(0xFF202027) else Color(0xFFF4F4F8)
    val foreground = if (dark) Color.White else Color(0xFF15151B)
    val label = nowLabel()
    val selectedIndex = items.indexOfFirst { it.label == label }.coerceAtLeast(0)
    val glassStyle = remember(base, dark) { HazeStyle(
            backgroundColor = base,
            tint = base.copy(alpha = if (dark) .28f else .38f),
            blurRadius = 12.dp,
            noiseFactor = 0f
        ) }
    val glass = if (Build.VERSION.SDK_INT >= 31 && hazeState != null) {
        Modifier.hazeChild(hazeState, glassStyle)
    } else Modifier.background(base.copy(alpha = .94f))

    Box(modifier.fillMaxWidth().navigationBarsHeight(74.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp).widthIn(max = 440.dp)
            .fillMaxWidth().height(64.dp)
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .then(glass)
            .background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (dark) .15f else .5f),
                Color.White.copy(alpha = .025f),
                Color.White.copy(alpha = .07f)
            )))
            .border(1.dp, Brush.linearGradient(listOf(
                Color.White.copy(alpha = .65f),
                Color.White.copy(alpha = .08f),
                Color.White.copy(alpha = .3f)
            )), CircleShape)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
                val segmentWidth = maxWidth / items.size
                val selectionOffset = animateDpAsState(
                    targetValue = segmentWidth * selectedIndex,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
                    label = "Glass tab position"
                )
                val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
                Box(Modifier.width(segmentWidth).fillMaxHeight()
                    .graphicsLayer { translationX = selectionOffset.value.toPx() * direction }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(
                        foreground.copy(alpha = if (dark) .22f else .12f),
                        foreground.copy(alpha = if (dark) .10f else .055f)
                    )))
                    .border(.6.dp, Color.White.copy(alpha = if (dark) .2f else .55f), CircleShape))
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        val tint = animateColorAsState(
                            if (selected) foreground else foreground.copy(alpha = .62f), label = "Glass tab tint")
                        Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onLabelChange(item.label) }),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(item.iconResId), contentDescription = null,
                                tint = tint.value, modifier = Modifier.size(25.dp))
                            Text(item.label, color = tint.value, fontSize = 11.sp, lineHeight = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
