package com.msharwaan.exoplayer.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

/**
 * The one ExoPlayer, kept outside any screen so a display change, a screen swap or the
 * activity being recreated never restarts the film (plan: two-screen rules).
 */
@OptIn(UnstableApi::class)
class PlaybackHolder(private val context: Context) {

    private var instance: ExoPlayer? = null

    val player: ExoPlayer
        get() = instance ?: build().also { instance = it }

    private fun build(): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            // Hardware decoders first; if one refuses a stream, try the next instead of failing.
            .setEnableDecoderFallback(true)
            // FFmpeg (Jellyfin's Media3 build) only for audio the device can't decode: DTS, TrueHD, AC3 (D28).
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        return ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply { videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS }
    }

    /** Auto refresh rate: follow the film's frame rate when the switch is seamless; a fixed rate: never switch. */
    fun followFilmRate(follow: Boolean) {
        player.videoChangeFrameRateStrategy =
            if (follow) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
    }

    fun release() {
        instance?.release()
        instance = null
    }
}
