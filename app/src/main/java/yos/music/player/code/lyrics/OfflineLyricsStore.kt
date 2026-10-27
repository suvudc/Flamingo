package yos.music.player.code.lyrics

import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Durable downloads, separate from Android's evictable cache directory. */
class OfflineLyricsStore(private val directory: File) {
    fun key(title: String, artist: String, album: String, durationMs: Long, provider: String = "auto"): String =
        MessageDigest.getInstance("SHA-256").digest(
            (listOf(title.trim(), artist.trim(), album.trim(), durationMs.toString()) +
                if (provider == "auto") emptyList() else listOf(provider)).joinToString("\u0000").toByteArray()
        ).joinToString("") { "%02x".format(it) }

    private fun file(key: String): File {
        require(key.matches(Regex("[a-f0-9]{64}")))
        return File(directory, "$key.lyrics")
    }
    fun read(key: String): String? = runCatching {
        file(key).takeIf { it.length() in 1..MAX_BYTES }?.readText()
    }.getOrNull()

    @Synchronized fun write(key: String, content: String) {
        require(content.toByteArray().size.toLong() in 1..MAX_BYTES)
        check(directory.isDirectory || directory.mkdirs())
        val destination = file(key)
        val temporary = File(directory, ".$key-${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { it.write(content.toByteArray()); it.fd.sync() }
            check(temporary.renameTo(destination)) { "Could not save lyrics" }
        } finally { temporary.delete() }
    }
    companion object { const val MAX_BYTES = 2_000_000L }
}
