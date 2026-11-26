package com.msharwaan.exoplayer

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The icon opens the player through LaunchActivity, which never stays (D55). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchActivityTest {

    @Test
    fun theIconIsTheLaunchStep() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertEquals(LaunchActivity::class.java.name, launch?.component?.className)
    }

    @Test
    fun opensThePlayerInItsOwnTaskAndStepsAside() {
        val activity = Robolectric.buildActivity(LaunchActivity::class.java, Intent(Intent.ACTION_MAIN)).setup().get()
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue("it never stays on screen", activity.isFinishing)
    }
}
