package com.msharwaan.exoplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msharwaan.exoplayer.browse.BrowseModel
import com.msharwaan.exoplayer.browse.BrowseResult
import com.msharwaan.exoplayer.diagnostics.DiagnosticsModel
import com.msharwaan.exoplayer.display.DisplayMonitor
import com.msharwaan.exoplayer.display.DisplaySetup
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.library.Card
import com.msharwaan.exoplayer.nav.Navigator
import com.msharwaan.exoplayer.nav.Route
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One state object read by both screens, so the top and bottom windows never disagree.
 *
 * Every button press arrives here through [onAction], whichever window received it.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val exo = app as ExoApp
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

    /** A short message on the bottom screen (never over the video, rule R1). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private var noticeJob: Job? = null

    val diagnostics = DiagnosticsModel(app)
    val browse = BrowseModel(viewModelScope, exo.db, exo.prefs, exo.scanner)

    private val held = mutableSetOf<PadAction>()

    fun toggleBottomScreen() { _bottomOff.value = !_bottomOff.value; touched() }

    fun touched() { _lastInteraction.value = System.currentTimeMillis() }

    fun showNotice(text: String) {
        noticeJob?.cancel()
        _notice.value = text
        noticeJob = viewModelScope.launch { delay(4_000); _notice.value = null }
    }

    fun onButtonUp(action: PadAction) { held -= action }

    /** Opens a Browsing card: shows go to the series flow, everything else plays (D8, D14, D21, D22). */
    fun open(card: Card) {
        when {
            card.kind == "SHOW" && card.resumeNote != null && card.playFileId != null -> {
                // Continuing a show plays the episode straight away; Back leads to its episode cards (D14).
                navigator.push(Route.Show(card.groupKey, openEpisodes = true))
                navigator.push(Route.Player(card.playFileId))
            }
            card.kind == "SHOW" -> navigator.push(Route.Show(card.groupKey))
            card.playFileId != null -> navigator.push(Route.Player(card.playFileId))
        }
    }

    /** ⟳ Fetch info: the only way Browsing goes online (D32, D36). Arrives in stage 6. */
    fun fetchInfo() {
        showNotice("Fetch info needs your TMDB key. It comes in the next build stage.")
    }

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
            Route.Browse -> when (val r = browse.onAction(action)) {
                is BrowseResult.Open -> { open(r.card); true }
                BrowseResult.Used -> true
                BrowseResult.NotUsed -> false
            }
            else -> false
        }
        if (used) return true
        // B always goes one step back (decision D20). At the root, Back is left to the system.
        if (action == PadAction.B) return navigator.pop()
        return false
    }
}
