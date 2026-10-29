package yos.music.player.code

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.URI

/** A cover becomes visible only after every resource is on disk and the directory is committed. */
internal class OfflineArtworkStore(
    private val readText: suspend (String) -> String,
    private val download: suspend (String, File, Long) -> Long,
    private val validateVideo: suspend (File) -> Boolean,
) {
    companion object {
        private const val MAX_BYTES = 50L * 1024 * 1024
        private const val MAX_RESOURCES = 256
        private val uriAttribute = Regex("URI=\"([^\"]+)\"")

        /** The marker lists exact sizes, so partial, missing or truncated files are never reused. */
        fun cached(directory: File): File? = runCatching {
            val lines = File(directory, "complete").readLines()
            val entry = lines.firstOrNull() ?: return null
            if (entry != "cover.mp4" && entry != "cover.m3u8") return null
            if (lines.size < 2) return null
            for (line in lines.drop(1)) {
                val parts = line.split(':')
                if (parts.size != 2 || !Regex("[A-Za-z0-9_.-]+").matches(parts[0])) return null
                val file = File(directory, parts[0])
                val size = parts[1].toLongOrNull() ?: return null
                if (size <= 0 || !file.isFile || file.length() != size) return null
            }
            File(directory, entry).takeIf { it.isFile && it.length() > 0 }
        }.getOrNull()
    }

    suspend fun save(url: String, directory: File): File? {
        cached(directory)?.let { return it }
        val staging = File(directory.parentFile, ".${directory.name}.download")
        staging.deleteRecursively()
        if (!staging.mkdirs()) return null
        try {
            val entry = if (URI(url).path.orEmpty().endsWith(".m3u8", true)) {
                saveHls(url, staging)
                "cover.m3u8"
            } else {
                val video = File(staging, "cover.mp4")
                download(url, video, MAX_BYTES)
                if (!validateVideo(video)) throw IOException("Invalid video")
                "cover.mp4"
            }
            currentCoroutineContext().ensureActive()
            val resources = staging.listFiles().orEmpty().filter { it.isFile }
            File(staging, "complete").writeText(
                entry + "\n" + resources.joinToString("\n") { "${it.name}:${it.length()}" }
            )
            // No existing complete cover is removed while another download is in flight.
            cached(directory)?.let { return it }
            directory.deleteRecursively()
            if (!staging.renameTo(directory)) throw IOException("Cannot commit cover")
            return cached(directory)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun saveHls(source: String, directory: File, depth: Int = 0) {
        if (depth >= 3) throw IOException("Too many nested cover playlists")
        val playlist = readText(source)
        val variants = AnimatedArtworkLibrary.streamVariantUrls(playlist, source)
        if (variants.isNotEmpty()) {
            // Streaming BitChord can select a lower rendition. Offline download must do so too
            // when the largest version is unavailable or exceeds the complete-cover size budget.
            for (variant in variants.take(6)) {
                currentCoroutineContext().ensureActive()
                try {
                    saveHls(variant, directory, depth + 1)
                    return
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    directory.listFiles().orEmpty().forEach { it.delete() }
                }
            }
            throw IOException("No downloadable rendition")
        }
        val plan = localizePlaylist(playlist, source)
        var remaining = MAX_BYTES
        for ((remote, local) in plan.resources) {
            currentCoroutineContext().ensureActive()
            val size = download(remote, File(directory, local), remaining)
            if (size <= 0 || size > remaining) throw IOException("Invalid resource size")
            remaining -= size
        }
        File(directory, "cover.m3u8").writeText(plan.text)
    }

    internal data class PlaylistPlan(val text: String, val resources: Map<String, String>)

    internal fun localizePlaylist(text: String, baseUrl: String): PlaylistPlan {
        if (!text.trimStart().startsWith("#EXTM3U") || !text.contains("#EXT-X-ENDLIST")) {
            throw IOException("Only complete VOD covers can be saved")
        }
        if (text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-MEDIA:") ||
            text.contains("#EXT-X-SESSION-KEY") || text.contains("#EXT-X-PART")) {
            throw IOException("Unresolved playlist")
        }
        val resources = linkedMapOf<String, String>()
        fun local(remote: String): String {
            val resolved = URI(baseUrl).resolve(remote)
            if (resolved.scheme !in listOf("https", "http")) throw IOException("Unsupported cover URI")
            val key = resolved.toASCIIString()
            return resources.getOrPut(key) {
                if (resources.size >= MAX_RESOURCES) throw IOException("Too many cover resources")
                // Preserve common extensions to help the HLS extractor identify media.
                val extension = resolved.path.substringAfterLast('.', "bin")
                    .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "bin"
                "asset-${resources.size}.$extension"
            }
        }
        var segments = 0
        val rewritten = text.lineSequence().map { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-KEY:") -> {
                    if (!line.contains("METHOD=NONE") &&
                        (!line.contains("METHOD=AES-128") || line.contains("KEYFORMAT="))) {
                        throw IOException("Unsupported cover encryption")
                    }
                    uriAttribute.replace(line) { "URI=\"${local(it.groupValues[1])}\"" }
                }
                line.startsWith("#") -> uriAttribute.replace(line) { "URI=\"${local(it.groupValues[1])}\"" }
                line.isNotEmpty() -> { segments++; local(line) }
                else -> line
            }
        }.joinToString("\n")
        if (segments == 0 || resources.isEmpty()) throw IOException("Empty cover playlist")
        return PlaylistPlan(rewritten, resources)
    }
}
