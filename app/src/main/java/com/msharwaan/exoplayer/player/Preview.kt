package com.msharwaan.exoplayer.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.msharwaan.exoplayer.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Previews on or off (on by default), and their sound (off by default), from Settings (D51). */
class PreviewPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("preview", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _sound = MutableStateFlow(prefs.getBoolean(KEY_SOUND, false))
    val sound: StateFlow<Boolean> = _sound.asStateFlow()

    fun setEnabled(on: Boolean) { prefs.edit { putBoolean(KEY_ENABLED, on) }; _enabled.value = on }

    fun setSound(on: Boolean) { prefs.edit { putBoolean(KEY_SOUND, on) }; _sound.value = on }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_SOUND = "sound"
    }
}

/**
 * Plays moments of the focused movie or episode behind its details on the top screen (D51).
 *
 * Its own player, so a preview never touches where a film was stopped. It starts after a second
 * on the same card, jumps to a new random moment every 12 s from the middle of the file (no
 * intro, no ending), and stops, freeing its decoder, as soon as nothing is focused.
 */
@OptIn(UnstableApi::class)
class Preview(private val context: Context, private val db: AppDatabase, val prefs: PreviewPrefs) {

    private val scope = MainScope()
    private var job: Job? = null
    private var current: Long? = null
    private var instance: ExoPlayer? = null

    private val _ready = MutableStateFlow<Long?>(null)
    /** The file whose picture is showing, so its art can step aside; null before the first frame. */
    val ready: StateFlow<Long?> = _ready.asStateFlow()

    val player: ExoPlayer
        get() = instance ?: build().also { instance = it }

    /** Previews [fileId] after a moment on it, or stops with null. Calling again with the same file does nothing. */
    fun show(fileId: Long?) {
        val id = fileId.takeIf { prefs.enabled.value }
        if (id == current) return
        current = id
        job?.cancel()
        _ready.value = null
        instance?.stop()
        if (id == null) return
        job = scope.launch {
            delay(START_AFTER_MS)
            val file = withContext(Dispatchers.IO) { db.library().file(id) } ?: return@launch
            val p = player
            p.volume = if (prefs.sound.value) SOUND_VOLUME else 0f
            p.setMediaItem(MediaItem.fromUri(Uri.parse(file.uri)), randomMoment(file.durationMs))
            p.prepare()
            p.play()
            var moved = file.durationMs > 0
            while (isActive) {
                delay(if (moved) MOMENT_MS else 500)
                val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: continue
                p.seekTo(randomMoment(duration))
                moved = true
            }
        }
    }

    private fun build(): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setMediaCodecSelector(PlaybackHolder.codecSelector(FfmpegLibrary::supportsFormat))
        return ExoPlayer.Builder(context, renderers)
            // A preview never takes the sound away from anything else.
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), false)
            // Jumping to the nearest key frame is near instant, and decodes far less.
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { _ready.value = current }
                    override fun onPlayerError(error: PlaybackException) { _ready.value = null; stop() }
                })
            }
    }

    companion object {
        const val START_AFTER_MS = 1_000L
        const val MOMENT_MS = 12_000L
        private const val SOUND_VOLUME = 0.6f

        /** Somewhere between 15 % and 70 % through: past the intro, before anything that gives the ending away. */
        fun randomMoment(durationMs: Long, random: Random = Random): Long =
            if (durationMs <= 0) 0 else (durationMs * (0.15 + random.nextDouble() * 0.55)).toLong()
    }
}
