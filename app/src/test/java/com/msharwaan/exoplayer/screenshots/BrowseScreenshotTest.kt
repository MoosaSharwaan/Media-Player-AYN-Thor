package com.msharwaan.exoplayer.screenshots

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.msharwaan.exoplayer.browse.BrowseBottom
import com.msharwaan.exoplayer.browse.BrowseModel
import com.msharwaan.exoplayer.browse.BrowseTop
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import com.msharwaan.exoplayer.data.TitleInfo
import com.msharwaan.exoplayer.library.LibraryPrefs
import com.msharwaan.exoplayer.library.LibraryScanner
import com.msharwaan.exoplayer.library.Tab
import com.msharwaan.exoplayer.screenshots.Screens.Panel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders Browsing with sample data so the layouts can be checked without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class BrowseScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: BrowseModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        context.getSharedPreferences("library", Context.MODE_PRIVATE).edit().putString("folder", "content://test").commit()
        runBlocking { seed(db) }
        model = BrowseModel(MainScope(), db, LibraryPrefs(context), LibraryScanner(context, db))
        rule.waitUntil(5_000) { model.library.value.values.any { it.isNotEmpty() } }
    }

    private suspend fun seed(db: AppDatabase) {
        val dao = db.library()
        val movies = listOf(
            Triple("sintel", "Sintel", 2010), Triple("tears-of-steel", "Tears of Steel", 2012),
            Triple("big-buck-bunny", "Big Buck Bunny", 2008), Triple("cosmos-laundromat", "Cosmos Laundromat", 2015),
            Triple("elephants-dream", "Elephants Dream", 2006), Triple("charge", "Charge", 2022),
        )
        movies.forEachIndexed { i, (art, name, year) ->
            val key = "m:$art"
            dao.upsertTitle(
                TitleInfo(
                    key, "MOVIE", "MOVIES", name, year, overview = "A short film from the Blender Foundation, used here as sample content.",
                    runtimeMin = 12, genres = "Animation", posterPath = Screens.art("posters", art), backdropPath = Screens.art("backdrops", art),
                ),
            )
            val id = dao.insertFile(MediaFile(0, "u$i", "", "$name.mkv", 1, 1, "MOVIE", key, name, year, null, null, false))
            if (i == 0) dao.upsertPlayback(PlaybackState(id, positionMs = 6 * 60_000, durationMs = 15 * 60_000, lastPlayed = 10))
        }
        dao.upsertTitle(TitleInfo("s:caminandes", "SHOW", "SERIES", "Caminandes", 2013, posterPath = Screens.art("posters", "caminandes"), backdropPath = Screens.art("backdrops", "caminandes")))
        for (e in 1..3) dao.insertFile(MediaFile(0, "c$e", "", "Caminandes.S01E0$e.mkv", 1, 1, "EPISODE", "s:caminandes", "Caminandes", null, 1, e, false))
        dao.upsertTitle(TitleInfo("s:frieren", "SHOW", "ANIME", "Frieren", null))
        dao.insertFile(MediaFile(0, "f1", "", "[SubsPlease] Frieren - 01.mkv", 1, 1, "EPISODE", "s:frieren", "Frieren", null, null, 1, true))
    }

    @Test
    fun posterMix() {
        model.selectTab(Tab.MOVIES)
        Screens.shoot(rule, "browse_top_mix", Panel.TOP) { BrowseTop(model) }
    }

    @Test
    fun focusedTitle() {
        model.selectTab(Tab.MOVIES)
        model.focusCard(1)
        Screens.shoot(rule, "browse_top_focused", Panel.TOP) { BrowseTop(model) }
    }

    @Test
    fun cards() {
        model.selectTab(Tab.MOVIES)
        model.focusCard(1)
        Screens.shoot(rule, "browse_bottom_movies", Panel.BOTTOM) { BrowseBottom(model, onOpen = {}, onFetchInfo = {}) }
    }

    @Test
    fun continueWatching() {
        model.selectTab(Tab.CONTINUE)
        model.focusCard(0)
        Screens.shoot(rule, "browse_top_continue", Panel.TOP) { BrowseTop(model) }
    }
}
