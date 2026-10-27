package yos.music.player.ui.widgets.basic

import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.*
import yos.music.player.ui.theme.isFlamingoInDarkMode

val LocalMusicGlassHaze = staticCompositionLocalOf<HazeState?> { null }

/** The refracted backdrop is a separate layer, so labels/icons never get bent or blurred. */
@Composable
fun MusicGlass(modifier: Modifier = Modifier, haze: HazeState? = LocalMusicGlassHaze.current,
    content: @Composable BoxScope.() -> Unit) {
    val dark = isFlamingoInDarkMode()
    val base = if (dark) Color(0xFF18181B) else Color(0xFFF3F3F7)
    val style = remember(dark) { HazeStyle(backgroundColor = base,
        tint = base.copy(alpha = if (dark) .31f else .40f), blurRadius = 12.dp, noiseFactor = 0f) }
    val blur = if (Build.VERSION.SDK_INT >= 31 && haze != null) Modifier.hazeChild(haze, style)
        else Modifier.background(base.copy(alpha = .94f))
    val lens = if (Build.VERSION.SDK_INT >= 33) rememberMusicLens(dark) else Modifier
    val gloss = remember(dark) { Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .14f else .44f),
        Color.White.copy(alpha = .01f), Color.White.copy(alpha = .055f))) }
    val rim = remember { Brush.linearGradient(listOf(Color.White.copy(alpha = .55f),
        Color.White.copy(alpha = .11f), Color.White.copy(alpha = .30f))) }
    Box(modifier.shadow(5.dp, CircleShape).clip(CircleShape)) {
        Box(Modifier.matchParentSize().then(lens).then(blur).background(base.copy(alpha = .16f)))
        Box(Modifier.matchParentSize().background(gloss).border(.8.dp, rim, CircleShape))
        content()
    }
}

@Composable
fun MusicGlassButton(icon: ImageVector, description: String, modifier: Modifier = Modifier.size(44.dp),
    onClick: () -> Unit) {
    MusicGlass(modifier) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, description, tint = if (isFlamingoInDarkMode()) Color.White else Color(0xFF202024),
                modifier = Modifier.size(23.dp))
        }
    }
}

@RequiresApi(33)
@Composable
private fun rememberMusicLens(dark: Boolean): Modifier {
    val density = LocalDensity.current.density
    val lens = remember(dark, density) { runCatching { MusicLens(dark, density) }.getOrNull() }
    return if (lens == null) Modifier else Modifier.graphicsLayer {
        if (size.width > 0f && size.height > 0f) {
            lens.resize(size.width, size.height)
            renderEffect = lens.effect
        }
    }
}

@RequiresApi(33)
private class MusicLens(dark: Boolean, density: Float) {
    private val shader = android.graphics.RuntimeShader(MusicGlassShader)
    var effect: androidx.compose.ui.graphics.RenderEffect? = null
        private set
    private var width = -1f
    private var height = -1f
    init {
        shader.setFloatUniform("edgeWidth", 9f * density)
        shader.setFloatUniform("refraction", 3.5f * density)
        shader.setFloatUniform("shine", if (dark) .24f else .17f)
    }
    fun resize(w: Float, h: Float) {
        if (w == width && h == height) return
        width = w; height = h
        shader.setFloatUniform("extent", w, h)
        // RenderEffect snapshots its shader builder: update the cached effect only
        // after dimensions are known, and rebuild it only when those dimensions change.
        effect = runCatching {
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
        }.getOrNull()
    }
}

// The APK was a visual/technical reference. This lens is written for Music's single-pill
// Haze pipeline, with an analytic capsule normal rather than the APK's merged-shape renderer.
internal const val MusicGlassShader = """
uniform shader backdrop;
uniform float2 extent;
uniform float edgeWidth;
uniform float refraction;
uniform float shine;
half4 main(float2 xy) {
    float radius = min(extent.x, extent.y) * 0.5;
    float2 center = extent * 0.5;
    float2 point = xy - center;
    float2 spine = float2(clamp(point.x, -center.x + radius, center.x - radius),
                          clamp(point.y, -center.y + radius, center.y - radius));
    float2 radial = point - spine;
    float radialLength = length(radial);
    float distance = radialLength - radius;
    float coverage = clamp(0.5 - distance, 0.0, 1.0);
    float2 normal = radial / max(radialLength, 0.0001);
    float depth = max(-distance, 0.0);
    float edge = 1.0 - smoothstep(0.0, edgeWidth, depth);
    float2 shift = normal * refraction * edge * edge;
    float2 low = float2(0.5);
    float2 high = max(extent - float2(0.5), low);
    half4 texel = backdrop.eval(clamp(xy + shift, low, high));
    half red = backdrop.eval(clamp(xy + shift * 1.08, low, high)).r;
    half blue = backdrop.eval(clamp(xy + shift * 0.92, low, high)).b;
    float light = max(dot(normal, normalize(float2(-0.55, -1.0))), 0.0);
    float highlight = shine * edge * edge * light * light;
    half3 colour = half3(red, texel.g, blue);
    colour += half3(1.0 - colour) * half(highlight);
    return half4(colour * half(coverage), texel.a * half(coverage));
}
"""
