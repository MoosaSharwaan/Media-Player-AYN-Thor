package com.msharwaan.exoplayer.library

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.EpisodeInfo
import com.msharwaan.exoplayer.data.MatchOverride
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import com.msharwaan.exoplayer.data.SeasonInfo
import com.msharwaan.exoplayer.data.SubtitleFile
import com.msharwaan.exoplayer.data.TitleInfo
import com.msharwaan.exoplayer.data.WatchSession
import com.msharwaan.exoplayer.online.ImageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** What belonged to videos that are gone goes too; nothing of what's left is touched (D54). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CleanupTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.library()
    private val images = ImageStore(ImageStore.rootOf(context).apply { deleteRecursively() })

    /** A show with one episode file, its fetched details, artwork and history. Returns the file. */
    private suspend fun show(key: String, name: String): MediaFile {
        val id = dao.insertFile(MediaFile(0, "content://card/$name", name, "$name - 01.mkv", 1, 1, "EPISODE", key, name, null, 1, 1, true))
        dao.upsertTitle(TitleInfo(key, "SHOW", "ANIME", name, null))
        dao.upsertSeasons(listOf(SeasonInfo(key, 1, "Season 1", "About it.", 2020, 12, null)))
        dao.upsertEpisodes(listOf(EpisodeInfo(key, 1, 1, "One", "It starts.", 24, null)))
        images.folderFor(key).apply { mkdirs() }.resolve("poster.jpg").writeBytes(ByteArray(5000))
        dao.upsertPlayback(PlaybackState(id, positionMs = 600_000, durationMs = 1_400_000))
        dao.insertSession(WatchSession(mediaFileId = id, started = 1, ended = 2, secondsWatched = 600))
        return dao.file(id)!!
    }

    @Test
    fun aGoneShowTakesItsDetailsArtAndHistoryWithIt() = runBlocking {
        val gone = show("s:old show", "Old Show")
        val kept = show("s:frieren", "Frieren")
        // A subtitle the app kept itself (the video's folder couldn't be written to), and one next to the video on the card.
        val inApp = File(File(context.filesDir, "subtitles").apply { mkdirs() }, "${gone.id}.en.srt").apply { writeText("1\n00:00:01,000 --> 00:00:02,000\nHi\n") }
        dao.insertSubtitles(listOf(
            SubtitleFile(mediaFileId = gone.id, uri = Uri.fromFile(inApp).toString(), fileName = inApp.name, language = "en", source = "downloaded"),
            SubtitleFile(mediaFileId = gone.id, uri = "content://card/Old Show - 01.en.srt", fileName = "Old Show - 01.en.srt", language = "en"),
        ))
        dao.upsertOverride(MatchOverride("s:old show", 1234, "tv"))

        dao.deleteFiles(listOf(gone.id))
        val freed = Cleanup.run(context, db)

        assertEquals(listOf("s:frieren"), dao.titleKeys())
        assertTrue(dao.seasonsOf("s:old show").isEmpty())
        assertTrue(dao.episodesOf("s:old show").isEmpty())
        assertFalse(images.folderFor("s:old show").exists())
        assertFalse(inApp.exists())
        assertTrue(dao.orphanSubtitles().isEmpty())
        assertNull(dao.playback(gone.id))
        assertTrue(freed.bytes >= 5000)
        // A video that comes back is matched the same way.
        assertNotNull(dao.override("s:old show"))

        // What's left is untouched.
        assertEquals(1, dao.seasonsOf("s:frieren").size)
        assertEquals(1, dao.episodesOf("s:frieren").size)
        assertTrue(images.folderFor("s:frieren").resolve("poster.jpg").isFile)
        assertNotNull(dao.playback(kept.id))
    }

    @Test
    fun nothingGoneNothingCleared() = runBlocking {
        show("s:frieren", "Frieren")
        val freed = Cleanup.run(context, db)
        assertEquals(Cleanup.Freed(0, 0), freed)
        assertTrue(Cleanup.storageUsed(context) >= 5000)
    }
}
