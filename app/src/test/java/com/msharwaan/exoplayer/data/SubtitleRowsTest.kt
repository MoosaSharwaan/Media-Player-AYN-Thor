package com.msharwaan.exoplayer.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubtitleRowsTest {

    @Test
    fun aRescanDoesNotListADownloadedSubtitleTwice() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.library()
        val id = dao.insertFile(MediaFile(0, "v", "", "Film.mkv", 1, 1, "MOVIE", "m:film:", "Film", null, null, null, false))
        dao.insertSubtitles(listOf(SubtitleFile(mediaFileId = id, uri = "u/Film.en.srt", fileName = "Film.en.srt", language = "en", source = "downloaded")))
        // The scan finds the downloaded file next to the video, plus a local one.
        dao.replaceSubtitles(id, listOf(
            SubtitleFile(mediaFileId = id, uri = "u/Film.en.srt", fileName = "Film.en.srt", language = "en"),
            SubtitleFile(mediaFileId = id, uri = "u/Film.fr.srt", fileName = "Film.fr.srt", language = "fr"),
        ))
        val subs = dao.subtitlesOf(id).sortedBy { it.uri }
        assertEquals(listOf("u/Film.en.srt" to "downloaded", "u/Film.fr.srt" to "local"), subs.map { it.uri to it.source })
    }
}
