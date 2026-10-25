package com.msharwaan.exoplayer.online

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MatchOverride
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.TitleInfo
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Fetch info against a local server answering like TMDB, fanart.tv and AniList (shapes from
 * their documentation; the real services couldn't be reached from the build machine).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FetchInfoTest {

    private val server = MockWebServer()
    private val requests = mutableListOf<String>()
    private lateinit var db: AppDatabase
    private lateinit var fetch: FetchInfo
    private lateinit var images: ImageStore
    private val jpeg = File("../docs/mockups/art/posters/sintel.jpg").readBytes()
    private var tmdbStatus = 200
    private var throttleOnce = false

    /** The second season of "Caminandes" on TMDB, and the rest of the fake services. */
    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl!!.encodedPath
            requests += path
            if (throttleOnce && path.startsWith("/3/")) { throttleOnce = false; return MockResponse().setResponseCode(429).setHeader("Retry-After", "0") }
            if (path.startsWith("/3/") && tmdbStatus != 200) return MockResponse().setResponseCode(tmdbStatus).setBody("""{"status_message":"Invalid API key"}""")
            val q = request.requestUrl!!.queryParameter("query")
            return when {
                path.startsWith("/img/") -> MockResponse().setBody(Buffer().write(jpeg))
                path == "/3/search/movie" && q == "Sintel" -> json("""{"results":[{"id":45745,"title":"Sintel","release_date":"2010-09-27","overview":"A lonely young woman."}]}""")
                path == "/3/search/movie" -> json("""{"results":[]}""")
                path == "/3/movie/45745" -> json(
                    """{"id":45745,"title":"Sintel","release_date":"2010-09-27","overview":"A lonely young woman, Sintel, helps a dragon.",
                       "runtime":15,"genres":[{"id":16,"name":"Animation"},{"id":14,"name":"Fantasy"}],"vote_average":7.1,
                       "poster_path":"/sintel_p.jpg","backdrop_path":"/sintel_b_text.jpg",
                       "images":{"backdrops":[{"file_path":"/sintel_b_text.jpg","iso_639_1":"en","vote_average":5.3,"vote_count":3},
                                              {"file_path":"/sintel_b_clean.jpg","iso_639_1":null,"vote_average":5.1,"vote_count":2}]}}""",
                )
                path == "/3/search/tv" && q == "Caminandes" -> json("""{"results":[{"id":100,"name":"Caminandes","first_air_date":"2013-01-01","overview":"Koro the llama."}]}""")
                path == "/3/search/tv" && q == "Long Anime" -> json("""{"results":[{"id":200,"name":"Long Anime","first_air_date":"2020-01-01"}]}""")
                path == "/3/search/tv" -> json("""{"results":[]}""")
                path == "/3/tv/100" -> json(
                    """{"id":100,"name":"Caminandes","first_air_date":"2013-01-01","overview":"Koro the llama.","genres":[{"name":"Animation"}],
                       "poster_path":"/cam_p.jpg","backdrop_path":"/cam_b.jpg","number_of_episodes":6,"images":{"backdrops":[]},"external_ids":{"tvdb_id":null},
                       "seasons":[{"season_number":0,"name":"Specials","episode_count":1},
                                  {"season_number":1,"name":"Season 1","episode_count":2,"air_date":"2013-01-01","poster_path":"/cam_s1.jpg"},
                                  {"season_number":2,"name":"Season 2","episode_count":2,"air_date":"2014-01-01","poster_path":null},
                                  {"season_number":3,"name":"Season 3","episode_count":2,"air_date":"2016-01-01"}]}""",
                )
                path.matches(Regex("/3/tv/100/season/\\d")) -> {
                    val n = path.last()
                    json("""{"episodes":[{"episode_number":1,"name":"S${n} One","overview":"o","runtime":3,"still_path":"/st${n}1.jpg"},
                                         {"episode_number":2,"name":"S${n} Two","overview":"o","runtime":3,"still_path":null}]}""")
                }
                path == "/3/tv/200" -> json(
                    """{"id":200,"name":"Long Anime","first_air_date":"2020-01-01","overview":"Long.","poster_path":"/la_p.jpg","backdrop_path":"/la_b.jpg",
                       "number_of_episodes":24,"images":{"backdrops":[]},
                       "seasons":[{"season_number":1,"name":"Season 1","episode_count":12},{"season_number":2,"name":"Season 2","episode_count":12}]}""",
                )
                path.matches(Regex("/3/tv/200/season/\\d")) -> json(
                    """{"episodes":[${(1..12).joinToString(",") { """{"episode_number":$it,"name":"Ep $it of season ${path.last()}"}""" }}]}""",
                )
                path == "/anilist" -> json(
                    """{"data":{"Media":{"id":777,"format":"TV","episodes":28,"seasonYear":2023,"genres":["Adventure","Fantasy"],
                       "description":"An elf mage<br>outlives her party. (Source: Crunchyroll)","title":{"romaji":"Sousou no Frieren","english":"Frieren"},
                       "coverImage":{"extraLarge":"${server.url("/img/fr.jpg")}"},"bannerImage":null}}}""",
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Before
    fun setUp() {
        com.msharwaan.exoplayer.screenshots.Screens.warmUpGraphics()
        server.dispatcher = dispatcher
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        images = ImageStore(File(context.cacheDir, "art-test").apply { deleteRecursively() })
        val http = Http(sleep = {})
        fetch = FetchInfo(
            db, http, images,
            Tmdb(http, "0123456789abcdef0123456789abcdef", server.url("/3/").toString(), server.url("/img/").toString()),
            fanart = null, anilist = AniList(http, server.url("/anilist").toString()),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun file(id: Long, key: String, season: Int?, episode: Int?, kind: String = "EPISODE") =
        MediaFile(id, "u$id", "", "f$id.mkv", 1, 1, kind, key, "x", null, season, episode, season == null)

    @Test
    fun movieThenNothingTheSecondTime() = runBlocking {
        val dao = db.library()
        dao.upsertTitle(TitleInfo("m:sintel:2010", "MOVIE", "MOVIES", "Sintel", 2010))
        dao.insertFile(file(1, "m:sintel:2010", null, null, "MOVIE"))

        val report = fetch.fetch(listOf("m:sintel:2010"))
        assertEquals(1, report.fetched)
        val t = dao.title("m:sintel:2010")!!
        assertEquals("A lonely young woman, Sintel, helps a dragon.", t.overview)
        assertEquals("Animation, Fantasy", t.genres)
        assertEquals(15, t.runtimeMin)
        assertTrue(images.has(t.posterPath))
        assertTrue(images.has(t.backdropPath))
        // The textless backdrop is preferred for the top screen (D7).
        assertTrue(requests.contains("/img/w1280/sintel_b_clean.jpg"))

        requests.clear()
        val again = fetch.fetch(listOf("m:sintel:2010"))
        assertEquals(1, again.upToDate)
        assertEquals(emptyList<String>(), requests) // D38: nothing fetched again
    }

    @Test
    fun showFetchesOnlyTheNewSeason() = runBlocking {
        val dao = db.library()
        dao.upsertTitle(TitleInfo("s:caminandes", "SHOW", "SERIES", "Caminandes", null))
        dao.insertFile(file(1, "s:caminandes", 1, 1))
        dao.insertFile(file(2, "s:caminandes", 1, 2))
        dao.insertFile(file(3, "s:caminandes", 2, 1))

        fetch.fetch(listOf("s:caminandes"))
        assertTrue(requests.containsAll(listOf("/3/search/tv", "/3/tv/100", "/3/tv/100/season/1", "/3/tv/100/season/2")))
        assertTrue("/3/tv/100/season/3" !in requests)
        val eps = dao.episodesOf("s:caminandes")
        assertEquals(listOf("S1 One", "S1 Two", "S2 One", "S2 Two"), eps.map { it.title })
        assertNotNull(eps.first { it.season == 1 && it.number == 1 }.stillPath)
        assertNull(eps.first { it.season == 2 && it.number == 2 }.stillPath) // not on the card: no still
        assertEquals(listOf(1, 2, 3), dao.seasonsOf("s:caminandes").map { it.number }) // specials left out
        assertTrue(images.has(dao.seasonsOf("s:caminandes").first().posterPath))

        // A new file in season 3: only the show and that season are asked for.
        requests.clear()
        dao.insertFile(file(4, "s:caminandes", 3, 1))
        fetch.fetch(listOf("s:caminandes"))
        assertEquals(listOf("/3/tv/100", "/3/tv/100/season/3", "/img/w300/st31.jpg"), requests)

        requests.clear()
        fetch.fetch(listOf("s:caminandes"))
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun animeNumberedStraightThroughMapsOntoSeasons() = runBlocking {
        val dao = db.library()
        dao.upsertTitle(TitleInfo("s:long anime", "SHOW", "ANIME", "Long Anime", null))
        dao.insertFile(file(1, "s:long anime", null, 1))
        dao.insertFile(file(2, "s:long anime", null, 13))
        fetch.fetch(listOf("s:long anime"))
        val eps = dao.episodesOf("s:long anime")
        assertEquals(24, eps.size)
        assertTrue(eps.all { it.season == 1 })
        assertEquals("Ep 1 of season 2", eps.first { it.number == 13 }.title)
    }

    @Test
    fun animeTmdbDoesNotKnowComesFromAniList() = runBlocking {
        val dao = db.library()
        dao.upsertTitle(TitleInfo("s:frieren", "SHOW", "ANIME", "Frieren", null))
        dao.insertFile(file(1, "s:frieren", null, 1))
        val report = fetch.fetch(listOf("s:frieren"))
        assertEquals(1, report.fetched)
        val t = dao.title("s:frieren")!!
        assertEquals("anilist", t.tmdbType)
        assertEquals("An elf mage\noutlives her party.", t.overview)
        assertTrue(images.has(t.posterPath))
    }

    @Test
    fun notFoundIsReported() = runBlocking {
        db.library().upsertTitle(TitleInfo("m:nothing:", "MOVIE", "MOVIES", "Nothing Like This", null))
        val report = fetch.fetch(listOf("m:nothing:"))
        assertEquals(listOf("Nothing Like This"), report.notFound)
    }

    @Test(expected = KeyRejected::class)
    fun aRejectedKeyStopsTheRun() {
        runBlocking {
            db.library().upsertTitle(TitleInfo("m:sintel:2010", "MOVIE", "MOVIES", "Sintel", 2010))
            tmdbStatus = 401
            fetch.fetch(listOf("m:sintel:2010"))
        }
    }

    @Test
    fun tooManyRequestsIsRetried() = runBlocking {
        db.library().upsertTitle(TitleInfo("m:sintel:2010", "MOVIE", "MOVIES", "Sintel", 2010))
        throttleOnce = true
        assertEquals(1, fetch.fetch(listOf("m:sintel:2010")).fetched)
        assertEquals("/3/search/movie", requests[0])
        assertEquals("/3/search/movie", requests[1])
    }

    @Test
    fun fixMatchRefetchesAsTheChosenTitle() = runBlocking {
        val dao = db.library()
        dao.upsertTitle(TitleInfo("s:sintel", "SHOW", "SERIES", "Sintel the Series", null))
        dao.insertFile(file(1, "s:sintel", 1, 1))
        assertTrue(fetch.fix("s:sintel", MatchOverride("s:sintel", 45745, "movie")))
        val t = dao.title("s:sintel")!!
        assertEquals("MOVIE", t.kind)
        assertEquals("Sintel", t.name)
        assertEquals(45745L, t.tmdbId)
        assertTrue("/3/search/movie" !in requests) // the chosen id is used, no search
    }
}
