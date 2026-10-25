package com.msharwaan.exoplayer.online

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OpenSubtitles against a local server answering like opensubtitles.com's REST API (from its documentation). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenSubtitlesTest {

    private val server = MockWebServer()
    private val seen = mutableListOf<RecordedRequest>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                val path = request.requestUrl!!.encodedPath
                return when {
                    path.endsWith("/login") -> MockResponse().setBody("""{"token":"tok123","user":{"allowed_downloads":20},"base_url":"${server.hostName}:${server.port}"}""")
                    path.endsWith("/subtitles") -> MockResponse().setBody(
                        """{"total_count":3,"data":[
                          {"attributes":{"language":"en","release":"Popular.But.Other.Release","download_count":9000,"moviehash_match":false,"hearing_impaired":false,"files":[{"file_id":1,"file_name":"a.srt"}]}},
                          {"attributes":{"language":"fr","release":"French","download_count":50000,"moviehash_match":false,"files":[{"file_id":2}]}},
                          {"attributes":{"language":"en","release":"Exact.Release","download_count":10,"moviehash_match":true,"hearing_impaired":true,"files":[{"file_id":3,"file_name":"b.srt"}]}}]}""",
                    )
                    path.endsWith("/download") -> MockResponse().setBody("""{"link":"${server.url("/file/b.srt")}","file_name":"b.srt","remaining":19,"reset_time":"23 hours"}""")
                    path == "/file/b.srt" -> MockResponse().setBody("1\n00:00:01,000 --> 00:00:02,000\nHello\n")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client() = OpenSubtitles(Http(sleep = {}), "KEY", "me", "secret", server.url("/api/v1/").toString())

    @Test
    fun logsInSearchesInOrderAndSortsExactMatchesFirst() {
        val os = client()
        val hits = os.search("8e245d9679d31e12", "Sintel 2010", null, null, listOf("en"))
        assertEquals(listOf(3L, 1L, 2L), hits.map { it.fileId }) // exact, then English, then the rest
        assertEquals(20, os.allowedPerDay)
        val login = seen.first { it.path!!.endsWith("/login") }
        assertEquals("KEY", login.getHeader("Api-Key"))
        val search = seen.first { it.path!!.contains("/subtitles") }
        // Alphabetical, lower case: otherwise OpenSubtitles redirects.
        assertTrue(search.path!!.endsWith("subtitles?languages=en&moviehash=8e245d9679d31e12&query=sintel%202010"))
        assertEquals("Bearer tok123", search.getHeader("Authorization"))
        assertTrue(search.getHeader("User-Agent")!!.startsWith("ExoPlayer-Thor"))
    }

    @Test
    fun downloadReturnsTheFileAndWhatsLeft() {
        val d = client().download(3)
        assertEquals("1\n00:00:01,000 --> 00:00:02,000\nHello\n", String(d.bytes))
        assertEquals(19, d.remaining)
        val body = seen.first { it.path!!.endsWith("/download") }.body.readUtf8()
        assertTrue(body.contains("\"file_id\":3") && body.contains("\"sub_format\":\"srt\""))
    }

    @Test
    fun episodesSendSeasonAndEpisode() {
        client().search(null, "Caminandes", 1, 2, listOf("en"))
        val search = seen.first { it.path!!.contains("/subtitles") }
        assertTrue(search.path!!.endsWith("subtitles?episode_number=2&languages=en&query=caminandes&season_number=1"))
    }

    @Test
    fun movieHashSumsSizeHeadAndTail() {
        val head = ByteArray(64 * 1024) { 1 }
        val tail = ByteArray(64 * 1024) { 2 }
        // Worked out separately: (1 000 000 + 8192 × 0x0101… + 8192 × 0x0202…) mod 2^64.
        assertEquals("60606060606fa240", MovieHash.compute(head, tail, 1_000_000))
    }
}
