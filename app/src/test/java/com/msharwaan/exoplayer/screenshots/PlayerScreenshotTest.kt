package com.msharwaan.exoplayer.screenshots

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.msharwaan.exoplayer.player.PlayerBottom
import com.msharwaan.exoplayer.player.PlayerButton
import com.msharwaan.exoplayer.player.PlayerTop
import com.msharwaan.exoplayer.player.PlayerUi
import com.msharwaan.exoplayer.player.UpNext
import com.msharwaan.exoplayer.screenshots.Screens.Panel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the player's screens from sample states; the picture itself is left out. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class PlayerScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val movie = PlayerUi(
        fileId = 1, groupKey = "m:sintel", title = "Sintel", meta = "2010 · 15 min · Animation",
        overview = "A lonely young woman, Sintel, helps and befriends a dragon, whom she calls Scales.",
        posterPath = Screens.art("posters", "sintel"), backdropPath = Screens.art("backdrops", "sintel"),
        playing = true, positionMs = 372_000, durationMs = 888_000,
        audioLabel = "ENG · Stereo", subtitleLabel = "EN · SRT", subtitleOn = true, subtitleDelayMs = -150,
    )

    private val episode = movie.copy(
        fileId = 2, groupKey = "s:caminandes", isEpisode = true, title = "Caminandes · S01E02", meta = "2013 · Animation",
        posterPath = Screens.art("posters", "caminandes"), backdropPath = Screens.art("backdrops", "caminandes"),
        hasPrev = true, hasNext = true, subtitleLabel = "None in file · Find subtitles", subtitleMissing = true,
        subtitleOn = false, subtitleDelayMs = 0, focus = PlayerButton.SUBTITLES,
    )

    @Test
    fun pausedTop() = Screens.shoot(rule, "player_top_paused", Panel.TOP) { PlayerTop(movie.copy(paused = true), null) {} }

    @Test
    fun pausedTopWithPlaybackInfo() = Screens.shoot(rule, "player_top_paused_info", Panel.TOP) {
        PlayerTop(movie.copy(paused = true, playbackInfo = "Video  FFmpeg mpeg4 · 720×400 · 23.98 fps · 3 dropped\nAudio  c2.android.mp3.decoder · EN · Stereo · MP3"), null) {}
    }

    @Test
    fun subtitleTop() = Screens.shoot(rule, "player_top_subtitle", Panel.TOP) {
        PlayerTop(movie, "I've come to find my dragon.\nHave you seen him?") {}
    }

    /** An ASS sign where the file puts it, a quarter down a 16:9 frame, and the dialogue below. */
    @Test
    fun signTop() = Screens.shoot(rule, "player_top_sign", Panel.TOP) {
        PlayerTop(movie, "That's a third-generation pyrokinetic for you.") {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF3A4A5A)),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                com.msharwaan.exoplayer.player.SubtitleSigns(
                    listOf(com.msharwaan.exoplayer.player.SignCue("Head Spa", 0.5f, 0.25f, 0.5f, 0.5f, 18f / 360)),
                    androidx.compose.ui.Modifier.aspectRatio(16f / 9f),
                )
            }
        }
    }

    @Test
    fun movieBottom() = Screens.shoot(rule, "player_bottom_movie", Panel.BOTTOM) { PlayerBottom(movie, 65, {}, {}, {}) }

    @Test
    fun moviePausedBottom() = Screens.shoot(rule, "player_bottom_movie_paused", Panel.BOTTOM) { PlayerBottom(movie.copy(paused = true), 65, {}, {}, {}) }

    @Test
    fun episodeBottom() = Screens.shoot(rule, "player_bottom_episode", Panel.BOTTOM) { PlayerBottom(episode.copy(paused = true), 150, {}, {}, {}) }

    @Test
    fun settingsPanel() = Screens.shoot(rule, "player_bottom_settings", Panel.BOTTOM) {
        PlayerBottom(
            movie.copy(panelRow = 2), 65, {}, {}, {},
            settings = com.msharwaan.exoplayer.player.PlaybackSettings(speed = 1.25f, picture = com.msharwaan.exoplayer.player.Picture.ZOOM, audioDelayMs = 150),
        )
    }

    @Test
    fun upNext() = Screens.shoot(rule, "player_bottom_upnext", Panel.BOTTOM) {
        PlayerBottom(episode.copy(upNext = UpNext(3, "Caminandes", "S01E03", 7)), 65, {}, {}, {})
    }
}
