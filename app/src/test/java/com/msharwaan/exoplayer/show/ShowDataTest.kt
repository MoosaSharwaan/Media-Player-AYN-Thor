package com.msharwaan.exoplayer.show

import com.msharwaan.exoplayer.data.EpisodeInfo
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import com.msharwaan.exoplayer.data.SeasonInfo
import com.msharwaan.exoplayer.data.TitleInfo
import com.msharwaan.exoplayer.library.Library
import com.msharwaan.exoplayer.library.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowDataTest {

    private fun ep(id: Long, s: Int?, e: Int, key: String = "s:bb") = MediaFile(
        id = id, uri = "u$id", folderPath = "", fileName = "file$id.mkv", size = 1, modified = 1,
        kind = "EPISODE", groupKey = key, title = "Show", year = null, season = s, episode = e, anime = s == null,
    )

    private val show = TitleInfo("s:bb", "SHOW", "SERIES", "Breaking Bad", 2008)

    @Test
    fun groupsFilesIntoSeasons() {
        val d = ShowData.build(show, listOf(ep(3, 2, 1), ep(1, 1, 1), ep(2, 1, 2)), emptyList(), emptyList(), emptyList())
        assertEquals(listOf(1, 2), d.seasons.map { it.number })
        assertEquals(listOf(1L, 2L), d.seasons[0].episodes.map { it.fileId })
        assertEquals("Episode 2", d.seasons[0].episodes[1].title)
        assertFalse(d.singleSeason)
        assertEquals("2008 · Series · 2 seasons · 3 episodes", d.meta)
    }

    @Test
    fun animeWithoutSeasonNumbersIsOneSeason() {
        val anime = show.copy(groupKey = "s:frieren", tab = "ANIME", name = "Frieren")
        val d = ShowData.build(anime, listOf(ep(1, null, 1, "s:frieren"), ep(2, null, 2, "s:frieren")), emptyList(), emptyList(), emptyList())
        assertTrue(d.singleSeason)
        assertEquals("Episode 2", ShowData.label(d.seasons[0].episodes[1], d.singleSeason))
    }

    @Test
    fun tmdbFillsNamesAndShowsMissingEpisodes() {
        val d = ShowData.build(
            show,
            listOf(ep(1, 1, 1)),
            emptyList(),
            listOf(SeasonInfo("s:bb", 1, "Season 1", "Walt starts cooking.", 2008, 2, "/p.jpg")),
            listOf(EpisodeInfo("s:bb", 1, 1, "Pilot", "…", 58, null), EpisodeInfo("s:bb", 1, 2, "Cat's in the Bag...", "…", 48, null)),
        )
        val eps = d.seasons.single().episodes
        assertEquals(listOf("Pilot", "Cat's in the Bag..."), eps.map { it.title })
        assertFalse(eps[0].missing)
        assertTrue(eps[1].missing)
        assertEquals("Walt starts cooking.", d.seasons.single().overview)
    }

    @Test
    fun marksWatchedAndProgress() {
        val state = listOf(
            PlaybackState(1, positionMs = 50, durationMs = 50, watched = true, lastPlayed = 1),
            PlaybackState(2, positionMs = 15 * 60_000, durationMs = 45 * 60_000, lastPlayed = 2),
        )
        val d = ShowData.build(show, listOf(ep(1, 1, 1), ep(2, 1, 2)), state, emptyList(), emptyList())
        val (first, second) = d.seasons.single().episodes
        assertTrue(first.watched)
        assertEquals(1 / 3f, second.progress, 0.001f)
        assertEquals("30 min left", second.leftNote)
        assertEquals(2L, d.nextUp?.fileId)
    }

    @Test
    fun oneEpisodeShowIsTreatedAsAMovie() {
        val film = TitleInfo("s:film", "SHOW", "ANIME", "Suzume", 2022)
        val tabs = Library.build(listOf(ep(9, null, 1, "s:film")), listOf(film), emptyList())
        assertEquals("MOVIE", tabs.getValue(Tab.ANIME).single().kind)
    }
}
