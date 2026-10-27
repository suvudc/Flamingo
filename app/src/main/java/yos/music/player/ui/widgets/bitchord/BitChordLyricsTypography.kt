package yos.music.player.ui.widgets.bitchord

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import yos.music.player.R

// Exact lyric weights/metrics from BitChord's Android Theme.kt. Scoped to lyrics.
private val lyricFamily = FontFamily(
    Font(R.font.music_bitchord_semibold, FontWeight.W600),
    Font(R.font.music_bitchord_bold, FontWeight.W700),
    Font(R.font.music_bitchord_heavy, FontWeight.W800),
)

@Composable
internal fun BitChordLyricsTypography(content: @Composable () -> Unit) {
    val typography = MaterialTheme.typography.copy(
        headlineLarge = TextStyle(fontFamily = lyricFamily, fontWeight = FontWeight.W800,
            fontSize = 30.sp, letterSpacing = (-0.7).sp),
        headlineMedium = TextStyle(fontFamily = lyricFamily, fontWeight = FontWeight.W700,
            fontSize = 22.sp, letterSpacing = (-0.4).sp),
        titleMedium = TextStyle(fontFamily = lyricFamily, fontWeight = FontWeight.W600,
            fontSize = 16.sp, letterSpacing = (-0.2).sp),
        labelMedium = TextStyle(fontFamily = lyricFamily, fontWeight = FontWeight.W600, fontSize = 12.sp),
    )
    MaterialTheme(typography = typography, content = content)
}
