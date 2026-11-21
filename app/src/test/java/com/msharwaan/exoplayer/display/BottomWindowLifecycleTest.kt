package com.msharwaan.exoplayer.display

import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Display
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.msharwaan.exoplayer.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowDisplayManager

/** The bottom screen's window leaves with the app (Home, another app) and comes back with it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class BottomWindowLifecycleTest {

    /** A second screen like the Thor's: a presentation display named "Screen-2". */
    private fun addThorBottomScreen() {
        // Robolectric lists only external-type displays as presentation displays.
        val id = ShadowDisplayManager.addDisplay("w620dp-h540dp-xhdpi", 2) /* Display.TYPE_EXTERNAL, hidden from apps */
        val display = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(DisplayManager::class.java).getDisplay(id)
        shadowOf(display).apply { setName(DisplayMonitor.THOR_BOTTOM_NAME); setFlags(Display.FLAG_PRESENTATION) }
    }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }

    @Test
    fun leavesWithTheAppAndComesBack() {
        addThorBottomScreen()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idle()
            val first = ShadowDialog.getLatestDialog()
            assertTrue("bottom window shown on start", first is BottomPresentation && first.isShowing)

            scenario.moveToState(Lifecycle.State.CREATED) // Home: the activity is stopped
            idle()
            assertFalse("bottom window gone while the app is in the background", first.isShowing)

            scenario.moveToState(Lifecycle.State.RESUMED)
            idle()
            val again = ShadowDialog.getLatestDialog()
            assertNotSame(first, again)
            assertTrue("bottom window back when the app returns", again is BottomPresentation && again.isShowing)
        }
    }
}
