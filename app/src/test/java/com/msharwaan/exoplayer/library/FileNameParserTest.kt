package com.msharwaan.exoplayer.library

import com.msharwaan.exoplayer.library.ParsedName.Kind.EPISODE
import com.msharwaan.exoplayer.library.ParsedName.Kind.MOVIE
import com.msharwaan.exoplayer.library.ParsedName.Kind.OTHER
import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameParserTest {

    private data class Case(
        val path: String,
        val kind: ParsedName.Kind,
        val title: String,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val anime: Boolean = false,
    )

    private val cases = listOf(
        // Movies
        Case("The.Dark.Knight.2008.1080p.BluRay.x264.mkv", MOVIE, "The Dark Knight", 2008),
        Case("Interstellar (2014) [2160p] [HDR].mkv", MOVIE, "Interstellar", 2014),
        Case("Pulp_Fiction_1994_720p.mp4", MOVIE, "Pulp Fiction", 1994),
        Case("Parasite.2019.KOREAN.1080p.WEB-DL.DDP5.1.H.264.mkv", MOVIE, "Parasite", 2019),
        Case("The Godfather 1972 REMASTERED 1080p.mkv", MOVIE, "The Godfather", 1972),
        Case("Blade.Runner.2049.2017.2160p.UHD.mkv", MOVIE, "Blade Runner 2049", 2017),
        Case("2001.A.Space.Odyssey.1968.1080p.mkv", MOVIE, "2001 A Space Odyssey", 1968),
        Case("Fight Club.mkv", MOVIE, "Fight Club"),
        Case("Heat (1995)/movie.mkv", MOVIE, "Heat", 1995),
        Case("Movies/The Lord of the Rings The Return of the King (2003)/LOTR.ROTK.EXTENDED.mkv", MOVIE, "The Lord of the Rings The Return of the King", 2003),
        Case("Project.Hail.Mary.2026.1080p.WEBRip.x265.mkv", MOVIE, "Project Hail Mary", 2026),
        Case("Spirited.Away.2001.JAPANESE.1080p.BluRay.mkv", MOVIE, "Spirited Away", 2001),
        // Episodes: loose files
        Case("Breaking.Bad.S02E05.720p.HDTV.x264.mkv", EPISODE, "Breaking Bad", season = 2, episode = 5),
        Case("breaking bad s2e5.mkv", EPISODE, "breaking bad", season = 2, episode = 5),
        Case("Dexter - 4x12 - The Getaway.mkv", EPISODE, "Dexter", season = 4, episode = 12),
        Case("The.Blacklist.S03E10.1080p.WEB.mkv", EPISODE, "The Blacklist", season = 3, episode = 10),
        Case("House.M.D.S01E01.Pilot.mkv", EPISODE, "House M D", season = 1, episode = 1),
        Case("Rick and Morty S05E03 1080p.mp4", EPISODE, "Rick and Morty", season = 5, episode = 3),
        Case("Greys.Anatomy.S21E18.mkv", EPISODE, "Greys Anatomy", season = 21, episode = 18),
        Case("Modern Family Season 3 Episode 7.mkv", EPISODE, "Modern Family", season = 3, episode = 7),
        Case("The.Mentalist.S01E01E02.mkv", EPISODE, "The Mentalist", season = 1, episode = 1),
        Case("Doctor.Who.2005.S01E01.Rose.mkv", EPISODE, "Doctor Who", 2005, season = 1, episode = 1),
        // Episodes: Show/Season folders
        Case("Breaking Bad/Season 2/05 - Breakage.mkv", EPISODE, "Breaking Bad", season = 2, episode = 5),
        Case("Breaking Bad/Season 02/Breakage E05.mkv", EPISODE, "Breaking Bad", season = 2, episode = 5),
        Case("Series/Dexter/S4/12.mkv", EPISODE, "Dexter", season = 4, episode = 12),
        Case("The Office/Season 1/The.Office.S01E02.mkv", EPISODE, "The Office", season = 1, episode = 2),
        // Anime
        Case("[SubsPlease] Sousou no Frieren - 12 (1080p) [ABCD1234].mkv", EPISODE, "Sousou no Frieren", episode = 12, anime = true),
        Case("[Erai-raws] One Piece - 1050 [1080p].mkv", EPISODE, "One Piece", episode = 1050, anime = true),
        Case("Anime/Cowboy Bebop/Cowboy Bebop - 05.mkv", EPISODE, "Cowboy Bebop", episode = 5, anime = true),
        Case("[HorribleSubs] Black Clover - 101v2 [720p].mkv", EPISODE, "Black Clover", episode = 101, anime = true),
        Case("Anime/Attack on Titan/Season 4/03.mkv", EPISODE, "Attack on Titan", season = 4, episode = 3, anime = true),
        Case("Hunter x Hunter (2011) - 148.mkv", EPISODE, "Hunter x Hunter", episode = 148),
        Case("Violet Evergarden Episode 13.mkv", EPISODE, "Violet Evergarden", episode = 13),
        Case("Anime/Your Name (2016).mkv", MOVIE, "Your Name", 2016, anime = true),
        // Others
        Case("VID_20240512_183022.mp4", OTHER, "VID_20240512_183022"),
        Case("PXL_20251003_101500123.mp4", OTHER, "PXL_20251003_101500123"),
        Case("Home Videos/Birthday party.mp4", OTHER, "Birthday party"),
        Case("Others/Concert 2019.mkv", OTHER, "Concert 2019"),
    )

    @Test
    fun parsesSampleNames() {
        val failures = cases.mapNotNull { c ->
            val parts = c.path.split('/')
            val got = FileNameParser.parse(parts.last(), parts.dropLast(1))
            val want = ParsedName(c.kind, c.title, c.year, c.season, c.episode, c.anime)
            if (got != want) "${c.path}\n   want $want\n   got  $got" else null
        }
        assertEquals("Mismatches:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun recognisesVideoExtensions() {
        assertEquals(true, FileNameParser.isVideo("a.MKV"))
        assertEquals(true, FileNameParser.isVideo("a.m2ts"))
        assertEquals(false, FileNameParser.isVideo("a.srt"))
        assertEquals(false, FileNameParser.isVideo("poster.jpg"))
    }
}
