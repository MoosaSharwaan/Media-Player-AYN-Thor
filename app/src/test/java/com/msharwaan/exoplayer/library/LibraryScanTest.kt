package com.msharwaan.exoplayer.library

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.TitleInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A library laid out like the anime batches on the Thor: one show per season name, with
 * openings, endings and an extras folder beside the episodes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryScanTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val scanner = LibraryScanner(context, db)

    private val paths = listOf(
        "Bungo Stray Dogs/[Judas] Bungo Stray Dogs S1 - 01.mkv",
        "Bungo Stray Dogs/[Judas] Bungo Stray Dogs S1 - 02.mkv",
        "Bungo Stray Dogs/[Judas] Bungo Stray Dogs S2 - 01.mkv",
        "Bungo Stray Dogs/[Judas] Bungo Stray Dogs S3 - 01.mkv",
        "Bungo Stray Dogs/[Judas] Bungo Stray Dogs S1 - Opening 1.mkv",
        "Bungo Stray Dogs/[Judas] Bungo Stray Dogs S1 - Ending 1.mkv",
        "Bungo Stray Dogs/[Judas] Bungo Stray Dogs S2 - Ending.mkv",
        "Bungo Stray Dogs/[Judas] Bungo Stray Dogs S2 - Opening 3.mkv",
        "Bungo Stray Dogs/[Judas] Bungo Stray Dogs - Movie 01 - Dead Apple.mkv",
        "Overlord (Seasons 1-3) [Dual-Audio][Eng-Subs]/Overlord - S1/[Judas] Overlord - S01E01.mkv",
        "Overlord (Seasons 1-3) [Dual-Audio][Eng-Subs]/Overlord - S1/[Judas] Overlord - S01E02.mkv",
        "Overlord (Seasons 1-3) [Dual-Audio][Eng-Subs]/Overlord - S1/S1 Extras/[Judas] Overlord - S1 - NCOP.mkv",
        "Overlord (Seasons 1-3) [Dual-Audio][Eng-Subs]/Overlord - S1/S1 Extras/[Judas] Overlord - S1 - NCED.mkv",
    )

    private fun entries() = paths.map { p ->
        val parts = p.split('/')
        LibraryScanner.Entry(Uri.parse("content://test/" + Uri.encode(p)), parts.last(), 100, 1, parts.dropLast(1))
    }

    private fun animeTab(): List<String> = runBlocking {
        Library.build(db.library().allFiles(), db.library().titles(), emptyList()).getValue(Tab.ANIME).map { it.name + " · " + it.meta }
    }

    @Test
    fun oneCardPerShowAndNoExtras() = runBlocking {
        scanner.index(entries(), emptyList())
        val tab = animeTab()
        assertEquals(tab.toString(), 3, tab.size)
        assertEquals(listOf("Bungo Stray Dogs", "Bungo Stray Dogs: Dead Apple", "Overlord"), db.library().titles().map { it.name }.sorted())
        val bungo = db.library().allFiles().filter { it.groupKey == "s:bungo stray dogs" }
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1, 3 to 1), bungo.map { it.season to it.episode }.sortedWith(compareBy({ it.first }, { it.second })))
        // The extras are still in the library, for the folder browser.
        assertEquals(6, db.library().allFiles().count { it.kind == ParsedName.Kind.EXTRA.name })
    }

    @Test
    fun aRescanFixesFilesReadByAnOlderVersion() = runBlocking {
        // How v1.0.1 filed them: "Bungo Stray Dogs S1" as its own show, the opening as a movie.
        val dao = db.library()
        dao.insertFile(MediaFile(0, "content://test/" + Uri.encode(paths[0]), "Bungo Stray Dogs", paths[0].substringAfter('/'), 100, 1, "EPISODE", "s:bungo stray dogs s1", "Bungo Stray Dogs S1", null, null, 1, true))
        dao.upsertTitle(TitleInfo(groupKey = "s:bungo stray dogs s1", kind = "SHOW", tab = "ANIME", name = "Bungo Stray Dogs S1", year = null))
        dao.insertFile(MediaFile(0, "content://test/" + Uri.encode(paths[4]), "Bungo Stray Dogs", paths[4].substringAfter('/'), 100, 1, "MOVIE", "m:bungo stray dogs s1 opening 1:", "Bungo Stray Dogs S1 - Opening 1", null, null, null, true))
        dao.upsertTitle(TitleInfo(groupKey = "m:bungo stray dogs s1 opening 1:", kind = "MOVIE", tab = "ANIME", name = "Bungo Stray Dogs S1 - Opening 1", year = null))

        // Same files, same size and date: only the reading changed.
        scanner.index(entries(), emptyList())
        assertEquals(listOf("Bungo Stray Dogs", "Bungo Stray Dogs: Dead Apple", "Overlord"), dao.titles().map { it.name }.sorted())
        assertEquals(3, animeTab().size)
    }

    private fun entriesOf(vararg paths: String) = paths.map { p ->
        val parts = p.split('/')
        LibraryScanner.Entry(Uri.parse("content://test/" + Uri.encode(p)), parts.last(), 100, 1, parts.dropLast(1))
    }

    /** What Fetch info leaves behind: the title matched to [tmdbId], named as TMDB names it. */
    private suspend fun matched(key: String, tmdbId: Long, name: String) {
        val dao = db.library()
        dao.upsertTitle(dao.title(key)!!.copy(name = name, tmdbId = tmdbId, tmdbType = com.msharwaan.exoplayer.online.Tmdb.TV))
    }

    @Test
    fun twoNamesOfOneShowBecomeOneCard() = runBlocking {
        val files = entriesOf(
            "Fire Force/[Judas] Enen no Shouboutai - S02E23.mkv",
            "Fire Force/[Judas] Enen no Shouboutai - S02E24.mkv",
            "Fire Force/[Judas] Fire Force - S03E01.mkv",
        )
        scanner.index(files, emptyList())
        assertEquals(2, db.library().titles().size)

        // Fetch info finds both on TMDB as Fire Force.
        matched("s:enen no shouboutai", 88046, "Fire Force")
        matched("s:fire force", 88046, "Fire Force")
        assertEquals(setOf("s:fire force"), SameShow.merge(db))

        val dao = db.library()
        assertEquals(listOf("s:fire force"), dao.titles().map { it.groupKey })
        assertEquals(listOf(2 to 23, 2 to 24, 3 to 1), dao.filesOf("s:fire force").map { it.season to it.episode })
        assertEquals(1, animeTab().size)

        // A rescan reads "Enen no Shouboutai" again, and files it under Fire Force again.
        scanner.index(files, emptyList())
        assertEquals(listOf("s:fire force"), dao.titles().map { it.groupKey })
        assertEquals(3, dao.filesOf("s:fire force").size)
    }

    @Test
    fun differentShowsInOneFolderStayApart() = runBlocking {
        scanner.index(entriesOf("Naruto/[Judas] Naruto - S01E01.mkv", "Naruto/[Judas] Naruto Shippuden - S01E01.mkv"), emptyList())
        matched("s:naruto", 46260, "Naruto")
        matched("s:naruto shippuden", 31910, "Naruto Shippūden")
        assertEquals(emptySet<String>(), SameShow.merge(db))
        assertEquals(2, db.library().titles().size)
    }
}
