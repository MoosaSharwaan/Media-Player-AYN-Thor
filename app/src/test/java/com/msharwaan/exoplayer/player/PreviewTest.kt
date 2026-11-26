package com.msharwaan.exoplayer.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.library.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Previews of the focused movie or episode (D51). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreviewTest {

    @Test
    fun momentsComeFromTheMiddleNeverTheEnding() {
        val random = Random(7)
        val hour = 3_600_000L
        repeat(500) {
            val t = Preview.randomMoment(hour, random)
            assertTrue("$t", t >= hour * 15 / 100 && t <= hour * 70 / 100)
        }
        assertEquals(0L, Preview.randomMoment(0))
    }

    private fun card(kind: String, resume: String? = null) =
        Card("k", kind, "T", null, "", null, null, null, playFileId = 9, resumeNote = resume)

    @Test
    fun onlyCardsThatPlayAFile() {
        assertEquals(9L, card("MOVIE").previewFileId)
        assertEquals(9L, card("OTHER").previewFileId)
        // A whole series opens its seasons, so it keeps its art; Continue watching plays the episode.
        assertNull(card("SHOW").previewFileId)
        assertEquals(9L, card("SHOW", resume = "S02E05 · 20 min left").previewFileId)
    }

    @Test
    fun onByDefaultSilentByDefaultAndRemembered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreviewPrefs(context)
        assertEquals(true, prefs.enabled.value)
        assertEquals(false, prefs.sound.value)
        prefs.setEnabled(false)
        prefs.setSound(true)
        val again = PreviewPrefs(context)
        assertEquals(false, again.enabled.value)
        assertEquals(true, again.sound.value)
    }
}
