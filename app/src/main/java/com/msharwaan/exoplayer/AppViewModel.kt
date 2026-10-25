package com.msharwaan.exoplayer

import android.app.Application
import android.content.ClipboardManager
import android.net.Uri
import android.view.Display
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msharwaan.exoplayer.browse.BrowseModel
import com.msharwaan.exoplayer.browse.BrowseResult
import com.msharwaan.exoplayer.browse.FoldersModel
import com.msharwaan.exoplayer.browse.SearchModel
import com.msharwaan.exoplayer.diagnostics.DiagnosticsModel
import com.msharwaan.exoplayer.display.DisplayMonitor
import com.msharwaan.exoplayer.display.DisplaySetup
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.library.Card
import com.msharwaan.exoplayer.nav.Navigator
import com.msharwaan.exoplayer.nav.Route
import com.msharwaan.exoplayer.online.FindSubtitlesModel
import com.msharwaan.exoplayer.online.FixMatchModel
import com.msharwaan.exoplayer.online.KeyRejected
import com.msharwaan.exoplayer.player.PlayerModel
import com.msharwaan.exoplayer.settings.DisplayPrefs
import com.msharwaan.exoplayer.settings.Screen
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
import java.io.IOException

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
    val player = PlayerModel(app, viewModelScope, exo.db, exo.playback, exo.writeScope, ::showNotice) {
        if (navigator.current is Route.Player) navigator.pop()
    }

    /** Series and anime: details → seasons → episodes (D14, D15, D21). */
    val show = ShowModel(viewModelScope, exo.db, ::showNotice)

    /** Settings: API keys, the OpenSubtitles login, the video folder, About. */
    val settings = SettingsModel(
        secrets = exo.secrets,
        displayPrefs = exo.displayPrefs,
        rates = { screen ->
            val id = if (screen == Screen.TOP) Display.DEFAULT_DISPLAY else displaySetup.value.bottomDisplayId
            id?.let { displayMonitor.display(it) }?.let(DisplayPrefs::ratesOf)
        },
        folder = { exo.prefs.folder },
        readClipboard = { clipboard() },
        onChooseFolder = { browse.chooseFolder() },
        onNotice = ::showNotice,
    )

    private val clipboard: () -> String? = {
        app.getSystemService(ClipboardManager::class.java)?.primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(app)?.toString()
    }

    /** ✎ Fix match (D11). */
    val fixMatch = FixMatchModel(viewModelScope, exo.db, exo.online, clipboard, ::showNotice) { navigator.pop() }

    /** Find subtitles (D9, D37). */
    val findSubtitles = FindSubtitlesModel(
        app, viewModelScope, exo.db, exo.online, { exo.prefs.folder }, ::showNotice,
        onAdded = { player.addSubtitle(it) },
        onDone = { if (navigator.current is Route.FindSubtitles) navigator.pop() },
    )

    /** Search (X on Browsing). */
    val search = SearchModel({ browse.allCards }, clipboard, ::showNotice)

    /** The folder browser (R1 on Browsing). */
    val folders = FoldersModel(viewModelScope, exo.db)

    /** True while Fetch info runs; ⟳ shows "…". */
    private val _fetching = MutableStateFlow(false)
    val fetching: StateFlow<Boolean> = _fetching.asStateFlow()

    fun onFolderChosen(uri: Uri) {
        browse.onFolderChosen(uri)
        settings.refresh()
    }

    init {
        player.onFindSubtitles = { navigator.push(Route.FindSubtitles(it)) }
        // A fixed top-screen rate holds during films too; Auto follows the film when seamless.
        viewModelScope.launch { exo.displayPrefs.top.collect { exo.playback.followFilmRate(it == null) } }
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
                if (route is Route.FixMatch) fixMatch.open(route.groupKey)
                if (route is Route.FindSubtitles) findSubtitles.open(route.fileId)
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

    private var comboUsed = false

    fun onButtonUp(action: PadAction) {
        held -= action
        if (action == PadAction.SELECT) {
            if (!comboUsed && navigator.current == Route.Browse && browse.hasFolder.value) {
                browse.cycleOrder()
                showNotice("Showing: ${browse.order.value.label}")
            }
            comboUsed = false
        }
    }

    fun openSearch() {
        search.open()
        navigator.push(Route.Search)
    }

    /** A folder opens a deeper level; a video plays. */
    fun onFolders(r: FoldersModel.Result): Boolean = when (r) {
        is FoldersModel.Result.OpenFolder -> { navigator.push(Route.Folders(r.path)); true }
        is FoldersModel.Result.Play -> { navigator.push(Route.Player(r.fileId)); true }
        FoldersModel.Result.Used -> true
        FoldersModel.Result.NotUsed -> false
    }

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

    /** ✎ Fix match for what's on screen: the film playing, the show open, or the focused card. */
    fun openFixMatch() {
        val key = when (val r = navigator.current) {
            is Route.Player -> player.ui.value.groupKey.also { player.pause() }
            is Route.Show -> r.groupKey
            Route.Browse -> browse.focusedCard?.groupKey
            else -> null
        } ?: return showNotice("Pick a title first.")
        openFixMatch(key)
    }

    fun openFixMatch(groupKey: String) {
        if (groupKey.startsWith("o:")) return showNotice("Others are your own videos: there's nothing to match.")
        navigator.push(Route.FixMatch(groupKey))
    }

    /**
     * ⟳ Fetch info for the tab on screen (D32, D36): only what's missing is fetched (D38).
     * The only way Browsing goes online.
     */
    fun fetchInfo() {
        if (_fetching.value) return showNotice("Already fetching…")
        val fetch = exo.online.fetchInfo() ?: return showNotice("Add your TMDB API key in Settings first (press Start).")
        if (!exo.online.isConnected()) return showNotice("No internet connection. Fetch info needs one.")
        val keys = browse.cards.map { it.groupKey }.filterNot { it.startsWith("o:") }.distinct()
        if (keys.isEmpty()) return showNotice("Nothing in this tab to fetch info for.")
        _fetching.value = true
        browse.setFetching(true)
        viewModelScope.launch {
            try {
                val r = fetch.fetch(keys) { done, total -> if (done < total) showNotice("Fetching info… ${done + 1} of $total") }
                showNotice(
                    buildList {
                        if (r.fetched > 0) add("Fetched ${r.fetched}" + if (r.fetched == 1) " title." else " titles.")
                        if (r.fetched == 0 && r.notFound.isEmpty() && r.failed.isEmpty()) add("Everything here is up to date.")
                        if (r.notFound.isNotEmpty()) add("Not found: " + r.notFound.take(3).joinToString(", ") + (if (r.notFound.size > 3) "…" else "") + ". Use ✎ Fix match (Y).")
                        if (r.failed.isNotEmpty()) add("${r.failed.size} couldn't be fetched; press ⟳ again later.")
                    }.joinToString(" "),
                )
            } catch (e: KeyRejected) {
                showNotice("TMDB didn't accept your API key. Check it in Settings.")
            } catch (e: IOException) {
                showNotice("Fetch info stopped: the connection was lost. Press ⟳ to carry on.")
            } finally {
                _fetching.value = false
                browse.setFetching(false)
            }
        }
    }

    /** Returns true when the app used the button; false leaves it to the system. */
    fun onAction(action: PadAction): Boolean {
        touched()
        held += action
        // Select + Start: the hidden diagnostics page (the v0 measurements).
        if (PadAction.SELECT in held && PadAction.START in held) {
            comboUsed = true
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
            Route.Search -> when (val r = search.onAction(action)) {
                is SearchModel.Result.Open -> { open(r.card); true }
                SearchModel.Result.Used -> true
                SearchModel.Result.NotUsed -> false
            }
            is Route.Folders -> onFolders(folders.onAction((navigator.current as Route.Folders).path, action))
            Route.Browse -> if (action == PadAction.START) { openSettings(); true }
            else if (action == PadAction.X && browse.hasFolder.value) { openSearch(); true }
            else if (action == PadAction.R1 && browse.hasFolder.value) { navigator.push(Route.Folders("")); true }
            // Select changes the order when it's let go, so Select + Start (diagnostics) doesn't change it.
            else if (action == PadAction.SELECT) true
            else when (val r = browse.onAction(action)) {
                is BrowseResult.Open -> { open(r.card); true }
                is BrowseResult.FixMatch -> { openFixMatch(r.groupKey); true }
                BrowseResult.Used -> true
                BrowseResult.NotUsed -> false
            }
            is Route.Player -> player.onAction(action)
            is Route.FixMatch -> fixMatch.onAction(action)
            is Route.FindSubtitles -> findSubtitles.onAction(action)
            is Route.Show -> if (action == PadAction.Y) { openFixMatch(); true } else when (val r = show.onAction(action)) {
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
        player.release()
        exo.playback.release()
    }
}
