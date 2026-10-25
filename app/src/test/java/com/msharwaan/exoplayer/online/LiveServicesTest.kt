package com.msharwaan.exoplayer.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's clients against the real services. Skipped unless EXO_LIVE=1, so normal runs stay
 * offline (D32). Keys come from the environment, never from the repo:
 * EXO_TMDB_KEY, EXO_FANART_KEY, EXO_OS_KEY, EXO_OS_USER, EXO_OS_PASSWORD. EXO_OS_DOWNLOAD=1
 * also downloads one subtitle, which uses one of the day's downloads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveServicesTest {

    private val http = Http()

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    private fun live(vararg keys: String) {
        assumeTrue("EXO_LIVE=1 runs the live tests", env("EXO_LIVE") == "1")
        keys.forEach { assumeTrue("$it not set", env(it) != null) }
    }

    @Test
    fun aniList() {
        live()
        val anime = AniList(http).search("Frieren")
        assertNotNull(anime)
        println("AniList: $anime")
        assertTrue(anime!!.title.contains("Frieren"))
        assertEquals(2023, anime.year)
        assertNotNull(anime.posterUrl)
        assertTrue(http.bytes(anime.posterUrl!!).size > 1000)
    }

    @Test
    fun tmdbMovie() {
        live("EXO_TMDB_KEY")
        val tmdb = Tmdb(http, env("EXO_TMDB_KEY")!!)
        val hit = tmdb.searchMovies("Sintel", 2010).first()
        println("TMDB search: $hit")
        val movie = tmdb.movie(hit.id)
        println("TMDB movie: $movie")
        assertEquals("Sintel", movie.title)
        val poster = http.bytes(tmdb.imageUrl(movie.posterPath!!, "w342"))
        assertTrue(poster.size > 1000)
    }

    @Test
    fun tmdbShow() {
        live("EXO_TMDB_KEY")
        val tmdb = Tmdb(http, env("EXO_TMDB_KEY")!!)
        val hit = tmdb.searchShows("Breaking Bad").first()
        val show = tmdb.show(hit.id)
        println("TMDB show: ${show.name} ${show.year}, ${show.regularSeasons.size} seasons, tvdb ${show.tvdbId}")
        assertEquals(5, show.regularSeasons.size)
        val episodes = tmdb.season(show.id, 2)
        println("TMDB S02E05: ${episodes.first { it.number == 5 }}")
        assertEquals(13, episodes.size)
    }

    @Test
    fun fanart() {
        live("EXO_FANART_KEY")
        val art = Fanart(http, env("EXO_FANART_KEY")!!).movie(603) // The Matrix
        println("fanart.tv: $art")
        assertTrue(art.backdropUrl != null || art.posterUrl != null)
    }

    @Test
    fun openSubtitles() {
        live("EXO_OS_KEY")
        val os = OpenSubtitles(http, env("EXO_OS_KEY")!!, env("EXO_OS_USER"), env("EXO_OS_PASSWORD"))
        if (env("EXO_OS_USER") != null) {
            os.login()
            println("OpenSubtitles: logged in, ${os.allowedPerDay} downloads a day")
        }
        val hits = os.search(null, "Sintel", null, null, listOf("en"))
        println("OpenSubtitles: ${hits.size} hits, first ${hits.firstOrNull()}")
        assertTrue(hits.isNotEmpty())
        if (env("EXO_OS_DOWNLOAD") == "1") {
            val file = os.download(hits.first().fileId)
            println("OpenSubtitles download: ${file.fileName}, ${file.bytes.size} bytes, ${file.remaining} left, resets ${file.resetsAt}")
            assertTrue(String(file.bytes).contains("-->"))
        }
    }
}
