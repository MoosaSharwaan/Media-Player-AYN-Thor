package com.msharwaan.exoplayer.screenshots

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.screenshots.Screens.Panel
import com.msharwaan.exoplayer.settings.DisplayPrefs
import com.msharwaan.exoplayer.settings.ReversingCrypto
import com.msharwaan.exoplayer.settings.Screen
import com.msharwaan.exoplayer.settings.Secret
import com.msharwaan.exoplayer.settings.Secrets
import com.msharwaan.exoplayer.settings.SettingsBottom
import com.msharwaan.exoplayer.settings.SettingsModel
import com.msharwaan.exoplayer.settings.SettingsTop
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class SettingsScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: SettingsModel

    @Before
    fun setUp() {
        val secrets = Secrets(ApplicationProvider.getApplicationContext(), ReversingCrypto())
        secrets[Secret.TMDB_KEY] = "0123456789abcdef0123456789ab3f9a"
        secrets[Secret.OPENSUBTITLES_USER] = "msharwaan"
        val prefs = DisplayPrefs(ApplicationProvider.getApplicationContext()).apply { setRate(Screen.TOP, 120f) }
        model = SettingsModel(secrets, prefs, { listOf(60f, 120f) }, { Uri.parse("content://x/tree/1234-ABCD%3AVideos") }, { "k3y" }, {}, {})
    }

    @Test
    fun list() = Screens.shoot(rule, "settings_bottom_list", Panel.BOTTOM) { SettingsBottom(model) }

    @Test
    fun top() {
        model.onAction(PadAction.DOWN)
        model.onAction(PadAction.DOWN)
        Screens.shoot(rule, "settings_top", Panel.TOP) { SettingsTop(model) }
    }

    @Test
    fun editor() {
        model.onAction(PadAction.DOWN)
        model.onAction(PadAction.DOWN)
        model.onAction(PadAction.A) // OpenSubtitles API key, empty
        listOf(PadAction.A, PadAction.RIGHT, PadAction.A).forEach { model.onAction(it) }
        Screens.shoot(rule, "settings_bottom_editor", Panel.BOTTOM) { SettingsBottom(model) }
    }
}
