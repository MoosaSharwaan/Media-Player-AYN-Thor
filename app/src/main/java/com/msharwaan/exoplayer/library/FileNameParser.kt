package com.msharwaan.exoplayer.library

/** What a video file is, worked out from its name and folders alone (no internet). */
data class ParsedName(
    val kind: Kind,
    /** Clean title: the movie or show name. */
    val title: String,
    val year: Int? = null,
    /** Season number; null for absolute-numbered episodes (most anime). */
    val season: Int? = null,
    val episode: Int? = null,
    /** True for names like "[SubsPlease] …" or files under an "Anime" folder. */
    val animeHint: Boolean = false,
) {
    enum class Kind { MOVIE, EPISODE, OTHER }
}

/**
 * Reads names like `The.Dark.Knight.2008.1080p.mkv`, `Breaking.Bad.S02E05.mkv`,
 * `[SubsPlease] Frieren - 12 (1080p).mkv` and `Show/Season 2/05 - Title.mkv`.
 *
 * Works for loose files in one folder and for Show/Season folders (decision D33).
 * Plain Kotlin with no Android types, so it is unit tested on the PC.
 */
object FileNameParser {

    private val VIDEO_EXTENSIONS = setOf(
        "mkv", "mp4", "m4v", "avi", "webm", "ts", "m2ts", "mov", "wmv", "flv", "mpg", "mpeg", "3gp",
    )

    fun isVideo(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    /** Words that end a title: quality, source, codec and release tags. */
    private val STOP_WORDS = setOf(
        "2160p", "1080p", "1080i", "720p", "576p", "480p", "4k", "uhd", "hdr", "hdr10", "dv", "sdr",
        "bluray", "blu-ray", "bdrip", "brrip", "bdremux", "remux", "web-dl", "webdl", "webrip", "web", "hdtv",
        "dvdrip", "dvd", "hdrip", "x264", "x265", "h264", "h265", "h.264", "h.265", "hevc", "avc", "av1", "xvid",
        "10bit", "8bit", "aac", "ac3", "eac3", "dts", "dts-hd", "truehd", "atmos", "ddp5", "dd5", "flac", "opus",
        "proper", "repack", "extended", "unrated", "remastered", "directors", "imax", "internal", "limited",
        "multi", "dual", "subbed", "dubbed", "complete",
    )

    private val CAMERA_NAME = Regex("""^(VID|IMG|MOV|PXL|DSC|DJI|GOPR|GH\d|MVI|CIMG)[_-]?\d""", RegexOption.IGNORE_CASE)
    private val LEADING_GROUP = Regex("""^\s*\[[^\]]+]\s*""")
    private val BRACKETS = Regex("""[\[(][^\])]*[\])]""")
    private val SXXEXX = Regex("""(?i)\bS(\d{1,2})[ ._-]?E(\d{1,3})(?:[ ._-]?E\d{1,3})*\b""")
    private val NXNN = Regex("""(?i)(?<![\dx])(\d{1,2})x(\d{2,3})\b""")
    private val SEASON_EPISODE_WORDS = Regex("""(?i)\bseason[ ._-]*(\d{1,2})[ ._-]*(?:episode|ep)[ ._-]*(\d{1,3})\b""")
    private val EPISODE_WORD = Regex("""(?i)\b(?:episode|ep|e)[ ._-]?(\d{1,4})\b""")
    private val ANIME_DASH_NUMBER = Regex("""\s-\s(\d{1,4})(?:v\d)?(?=\s|$)""")
    private val LEADING_NUMBER = Regex("""^(\d{1,4})(?:\s*-\s*|\s+|$)""")
    private val YEAR = Regex("""(?<!\d)(19[2-9]\d|20[0-4]\d)(?!\d|p)""")
    private val SEASON_FOLDER = Regex("""(?i)^(?:season|series|staffel|saison|s)[ ._-]*(\d{1,2})$""")

    /**
     * @param fileName the file name with its extension
     * @param folders folder names from the library root down to the file's folder
     */
    fun parse(fileName: String, folders: List<String> = emptyList()): ParsedName {
        val base = fileName.substringBeforeLast('.')
        val animeHint = LEADING_GROUP.containsMatchIn(base) || folders.any { it.equals("anime", ignoreCase = true) }

        if (CAMERA_NAME.containsMatchIn(base) ||
            folders.any { f -> OTHER_FOLDERS.any { f.equals(it, ignoreCase = true) } }
        ) return ParsedName(ParsedName.Kind.OTHER, base, animeHint = false)

        var text = LEADING_GROUP.replace(base, "")
        // Keep a year in brackets — "Heat (1995)" — before dropping other bracketed tags.
        val bracketYear = Regex("""[\[(](19[2-9]\d|20[0-4]\d)[\])]""").find(text)?.groupValues?.get(1)?.toInt()
        text = BRACKETS.replace(text, " ")
        text = normalise(text)

        val seasonFolder = folders.lastOrNull()?.let { SEASON_FOLDER.find(it.trim())?.groupValues?.get(1)?.toInt() }
        val showFolder = if (seasonFolder != null) folders.getOrNull(folders.size - 2) else folders.lastOrNull()

        // 1. SxxEyy, 1x02, "Season 1 Episode 2"
        val se = SXXEXX.find(text) ?: SEASON_EPISODE_WORDS.find(text) ?: NXNN.find(text)
        if (se != null) {
            val before = text.substring(0, se.range.first)
            val showYear = findYear(before)
            // "Doctor.Who.2005.S01E01": the year tells two shows apart; it isn't part of the name.
            val title = cleanTitle(if (showYear != null) before.replace(Regex("""\s$showYear\W*$"""), "") else before)
                .ifEmpty { cleanFolder(showFolder) }
            return ParsedName(
                ParsedName.Kind.EPISODE, title, year = showYear,
                season = se.groupValues[1].toInt(), episode = se.groupValues[2].toInt(), animeHint = animeHint,
            )
        }

        // 2. Files inside a season folder: "Show/Season 2/05 - Title.mkv" or ".../E05.mkv"
        if (seasonFolder != null) {
            val number = LEADING_NUMBER.find(text)?.groupValues?.get(1)?.toInt()
                ?: EPISODE_WORD.find(text)?.groupValues?.get(1)?.toInt()
            if (number != null) {
                return ParsedName(
                    ParsedName.Kind.EPISODE, cleanFolder(showFolder), season = seasonFolder, episode = number,
                    animeHint = animeHint,
                )
            }
        }

        // 3. Anime style: "Frieren - 12", "Frieren - 12v2"
        ANIME_DASH_NUMBER.find(text)?.let { m ->
            val title = cleanTitle(text.substring(0, m.range.first))
            if (title.isNotEmpty()) {
                return ParsedName(ParsedName.Kind.EPISODE, title, episode = m.groupValues[1].toInt(), animeHint = animeHint)
            }
        }

        // 4. "Show Name Episode 5" / "Show Name E05"
        EPISODE_WORD.find(text)?.let { m ->
            val title = cleanTitle(text.substring(0, m.range.first))
            if (title.isNotEmpty()) {
                return ParsedName(ParsedName.Kind.EPISODE, title, episode = m.groupValues[1].toInt(), animeHint = animeHint)
            }
        }

        // 5. A movie: title up to the year or the first release tag.
        val year = bracketYear ?: findYear(text)
        val cut = listOfNotNull(
            year?.let { y -> Regex("""(?<!\d)$y(?!\d)""").find(text)?.range?.first },
            firstStopWord(text),
        ).minOrNull()
        var title = cleanTitle(if (cut != null) text.substring(0, cut) else text)
        // A junk file name inside a movie folder: "Heat (1995)/movie.mkv"
        if (title.isEmpty() || (year == null && folders.isNotEmpty() && folderYear(folders.last()) != null)) {
            val folder = folders.lastOrNull()
            if (folder != null) {
                val parsed = parse("$folder.mkv")
                if (parsed.kind == ParsedName.Kind.MOVIE) return parsed.copy(animeHint = animeHint || parsed.animeHint)
            }
        }
        if (title.isEmpty()) title = cleanTitle(text)
        return ParsedName(ParsedName.Kind.MOVIE, title, year = year, animeHint = animeHint)
    }

    /** Folders whose videos always go to Others (home videos and the like). */
    private val OTHER_FOLDERS = listOf("others", "other", "home videos", "home video", "camera", "dcim", "clips", "recordings")

    private fun normalise(s: String): String =
        s.replace('_', ' ')
            .replace(Regex("""(?<=[^\s.])\.(?=[^\s.])"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun cleanTitle(s: String): String =
        s.replace(Regex("""[\s\-–:._]+$"""), "").replace(Regex("""^[\s\-–:._]+"""), "").replace(Regex("""\s+"""), " ").trim()

    private fun cleanFolder(folder: String?): String =
        folder?.let { cleanTitle(normalise(BRACKETS.replace(LEADING_GROUP.replace(it, ""), " "))) }.orEmpty()

    private fun folderYear(folder: String): Int? = YEAR.find(folder)?.groupValues?.get(1)?.toInt()

    /** The last plausible year that is not the whole title ("2001: A Space Odyssey" keeps 2001 as title). */
    private fun findYear(text: String): Int? {
        val matches = YEAR.findAll(text).toList()
        val candidate = matches.lastOrNull { it.range.first > 0 } ?: return null
        return candidate.groupValues[1].toInt()
    }

    private fun firstStopWord(text: String): Int? {
        var index = 0
        for (word in text.split(' ')) {
            if (index > 0 && word.lowercase().trim('-', '.') in STOP_WORDS) return index
            index += word.length + 1
        }
        return null
    }
}
