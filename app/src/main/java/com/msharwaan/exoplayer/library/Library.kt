package com.msharwaan.exoplayer.library

import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import com.msharwaan.exoplayer.data.TitleInfo

/** The five Browsing tabs, in order (decision D2). */
enum class Tab(val label: String) {
    CONTINUE("Continue watching"), MOVIES("Movies"), SERIES("Series"), ANIME("Anime"), OTHERS("Others"),
}

/** One card in Browsing. */
data class Card(
    val groupKey: String,
    /** MOVIE, SHOW or OTHER. Shows open the series flow; the rest play straight away (D8, D21, D22). */
    val kind: String,
    val name: String,
    val year: Int?,
    /** "2014 · 2 h 49 m · Sci-fi, Drama", or for shows "… · 5 seasons · 62 episodes". */
    val meta: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    /** The file A plays: a movie's file, or the episode to continue. */
    val playFileId: Long?,
    /** Continue watching only: how far through, and "S02E05 · 28 min left". */
    val progress: Float = 0f,
    val resumeNote: String? = null,
    /** Newest file time, for "Recently added". */
    val addedAt: Long = 0,
    /** Every file of it watched, for "Unwatched only". */
    val watched: Boolean = false,
    /** Genres, for search. */
    val genres: String? = null,
)

/** Browsing's sort and filter, stepped through with Select. */
enum class BrowseOrder(val label: String) { NAME("A–Z"), ADDED("Recently added"), YEAR("Year"), UNWATCHED("Unwatched only") }

/** Builds the tabs from the database rows. Plain Kotlin, unit tested. */
object Library {

    /** Watched at 90 % (FEATURES: Resume and watched). */
    const val WATCHED_AT = 0.9f

    fun build(files: List<MediaFile>, titles: List<TitleInfo>, playback: List<PlaybackState>): Map<Tab, List<Card>> {
        val byGroup = files.groupBy { it.groupKey }
        val state = playback.associateBy { it.mediaFileId }
        val titleOf = titles.associateBy { it.groupKey }

        val cards = byGroup.mapNotNull { (key, groupFiles) ->
            val title = titleOf[key] ?: return@mapNotNull null
            val ordered = groupFiles.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }, { it.fileName }))
            title to card(title, ordered, state)
        }

        val result = Tab.entries.associateWith { mutableListOf<Card>() }
        cards.forEach { (title, card) ->
            val tab = runCatching { Tab.valueOf(title.tab) }.getOrDefault(Tab.OTHERS)
            result.getValue(tab) += card
        }
        result.forEach { (_, list) -> list.sortBy { it.name.lowercase() } }
        result.getValue(Tab.CONTINUE) += continueWatching(byGroup, titleOf, state)
        return result
    }

    private fun card(title: TitleInfo, files: List<MediaFile>, state: Map<Long, PlaybackState>): Card = Card(
        groupKey = title.groupKey,
        // A show with one episode (an anime film, a special) plays straight away like a movie (D22).
        kind = if (title.kind == "SHOW" && files.size == 1) "MOVIE" else title.kind,
        name = title.name,
        year = title.year,
        meta = meta(title, files),
        overview = title.overview,
        posterPath = title.posterPath,
        backdropPath = title.backdropPath,
        playFileId = if (title.kind == "SHOW") nextEpisode(files, state)?.id else files.firstOrNull()?.id,
        addedAt = files.maxOfOrNull { it.modified } ?: 0,
        watched = files.isNotEmpty() && files.all { state[it.id]?.watched == true },
        genres = title.genres,
    )

    /** Half-watched movies and the next episode of each show you've started, newest first. */
    fun continueWatching(
        byGroup: Map<String, List<MediaFile>>,
        titleOf: Map<String, TitleInfo>,
        state: Map<Long, PlaybackState>,
    ): List<Card> {
        data class Entry(val card: Card, val lastPlayed: Long)
        val entries = byGroup.mapNotNull { (key, groupFiles) ->
            val title = titleOf[key] ?: return@mapNotNull null
            val ordered = groupFiles.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }, { it.fileName }))
            val played = ordered.mapNotNull { f -> state[f.id]?.takeIf { it.lastPlayed > 0 }?.let { f to it } }
            if (played.isEmpty()) return@mapNotNull null
            val lastPlayed = played.maxOf { it.second.lastPlayed }
            val target = if (title.kind == "SHOW") nextEpisode(ordered, state) else ordered.firstOrNull { state[it.id]?.watched != true }
            target ?: return@mapNotNull null
            val s = state[target.id]
            val progress = if (s != null && s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
            val left = if (s != null && s.durationMs > 0) formatLeft(s.durationMs - s.positionMs) else null
            val label = if (title.kind == "SHOW") episodeLabel(target) else null
            Entry(
                card(title, ordered, state).copy(
                    playFileId = target.id,
                    progress = progress,
                    resumeNote = listOfNotNull(label, left?.let { "$it left" }).joinToString(" · ").ifEmpty { "Up next" },
                ),
                lastPlayed,
            )
        }
        return entries.sortedByDescending { it.lastPlayed }.map { it.card }
    }

    /** The episode to continue: the started one if unfinished, otherwise the next unwatched. */
    fun nextEpisode(ordered: List<MediaFile>, state: Map<Long, PlaybackState>): MediaFile? {
        val lastIndex = ordered.indexOfLast { (state[it.id]?.lastPlayed ?: 0) > 0 }
        if (lastIndex < 0) return ordered.firstOrNull()
        val last = ordered[lastIndex]
        if (state[last.id]?.watched != true) return last
        return ordered.drop(lastIndex + 1).firstOrNull { state[it.id]?.watched != true }
    }

    fun episodeLabel(f: MediaFile): String = when {
        f.season != null && f.episode != null -> "S%02dE%02d".format(f.season, f.episode)
        f.episode != null -> "Episode ${f.episode}"
        else -> f.fileName.substringBeforeLast('.')
    }

    private fun meta(title: TitleInfo, files: List<MediaFile>): String {
        val parts = mutableListOf<String>()
        title.year?.let { parts += it.toString() }
        when (title.kind) {
            "MOVIE" -> {
                title.runtimeMin?.let { parts += formatRuntime(it) }
                title.genres?.let { parts += it }
            }
            "SHOW" -> {
                parts += if (title.tab == "ANIME") "Anime series" else "Series"
                title.genres?.let { parts += it }
                title.seasonCount?.let { parts += if (it == 1) "1 season" else "$it seasons" }
                val count = title.episodeCount ?: files.size
                parts += if (count == 1) "1 episode" else "$count episodes"
            }
            else -> parts += if (files.size == 1) "1 video" else "${files.size} videos"
        }
        return parts.joinToString(" · ")
    }

    /** Applies Browsing's order to a tab's cards. Continue watching keeps its own order. */
    fun order(cards: List<Card>, order: BrowseOrder): List<Card> = when (order) {
        BrowseOrder.NAME -> cards.sortedBy { it.name.lowercase() }
        BrowseOrder.ADDED -> cards.sortedByDescending { it.addedAt }
        BrowseOrder.YEAR -> cards.sortedWith(compareByDescending<Card> { it.year ?: 0 }.thenBy { it.name.lowercase() })
        BrowseOrder.UNWATCHED -> cards.filterNot { it.watched }.sortedBy { it.name.lowercase() }
    }

    /** Titles matching every word of [query], in name, year or genres; accents and dots don't matter. */
    fun search(all: Collection<Card>, query: String): List<Card> {
        val words = fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return all.distinctBy { it.groupKey }.filter { c ->
            val hay = fold(listOfNotNull(c.name, c.year?.toString(), c.genres).joinToString(" "))
            words.all { w -> hay.split(' ').any { it.startsWith(w) } }
        }.sortedBy { it.name.lowercase() }
    }

    /** "Pokémon: The.Movie" → "pokemon the movie". */
    private fun fold(s: String): String =
        LibraryScanner.normalise(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), ""))

    fun formatRuntime(minutes: Int): String = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} m" else "$minutes min"

    fun formatLeft(ms: Long): String {
        val min = (ms / 60_000).coerceAtLeast(1)
        return if (min >= 60) "${min / 60} h ${min % 60} m" else "$min min"
    }
}
