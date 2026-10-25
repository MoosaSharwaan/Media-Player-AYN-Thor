package com.msharwaan.exoplayer.online

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/** Art from fanart.tv: used only for what TMDB doesn't have. */
data class FanartArt(val backdropUrl: String?, val posterUrl: String?)

/**
 * fanart.tv (optional key in Settings). Movies are looked up by TMDB id; series by their
 * TheTVDB id, which TMDB provides. English or textless art first.
 */
class Fanart(private val http: Http, private val key: String, private val base: String = "https://webservice.fanart.tv/v3/") {

    fun movie(tmdbId: Long): FanartArt = art(http.getJson(url("movies/$tmdbId")), "moviebackground", "movieposter")

    fun show(tvdbId: Long): FanartArt = art(http.getJson(url("tv/$tvdbId")), "showbackground", "tvposter")

    private fun url(path: String) = (base + path).toHttpUrl().newBuilder().addQueryParameter("api_key", key).build().toString()

    private fun art(o: JSONObject, backdrop: String, poster: String) = FanartArt(best(o, backdrop), best(o, poster))

    private fun best(o: JSONObject, name: String): String? {
        val items = o.optJSONArray(name).objects()
        val pick = items.filter { it.str("lang").let { l -> l == null || l == "en" || l == "00" } }.ifEmpty { items }
        return pick.maxByOrNull { it.str("likes")?.toIntOrNull() ?: 0 }?.str("url")
    }
}

/** An anime from AniList. */
data class AniListAnime(
    val id: Long, val title: String, val year: Int?, val overview: String?, val genres: String?, val episodes: Int?,
    val posterUrl: String?, val bannerUrl: String?, val isMovie: Boolean,
)

/** AniList (no key): only for anime that TMDB doesn't find. */
class AniList(private val http: Http, private val endpoint: String = "https://graphql.anilist.co") {

    fun search(title: String): AniListAnime? {
        val query = """
            query (${'$'}q: String) { Media(search: ${'$'}q, type: ANIME) {
              id format episodes seasonYear genres description(asHtml: false)
              title { romaji english } coverImage { extraLarge } bannerImage } }
        """.trimIndent()
        val body = JSONObject().put("query", query).put("variables", JSONObject().put("q", title))
        val m = runCatching { http.postJson(endpoint, body) }.getOrElse { e ->
            // AniList answers 404 when nothing matches.
            if (e is HttpException && e.code == 404) return null else throw e
        }.optJSONObject("data")?.optJSONObject("Media") ?: return null
        val t = m.optJSONObject("title")
        return AniListAnime(
            id = m.getLong("id"),
            title = t?.str("english") ?: t?.str("romaji") ?: title,
            year = m.intOrNull("seasonYear"),
            overview = m.str("description")?.let(::plainText),
            genres = m.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) }.take(3).joinToString(", ") }?.ifEmpty { null },
            episodes = m.intOrNull("episodes"),
            posterUrl = m.optJSONObject("coverImage")?.str("extraLarge"),
            bannerUrl = m.str("bannerImage"),
            isMovie = m.str("format") == "MOVIE",
        )
    }

    private fun plainText(s: String) = s.replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\n{3,}"), "\n\n").replace(Regex("\\(Source:[^)]*\\)"), "").trim()
}
