package com.msharwaan.exoplayer.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The displays the app can use right now. */
data class DisplaySetup(
    /** The Thor's bottom panel ("Screen-2"), or null when there is no second screen. */
    val bottomDisplayId: Int?,
    /** A monitor or TV beyond the built-in screens (docked over USB-C). */
    val hasExternalDisplay: Boolean,
)

/**
 * Watches the attached displays and picks the bottom screen.
 *
 * Rules from the research (docs/research/thor-dual-screen.md):
 * - the top screen is the default display; the bottom one is a public presentation display
 *   named "Screen-2" on the Thor;
 * - virtual displays from screen recorders and emulators ("HiddenDisplay",
 *   "WebRTC_ScreenCapture", anything private) are never the bottom screen;
 * - a display that is merely off (during wake-up) still counts as present;
 * - a docked TV is a third display and must not be mistaken for the bottom panel.
 */
class DisplayMonitor(context: Context) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)

    val setup: Flow<DisplaySetup> = callbackFlow {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { trySend(snapshot()) }
            override fun onDisplayRemoved(displayId: Int) { trySend(snapshot()) }
            override fun onDisplayChanged(displayId: Int) { trySend(snapshot()) }
        }
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        trySend(snapshot())
        awaitClose { displayManager.unregisterDisplayListener(listener) }
    }.distinctUntilChanged()

    fun snapshot(): DisplaySetup {
        val candidates = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { isUsableSecondary(it.displayId, it.flags, it.name) }
        val bottom = candidates.firstOrNull { it.name == THOR_BOTTOM_NAME }
            ?: candidates.firstOrNull { !looksExternal(it.name) }
            ?: candidates.firstOrNull()
        return DisplaySetup(
            bottomDisplayId = bottom?.displayId,
            hasExternalDisplay = candidates.size > 1 || (bottom != null && looksExternal(bottom.name)),
        )
    }

    fun display(id: Int): Display? = displayManager.getDisplay(id)

    companion object {
        const val THOR_BOTTOM_NAME = "Screen-2"
        private val EXCLUDED_NAMES = setOf("HiddenDisplay", "WebRTC_ScreenCapture")

        fun isUsableSecondary(displayId: Int, flags: Int, name: String?): Boolean {
            if (displayId == Display.DEFAULT_DISPLAY) return false
            if (name in EXCLUDED_NAMES) return false
            val isPresentation = flags and Display.FLAG_PRESENTATION != 0
            val isPrivate = flags and Display.FLAG_PRIVATE != 0
            return isPresentation && !isPrivate
        }

        /** HDMI and USB-C monitors report names like "HDMI Screen" or the TV's EDID name. */
        private fun looksExternal(name: String?): Boolean =
            name != null && (name.contains("HDMI", ignoreCase = true) || name.contains("DP", ignoreCase = false))
    }
}
