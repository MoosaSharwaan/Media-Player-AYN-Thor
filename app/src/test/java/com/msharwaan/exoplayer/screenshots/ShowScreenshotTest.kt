package com.msharwaan.exoplayer.screenshots

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.EpisodeInfo
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import com.msharwaan.exoplayer.data.SeasonInfo
import com.msharwaan.exoplayer.data.TitleInfo
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.screenshots.Screens.Panel
import com.msharwaan.exoplayer.show.ShowBottom
import com.msharwaan.exoplayer.show.ShowModel
import com.msharwaan.exoplayer.show.ShowTop
import com.msharwaan.exoplayer.show.ShowView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The series screens with a two-season sample show, driven with the same buttons as on the Thor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class ShowScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: ShowModel
    private val ids = mutableMapOf<Pair<Int, Int>, Long>()

    @Before
    fun setUp() {
        Screens.warmUpGraphics()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            val dao = db.library()
            dao.upsertTitle(
                TitleInfo(
                    "s:caminandes", "SHOW", "SERIES", "Caminandes", 2013, genres = "Animation",
                    overview = "Koro the llama keeps finding new ways to get past a fence in the Patagonian wilderness.",
                    posterPath = Screens.art("posters", "caminandes"), backdropPath = Screens.art("backdrops", "caminandes"),
                ),
            )
            dao.upsertSeasons(
                listOf(
                    SeasonInfo("s:caminandes", 1, "Season 1", "Koro meets the fence.", 2013, 3, Screens.art("posters", "caminandes")),
                    SeasonInfo("s:caminandes", 2, "Season 2", "Koro meets Oti the penguin.", 2016, 3, null),
                ),
            )
            dao.upsertEpisodes(
                listOf(
                    EpisodeInfo("s:caminandes", 1, 1, "Llama Drama", "Koro tries to cross the road.", 2, null),
                    EpisodeInfo("s:caminandes", 1, 2, "Gran Dillama", "The grass is greener on the other side.", 3, null),
                    EpisodeInfo("s:caminandes", 1, 3, "Llamigos", "Koro meets a penguin who wants his berries.", 3, null),
                ),
            )
            for (s in 1..2) for (e in 1..(if (s == 1) 2 else 3)) {
                ids[s to e] = dao.insertFile(
                    MediaFile(0, "c$s$e", "", "Caminandes.S0${s}E0$e.mkv", 1, 1, "EPISODE", "s:caminandes", "Caminandes", null, s, e, false),
                )
            }
            dao.upsertPlayback(PlaybackState(ids.getValue(1 to 1), 120_000, 120_000, watched = true, lastPlayed = 1))
            dao.upsertPlayback(PlaybackState(ids.getValue(1 to 2), 60_000, 180_000, lastPlayed = 2))
        }
        model = ShowModel(MainScope(), db) {}
    }

    private fun open(openEpisodes: Boolean = false, fileId: Long? = null) {
        model.open("s:caminandes", openEpisodes, fileId)
        // Room delivers on its own thread: give it real time while the main looper runs.
        val until = System.currentTimeMillis() + 5_000
        while (model.data.value == null && System.currentTimeMillis() < until) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun press(vararg actions: PadAction) = actions.forEach { model.onAction(it) }

    @Test
    fun details() {
        open()
        Screens.shoot(rule, "show_top_details", Panel.TOP) { ShowTop(model) }
    }

    @Test
    fun seasons() {
        open()
        press(PadAction.A)
        Screens.shoot(rule, "show_bottom_seasons", Panel.BOTTOM) { ShowBottom(model) {} }
    }

    @Test
    fun episodesAndBack() {
        open()
        press(PadAction.A, PadAction.A, PadAction.A, PadAction.RIGHT)
        assertEquals(ShowView(ShowView.Level.EPISODES, seasonFocus = 0, season = 0, episodeFocus = 1), model.view.value)
        Screens.shoot(rule, "show_bottom_episodes", Panel.BOTTOM) { ShowBottom(model) {} }
        // A plays the focused episode; B steps back to the season cards, the season still focused.
        assertEquals(ShowModel.Result.Play(ids.getValue(1 to 2)), model.onAction(PadAction.A))
        press(PadAction.B)
        assertEquals(ShowView(ShowView.Level.SEASONS, seasonFocus = 0), model.view.value)
    }

    @Test
    fun episodeTop() {
        open()
        press(PadAction.A, PadAction.A, PadAction.A, PadAction.RIGHT)
        Screens.shoot(rule, "show_top_episode", Panel.TOP) { ShowTop(model) }
    }

    @Test
    fun continuingLandsOnTheEpisode() {
        open(openEpisodes = true, fileId = ids.getValue(2 to 3))
        assertEquals(ShowView(ShowView.Level.EPISODES, seasonFocus = 1, season = 1, episodeFocus = 2), model.view.value)
        // B: back to the season cards with season 2 focused, so the top shows that season.
        press(PadAction.B)
        Screens.shoot(rule, "show_top_season", Panel.TOP) { ShowTop(model) }
    }
}
