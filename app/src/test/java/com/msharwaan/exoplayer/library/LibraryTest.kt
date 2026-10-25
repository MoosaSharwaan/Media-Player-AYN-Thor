package com.msharwaan.exoplayer.library

import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import com.msharwaan.exoplayer.data.TitleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {

    private fun ep(id: Long, s: Int, e: Int) = MediaFile(
        id = id, uri = "u$id", folderPath = "", fileName = "bb.s${s}e$e.mkv", size = 1, modified = 1,
        kind = "EPISODE", groupKey = "s:bb", title = "Breaking Bad", year = null, season = s, episode = e, anime = false,
    )

    private val movie = MediaFile(
        id = 100, uri = "m", folderPath = "", fileName = "Heat.1995.mkv", size = 1, modified = 1,
        kind = "MOVIE", groupKey = "m:heat:1995", title = "Heat", year = 1995, season = null, episode = null, anime = false,
    )
    private val titles = listOf(
        TitleInfo("s:bb", "SHOW", "SERIES", "Breaking Bad", null),
        TitleInfo("m:heat:1995", "MOVIE", "MOVIES", "Heat", 1995),
    )
    private val files = listOf(ep(1, 2, 4), ep(2, 2, 5), ep(3, 2, 6), movie)

    @Test
    fun cardsLandInTheirTabs() {
        val tabs = Library.build(files, titles, emptyList())
        assertEquals(listOf("Breaking Bad"), tabs.getValue(Tab.SERIES).map { it.name })
        assertEquals(listOf("Heat"), tabs.getValue(Tab.MOVIES).map { it.name })
        assertTrue(tabs.getValue(Tab.CONTINUE).isEmpty())
    }

    @Test
    fun continueWatchingResumesAnUnfinishedEpisode() {
        val state = listOf(
            PlaybackState(1, positionMs = 47 * 60_000, durationMs = 47 * 60_000, watched = true, lastPlayed = 10),
            PlaybackState(2, positionMs = 19 * 60_000, durationMs = 47 * 60_000, lastPlayed = 20),
        )
        val c = Library.build(files, titles, state).getValue(Tab.CONTINUE).single()
        assertEquals(2L, c.playFileId)
        assertEquals("S02E05 · 28 min left", c.resumeNote)
    }

    @Test
    fun continueWatchingMovesToTheNextEpisodeOnceFinished() {
        val state = listOf(PlaybackState(2, positionMs = 47 * 60_000, durationMs = 47 * 60_000, watched = true, lastPlayed = 20))
        val c = Library.build(files, titles, state).getValue(Tab.CONTINUE).single()
        assertEquals(3L, c.playFileId)
    }

    @Test
    fun continueWatchingListsHalfWatchedMovies() {
        val state = listOf(PlaybackState(100, positionMs = 60 * 60_000, durationMs = 170 * 60_000, lastPlayed = 5))
        val c = Library.build(files, titles, state).getValue(Tab.CONTINUE).single()
        assertEquals("Heat", c.name)
        assertEquals("1 h 50 m left", c.resumeNote)
    }

    @Test
    fun showMetaCountsEpisodesOnTheCard() {
        val c = Library.build(files, titles, emptyList()).getValue(Tab.SERIES).single()
        assertEquals("Series · 3 episodes", c.meta)
    }
}
