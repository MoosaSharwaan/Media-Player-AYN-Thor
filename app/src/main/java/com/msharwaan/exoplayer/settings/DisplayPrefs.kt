package com.msharwaan.exoplayer.settings

import android.content.Context
import android.view.Display
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which screen a setting is for. */
enum class Screen { TOP, BOTTOM }

/**
 * Refresh rate per screen, chosen in Settings. Null is Auto: the screen's own rate, and
 * during a film its frame rate when the switch is seamless (battery rule 2).
 */
class DisplayPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("display", Context.MODE_PRIVATE)

    private val _top = MutableStateFlow(read(Screen.TOP))
    val top: StateFlow<Float?> = _top.asStateFlow()

    private val _bottom = MutableStateFlow(read(Screen.BOTTOM))
    val bottom: StateFlow<Float?> = _bottom.asStateFlow()

    fun rate(screen: Screen): Float? = if (screen == Screen.TOP) _top.value else _bottom.value

    fun setRate(screen: Screen, hz: Float?) {
        prefs.edit { if (hz == null) remove(screen.name) else putFloat(screen.name, hz) }
        (if (screen == Screen.TOP) _top else _bottom).value = hz
    }

    private fun read(screen: Screen): Float? = if (prefs.contains(screen.name)) prefs.getFloat(screen.name, 60f) else null

    companion object {
        /** The rates a display offers at its current resolution, e.g. [60, 90, 120]. */
        fun ratesOf(display: Display): List<Float> {
            val now = display.mode
            return display.supportedModes
                .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
                .map { it.refreshRate.roundToInt().toFloat() }
                .distinct().sorted()
        }

        /** The mode to ask for to get [hz] at the current resolution; 0 lets Android choose (Auto). */
        fun modeIdFor(display: Display, hz: Float?): Int {
            hz ?: return 0
            val now = display.mode
            return display.supportedModes
                .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
                .minByOrNull { abs(it.refreshRate - hz) }?.modeId ?: 0
        }

        fun label(hz: Float?): String = hz?.let { "${it.roundToInt()} Hz" } ?: "Auto"
    }
}
