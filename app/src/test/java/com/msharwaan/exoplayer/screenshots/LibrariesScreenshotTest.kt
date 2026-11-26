package com.msharwaan.exoplayer.screenshots

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.library.LibraryPrefs
import com.msharwaan.exoplayer.screenshots.Screens.Panel
import com.msharwaan.exoplayer.settings.LibrariesBottom
import com.msharwaan.exoplayer.settings.LibrariesModel
import com.msharwaan.exoplayer.settings.LibrariesTop
import kotlinx.coroutines.MainScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Libraries (D52): the list, and one library's page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class LibrariesScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: LibrariesModel

    @Before
    fun setUp() {
        Screens.warmUpGraphics()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val prefs = LibraryPrefs(context)
        val films = prefs.create("Entertainment")
        prefs.addFolder(films.id, Uri.parse("content://x/tree/1234-ABCD%3AMovies"))
        prefs.addFolder(films.id, Uri.parse("content://x/tree/1234-ABCD%3AAnime"))
        val work = prefs.create("Work")
        prefs.addFolder(work.id, Uri.parse("content://x/tree/primary%3ACourses"))
        model = LibrariesModel(MainScope(), db, prefs, {}, {}, {}, {})
        model.reset()
    }

    @Test
    fun list() = Screens.shoot(rule, "libraries_bottom_list", Panel.BOTTOM) { LibrariesBottom(model) }

    @Test
    fun listTop() = Screens.shoot(rule, "libraries_top_list", Panel.TOP) { LibrariesTop(model) }

    @Test
    fun page() {
        model.onAction(PadAction.DOWN)
        model.onAction(PadAction.A)
        Screens.shoot(rule, "libraries_bottom_page", Panel.BOTTOM) { LibrariesBottom(model) }
    }

    @Test
    fun pageTop() {
        model.onAction(PadAction.A)
        Screens.shoot(rule, "libraries_top_page", Panel.TOP) { LibrariesTop(model) }
    }
}
