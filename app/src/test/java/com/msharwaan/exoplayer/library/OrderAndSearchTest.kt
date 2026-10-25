package com.msharwaan.exoplayer.library

import com.msharwaan.exoplayer.browse.FoldersModel
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderAndSearchTest {

    private fun card(name: String, year: Int?, added: Long, watched: Boolean = false, genres: String? = null) =
        Card("k:$name", "MOVIE", name, year, "", null, null, null, 1, addedAt = added, watched = watched, genres = genres)

    private val cards = listOf(
        card("Heat", 1995, 30, watched = true, genres = "Crime, Drama"),
        card("Amélie", 2001, 10, genres = "Comedy, Romance"),
        card("Pokémon: The First Movie", 1998, 20, genres = "Animation"),
    )

    @Test
    fun ordersByNameAddedAndYear() {
        assertEquals(listOf("Amélie", "Heat", "Pokémon: The First Movie"), Library.order(cards, BrowseOrder.NAME).map { it.name })
        assertEquals(listOf("Heat", "Pokémon: The First Movie", "Amélie"), Library.order(cards, BrowseOrder.ADDED).map { it.name })
        assertEquals(listOf(2001, 1998, 1995), Library.order(cards, BrowseOrder.YEAR).map { it.year })
        assertEquals(listOf("Amélie", "Pokémon: The First Movie"), Library.order(cards, BrowseOrder.UNWATCHED).map { it.name })
    }

    @Test
    fun searchIgnoresAccentsAndMatchesWordStartsYearsAndGenres() {
        assertEquals(listOf("Pokémon: The First Movie"), Library.search(cards, "pokemon first").map { it.name })
        assertEquals(listOf("Amélie"), Library.search(cards, "AMELIE").map { it.name })
        assertEquals(listOf("Heat"), Library.search(cards, "1995").map { it.name })
        assertEquals(listOf("Heat"), Library.search(cards, "crim").map { it.name })
        assertEquals(emptyList<String>(), Library.search(cards, "eat").map { it.name }) // word starts only
        assertEquals(emptyList<String>(), Library.search(cards, "   ").map { it.name })
    }

    @Test
    fun foldersListSubfoldersThenVideos() {
        fun f(id: Long, path: String, name: String) = MediaFile(id, "u$id", path, name, 1, 1, "MOVIE", "m:$id", name, null, null, null, false)
        val files = listOf(
            f(1, "", "Loose.mkv"), f(2, "Movies", "Heat.mkv"), f(3, "Movies/Old", "Psycho.mkv"),
            f(4, "Series/Caminandes/Season 1", "E1.mkv"), f(5, "Series/Caminandes/Season 1", "E2.mkv"),
        )
        val state = mapOf(2L to PlaybackState(2, positionMs = 30, durationMs = 120))
        val root = FoldersModel.entriesOf(files, state, "")
        assertEquals(listOf("Movies" to 2, "Series" to 2), root.filter { it.path != null }.map { it.name to it.count })
        assertEquals(listOf("Loose.mkv"), root.filter { it.file != null }.map { it.name })
        val movies = FoldersModel.entriesOf(files, state, "Movies")
        assertEquals(listOf("Old", "Heat.mkv"), movies.map { it.name })
        assertEquals("Movies/Old", movies[0].path)
        assertEquals(0.25f, movies[1].progress, 0.001f)
        assertEquals(listOf("Season 1"), FoldersModel.entriesOf(files, state, "Series/Caminandes").map { it.name })
    }
}
