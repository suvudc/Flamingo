package yos.music.player.code.canvas

/** Local file tags need more fallbacks than BitChord's catalogue metadata. */
internal object CanvasLookup {
    data class Query(val name: String, val artist: String, val album: String?, val albumSearch: Boolean) {
        fun accepts(cover: CanvasArtwork) = cover.matches(name, artist, album)
        val trackSpecific: Boolean get() = !albumSearch && album == null
    }

    fun queries(title: String?, artist: String?, albumArtist: String?, album: String?): List<Query> {
        val song = clean(title)
        val trackArtist = meaningful(clean(artist))
        val releaseArtist = meaningful(clean(albumArtist)).takeUnless {
            it?.normalizeForMatch() in setOf("various artists", "various", "va")
        } ?: trackArtist
        val credit = releaseArtist ?: return emptyList()
        val release = meaningful(album?.trim().orEmpty())
        val releases = listOfNotNull(release, release?.replace(editionSuffix, "")?.trim()?.takeIf { it.isNotBlank() })
            .distinct()
        return buildList {
            for (name in releases) add(Query(name, credit, name, true))
            if (song.isNotBlank()) {
                // Preserve version labels such as Live and Remix: those identify different recordings.
                if (release != null) add(Query(song, trackArtist ?: credit, release, false))
                // Like BitChord when its album has not resolved yet: match exact song + artist.
                add(Query(song, trackArtist ?: credit, null, false))
                if (trackArtist != null && trackArtist != credit) add(Query(song, credit, null, false))
            }
        }.distinct()
    }

    fun cacheAlbum(album: String?, title: String?): String = meaningful(album?.trim().orEmpty())
        ?: "\u0001track:${clean(title)}"

    private fun meaningful(value: String): String? = value.takeIf {
        it.isNotBlank() && it.normalizeForMatch() !in setOf("unknown", "unknown album", "unknown artist")
    }

    internal fun clean(value: String?): String = value.orEmpty()
        .replace(packaging, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ").trim()

    private val packaging = Regex(
        """[\[(](?:official(?: music)? (?:video|audio)|official|lyric(?:al|s)?(?: video)?|audio|video|4k(?: video)?)[^\])]*[\])]|\b(?:official (?:music video|video|audio)|full song|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val editionSuffix = Regex(
        """\s*[\[(](?:deluxe(?: edition| version)?|expanded(?: edition)?|(?:\d{4} )?remaster(?:ed)?(?: \d{4})?(?: edition)?|anniversary edition)[\])]\s*$""",
        RegexOption.IGNORE_CASE,
    )
}
