package yos.music.player.code

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import yos.music.player.code.canvas.AppleMusicCanvas
import yos.music.player.code.canvas.TidalCanvas
import yos.music.player.code.canvas.CommunityCanvas
import yos.music.player.code.canvas.CanvasArtwork
import yos.music.player.code.canvas.CanvasLookup
import yos.music.player.code.canvas.CANVAS_UA
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsName
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale

object AnimatedArtworkLibrary
{
    private const val AnimatedArtworkDirectoryName = "anim"
    private const val AnimatedArtworkCacheDirectoryName = "animated_artwork"
    private const val NetworkTimeoutMilliseconds = 15000
    private const val DefaultSampleBufferBytes = 1024 * 1024
    private const val MaximumPlaylistBytes = 1024 * 1024
    private const val MaximumArtworkBytes = 50L * 1024L * 1024L
    private const val MaximumExpectedStartOffsetMicroseconds = 500_000L
    private val bandwidthRegex = Regex("BANDWIDTH=(\\d+)")
    private val invalidFileNameCharacters = Regex("[\\\\/:*?\"<>|]")
    private val hlsMapUriRegex = Regex("URI=\"([^\"]+)\"")
    private val resolutionRegex = Regex("RESOLUTION=(\\d+)x(\\d+)")
    private val storageMutex = Mutex()

    private data class StreamVariant(
        val url: String,
        val isAvc: Boolean,
        val pixelCount: Int,
        val bandwidth: Long
    )

    suspend fun resolveArtworkFile(context: Context, music: YosMediaItem): File? = withContext(Dispatchers.IO)
    {
        if (!SettingsLibrary.AnimatedAlbumCovers) return@withContext null
        val albumName = music.album?.trim().orEmpty()
        val queries = CanvasLookup.queries(music.title, music.artistsName, music.albumArtists, music.album)
        localArtworkFile(context, music)?.takeIf { isPlayableVideoFile(it) }?.let { return@withContext it }
        val artistName = music.albumArtists?.trim()?.takeIf { it.isNotEmpty() }
            ?: music.artistsName?.trim().orEmpty()
        val identity = cachedArtworkFile(context, music, CanvasLookup.cacheAlbum(music.album, music.title))
        val directory = File(identity.parentFile, identity.nameWithoutExtension)
        val trackIdentity = animatedArtworkCacheFile(cacheDirectory(context), artistName, "\u0001track:${music.title}\u0000${music.album}")
        val trackDirectory = File(trackIdentity.parentFile, trackIdentity.nameWithoutExtension)

        storageMutex.withLock {
            // Always read durable downloads before checking network or the online lookup switch.
            OfflineArtworkStore.cached(trackDirectory)?.let { return@withLock it }
            OfflineArtworkStore.cached(directory)?.let { return@withLock it }
            // Keep covers downloaded by earlier Music builds; migrate out of evictable cache.
            val legacy = File(File(context.cacheDir, AnimatedArtworkCacheDirectoryName), identity.name)
            if (!identity.exists() && legacy.isFile && isPlayableVideoFile(legacy)) {
                identity.parentFile?.mkdirs()
                runCatching { legacy.copyTo(identity, overwrite = false) }
            }
            if (identity.isFile && normalizeCachedMp4File(identity)) return@withLock identity
            identity.delete()
            if (!SettingsLibrary.AnimatedAlbumCoversUseApi ||
                SettingsLibrary.isAnimatedAlbumCoverBlacklisted(albumName) || queries.isEmpty() ||
                !hasNetwork(context)) return@withLock null

            val store = OfflineArtworkStore(::readUrlText, ::downloadResource) { file ->
                normalizeCachedMp4File(file)
            }
            val tried = mutableSetOf<String>()
            for (query in queries) {
                val sources: List<() -> CanvasArtwork?> = if (query.albumSearch) listOf(
                    { AppleMusicCanvas.searchAlbum(query.name, query.artist) },
                    { TidalCanvas.searchAlbum(query.name, query.artist) },
                    { CommunityCanvas.searchAlbum(query.name, query.artist) }
                ) else listOf(
                    { AppleMusicCanvas.search(query.name, query.artist, query.album) },
                    { TidalCanvas.search(query.name, query.artist, query.album) },
                    { CommunityCanvas.search(query.name, query.artist, query.album) }
                )
                for (search in sources) {
                    currentCoroutineContext().ensureActive()
                    val cover = runCatching { search() }.getOrNull() ?: continue
                    if (!query.accepts(cover)) continue
                    for (url in listOfNotNull(cover.url, cover.fallbackUrl)) {
                        if (!tried.add(url)) continue
                        // A title-only fallback may belong to the original release, not the local
                        // compilation. Keep it per track instead of applying it to the whole album.
                        store.save(url, if (query.trackSpecific) trackDirectory else directory)
                            ?.let { return@withLock it }
                    }
                }
            }
            null
        }
    }

    private fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun deleteCachedArtworkFiles(context: Context): Int = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            listOf(cacheDirectory(context), File(context.cacheDir, AnimatedArtworkCacheDirectoryName))
                .sumOf { root -> root.listFiles()?.count { it.deleteRecursively() } ?: 0 }
        }
    }

    suspend fun cachedArtworkFilesSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        listOf(cacheDirectory(context), File(context.cacheDir, AnimatedArtworkCacheDirectoryName))
            .sumOf { root -> if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L }
    }

    private fun localArtworkFile(context: Context, music: YosMediaItem): File? {
        val albumName = music.album?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val songPath = AudioMetadataUtils.resolveLocalAudioFilePath(context, music.uri) ?: return null
        val songDirectory = File(songPath).parentFile?.takeIf { it.isDirectory } ?: return null
        return animatedArtworkFile(songDirectory, albumName)
    }

    private fun isPlayableVideoFile(artworkFile: File): Boolean
    {
        return artworkFile.isFile && artworkFile.length() > 0L && firstVideoSampleTimeUs(artworkFile) != null
    }

    private fun cacheDirectory(context: Context): File
    {
        return File(context.filesDir, AnimatedArtworkCacheDirectoryName)
    }

    private fun cachedArtworkFile(context: Context, music: YosMediaItem, albumName: String): File
    {
        val artistName = music.albumArtists?.trim()?.takeIf { it.isNotEmpty() }
            ?: music.artistsName?.trim().orEmpty()

        return animatedArtworkCacheFile(cacheDirectory(context), artistName, albumName)
    }

    internal fun animatedArtworkCacheFile(cacheDirectory: File, artistName: String, albumName: String): File
    {
        val cacheIdentity = "${artistName.lowercase(Locale.ROOT)}\u0000${albumName.lowercase(Locale.ROOT)}"
        val cacheHash = MessageDigest.getInstance("SHA-256")
            .digest(cacheIdentity.toByteArray())
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

        return File(cacheDirectory, "$cacheHash.mp4")
    }

    internal fun animatedArtworkFileName(albumName: String): String
    {
        val safeAlbumName = albumName
            .trim()
            .replace(invalidFileNameCharacters, "_")
            .ifEmpty { "animated_artwork" }

        return "$safeAlbumName.mp4"
    }

    internal fun animatedArtworkFile(songDirectory: File, albumName: String): File
    {
        return File(File(songDirectory, AnimatedArtworkDirectoryName), animatedArtworkFileName(albumName))
    }

    internal fun pickBestStreamUrl(masterPlaylistText: String, masterPlaylistUrl: String): String? =
        streamVariantUrls(masterPlaylistText, masterPlaylistUrl).firstOrNull()

    internal fun streamVariantUrls(masterPlaylistText: String, masterPlaylistUrl: String): List<String>
    {
        val playlistLines = masterPlaylistText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val streamVariants = mutableListOf<StreamVariant>()

        playlistLines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) {return@forEachIndexed}

            val streamPath = playlistLines.getOrNull(index + 1)?.takeIf { !it.startsWith("#") } ?: return@forEachIndexed
            val resolution = resolutionRegex.find(line)?.groupValues
            val pixelCount = if (resolution != null) {resolution[1].toInt() * resolution[2].toInt()} else {0}
            val bandwidth = bandwidthRegex.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

            streamVariants += StreamVariant(
                url = absoluteUrl(masterPlaylistUrl, streamPath),
                isAvc = line.contains("avc1", ignoreCase = true),
                pixelCount = pixelCount,
                bandwidth = bandwidth
            )
        }

        return streamVariants
            .sortedWith(compareByDescending<StreamVariant> { it.isAvc }
                .thenByDescending { it.pixelCount }.thenByDescending { it.bandwidth })
            .map { it.url }.distinct()
    }

    internal fun extractMappedMp4Url(mediaPlaylistText: String, mediaPlaylistUrl: String): String?
    {
        mediaPlaylistText.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("#EXT-X-MAP"))
            {
                val mapUri = hlsMapUriRegex.find(line)?.groupValues?.getOrNull(1) ?: return@forEach
                if (mapUri.endsWith(".mp4", ignoreCase = true)) {return absoluteUrl(mediaPlaylistUrl, mapUri)}
            }
        }

        mediaPlaylistText.lineSequence().map { it.trim() }.forEach { line ->
            if (!line.startsWith("#") && line.endsWith(".mp4", ignoreCase = true))
            {
                return absoluteUrl(mediaPlaylistUrl, line)
            }
        }

        return null
    }

    private fun absoluteUrl(baseUrl: String, path: String): String
    {
        return URL(URL(baseUrl), path).toString()
    }

    private suspend fun readUrlText(url: String): String
    {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = NetworkTimeoutMilliseconds
        connection.readTimeout = NetworkTimeoutMilliseconds
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", CANVAS_UA)

        try
        {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {throw IllegalStateException("HTTP $responseCode")}

            return connection.inputStream.use { readUrlBytes(it, MaximumPlaylistBytes) }.toString(Charsets.UTF_8)
        }
        finally
        {
            connection.disconnect()
        }
    }

    internal suspend fun readUrlBytes(inputStream: InputStream, maximumBytes: Int): ByteArray
    {
        val outputStream = ByteArrayOutputStream(minOf(maximumBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0

        while (true)
        {
            currentCoroutineContext().ensureActive()
            val bytesRead = inputStream.read(buffer)
            if (bytesRead < 0) {break}
            totalBytes += bytesRead
            if (totalBytes > maximumBytes) {throw IOException("Response exceeds $maximumBytes bytes")}
            outputStream.write(buffer, 0, bytesRead)
        }

        return outputStream.toByteArray()
    }

    private suspend fun downloadResource(sourceUrl: String, destinationFile: File, maximumBytes: Long): Long {
        val connection = URL(sourceUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = NetworkTimeoutMilliseconds
        connection.readTimeout = NetworkTimeoutMilliseconds
        connection.setRequestProperty("User-Agent", CANVAS_UA)
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            if (connection.contentLength.toLong() > maximumBytes) throw IOException("Cover exceeds download limit")
            var total = 0L
            connection.inputStream.use { input ->
                destinationFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maximumBytes) throw IOException("Cover exceeds download limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
            return total
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun normalizeCachedMp4File(artworkFile: File): Boolean
    {
        val firstVideoSampleTimeUs = firstVideoSampleTimeUs(artworkFile) ?: return false
        if (firstVideoSampleTimeUs <= MaximumExpectedStartOffsetMicroseconds) {return true}

        val normalizedTemporaryFile = File(artworkFile.parentFile, ".${artworkFile.name}.normalized.tmp")
        val backupFile = File(artworkFile.parentFile, ".${artworkFile.name}.backup.tmp")
        normalizedTemporaryFile.delete()
        backupFile.delete()

        if (!normalizeMp4File(artworkFile, normalizedTemporaryFile)) {return false}
        if (!artworkFile.renameTo(backupFile))
        {
            normalizedTemporaryFile.delete()
            return true
        }

        if (!normalizedTemporaryFile.renameTo(artworkFile))
        {
            normalizedTemporaryFile.delete()
            backupFile.renameTo(artworkFile)
            return false
        }

        backupFile.delete()
        return true
    }

    private fun firstVideoSampleTimeUs(artworkFile: File): Long?
    {
        val extractor = MediaExtractor()

        try
        {
            extractor.setDataSource(artworkFile.absolutePath)

            for (trackIndex in 0 until extractor.trackCount)
            {
                val format = extractor.getTrackFormat(trackIndex)
                val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mimeType.startsWith("video/")) {continue}

                extractor.selectTrack(trackIndex)
                return extractor.sampleTime.takeIf { it >= 0L }
            }

            return null
        }
        catch (_: Exception)
        {
            return null
        }
        finally
        {
            extractor.release()
        }
    }

    private suspend fun normalizeMp4File(sourceFile: File, destinationFile: File): Boolean
    {
        destinationFile.delete()

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var wroteSample = false
        var stopSucceeded = true

        try
        {
            extractor.setDataSource(sourceFile.absolutePath)
            muxer = MediaMuxer(destinationFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val muxerTrackIndexes = addSupportedTracks(extractor, muxer)
            if (muxerTrackIndexes.none { it >= 0 }) {return false}

            val trackStartTimesUs = LongArray(extractor.trackCount) { Long.MIN_VALUE }
            val sampleBuffer = ByteBuffer.allocate(maxSampleInputSize(extractor))
            val bufferInfo = MediaCodec.BufferInfo()

            muxer.start()
            muxerStarted = true

            while (true)
            {
                currentCoroutineContext().ensureActive()
                val sampleTrackIndex = extractor.sampleTrackIndex
                if (sampleTrackIndex < 0) {break}

                val muxerTrackIndex = muxerTrackIndexes[sampleTrackIndex]
                if (muxerTrackIndex < 0)
                {
                    extractor.advance()
                    continue
                }

                sampleBuffer.clear()
                val sampleSize = extractor.readSampleData(sampleBuffer, 0)
                if (sampleSize < 0) {break}

                val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                if (trackStartTimesUs[sampleTrackIndex] == Long.MIN_VALUE)
                {
                    trackStartTimesUs[sampleTrackIndex] = sampleTimeUs
                }

                bufferInfo.set(
                    0,
                    sampleSize,
                    (sampleTimeUs - trackStartTimesUs[sampleTrackIndex]).coerceAtLeast(0L),
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }
                    else
                    {
                        0
                    }
                )
                muxer.writeSampleData(muxerTrackIndex, sampleBuffer, bufferInfo)
                wroteSample = true

                extractor.advance()
            }
        }
        catch (cancellationException: CancellationException)
        {
            throw cancellationException
        }
        catch (_: Exception)
        {
            wroteSample = false
        }
        finally
        {
            extractor.release()
            if (muxerStarted)
            {
                stopSucceeded = runCatching { muxer?.stop() }.isSuccess
            }
            muxer?.release()
        }

        if (!wroteSample || !stopSucceeded || destinationFile.length() == 0L)
        {
            destinationFile.delete()
            return false
        }

        return true
    }

    private fun addSupportedTracks(extractor: MediaExtractor, muxer: MediaMuxer): IntArray
    {
        val muxerTrackIndexes = IntArray(extractor.trackCount) { -1 }

        for (trackIndex in 0 until extractor.trackCount)
        {
            val format = extractor.getTrackFormat(trackIndex)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mimeType.startsWith("video/")) {continue}

            muxerTrackIndexes[trackIndex] = muxer.addTrack(format)
            extractor.selectTrack(trackIndex)
        }

        return muxerTrackIndexes
    }

    private fun maxSampleInputSize(extractor: MediaExtractor): Int
    {
        var maxSampleInputSize = DefaultSampleBufferBytes

        for (trackIndex in 0 until extractor.trackCount)
        {
            val format = extractor.getTrackFormat(trackIndex)
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
            {
                maxSampleInputSize = maxOf(
                    maxSampleInputSize,
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtMost(MaximumArtworkBytes.toInt())
                )
            }
        }

        return maxSampleInputSize
    }
}
