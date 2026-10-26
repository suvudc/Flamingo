package yos.music.player.data.discovery

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import yos.music.player.code.AudioMetadataUtils
import yos.music.player.data.libraries.YosMediaItem
import java.io.File

/** Resolve real filesystem directories once per library snapshot, outside the UI thread. */
object DiscoveryFolders {
    private val mutex = Mutex()
    private var source: List<YosMediaItem>? = null
    private var cached: Map<String, List<YosMediaItem>> = emptyMap()
    suspend fun groups(context: Context, songs: List<YosMediaItem>): Map<String, List<YosMediaItem>> = withContext(Dispatchers.IO) {
        if (songs.isEmpty() || !yos.music.player.data.libraries.MusicLibrary.hasAudioPermission(context)) return@withContext emptyMap()
        mutex.withLock {
            if (source === songs) return@withLock cached
            val byUri = hashMapOf<String, String>()
            val byId = hashMapOf<Long, String>()
            val ambiguousIds = hashSetOf<Long>()
            val projection = mutableListOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= 29) projection.add(MediaStore.MediaColumns.VOLUME_NAME)
            runCatching {
                context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection.toTypedArray(), null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val volumeColumn = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME) else -1
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val id = cursor.getLong(idColumn)
                        val path = if (pathColumn >= 0) cursor.getString(pathColumn) else null
                        if (path.isNullOrBlank()) continue
                        if (byId.put(id, path)?.let { it != path } == true) ambiguousIds.add(id)
                        byUri[ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()] = path
                        if (Build.VERSION.SDK_INT >= 29 && volumeColumn >= 0) cursor.getString(volumeColumn)?.let { volume ->
                            byUri[ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(volume), id).toString()] = path
                        }
                    }
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            val groups = linkedMapOf<String, MutableList<YosMediaItem>>()
            for (song in songs) {
                currentCoroutineContext().ensureActive()
                val uri = song.uri ?: continue
                val numericId = uri.lastPathSegment?.toLongOrNull()
                val path = when {
                    uri.scheme == "file" || uri.scheme == null -> uri.path
                    else -> byUri[uri.toString()] ?: numericId?.takeUnless { it in ambiguousIds }?.let { byId[it] }
                        ?: AudioMetadataUtils.resolveLocalAudioFilePath(context, uri)
                }
                val directory = path?.let { File(it).parent }.orEmpty().ifBlank { "Unknown Folder" }
                groups.getOrPut(directory) { mutableListOf() }.add(song)
            }
            source = songs
            cached = groups.mapValues { it.value.toList() }
            cached
        }
    }
}
