package com.msharwaan.exoplayer.screenshots

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.TitleInfo
import com.msharwaan.exoplayer.online.FixMatchBottom
import com.msharwaan.exoplayer.online.FixMatchModel
import com.msharwaan.exoplayer.online.FixMatchTop
import com.msharwaan.exoplayer.online.Online
import com.msharwaan.exoplayer.screenshots.Screens.Panel
import com.msharwaan.exoplayer.settings.ReversingCrypto
import com.msharwaan.exoplayer.settings.Secrets
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class FixMatchScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: FixMatchModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            db.library().upsertTitle(TitleInfo("m:the matrix:", "MOVIE", "MOVIES", "The Matrix", null))
            db.library().insertFile(MediaFile(0, "u", "", "The.Matrix.1080p.BluRay.x264.mkv", 1, 1, "MOVIE", "m:the matrix:", "The Matrix", null, null, null, false))
        }
        model = FixMatchModel(MainScope(), db, Online(context, db, Secrets(context, ReversingCrypto())), { null }, {}, {})
        model.open("m:the matrix:")
        val until = System.currentTimeMillis() + 5_000
        while (model.ui.value.title == null && System.currentTimeMillis() < until) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun top() = Screens.shoot(rule, "fixmatch_top", Panel.TOP) { FixMatchTop(model) }

    @Test
    fun bottom() = Screens.shoot(rule, "fixmatch_bottom", Panel.BOTTOM) { FixMatchBottom(model) }
}
