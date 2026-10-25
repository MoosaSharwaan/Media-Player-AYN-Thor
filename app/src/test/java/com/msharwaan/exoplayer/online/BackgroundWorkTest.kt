package com.msharwaan.exoplayer.online

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundWorkTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun waitUntil(ok: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!ok() && System.currentTimeMillis() < until) Thread.sleep(10)
    }

    @Test
    fun runsInAForegroundServiceAndCleansUp() = runBlocking {
        val work = BackgroundWork(app)
        val gate = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<String>()
        assertTrue(work.start("Fetching info…") { progress -> progress("Fetching info… 2 of 3"); gate.await(); "Fetched 3 titles." })

        // The service holds the app in the foreground, and a second start waits its turn.
        val started = Shadows.shadowOf(app).nextStartedService
        assertEquals(WorkService::class.java.name, started.component!!.className)
        waitUntil { work.progress.value == "Fetching info… 2 of 3" }
        assertEquals("Fetching info… 2 of 3", work.progress.value)
        assertFalse(work.start("again") { "no" })
        assertTrue(ShadowPowerManager.getLatestWakeLock()!!.isHeld)

        // Listen first, as the app does: the end message isn't kept for late listeners.
        val listening = CompletableDeferred<Unit>()
        val collector = CoroutineScope(Dispatchers.Default).launch {
            work.finished.onSubscription { listening.complete(Unit) }.collect { finished.complete(it) }
        }
        listening.await()
        gate.complete(Unit)
        assertEquals("Fetched 3 titles.", withTimeout(5_000) { finished.await() })
        collector.cancel()
        waitUntil { work.progress.value == null }
        assertNull(work.progress.value)
        assertFalse(ShadowPowerManager.getLatestWakeLock()!!.isHeld)
        // Not on screen: the end is a notification you can tap.
        val nm = app.getSystemService(NotificationManager::class.java)
        waitUntil { Shadows.shadowOf(nm).allNotifications.isNotEmpty() }
        assertTrue(Shadows.shadowOf(nm).allNotifications.any { it.extras.getString("android.text") == "Fetched 3 titles." })
    }

    @Test
    fun onScreenTheEndIsNotANotification() = runBlocking {
        val work = BackgroundWork(app).apply { appVisible = true }
        work.start("Fetching info…") { "Everything here is up to date." }
        waitUntil { work.progress.value == null }
        Thread.sleep(100)
        val nm = app.getSystemService(NotificationManager::class.java)
        assertTrue(Shadows.shadowOf(nm).allNotifications.none { it.extras.getString("android.text") == "Everything here is up to date." })
    }
}
