package com.msharwaan.exoplayer.library

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.AppViewModel
import com.msharwaan.exoplayer.ExoApp
import com.msharwaan.exoplayer.MainActivity
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.input.PadAction.A
import com.msharwaan.exoplayer.input.PadAction.B
import com.msharwaan.exoplayer.input.PadAction.DOWN
import com.msharwaan.exoplayer.input.PadAction.LEFT
import com.msharwaan.exoplayer.input.PadAction.START
import com.msharwaan.exoplayer.input.PadAction.UP
import com.msharwaan.exoplayer.input.PadAction.X
import com.msharwaan.exoplayer.input.PadAction.Y
import com.msharwaan.exoplayer.nav.Route
import com.msharwaan.exoplayer.settings.LibrariesModel
import com.msharwaan.exoplayer.settings.LibraryRow
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A passkey on a library (D56), and L2 + R3 to switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class PasskeyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val combo = listOf(UP, UP, DOWN, X, Y)

    @Test
    fun onlyTheSameComboMatches() {
        val stored = Passkey.make(combo)
        assertFalse("never kept as it was pressed", stored.contains("UP"))
        assertTrue(Passkey.matches(stored, combo))
        assertFalse(Passkey.matches(stored, listOf(UP, UP, DOWN, Y, X)))
        assertFalse(Passkey.matches(stored, combo.dropLast(1)))
        assertFalse("salted", stored == Passkey.make(combo))
    }

    @Test
    fun enteringOnThePad() {
        var e = PasskeyEntry()
        assertEquals(PasskeyEntry.Result.Cancel, e.press(B).second)
        for (b in listOf(UP, DOWN, A)) e = e.press(b).first
        // Too short: Start says so.
        val (short, r) = e.press(START)
        assertEquals(PasskeyEntry.Result.Typing, r)
        assertNotNull(short.error)
        // B takes one back; Start isn't a press.
        e = e.press(B).first
        assertEquals(listOf(UP, DOWN), e.presses)
        repeat(10) { e = e.press(LEFT).first }
        assertEquals(Passkey.MAX, e.presses.size)
        assertEquals(PasskeyEntry.Result.Done(e.presses), e.press(START).second)
    }

    @Test
    fun oneLibraryAlwaysStaysOpen() {
        val prefs = LibraryPrefs(context)
        val films = prefs.create("Films")
        assertFalse("the only library can't be locked", prefs.canLock(films.id))
        prefs.setLock(films.id, combo)
        assertFalse(prefs.library(films.id)!!.locked)

        val private = prefs.create("Private")
        prefs.setLock(private.id, combo)
        assertTrue(prefs.library(private.id)!!.locked)
        assertFalse("the last open one can't be locked too", prefs.canLock(films.id))
        assertTrue(prefs.unlocks(private.id, combo))
        assertFalse(prefs.unlocks(private.id, listOf(A, A, A, A)))
        // Nor deleted while the locked one is left.
        assertFalse(prefs.delete(films.id))

        // Shown when the app closed: opening again starts on the open library.
        prefs.setActive(private.id)
        val again = LibraryPrefs(context)
        assertEquals(films.id, again.activeId.value)
        assertTrue("kept across restarts", again.library(private.id)!!.locked)
        // Deleting the locked one (forgotten passkey) is allowed.
        assertTrue(again.delete(private.id))
    }

    @Test
    fun settingsAsksBeforeOpeningALockedLibrary() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val prefs = LibraryPrefs(context)
        prefs.create("Films")
        val private = prefs.create("Private")
        val model = LibrariesModel(MainScope(), db, prefs, {}, {}, {}, {})
        fun press(vararg a: PadAction) = a.forEach { model.onAction(it) }
        model.reset()

        // Set: open Private, go to Set passkey, the combo twice.
        press(DOWN, A)
        val rows = model.rowsOf(private.id)
        repeat(rows.indexOf(LibraryRow.Passkey) - model.ui.value.focus) { press(DOWN) }
        press(A); press(*combo.toTypedArray()); press(START)
        press(*listOf(UP, UP, DOWN, X, X).toTypedArray()); press(START)
        assertNotNull("a different second try starts again", model.ui.value.passkey?.error)
        assertFalse(prefs.library(private.id)!!.locked)
        press(*combo.toTypedArray(), START, *combo.toTypedArray(), START)
        assertTrue(prefs.library(private.id)!!.locked)
        assertNull(model.ui.value.passkey)
        assertTrue(LibraryRow.RemovePasskey in model.rowsOf(private.id))

        // Back on the list and in again: it asks.
        model.reset()
        press(DOWN, A)
        assertEquals(private.id, model.ui.value.passkeyFor)
        assertNull(model.ui.value.open)
        press(A, A, A, A, START)
        assertNotNull(model.ui.value.passkey?.error)
        press(*combo.toTypedArray(), START)
        assertEquals(private.id, model.ui.value.open)

        // Forgotten: X twice on the list deletes it without the passkey.
        model.reset()
        press(DOWN, X)
        assertTrue(model.ui.value.confirmForget)
        press(X)
        assertNull(prefs.library(private.id))
    }

    private fun key(activity: MainActivity, code: Int, down: Boolean) {
        activity.dispatchKeyEvent(KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun tap(activity: MainActivity, vararg codes: Int) = codes.forEach { key(activity, it, true); key(activity, it, false) }

    /** L2 held, R3 pressed: the next library. */
    private fun chord(activity: MainActivity) {
        key(activity, KeyEvent.KEYCODE_BUTTON_L2, true)
        tap(activity, KeyEvent.KEYCODE_BUTTON_THUMBR)
        key(activity, KeyEvent.KEYCODE_BUTTON_L2, false)
    }

    @Test
    fun l2AndR3SwitchAndALockedLibraryAsks() {
        val prefs = (context as ExoApp).prefs
        val films = prefs.create("Films")
        val private = prefs.create("Private")
        val work = prefs.create("Work")
        prefs.setLock(private.id, combo)
        prefs.setActive(films.id)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[AppViewModel::class.java]
                shadowOf(Looper.getMainLooper()).idle()

                // L2 alone no longer switches.
                tap(activity, KeyEvent.KEYCODE_BUTTON_L2)
                assertEquals(films.id, prefs.activeId.value)

                chord(activity)
                assertEquals(Route.Unlock(private.id), vm.navigator.current)
                assertEquals("not shown before its passkey", films.id, prefs.activeId.value)
                // L2 + R3 again skips past it.
                chord(activity)
                assertEquals(Route.Browse, vm.navigator.current)
                assertEquals(work.id, prefs.activeId.value)

                chord(activity) // Work → Films
                chord(activity) // Films → Private: asks
                tap(activity, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START)
                assertNotNull(vm.unlockEntry.value.error)
                assertEquals(films.id, prefs.activeId.value)
                tap(
                    activity, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START,
                )
                assertEquals(Route.Browse, vm.navigator.current)
                assertEquals(private.id, prefs.activeId.value)
            }
            // Home: it locks again behind the first open library.
            scenario.moveToState(Lifecycle.State.CREATED)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(films.id, prefs.activeId.value)
        }
    }
}
