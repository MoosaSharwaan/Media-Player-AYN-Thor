package com.msharwaan.exoplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msharwaan.exoplayer.diagnostics.DiagnosticsModel
import com.msharwaan.exoplayer.display.DisplayMonitor
import com.msharwaan.exoplayer.display.DisplaySetup
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.nav.Navigator
import com.msharwaan.exoplayer.nav.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * One state object read by both screens, so the top and bottom windows never disagree.
 *
 * Every button press arrives here through [onAction], whichever window received it.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val navigator = Navigator()
    private val displayMonitor = DisplayMonitor(app)

    val displaySetup: StateFlow<DisplaySetup> = displayMonitor.setup
        .stateIn(viewModelScope, SharingStarted.Eagerly, displayMonitor.snapshot())

    /** The bottom screen turned off with ⏻ (decisions D10, D12, D18). Only ⏻ turns it back on. */
    private val _bottomOff = MutableStateFlow(false)
    val bottomOff: StateFlow<Boolean> = _bottomOff.asStateFlow()

    /** Time of the last touch or button press, for dimming the bottom screen after 10 s (rule R4). */
    private val _lastInteraction = MutableStateFlow(System.currentTimeMillis())
    val lastInteraction: StateFlow<Long> = _lastInteraction.asStateFlow()

    val diagnostics = DiagnosticsModel(app)

    private val held = mutableSetOf<PadAction>()

    fun toggleBottomScreen() { _bottomOff.value = !_bottomOff.value; touched() }

    fun touched() { _lastInteraction.value = System.currentTimeMillis() }

    fun onButtonUp(action: PadAction) { held -= action }

    /** Returns true when the app used the button; false leaves it to the system. */
    fun onAction(action: PadAction): Boolean {
        touched()
        held += action
        // Select + Start: the hidden diagnostics page (the v0 measurements).
        if (PadAction.SELECT in held && PadAction.START in held) {
            if (navigator.current != Route.Diagnostics) navigator.push(Route.Diagnostics)
            return true
        }
        val used = when (navigator.current) {
            Route.Diagnostics -> diagnostics.onAction(action)
            else -> false
        }
        if (used) return true
        // B always goes one step back (decision D20). At the root, Back is left to the system.
        if (action == PadAction.B) return navigator.pop()
        return false
    }
}
