package com.msharwaan.exoplayer.online

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** A search hit: what Fix match lists and what automatic matching picks from. */
data class TmdbHit(val id: Long, val type: String, val title: String, val year: Int?, val overview: String?)

data class TmdbMovie(
    val id: Long, val title: String, val year: Int?, val overview: String?, val runtimeMin: Int?, val genres: String?,
    val rating: Double?, val posterPath: String?, val backdropPath: String?,
)

data class TmdbSeason(val number: Int, val name: String, val overview: String?, val year: Int?, val episodeCount: Int, val posterPath: String?)

data class TmdbShow(
    val id: Long, val name: String, val year: Int?, val overview: String?, val genres: String?, val rating: Double?,
    val posterPath: String?, val backdropPath: String?, val seasons: List<TmdbSeason>, val episodeCount: Int?, val tvdbId: Long?,
) {
    /** Seasons without specials (season 0). */
    val regularSeasons get() = seasons.filter { it.number > 0 }.sortedBy { it.number }
}

data class TmdbEpisode(val number: Int, val title: String, val overview: String?, val runtimeMin: Int?, val stillPath: String?)

/**
 * TMDB, for Fetch info and Fix match. [key] is the API key (32 characters) or the longer
 * Read Access Token; both work. Image paths are TMDB's (like "/abc.jpg"); [imageUrl] turns
 * one into a download link at the size its screen needs (D7).
 */
class Tmdb(
    private val http: Http,
    private val key: String,
    private val base: String = "https://api.themoviedb.org/3/",
    private val imageBase: String = "https://image.tmdb.org/t/p/",
) {
    private val isToken = key.length > 40

    private fun get(path: String, vararg query: Pair<String, String?>): JSONObject {
        val url = (base + path).toHttpUrl().newBuilder().apply {
            if (!isToken) addQueryParameter("api_key", key)
            query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build().toString()
        return http.getJson(url, if (isToken) mapOf("Authorization" to "Bearer $key") else emptyMap())
    }

    fun searchMovies(query: String, year: Int? = null): List<TmdbHit> =
        get("search/movie", "query" to query, "primary_release_year" to year?.toString(), "include_adult" to "false")
            .optJSONArray("results").objects().map { TmdbHit(it.getLong("id"), MOVIE, it.str("title").orEmpty(), yearOf(it.str("release_date")), it.str("overview")) }

    fun searchShows(query: String, year: Int? = null): List<TmdbHit> =
        get("search/tv", "query" to query, "first_air_date_year" to year?.toString(), "include_adult" to "false")
            .optJSONArray("results").objects().map { TmdbHit(it.getLong("id"), TV, it.str("name").orEmpty(), yearOf(it.str("first_air_date")), it.str("overview")) }

    fun movie(id: Long): TmdbMovie {
        val o = get("movie/$id", "append_to_response" to "images", "include_image_language" to "en,null")
        return TmdbMovie(
            id = o.getLong("id"), title = o.str("title").orEmpty(), year = yearOf(o.str("release_date")), overview = o.str("overview"),
            runtimeMin = o.intOrNull("runtime")?.takeIf { it > 0 }, genres = genres(o), rating = o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
            posterPath = o.str("poster_path"), backdropPath = textlessBackdrop(o) ?: o.str("backdrop_path"),
        )
    }

    fun show(id: Long): TmdbShow {
        val o = get("tv/$id", "append_to_response" to "images,external_ids", "include_image_language" to "en,null")
        return TmdbShow(
            id = o.getLong("id"), name = o.str("name").orEmpty(), year = yearOf(o.str("first_air_date")), overview = o.str("overview"),
            genres = genres(o), rating = o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
            posterPath = o.str("poster_path"), backdropPath = textlessBackdrop(o) ?: o.str("backdrop_path"),
            seasons = o.optJSONArray("seasons").objects().map {
                TmdbSeason(it.optInt("season_number"), it.str("name") ?: "Season ${it.optInt("season_number")}", it.str("overview"),
                    yearOf(it.str("air_date")), it.optInt("episode_count"), it.str("poster_path"))
            },
            episodeCount = o.intOrNull("number_of_episodes"),
            tvdbId = o.optJSONObject("external_ids")?.let { if (it.isNull("tvdb_id")) null else it.optLong("tvdb_id").takeIf { n -> n > 0 } },
        )
    }

    fun season(showId: Long, number: Int): List<TmdbEpisode> =
        get("tv/$showId/season/$number").optJSONArray("episodes").objects().map {
            TmdbEpisode(it.optInt("episode_number"), it.str("name") ?: "Episode ${it.optInt("episode_number")}", it.str("overview"),
                it.intOrNull("runtime")?.takeIf { r -> r > 0 }, it.str("still_path"))
        }

    /** [size]: w342 for posters on the cards, w1280 for backdrops on the top screen, w300 for episode stills. */
    fun imageUrl(path: String, size: String): String = imageBase + size + path

    private fun genres(o: JSONObject): String? =
        o.optJSONArray("genres").objects().mapNotNull { it.str("name") }.take(3).joinToString(", ").ifEmpty { null }

    /** The top screen wants art without words on it (D7): a backdrop with no language is textless. */
    private fun textlessBackdrop(o: JSONObject): String? =
        o.optJSONObject("images")?.optJSONArray("backdrops").objects()
            .filter { it.isNull("iso_639_1") }
            .maxByOrNull { it.optDouble("vote_average", 0.0) * 1000 + it.optInt("vote_count") }
            ?.str("file_path")

    companion object {
        const val MOVIE = "movie"
        const val TV = "tv"
    }
}

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
