package com.msharwaan.exoplayer.screenshots

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
    fun subtitleTop() = Screens.shoot(rule, "player_top_subtitle", Panel.TOP) {
        PlayerTop(movie, "I've come to find my dragon.\nHave you seen him?") {}
    }

    @Test
    fun movieBottom() = Screens.shoot(rule, "player_bottom_movie", Panel.BOTTOM) { PlayerBottom(movie, 65, {}, {}, {}) }

    @Test
    fun episodeBottom() = Screens.shoot(rule, "player_bottom_episode", Panel.BOTTOM) { PlayerBottom(episode.copy(paused = true), 150, {}, {}, {}) }

    @Test
    fun upNext() = Screens.shoot(rule, "player_bottom_upnext", Panel.BOTTOM) {
        PlayerBottom(episode.copy(upNext = UpNext(3, "Caminandes", "S01E03", 7)), 65, {}, {}, {})
    }
}
