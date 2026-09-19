package yos.music.player.ui.widgets.basic

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.NormalSaver

private val profilePictureUri = mutableStateOf(NormalSaver.readData("profile_picture_uri", ""))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileButton(onClick: () -> Unit) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            profilePictureUri.value = uri.toString()
            NormalSaver.saveData("profile_picture_uri", profilePictureUri.value)
        } catch (_: SecurityException) {
            // The provider did not offer durable access, so don't save a URI that will later break.
        }
    }
    val modifier = Modifier
        .size(if (profilePictureUri.value.isEmpty()) 24.dp else 32.dp)
        .clip(CircleShape)
        .combinedClickable(
            onClick = onClick,
            onLongClick = {
                Vibrator.longClick(context)
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

    if (profilePictureUri.value.isEmpty()) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = stringResource(R.string.profile_picture),
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )
    } else {
        AsyncImage(
            model = profilePictureUri.value,
            contentDescription = stringResource(R.string.profile_picture),
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
