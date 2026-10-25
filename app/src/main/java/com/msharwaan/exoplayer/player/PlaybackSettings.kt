package com.msharwaan.exoplayer.player

import android.content.Context
import androidx.core.content.edit

/** How the picture fills the top screen. */
enum class Picture(val label: String) {
    FIT("Fit"), ZOOM("Zoom to fill"), WIDE("16:9"), STANDARD("4:3"), STRETCH("Stretch");
}

enum class SubtitleSize(val label: String, val sp: Int) { SMALL("Small", 22), MEDIUM("Medium", 30), LARGE("Large", 38), HUGE("Extra large", 46) }

/** How far above the bottom edge subtitles sit, e.g. to clear a letterbox bar. */
enum class SubtitlePosition(val label: String, val dp: Int) { LOW("Bottom", 36), MIDDLE("Raised", 96), HIGH("High", 170) }

/**
 * Playback settings (Start in the player). Speed lasts for the session; audio delay is kept
 * per file; the rest are kept for every film.
 */
data class PlaybackSettings(
    val speed: Float = 1f,
    val picture: Picture = Picture.FIT,
    val audioDelayMs: Int = 0,
    val subtitleSize: SubtitleSize = SubtitleSize.MEDIUM,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.LOW,
    val subtitleBackground: Boolean = false,
    /** Top screen brightness 0.1–1, or null for the system's own. */
    val brightness: Float? = null,
) {
    /** The rows of the panel, in order. */
    enum class Row(val label: String) {
        SPEED("Speed"), PICTURE("Picture"), AUDIO_DELAY("Audio delay"),
        SUBTITLE_SIZE("Subtitle size"), SUBTITLE_POSITION("Subtitle position"), SUBTITLE_BACKGROUND("Subtitle background"),
        BRIGHTNESS("Top screen brightness"),
    }

    fun valueOf(row: Row): String = when (row) {
        Row.SPEED -> if (speed == 1f) "Normal" else "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}×"
        Row.PICTURE -> picture.label
        Row.AUDIO_DELAY -> if (audioDelayMs == 0) "0 ms" else "%+d ms".format(audioDelayMs)
        Row.SUBTITLE_SIZE -> subtitleSize.label
        Row.SUBTITLE_POSITION -> subtitlePosition.label
        Row.SUBTITLE_BACKGROUND -> if (subtitleBackground) "Dark box" else "Off"
        Row.BRIGHTNESS -> brightness?.let { "${(it * 100).toInt()} %" } ?: "System"
    }

    /** One step left (-1) or right (+1) on [row]. */
    fun step(row: Row, dir: Int): PlaybackSettings = when (row) {
        Row.SPEED -> copy(speed = SPEEDS[(SPEEDS.indexOf(speed).coerceAtLeast(0) + dir).coerceIn(0, SPEEDS.size - 1)])
        Row.PICTURE -> copy(picture = cycle(Picture.entries, picture, dir))
        Row.AUDIO_DELAY -> copy(audioDelayMs = (audioDelayMs + dir * 50).coerceIn(-MAX_AUDIO_DELAY, MAX_AUDIO_DELAY))
        Row.SUBTITLE_SIZE -> copy(subtitleSize = SubtitleSize.entries[(subtitleSize.ordinal + dir).coerceIn(0, SubtitleSize.entries.size - 1)])
        Row.SUBTITLE_POSITION -> copy(subtitlePosition = SubtitlePosition.entries[(subtitlePosition.ordinal + dir).coerceIn(0, SubtitlePosition.entries.size - 1)])
        Row.SUBTITLE_BACKGROUND -> copy(subtitleBackground = !subtitleBackground)
        Row.BRIGHTNESS -> {
            val i = BRIGHTNESS.indexOf(brightness).coerceAtLeast(0)
            copy(brightness = BRIGHTNESS[(i + dir).coerceIn(0, BRIGHTNESS.size - 1)])
        }
    }

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        /** System first, then 10 % to 100 %. */
        val BRIGHTNESS: List<Float?> = listOf(null) + (1..10).map { it / 10f }
        const val MAX_AUDIO_DELAY = 2_000

        private fun <T> cycle(all: List<T>, now: T, dir: Int) = all[(all.indexOf(now) + dir + all.size) % all.size]
    }
}

/** Keeps the settings that last beyond one film. */
class PlaybackPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    fun load(): PlaybackSettings = PlaybackSettings(
        picture = runCatching { Picture.valueOf(prefs.getString("picture", null)!!) }.getOrDefault(Picture.FIT),
        subtitleSize = runCatching { SubtitleSize.valueOf(prefs.getString("subSize", null)!!) }.getOrDefault(SubtitleSize.MEDIUM),
        subtitlePosition = runCatching { SubtitlePosition.valueOf(prefs.getString("subPos", null)!!) }.getOrDefault(SubtitlePosition.LOW),
        subtitleBackground = prefs.getBoolean("subBg", false),
        brightness = prefs.getFloat("brightness", -1f).takeIf { it > 0f },
    )

    fun save(s: PlaybackSettings) = prefs.edit {
        putString("picture", s.picture.name)
        putString("subSize", s.subtitleSize.name)
        putString("subPos", s.subtitlePosition.name)
        putBoolean("subBg", s.subtitleBackground)
        putFloat("brightness", s.brightness ?: -1f)
    }

    fun audioDelay(fileId: Long): Int = prefs.getInt("audioDelay_$fileId", 0)

    fun saveAudioDelay(fileId: Long, ms: Int) = prefs.edit { if (ms == 0) remove("audioDelay_$fileId") else putInt("audioDelay_$fileId", ms) }
}
