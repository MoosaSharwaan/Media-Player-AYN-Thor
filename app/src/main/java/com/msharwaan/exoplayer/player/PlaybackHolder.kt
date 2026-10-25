package com.msharwaan.exoplayer.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.session.MediaSession

/**
 * The one ExoPlayer, kept outside any screen so a display change, a screen swap or the
 * activity being recreated never restarts the film (plan: two-screen rules).
 */
@OptIn(UnstableApi::class)
class PlaybackHolder(private val context: Context) {

    private var instance: ExoPlayer? = null
    private var session: MediaSession? = null

    /** Audio delay (Playback settings): positive plays the sound later than the picture. */
    @Volatile var audioDelayUs: Long = 0

    val player: ExoPlayer
        get() = instance ?: build().also { instance = it }

    private fun build(): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? =
                super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)?.let { DelayedAudioSink(it) { audioDelayUs } }
        }
            // Hardware decoders first; if one refuses a stream, try the next instead of failing.
            .setEnableDecoderFallback(true)
            // FFmpeg (Jellyfin's Media3 build) only for audio the device can't decode: DTS, TrueHD, AC3 (D28).
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val player = ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply { videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS }
        // Headset and Bluetooth play / pause buttons, while the app is open.
        session = runCatching { MediaSession.Builder(context, player).build() }.getOrNull()
        return player
    }

    /** Auto refresh rate: follow the film's frame rate when the switch is seamless; a fixed rate: never switch. */
    fun followFilmRate(follow: Boolean) {
        player.videoChangeFrameRateStrategy =
            if (follow) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
    }

    fun release() {
        session?.release()
        session = null
        instance?.release()
        instance = null
    }
}

/**
 * Shifts the clock the picture follows: reporting the sound [offsetUs] ahead makes frames
 * show that much earlier, so the sound is heard later relative to the picture.
 */
@OptIn(UnstableApi::class)
private class DelayedAudioSink(sink: AudioSink, private val offsetUs: () -> Long) : ForwardingAudioSink(sink) {
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        return if (position == AudioSink.CURRENT_POSITION_NOT_SET) position else (position + offsetUs()).coerceAtLeast(0)
    }
}
