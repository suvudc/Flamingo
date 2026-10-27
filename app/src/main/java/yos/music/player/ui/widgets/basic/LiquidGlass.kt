package yos.music.player.ui.widgets.basic

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.*
import yos.music.player.ui.theme.isFlamingoInDarkMode

val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }
val MusicGlassAccent = Color(0xFFFF0054)

/** A shared, restrained glass material for the dock and circular toolbar actions. */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    haze: HazeState? = LocalGlassBackdrop.current,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = isFlamingoInDarkMode()
    val base = if (dark) Color(0xFF18181C) else Color(0xFFF4F4F7)
    val style = remember(base) { HazeStyle(backgroundColor = base,
        tint = base.copy(alpha = if (dark) .30f else .42f), blurRadius = 18.dp, noiseFactor = 0f) }
    val backdrop = if (Build.VERSION.SDK_INT >= 31 && haze != null) Modifier.hazeChild(haze, style)
        else Modifier.background(base.copy(alpha = .94f))
    val gloss = remember(dark) { Brush.verticalGradient(listOf(
        Color.White.copy(alpha = if (dark) .16f else .50f),
        Color.White.copy(alpha = .025f), Color.Black.copy(alpha = .08f))) }
    val rim = remember { Brush.linearGradient(listOf(Color.White.copy(alpha = .58f),
        Color.White.copy(alpha = .10f), Color.White.copy(alpha = .30f))) }
    Box(modifier.shadow(5.dp, CircleShape).clip(CircleShape).then(backdrop)
        .background(base.copy(alpha = .18f)).background(gloss).border(1.dp, rim, CircleShape), content = content)
}

@Composable
fun LiquidGlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, haze: HazeState? = LocalGlassBackdrop.current) {
    LiquidGlassSurface(modifier.size(44.dp), haze) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, description, Modifier.size(24.dp),
                tint = if (isFlamingoInDarkMode()) Color.White.copy(alpha = .9f) else Color(0xFF202026))
        }
    }
}
