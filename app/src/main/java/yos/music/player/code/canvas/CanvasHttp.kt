package yos.music.player.code.canvas

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// One bounded client shared by the adapted BitChord catalogue providers.
internal object Http {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}

internal fun canvasGet(url: String, headers: Map<String, String> = emptyMap()): String? {
    val request = Request.Builder().url(url).apply {
        headers.forEach { (name, value) -> header(name, value) }
    }.build()
    return runCatching {
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.body == null) return@use null
            // Catalogue pages and manifests only; video downloads use a separate size budget.
            response.body!!.byteStream().use inputStream@ { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 8 * 1024 * 1024) return@inputStream null
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        }
    }.getOrNull()
}

internal const val CANVAS_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/122.0.0.0 Safari/537.36"
