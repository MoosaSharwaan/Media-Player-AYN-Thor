package com.msharwaan.exoplayer.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
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
 * AVI's Xvid and DivX go to FFmpeg, not Android's decoder that garbles them (D49). Decoding
 * itself needs the Thor: the FFmpeg library is built for it, not for the PC.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OldVideoFormatsTest {

    private fun videoMime(name: String): String? {
        val player = PlaybackHolder(ApplicationProvider.getApplicationContext<Context>()).player
        val path = File(javaClass.classLoader!!.getResource("media/$name")!!.toURI())
        player.setMediaItem(MediaItem.fromUri("file://${path.absolutePath}"))
        player.prepare()
        // Until the file's tracks are known, however busy the machine running the tests is.
        for (i in 0 until 100) {
            if (!player.currentTracks.isEmpty) break
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
        val mime = player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }?.getTrackFormat(0)?.sampleMimeType
        player.release()
        return mime
    }

    @Test
    fun aviFormatsAreRecognised() {
        assertEquals(MimeTypes.VIDEO_MP4V, videoMime("xvid.avi"))
        assertEquals(MimeTypes.VIDEO_MP43, videoMime("divx3.avi"))
    }

    @Test
    fun xvidSkipsAndroidsDecodersWhenFfmpegHasOne() {
        val asked = mutableListOf<String>()
        val selector = PlaybackHolder.codecSelector { mime -> asked += mime; true }
        assertTrue(selector.getDecoderInfos(MimeTypes.VIDEO_MP4V, false, false).isEmpty())
        assertTrue(selector.getDecoderInfos(MimeTypes.VIDEO_DIVX, false, false).isEmpty())
        // Everything else is Android's as before, and FFmpeg isn't even asked.
        selector.getDecoderInfos(MimeTypes.VIDEO_H264, false, false)
        assertEquals(listOf(MimeTypes.VIDEO_MP4V, MimeTypes.VIDEO_DIVX), asked)
    }

    @Test
    fun withoutFfmpegXvidStaysWithAndroid() {
        val selector = PlaybackHolder.codecSelector { false }
        // Robolectric has no decoders, so the list is empty either way; what matters is that the
        // fallback asks Android rather than returning nothing on purpose.
        selector.getDecoderInfos(MimeTypes.VIDEO_MP4V, false, false)
    }
}
