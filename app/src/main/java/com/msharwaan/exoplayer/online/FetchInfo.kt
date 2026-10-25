package com.msharwaan.exoplayer.online

import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.EpisodeInfo
import com.msharwaan.exoplayer.data.MatchOverride
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.SeasonInfo
import com.msharwaan.exoplayer.data.TitleInfo
import com.msharwaan.exoplayer.library.LibraryScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** TMDB refused the key (401): stops the whole run, since every request would fail the same way. */
class KeyRejected : IOException("TMDB didn't accept the API key")

/**
 * Fetch info (D32, D36, D38): fills in titles, artwork, seasons and episodes, fetching only
 * what's missing. A title that's complete makes no requests at all; for a show, only seasons
 * holding episodes that aren't filled in yet are asked for. [fix] refetches one title from
 * scratch after Fix match.
 */
class FetchInfo(
    private val db: AppDatabase,
    private val http: Http,
    private val images: ImageStore,
    private val tmdb: Tmdb,
    private val fanart: Fanart?,
    private val anilist: AniList?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Report(val fetched: Int, val upToDate: Int, val notFound: List<String>, val failed: List<String>)

    private enum class Outcome { FETCHED, UP_TO_DATE, NOT_FOUND, SKIPPED }

    suspend fun fetch(keys: List<String>, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): Report = withContext(Dispatchers.IO) {
        var fetched = 0
        var upToDate = 0
        val notFound = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var offline = 0
        keys.distinct().forEachIndexed { i, key ->
            progress(i, keys.size)
            val name = db.library().title(key)?.name ?: key
            try {
                when (one(key, force = false)) {
                    Outcome.FETCHED -> fetched++
                    Outcome.UP_TO_DATE -> upToDate++
                    Outcome.NOT_FOUND -> notFound += name
                    Outcome.SKIPPED -> Unit
                }
                offline = 0
            } catch (e: HttpException) {
                if (e.code == 401) throw KeyRejected()
                failed += name
            } catch (e: IOException) {
                failed += name
                // Three connection failures in a row: the connection is gone, stop asking.
                if (++offline >= 3) throw e
            }
        }
        progress(keys.size, keys.size)
        Report(fetched, upToDate, notFound, failed)
    }

    /** After Fix match: forget the old match and fetch this title fresh. Returns false when TMDB has nothing for it. */
    suspend fun fix(key: String, override: MatchOverride): Boolean = withContext(Dispatchers.IO) {
        val dao = db.library()
        dao.upsertOverride(override)
        dao.deleteSeasons(key)
        dao.deleteEpisodes(key)
        images.clear(key)
        val t = dao.title(key) ?: return@withContext false
        dao.upsertTitle(
            t.copy(
                kind = if (override.tmdbType == Tmdb.MOVIE) "MOVIE" else "SHOW",
                overview = null, genres = null, runtimeMin = null, rating = null, seasonCount = null, episodeCount = null,
                tmdbId = null, tmdbType = null, posterPath = null, backdropPath = null, logoPath = null, fetchedAt = null,
            ),
        )
        try {
            one(key, force = true) == Outcome.FETCHED
        } catch (e: HttpException) {
            if (e.code == 401) throw KeyRejected() else throw e
        }
    }

    private suspend fun one(key: String, force: Boolean): Outcome {
        val dao = db.library()
        val title = dao.title(key) ?: return Outcome.SKIPPED
        // Others are your own videos: nothing to look up.
        if (title.kind == "OTHER") return Outcome.SKIPPED
        val override = dao.override(key)
        val asMovie = override?.tmdbType?.let { it == Tmdb.MOVIE } ?: (title.kind == "MOVIE")
        return if (asMovie) movie(title, override, force) else show(title, dao.filesOf(key), override, force)
    }

    // ---- movies ----

    private suspend fun movie(title: TitleInfo, override: MatchOverride?, force: Boolean): Outcome {
        val fromAniList = title.tmdbType == ANILIST
        val complete = title.tmdbId != null && title.overview != null && images.has(title.posterPath) && (fromAniList || images.has(title.backdropPath))
        if (complete && !force) return Outcome.UP_TO_DATE

        val id = override?.tmdbId?.takeIf { override.tmdbType == Tmdb.MOVIE }
            ?: title.tmdbId?.takeIf { title.tmdbType == Tmdb.MOVIE }
            ?: best(tmdb.searchMovies(title.name, title.year).ifEmpty { if (title.year != null) tmdb.searchMovies(title.name) else emptyList() }, title)?.id
            ?: return if (title.tab == "ANIME") fromAniList(title) else Outcome.NOT_FOUND

        val m = tmdb.movie(id)
        var poster = title.posterPath.takeIf { images.has(it) }
        var backdrop = title.backdropPath.takeIf { images.has(it) }
        if (poster == null) poster = m.posterPath?.let { images.save(http, title.groupKey, "poster", tmdb.imageUrl(it, "w342"), ImageStore.POSTER) }
        if (backdrop == null) backdrop = m.backdropPath?.let { images.save(http, title.groupKey, "backdrop", tmdb.imageUrl(it, "w1280"), ImageStore.BACKDROP) }
        val fanart = fanart
        if ((poster == null || backdrop == null) && fanart != null) {
            val art = runCatching { fanart.movie(id) }.getOrNull()
            if (poster == null) poster = art?.posterUrl?.let { images.save(http, title.groupKey, "poster", it, ImageStore.POSTER) }
            if (backdrop == null) backdrop = art?.backdropUrl?.let { images.save(http, title.groupKey, "backdrop", it, ImageStore.BACKDROP) }
        }
        db.library().upsertTitle(
            title.copy(
                kind = "MOVIE", name = m.title.ifEmpty { title.name }, year = m.year ?: title.year, overview = m.overview,
                runtimeMin = m.runtimeMin, genres = m.genres, rating = m.rating, tmdbId = id, tmdbType = Tmdb.MOVIE,
                posterPath = poster, backdropPath = backdrop, fetchedAt = now(),
            ),
        )
        return Outcome.FETCHED
    }

    // ---- series and anime ----

    private suspend fun show(title: TitleInfo, files: List<MediaFile>, override: MatchOverride?, force: Boolean): Outcome {
        val dao = db.library()
        val have = dao.episodesOf(title.groupKey).map { it.season to it.number }.toSet()
        val onCard = files.mapNotNull { f -> f.episode?.let { (f.season ?: 1) to it } }.toSet()
        val missing = onCard - have
        val fromAniList = title.tmdbType == ANILIST
        val complete = title.tmdbId != null && title.overview != null && images.has(title.posterPath) &&
            (fromAniList || (images.has(title.backdropPath) && missing.isEmpty()))
        if (complete && !force) return Outcome.UP_TO_DATE

        val id = override?.tmdbId?.takeIf { override.tmdbType == Tmdb.TV }
            ?: title.tmdbId?.takeIf { title.tmdbType == Tmdb.TV }
            ?: best(tmdb.searchShows(title.name, title.year).ifEmpty { if (title.year != null) tmdb.searchShows(title.name) else emptyList() }, title)?.id
            ?: return if (title.tab == "ANIME") fromAniList(title) else Outcome.NOT_FOUND

        val s = tmdb.show(id)
        var poster = title.posterPath.takeIf { images.has(it) }
        var backdrop = title.backdropPath.takeIf { images.has(it) }
        if (poster == null) poster = s.posterPath?.let { images.save(http, title.groupKey, "poster", tmdb.imageUrl(it, "w342"), ImageStore.POSTER) }
        if (backdrop == null) backdrop = s.backdropPath?.let { images.save(http, title.groupKey, "backdrop", tmdb.imageUrl(it, "w1280"), ImageStore.BACKDROP) }
        val fanart = fanart
        if ((poster == null || backdrop == null) && fanart != null && s.tvdbId != null) {
            val art = runCatching { fanart.show(s.tvdbId) }.getOrNull()
            if (poster == null) poster = art?.posterUrl?.let { images.save(http, title.groupKey, "poster", it, ImageStore.POSTER) }
            if (backdrop == null) backdrop = art?.backdropUrl?.let { images.save(http, title.groupKey, "backdrop", it, ImageStore.BACKDROP) }
        }

        val wanted = if (force) onCard else missing
        val regular = s.regularSeasons
        val maxOnCard = onCard.maxOfOrNull { it.second } ?: 0
        // Anime numbered straight through ("One Piece - 1050") against TMDB's seasons.
        val absolute = files.isNotEmpty() && files.all { it.season == null } && regular.size > 1 && maxOnCard > (regular.firstOrNull()?.episodeCount ?: 0)
        val episodes = mutableListOf<EpisodeInfo>()
        if (absolute) {
            var offset = 0
            for (season in regular) {
                val range = (offset + 1)..(offset + season.episodeCount)
                if (wanted.any { it.second in range }) {
                    tmdb.season(id, season.number).forEach { e -> episodes += episode(title.groupKey, 1, offset + e.number, e, onCard) }
                }
                offset += season.episodeCount
            }
            dao.upsertSeasons(listOf(SeasonInfo(title.groupKey, 1, "Episodes", null, s.year, offset, null)))
        } else {
            val seasonNumbers = wanted.map { it.first }.toSet()
            for (n in seasonNumbers.sorted()) {
                if (s.seasons.none { it.number == n }) continue
                tmdb.season(id, n).forEach { e -> episodes += episode(title.groupKey, n, e.number, e, onCard) }
            }
            val kept = dao.seasonsOf(title.groupKey).associateBy { it.number }
            val withFiles = onCard.map { it.first }.toSet()
            dao.upsertSeasons(
                regular.map { t ->
                    val local = kept[t.number]?.posterPath?.takeIf { images.has(it) }
                        ?: if (t.number in withFiles) t.posterPath?.let { images.save(http, title.groupKey, "season${t.number}", tmdb.imageUrl(it, "w342"), ImageStore.POSTER) } else null
                    SeasonInfo(title.groupKey, t.number, t.name, t.overview, t.year, t.episodeCount, local)
                },
            )
        }
        if (episodes.isNotEmpty()) dao.upsertEpisodes(episodes)
        dao.upsertTitle(
            title.copy(
                kind = "SHOW", name = s.name.ifEmpty { title.name }, year = s.year ?: title.year, overview = s.overview, genres = s.genres,
                rating = s.rating, seasonCount = if (absolute) null else regular.size, episodeCount = s.episodeCount,
                tmdbId = id, tmdbType = Tmdb.TV, posterPath = poster, backdropPath = backdrop, fetchedAt = now(),
            ),
        )
        return Outcome.FETCHED
    }

    /** Stills only for episodes you have: greyed missing episodes don't need one. */
    private suspend fun episode(key: String, season: Int, number: Int, e: TmdbEpisode, onCard: Set<Pair<Int, Int>>): EpisodeInfo {
        val still = if ((season to number) in onCard) {
            e.stillPath?.let { images.save(http, key, "s${season}e$number", tmdb.imageUrl(it, "w300"), ImageStore.STILL) }
        } else null
        return EpisodeInfo(key, season, number, e.title, e.overview, e.runtimeMin, still)
    }

    // ---- anime TMDB doesn't know ----

    private suspend fun fromAniList(title: TitleInfo): Outcome {
        val a = anilist?.search(title.name) ?: return Outcome.NOT_FOUND
        // AniList's banner is a thin strip that would blur on the top screen, so the poster stands in.
        val poster = title.posterPath.takeIf { images.has(it) }
            ?: a.posterUrl?.let { images.save(http, title.groupKey, "poster", it, ImageStore.POSTER) }
        db.library().upsertTitle(
            title.copy(
                name = a.title, year = a.year ?: title.year, overview = a.overview, genres = a.genres, episodeCount = a.episodes,
                tmdbId = a.id, tmdbType = ANILIST, posterPath = poster, fetchedAt = now(),
            ),
        )
        return Outcome.FETCHED
    }

    /** The hit whose name matches the title exactly, preferring the same year; else TMDB's first. */
    private fun best(hits: List<TmdbHit>, title: TitleInfo): TmdbHit? {
        val name = LibraryScanner.normalise(title.name)
        return hits.firstOrNull { LibraryScanner.normalise(it.title) == name && (title.year == null || it.year == title.year) }
            ?: hits.firstOrNull { LibraryScanner.normalise(it.title) == name }
            ?: hits.firstOrNull { title.year != null && it.year == title.year }
            ?: hits.firstOrNull()
    }

    companion object {
        /** tmdbType for titles found on AniList instead; tmdbId then holds the AniList id. */
        const val ANILIST = "anilist"
    }
}
