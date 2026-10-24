package com.msharwaan.exoplayer.show

import com.msharwaan.exoplayer.data.EpisodeInfo
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import com.msharwaan.exoplayer.data.SeasonInfo
import com.msharwaan.exoplayer.data.TitleInfo
import com.msharwaan.exoplayer.library.Library

/** One episode card: a file on the SD card, or an episode TMDB knows that isn't there (greyed). */
data class EpisodeEntry(
    val season: Int,
    val number: Int?,
    val title: String,
    val overview: String?,
    val stillPath: String?,
    val runtimeMin: Int?,
    /** Null when the episode isn't on the SD card. */
    val fileId: Long?,
    val watched: Boolean,
    /** How far through, 0 when not started. */
    val progress: Float,
    val leftNote: String?,
) {
    val missing get() = fileId == null
}

data class SeasonEntry(
    val number: Int,
    val name: String,
    val overview: String?,
    val year: Int?,
    val posterPath: String?,
    val episodes: List<EpisodeEntry>,
)

/** A series or anime as the series screens show it (D14, D15, D21). */
data class ShowData(
    val title: TitleInfo,
    val seasons: List<SeasonEntry>,
    /** The episode Continue watching would play. */
    val nextUp: EpisodeEntry?,
) {
    /** One season: the season cards are skipped (D14). */
    val singleSeason get() = seasons.size <= 1

    val meta: String get() = buildList {
        title.year?.let { add(it.toString()) }
        add(if (title.tab == "ANIME") "Anime series" else "Series")
        title.genres?.let { add(it) }
        if (seasons.size > 1) add("${seasons.size} seasons")
        val count = title.episodeCount ?: seasons.sumOf { s -> s.episodes.count { !it.missing } }
        add(if (count == 1) "1 episode" else "$count episodes")
    }.joinToString(" · ")

    companion object {
        /**
         * Seasons come from the files (an anime with no season number is season 1), filled in
         * with TMDB's names, art and missing episodes once Fetch info has run. Plain Kotlin, unit tested.
         */
        fun build(
            title: TitleInfo,
            files: List<MediaFile>,
            playback: List<PlaybackState>,
            seasonInfo: List<SeasonInfo>,
            episodeInfo: List<EpisodeInfo>,
        ): ShowData {
            val state = playback.associateBy { it.mediaFileId }
            val ordered = files.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: Int.MAX_VALUE }, { it.fileName }))
            val infoOf = episodeInfo.associateBy { it.season to it.number }
            val seasonNumbers = (ordered.map { it.season ?: 1 } + episodeInfo.map { it.season }).filter { it > 0 }.distinct().sorted()
                .ifEmpty { listOf(1) }

            fun entry(f: MediaFile): EpisodeEntry {
                val season = f.season ?: 1
                val info = f.episode?.let { infoOf[season to it] }
                val s = state[f.id]
                val started = s != null && s.durationMs > 0 && s.positionMs > 0 && !s.watched
                return EpisodeEntry(
                    season = season,
                    number = f.episode,
                    title = info?.title ?: f.episode?.let { "Episode $it" } ?: f.fileName.substringBeforeLast('.'),
                    overview = info?.overview,
                    stillPath = info?.stillPath,
                    runtimeMin = info?.runtimeMin ?: (f.durationMs / 60_000).toInt().takeIf { it > 0 },
                    fileId = f.id,
                    watched = s?.watched == true,
                    progress = if (started) (s!!.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f,
                    leftNote = if (started) Library.formatLeft(s!!.durationMs - s.positionMs) + " left" else null,
                )
            }

            val seasons = seasonNumbers.map { n ->
                val info = seasonInfo.firstOrNull { it.number == n }
                val onCard = ordered.filter { (it.season ?: 1) == n }.map(::entry)
                val have = onCard.mapNotNull { it.number }.toSet()
                val missing = episodeInfo.filter { it.season == n && it.number !in have }.map {
                    EpisodeEntry(n, it.number, it.title, it.overview, it.stillPath, it.runtimeMin, null, false, 0f, null)
                }
                SeasonEntry(
                    number = n,
                    name = info?.name ?: "Season $n",
                    overview = info?.overview,
                    year = info?.year,
                    posterPath = info?.posterPath,
                    episodes = (onCard + missing).sortedWith(compareBy({ it.number ?: Int.MAX_VALUE }, { it.title })),
                )
            }.filter { it.episodes.isNotEmpty() }

            val next = Library.nextEpisode(ordered, state)?.let(::entry)
            return ShowData(title, seasons, next)
        }

        /** "S01E02", or "E02" when the show has one season. */
        fun label(e: EpisodeEntry, singleSeason: Boolean): String = when {
            e.number == null -> e.title
            singleSeason -> "Episode ${e.number}"
            else -> "S%02dE%02d".format(e.season, e.number)
        }
    }
}
