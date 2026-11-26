package com.msharwaan.exoplayer.browse

import android.net.Uri
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.library.BrowseOrder
import com.msharwaan.exoplayer.library.Card
import com.msharwaan.exoplayer.library.Library
import com.msharwaan.exoplayer.library.LibraryPrefs
import com.msharwaan.exoplayer.library.LibraryScanner
import com.msharwaan.exoplayer.library.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Browsing: five tabs moved with L1 / R1 in a full circle (D2, D3, D5), cards on the bottom
 * screen (D6), and the top screen showing either the tab's poster mix or the focused title.
 */
class BrowseModel(
    private val scope: CoroutineScope,
    private val db: AppDatabase,
    private val prefs: LibraryPrefs,
    private val scanner: LibraryScanner,
) {
    /** The active library's cards only (D52). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val all: StateFlow<Map<Tab, List<Card>>> = prefs.activeId.flatMapLatest { id ->
        combine(db.library().observeFilesIn(id), db.library().observeTitles(), db.library().observePlayback()) { files, titles, playback ->
            Library.build(files, titles, playback)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The active library's name, shown when there's more than one (D52). */
    val libraryName: StateFlow<String?> = combine(prefs.libraries, prefs.activeId) { all, _ ->
        prefs.active?.name?.takeIf { all.size > 1 }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _order = MutableStateFlow(BrowseOrder.NAME)
    /** A–Z, Recently added, Year or Unwatched only (Select). */
    val order: StateFlow<BrowseOrder> = _order.asStateFlow()

    /** The tabs as shown: in the chosen order. Continue watching keeps its own (newest first). */
    val library: StateFlow<Map<Tab, List<Card>>> = combine(all, _order) { tabs, order ->
        tabs.mapValues { (tab, cards) -> if (tab == Tab.CONTINUE) cards else Library.order(cards, order) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Every title in the library, for Search. */
    val allCards: List<Card> get() = all.value.filterKeys { it != Tab.CONTINUE }.values.flatten()

    fun cycleOrder() {
        _order.value = BrowseOrder.entries[(_order.value.ordinal + 1) % BrowseOrder.entries.size]
        _focus.value = -1
    }

    private val _tab = MutableStateFlow(Tab.CONTINUE)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    /** Only tabs with something in them (D53): no Movies tab in a library without films. */
    val tabs: StateFlow<List<Tab>> = all.map(Library::visibleTabs).stateIn(scope, SharingStarted.Eagerly, Tab.entries)

    init {
        // The tab you're on emptied (or the library changed): the first one with something in it.
        scope.launch {
            combine(tabs, _tab) { visible, tab -> visible to tab }.collect { (visible, tab) ->
                if (tab !in visible) { _tab.value = visible.first(); _focus.value = -1 }
            }
        }
    }

    /** -1: nothing focused yet, so the top screen shows the poster mix (D6). */
    private val _focus = MutableStateFlow(-1)
    val focus: StateFlow<Int> = _focus.asStateFlow()

    /** The active library has a folder to read; until then Browsing asks for one. */
    val hasFolder: StateFlow<Boolean> = combine(prefs.libraries, prefs.activeId) { _, _ -> prefs.active?.folders?.isNotEmpty() == true }
        .stateIn(scope, SharingStarted.Eagerly, prefs.active?.folders?.isNotEmpty() == true)

    private val _fetching = MutableStateFlow(false)
    /** Fetch info is running: ⟳ shows "…". */
    val fetching: StateFlow<Boolean> = _fetching.asStateFlow()
    fun setFetching(on: Boolean) { _fetching.value = on }

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _folderRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** The activity opens Android's folder picker when this fires. */
    val folderRequests: SharedFlow<Unit> = _folderRequests.asSharedFlow()

    val cards: List<Card> get() = library.value[_tab.value].orEmpty()
    val focusedCard: Card? get() = cards.getOrNull(_focus.value)

    fun chooseFolder() { _folderRequests.tryEmit(Unit) }

    /** The first folder, chosen from Browsing: it goes to the active library (made if there's none). */
    fun onFolderChosen(uri: Uri) {
        val library = prefs.active ?: prefs.create()
        prefs.addFolder(library.id, uri)
        rescan()
    }

    /** Offline: reads the active library's folders for new, changed and removed files. */
    fun rescan() {
        val library = prefs.active?.takeIf { it.folders.isNotEmpty() } ?: return
        if (_scanning.value) return
        scope.launch {
            _scanning.value = true
            runCatching { scanner.scan(library) }
            _scanning.value = false
        }
    }

    /** Where each library was left: its tab and focused card. */
    private val places = mutableMapOf<Long, Pair<Tab, Int>>()

    /** L2 + R3: the next library, back at its own tab and card (D52). Null when there's only one. */
    fun nextLibrary(): String? = switchTo { prefs.next() }

    /** A library picked in Settings › Libraries. */
    fun useLibrary(id: Long): String? = switchTo { prefs.library(id)?.also { prefs.setActive(it.id) } }

    private fun switchTo(pick: () -> com.msharwaan.exoplayer.library.LibrarySpec?): String? {
        val from = prefs.activeId.value
        places[from] = _tab.value to _focus.value
        val to = pick() ?: return null
        if (to.id != from) {
            val (tab, focus) = places[to.id] ?: (Tab.CONTINUE to -1)
            _tab.value = tab
            _focus.value = focus
            rescan()
        }
        return to.name
    }

    fun selectTab(tab: Tab) { _tab.value = tab; _focus.value = -1 }

    /** L1 / R1: round the tabs that are showing, in a full circle (D5, D53). */
    private fun stepTab(step: Int) {
        val visible = tabs.value
        val i = visible.indexOf(_tab.value).coerceAtLeast(0)
        selectTab(visible[(i + step + visible.size) % visible.size])
    }

    fun focusCard(index: Int) { if (index in cards.indices) _focus.value = index }

    /**
     * Returns the card A opens, or null. A on a card that isn't focused yet focuses it,
     * so a tap focuses and a second tap opens, like the mockup.
     */
    fun onAction(action: PadAction): BrowseResult {
        if (!hasFolder.value) {
            if (action == PadAction.A) { chooseFolder(); return BrowseResult.Used }
            return BrowseResult.NotUsed
        }
        val n = cards.size
        when (action) {
            PadAction.L1 -> stepTab(-1)
            PadAction.R1 -> stepTab(1)
            PadAction.LEFT -> move(-1)
            PadAction.RIGHT -> move(1)
            PadAction.UP -> move(-COLUMNS)
            PadAction.DOWN -> move(COLUMNS)
            PadAction.A -> {
                if (n == 0) return BrowseResult.NotUsed
                if (_focus.value < 0) { _focus.value = 0; return BrowseResult.Used }
                return focusedCard?.let { BrowseResult.Open(it) } ?: BrowseResult.NotUsed
            }
            PadAction.Y -> return focusedCard?.let { BrowseResult.FixMatch(it.groupKey) } ?: BrowseResult.NotUsed
            PadAction.B -> {
                // One step back: leave the cards; the top screen goes back to the poster mix.
                if (_focus.value < 0) return BrowseResult.NotUsed
                _focus.value = -1
            }
            else -> return BrowseResult.NotUsed
        }
        return BrowseResult.Used
    }

    /** A tap on a card: the first tap focuses it, a second tap on the same card opens it. */
    fun onCardTapped(index: Int): BrowseResult {
        if (index == _focus.value) return focusedCard?.let { BrowseResult.Open(it) } ?: BrowseResult.NotUsed
        focusCard(index)
        return BrowseResult.Used
    }

    private fun move(step: Int) {
        val n = cards.size
        if (n == 0) return
        if (_focus.value < 0) { _focus.value = 0; return }
        val next = _focus.value + step
        if (next in 0 until n) _focus.value = next
    }

    companion object {
        const val COLUMNS = 4
    }
}

sealed interface BrowseResult {
    data object Used : BrowseResult
    data object NotUsed : BrowseResult
    data class Open(val card: Card) : BrowseResult
    data class FixMatch(val groupKey: String) : BrowseResult
}
