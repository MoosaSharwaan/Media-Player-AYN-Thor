package com.msharwaan.exoplayer

import android.app.Application
import android.content.ClipboardManager
import android.net.Uri
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
import com.msharwaan.exoplayer.player.PlayerModel
import com.msharwaan.exoplayer.settings.SettingsModel
import com.msharwaan.exoplayer.show.ShowModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    /** One player for movies and episodes (D13). A finished movie goes back to where it was opened. */
    val player = PlayerModel(app, viewModelScope, exo.db, exo.playback, ::showNotice) {
        if (navigator.current is Route.Player) navigator.pop()
    }

    /** Series and anime: details → seasons → episodes (D14, D15, D21). */
    val show = ShowModel(viewModelScope, exo.db, ::showNotice)

    /** Settings: API keys, the OpenSubtitles login, the video folder, About. */
    val settings = SettingsModel(
        secrets = exo.secrets,
        folder = { exo.prefs.folder },
        readClipboard = {
            app.getSystemService(ClipboardManager::class.java)?.primaryClip
                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(app)?.toString()
        },
        onChooseFolder = { browse.chooseFolder() },
        onNotice = ::showNotice,
    )

    fun onFolderChosen(uri: Uri) {
        browse.onFolderChosen(uri)
        settings.refresh()
    }

    init {
        // The Player route opens its file; leaving it stops and saves (D8).
        viewModelScope.launch {
            var previous: Route? = null
            navigator.stack.collect { stack ->
                val route = stack.last()
                // The show under the top of the stack; continuing a show puts its Player right above it.
                val showIndex = stack.indexOfLast { it is Route.Show }
                (stack.getOrNull(showIndex) as? Route.Show)?.let { s ->
                    show.open(s.groupKey, s.openEpisodes, (stack.getOrNull(showIndex + 1) as? Route.Player)?.fileId)
                }
                val left = previous
                if (route is Route.Show && left is Route.Player) show.returnedFrom(left.fileId)
                if (route == Route.Settings && left != Route.Settings) settings.refresh()
                previous = route
                when {
                    route is Route.Player -> if (player.openFileId != route.fileId) player.open(route.fileId)
                    stack.none { it is Route.Player } -> player.stop()
                }
            }
        }
        // ‹ Prev, Next › and Up next change the file inside the player; keep the route in step,
        // so coming back from the diagnostics page doesn't reopen the earlier episode.
        viewModelScope.launch {
            player.ui.map { it.fileId }.distinctUntilChanged().collect { id ->
                val route = navigator.current
                if (id != 0L && route is Route.Player && route.fileId != id) navigator.replace(Route.Player(id))
            }
        }
    }

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

    /** Start or ⚙ on Browsing. */
    fun openSettings() {
        if (navigator.current != Route.Settings) navigator.push(Route.Settings)
    }

    /** ✎ Fix match: pick the right title, or type its TMDB ID (D11). Arrives in stage 6. */
    fun fixMatch() {
        showNotice("Fix match uses TMDB search. It comes in the next build stage.")
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
            when (navigator.current) {
                Route.Diagnostics -> Unit
                // Start alone opens Settings, so Start then Select lands there first: swap it.
                Route.Settings -> navigator.replace(Route.Diagnostics)
                else -> navigator.push(Route.Diagnostics)
            }
            return true
        }
        val used = when (navigator.current) {
            Route.Diagnostics -> diagnostics.onAction(action)
            Route.Settings -> settings.onAction(action)
            Route.Browse -> if (action == PadAction.START) { openSettings(); true } else when (val r = browse.onAction(action)) {
                is BrowseResult.Open -> { open(r.card); true }
                BrowseResult.Used -> true
                BrowseResult.NotUsed -> false
            }
            is Route.Player -> player.onAction(action)
            is Route.Show -> when (val r = show.onAction(action)) {
                is ShowModel.Result.Play -> { navigator.push(Route.Player(r.fileId)); true }
                ShowModel.Result.Used -> true
                ShowModel.Result.NotUsed -> false
            }
            else -> false
        }
        if (used) return true
        // B always goes one step back (decision D20). At the root, Back is left to the system.
        if (action == PadAction.B) return navigator.pop()
        return false
    }

    override fun onCleared() {
        player.stop()
        exo.playback.release()
    }
}
