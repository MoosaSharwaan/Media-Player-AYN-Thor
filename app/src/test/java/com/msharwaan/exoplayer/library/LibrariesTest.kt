package com.msharwaan.exoplayer.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MatchOverride
import com.msharwaan.exoplayer.online.Tmdb
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Libraries: one for films, another for work, each its own titles (D52). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrariesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theOneFolderFromBeforeBecomesLibraryOne() {
        context.getSharedPreferences("library", Context.MODE_PRIVATE).edit().putString("folder", "content://t/tree/primary%3AMovies").commit()
        val prefs = LibraryPrefs(context)
        val first = prefs.libraries.value.single()
        assertEquals(LibraryPrefs.FIRST_ID, first.id)
        assertEquals("Library", first.name)
        assertEquals(listOf(Uri.parse("content://t/tree/primary%3AMovies")), first.folders)
        // Read back as the new list, not converted twice.
        assertEquals(listOf(first), LibraryPrefs(context).libraries.value)
    }

    @Test
    fun makeNameSwitchAndDelete() {
        val prefs = LibraryPrefs(context)
        val films = prefs.create()
        assertEquals("Library", films.name)
        assertEquals(films.id, prefs.activeId.value)
        val work = prefs.create()
        prefs.rename(work.id, "Work")
        prefs.addFolder(work.id, Uri.parse("content://t/tree/primary%3ACourses"))
        assertEquals("Work", prefs.next()?.name)
        assertEquals(work.id, prefs.activeId.value)
        assertEquals("Library", prefs.next()?.name)
        // Remembered.
        assertEquals(listOf("Library", "Work"), LibraryPrefs(context).libraries.value.map { it.name })
        assertTrue(prefs.delete(work.id))
        assertFalse("the last library stays", prefs.delete(films.id))
        assertNull(prefs.next())
    }

    private fun entries(vararg paths: String) = paths.map { p ->
        val parts = p.split('/')
        LibraryScanner.Entry(Uri.parse("content://test/" + Uri.encode(p)), parts.last(), 100, 1, parts.dropLast(1))
    }

    @Test
    fun eachLibraryHasItsOwnTitlesAndFiles() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val scanner = LibraryScanner(context, db)
        val dao = db.library()
        scanner.index(entries("Anime/Frieren - 01.mkv", "Anime/Frieren - 02.mkv"), emptyList(), libraryId = 1)
        scanner.index(entries("Work/Frieren - 01.mkv", "Work/Budget review.mp4"), emptyList(), libraryId = 2)

        assertEquals(2, dao.filesIn(1).size)
        assertEquals(2, dao.filesIn(2).size)
        // The same show in two libraries is two titles; library 1's keys are as they always were.
        assertEquals(setOf("s:frieren", "2|s:frieren"), dao.titles().filter { it.kind == "SHOW" }.map { it.groupKey }.toSet())

        // Reading library 2 again never removes library 1's files.
        scanner.index(entries("Work/Budget review.mp4"), emptyList(), libraryId = 2)
        assertEquals(2, dao.filesIn(1).size)
        assertEquals(1, dao.filesIn(2).size)

        // A folder that couldn't be read (SD card out) keeps its files and their history.
        scanner.index(emptyList(), emptyList(), libraryId = 1, removeMissing = false)
        assertEquals(2, dao.filesIn(1).size)
    }

    @Test
    fun oneShowInTwoLibrariesIsNeverMerged() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val scanner = LibraryScanner(context, db)
        scanner.index(entries("A/Fire Force - S01E01.mkv"), emptyList(), libraryId = 1)
        scanner.index(entries("B/Fire Force - S01E01.mkv"), emptyList(), libraryId = 2)
        val dao = db.library()
        for (key in listOf("s:fire force", "2|s:fire force")) {
            dao.upsertTitle(dao.title(key)!!.copy(name = "Fire Force", tmdbId = 88046, tmdbType = Tmdb.TV))
        }
        assertEquals(emptySet<String>(), SameShow.merge(db))
        assertEquals(2, dao.titles().size)
        // Within a library it still merges.
        scanner.index(entries("B/Enen no Shouboutai - S02E01.mkv", "B/Fire Force - S01E01.mkv"), emptyList(), libraryId = 2)
        dao.upsertTitle(dao.title("2|s:enen no shouboutai")!!.copy(name = "Fire Force", tmdbId = 88046, tmdbType = Tmdb.TV))
        assertEquals(setOf("2|s:fire force"), SameShow.merge(db))
        assertEquals(setOf("s:fire force", "2|s:fire force"), dao.titles().map { it.groupKey }.toSet())
        assertEquals(MatchOverride("2|s:enen no shouboutai", 88046, Tmdb.TV), dao.override("2|s:enen no shouboutai"))
    }

    @Test
    fun aLibraryFromBeforeLibrariesUpgradesAsLibraryOne() = runBlocking {
        // A database exactly as v1.0.5 made it, from the exported version 1 schema.
        val name = "upgrade-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.msharwaan.exoplayer.data.AppDatabase/1.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            for (q in 0 until schema.getJSONArray("setupQueries").length()) old.execSQL(schema.getJSONArray("setupQueries").getString(q))
            old.execSQL(
                "INSERT INTO media_file (uri, folderPath, fileName, size, modified, kind, groupKey, title, anime, durationMs) " +
                    "VALUES ('content://x/1', 'Anime', 'Frieren - 01.mkv', 1, 1, 'EPISODE', 's:frieren', 'Frieren', 1, 0)",
            )
            old.version = 1
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        val file = db.library().filesIn(LibraryPrefs.FIRST_ID).single()
        assertEquals("Frieren - 01.mkv", file.fileName)
        assertEquals(1L, file.libraryId)
        db.close()
    }
}
