package com.msharwaan.exoplayer.browse

import android.net.Uri
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.library.Card
import com.msharwaan.exoplayer.library.Library
import com.msharwaan.exoplayer.library.LibraryPrefs
import com.msharwaan.exoplayer.library.LibraryScanner
import com.msharwaan.exoplayer.library.Tab
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Browsing: five tabs moved with L2 / R2 in a full circle (D2, D3, D5), cards on the bottom
 * screen (D6), and the top screen showing either the tab's poster mix or the focused title.
 */
class BrowseModel(
    private val scope: CoroutineScope,
    private val db: AppDatabase,
    private val prefs: LibraryPrefs,
    private val scanner: LibraryScanner,
) {
    val library: StateFlow<Map<Tab, List<Card>>> = combine(
        db.library().observeFiles(), db.library().observeTitles(), db.library().observePlayback(),
    ) { files, titles, playback -> Library.build(files, titles, playback) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _tab = MutableStateFlow(Tab.CONTINUE)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    /** -1: nothing focused yet, so the top screen shows the poster mix (D6). */
    private val _focus = MutableStateFlow(-1)
    val focus: StateFlow<Int> = _focus.asStateFlow()

    private val _hasFolder = MutableStateFlow(prefs.folder != null)
    val hasFolder: StateFlow<Boolean> = _hasFolder.asStateFlow()

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

    fun onFolderChosen(uri: Uri) {
        prefs.choose(uri)
        _hasFolder.value = true
        rescan()
    }

    /** Offline: reads the SD card folder for new, changed and removed files. */
    fun rescan() {
        val folder = prefs.folder ?: return
        if (_scanning.value) return
        scope.launch {
            _scanning.value = true
            runCatching { scanner.scan(folder) }
            _scanning.value = false
        }
    }

    fun selectTab(tab: Tab) { _tab.value = tab; _focus.value = -1 }

    fun focusCard(index: Int) { if (index in cards.indices) _focus.value = index }

    /**
     * Returns the card A opens, or null. A on a card that isn't focused yet focuses it,
     * so a tap focuses and a second tap opens, like the mockup.
     */
    fun onAction(action: PadAction): BrowseResult {
        if (!_hasFolder.value) {
            if (action == PadAction.A) { chooseFolder(); return BrowseResult.Used }
            return BrowseResult.NotUsed
        }
        val n = cards.size
        when (action) {
            PadAction.L2 -> selectTab(Tab.entries[(_tab.value.ordinal + Tab.entries.size - 1) % Tab.entries.size])
            PadAction.R2 -> selectTab(Tab.entries[(_tab.value.ordinal + 1) % Tab.entries.size])
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
