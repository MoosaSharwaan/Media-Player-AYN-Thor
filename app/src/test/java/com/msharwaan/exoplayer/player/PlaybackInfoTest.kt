package com.msharwaan.exoplayer.player

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The paused screen's info line: enough to tell a slow decoder from a slow file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackInfoTest {

    private val xvid = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_MP4V).setWidth(720).setHeight(400).setFrameRate(23.976f).build()
    private val mp3 = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_MPEG).setChannelCount(2).setLanguage("en").build()

    @Test
    fun ffmpegVideoAndAndroidAudio() {
        assertEquals(
            "Video  FFmpeg mpeg4 · 720×400 · 23.98 fps · 3 dropped\nAudio  c2.android.mp3.decoder · EN · Stereo · MP3",
            PlayerModel.playbackInfo("ffmpegLavc60.3.100-mpeg4", xvid, 3, 0, "c2.android.mp3.decoder", mp3),
        )
    }

    @Test
    fun waitingForTheFileIsShown() {
        assertEquals(
            "Video  c2.qti.avc.decoder · 720×400 · 23.98 fps · 0 dropped · waited 4×",
            PlayerModel.playbackInfo("c2.qti.avc.decoder", xvid, 0, 4, null, null),
        )
    }

    @Test
    fun nothingYet() = assertNull(PlayerModel.playbackInfo(null, null, 0, 0, null, null))
}
