package yos.music.player.code

/** Small in-memory trace for the debug artwork report; never written or sent anywhere. */
object ArtworkDiagnostics {
    private val events = ArrayDeque<String>()

    @Synchronized
    fun record(event: String) {
        if (events.size == 8) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): String = events.joinToString("\n").ifEmpty { "No artwork requests recorded yet." }
}
