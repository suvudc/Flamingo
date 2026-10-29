package yos.music.player.ui.widgets.basic

import yos.music.player.ui.widgets.basic.hapticClickable as clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import yos.music.player.R

@Composable
fun StartupBrand(visible: Boolean) {
    AnimatedVisibility(visible, enter = EnterTransition.None, exit = fadeOut(tween(140)),
        modifier = Modifier.fillMaxSize().zIndex(100f)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = {})) {
            Image(painterResource(R.drawable.suvu_startup), "Music by Suvu",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}
