package com.msharwaan.exoplayer.settings

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.input.PadAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stands in for the Android Keystore, which Robolectric doesn't have; it still never stores plain text. */
class ReversingCrypto : Crypto {
    override fun encrypt(plain: String) = "enc:" + plain.reversed()
    override fun decrypt(stored: String) = stored.removePrefix("enc:").reversed()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsModelTest {

    private lateinit var secrets: Secrets
    private lateinit var model: SettingsModel
    private var clipboard: String? = null
    private val notices = mutableListOf<String>()

    @Before
    fun setUp() {
        secrets = Secrets(ApplicationProvider.getApplicationContext(), ReversingCrypto())
        model = SettingsModel(secrets, { Uri.parse("content://x/tree/1234-ABCD%3AVideos") }, { clipboard }, {}, { notices += it })
    }

    @Test
    fun pastesAndSavesTheTmdbKey() {
        clipboard = "  0123456789abcdef0123456789abcdef\n"
        model.onAction(PadAction.A) // open TMDB key
        repeat(4) { model.onAction(PadAction.DOWN) } // to the bottom row
        repeat(2) { model.onAction(PadAction.RIGHT) } // #+= → Space → Paste
        model.onAction(PadAction.A)
        model.onAction(PadAction.START)
        assertEquals("0123456789abcdef0123456789abcdef", secrets[Secret.TMDB_KEY])
        assertEquals("Set · …cdef", model.ui.value.summaries[SettingRow.TMDB_KEY])
        assertNull(model.ui.value.editing)
        assertEquals("TMDB API key saved.", notices.last())
    }

    @Test
    fun bCancelsWithoutSaving() {
        model.onAction(PadAction.A)
        model.onAction(PadAction.A) // types "q"
        model.onAction(PadAction.B)
        assertNull(secrets[Secret.TMDB_KEY])
        assertNull(model.ui.value.editing)
        // B on the list is left to the app: it closes Settings.
        assertEquals(false, model.onAction(PadAction.B))
    }

    @Test
    fun passwordIsStoredEncryptedAndNeverShown() {
        repeat(4) { model.onAction(PadAction.DOWN) }
        model.onAction(PadAction.A)
        model.onAction(PadAction.A) // "q"
        model.onAction(PadAction.START)
        assertEquals("q", secrets[Secret.OPENSUBTITLES_PASSWORD])
        val raw = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("secrets", 0).getString(Secret.OPENSUBTITLES_PASSWORD.name, null)
        assertNotEquals("q", raw)
        assertEquals("Set", model.ui.value.summaries[SettingRow.OPENSUBTITLES_PASSWORD])
    }

    @Test
    fun xClearsASavedKey() {
        secrets[Secret.TMDB_KEY] = "abc"
        model.refresh()
        model.onAction(PadAction.X)
        assertNull(secrets[Secret.TMDB_KEY])
        assertEquals("Not set", model.ui.value.summaries[SettingRow.TMDB_KEY])
    }

    @Test
    fun emptyClipboardSaysSo() {
        model.onAction(PadAction.A)
        repeat(4) { model.onAction(PadAction.DOWN) }
        repeat(2) { model.onAction(PadAction.RIGHT) }
        model.onAction(PadAction.A)
        assertEquals("", model.ui.value.editing?.keyboard?.text)
        assertEquals(true, notices.last().startsWith("Nothing to paste"))
    }

    @Test
    fun folderNames() {
        assertEquals("SD card › Videos", model.ui.value.summaries[SettingRow.FOLDER])
        assertEquals("Movies/Kids", SettingsModel.folderName(Uri.parse("content://x/tree/primary%3AMovies%2FKids")))
    }
}
