package com.msharwaan.exoplayer.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/**
 * Every common audio format inside MKV and MP4 is found, with a label that tells the tracks
 * apart. Half-second clips made with ffmpeg. Decoding itself needs the device (Android's
 * decoders, then FFmpeg), so it isn't tested here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioTracksTest {

    private fun labels(name: String): List<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = PlaybackHolder(context).player
        val path = File(javaClass.classLoader!!.getResource("media/$name")!!.toURI())
        player.setMediaItem(MediaItem.fromUri("file://${path.absolutePath}"))
        player.prepare()
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.sleep(20) }
        val labels = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.map { TrackLabels.audio(it.getTrackFormat(0)) }
        player.release()
        return labels
    }

    @Test
    fun allFormatsInMkv() {
        assertEquals(
            listOf(
                "EN · Stereo · AAC · Main",
                "EN · 5.1 · AC3 · Commentary",
                "JA · 5.1 · E-AC3",
                "FR · 5.1 · DTS",
                "DE · 5.1 · TrueHD",
                "Stereo · FLAC",
                "Stereo · Opus",
                "Stereo · MP3",
                "Stereo · Vorbis",
                "Stereo · PCM",
            ),
            labels("audio-tracks.mkv"),
        )
    }

    @Test
    fun dolbyInMp4() {
        assertEquals(listOf("EN · Stereo · AAC", "ES · 5.1 · AC3", "5.1 · E-AC3"), labels("audio-tracks.mp4"))
    }
}
