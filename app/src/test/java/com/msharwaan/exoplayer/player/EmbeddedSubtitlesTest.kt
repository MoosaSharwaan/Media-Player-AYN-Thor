package com.msharwaan.exoplayer.player

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/**
 * Subtitles inside the video file reach the top screen. The files in resources/media are
 * 5-second clips with "Hello from the file" from 0.2 s to 4.5 s; image-pgs.mkv has a white
 * box instead (made by media/src/make_pgs.py).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmbeddedSubtitlesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class Played(val label: String, val lines: List<String>, val images: List<ImageCue>)

    /** Plays [name] through the app's player to its end and records what the top screen was given. */
    private fun play(name: String): Played {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val path = File(javaClass.classLoader!!.getResource("media/$name")!!.toURI())
        val id = runBlocking {
            db.library().insertFile(MediaFile(0, "file://${path.absolutePath}", "", name, path.length(), 1, "MOVIE", "m:t:", "T", null, null, null, false))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val model = PlayerModel(context, scope, db, PlaybackHolder(context), scope, {}, {})
        val lines = mutableListOf<String>()
        val images = mutableListOf<ImageCue>()
        model.open(id)
        // To the end of the clip, however busy the machine running the tests is (at most 20 s).
        for (i in 0 until 200) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
            model.subtitle.value?.let { if (lines.lastOrNull() != it) lines += it }
            images += model.subtitleImages.value.filter { it !in images }
            if (model.player.playbackState == androidx.media3.common.Player.STATE_ENDED) break
        }
        val label = model.ui.value.subtitleLabel
        model.player.release()
        return Played(label, lines, images)
    }

    @Test
    fun srtInsideMkv() {
        val played = play("text-srt.mkv")
        assertEquals("EN · SRT", played.label)
        assertEquals(listOf("Hello from the file"), played.lines)
    }

    @Test
    fun assInsideMkv() {
        val played = play("text-ass.mkv")
        assertEquals("EN · ASS", played.label)
        assertEquals(listOf("Hello from the file"), played.lines)
    }

    @Test
    fun movTextInsideMp4() {
        val played = play("text-movtext.mp4")
        assertEquals("EN · Text", played.label)
        assertEquals(listOf("Hello from the file"), played.lines)
    }

    @Test
    fun pgsPictureInsideMkv() {
        val played = play("image-pgs.mkv")
        assertEquals("EN · PGS", played.label)
        assertEquals(1, played.images.size)
        // A 200 × 40 box at (60, 120) on a 320 × 180 frame.
        val cue = played.images.single()
        assertEquals(200, cue.bitmap.width)
        assertEquals(60f / 320, cue.left, 0.01f)
        assertEquals(120f / 180, cue.top, 0.01f)
        assertEquals(200f / 320, cue.width, 0.01f)
        assertEquals(40f / 180, cue.height!!, 0.01f)
        assertTrue(played.lines.isEmpty())
    }
}
