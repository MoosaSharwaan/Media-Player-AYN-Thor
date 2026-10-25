package com.msharwaan.exoplayer.online

import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MatchOverride
import com.msharwaan.exoplayer.data.TitleInfo
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.ui.KeyboardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class FixMatchUi(
    val groupKey: String = "",
    val title: TitleInfo? = null,
    val fileName: String = "",
    val type: String = Tmdb.MOVIE,
    val keyboard: KeyboardState = KeyboardState(),
    val phase: Phase = Phase.TYPING,
    val results: List<TmdbHit> = emptyList(),
    val focus: Int = 0,
) {
    enum class Phase { TYPING, SEARCHING, RESULTS, APPLYING }
}

/**
 * ✎ Fix match (D11): search TMDB by name, or type a TMDB ID, and pick the right title. The
 * choice is saved (it survives rescans) and that title is fetched fresh.
 */
class FixMatchModel(
    private val scope: CoroutineScope,
    private val db: AppDatabase,
    private val online: Online,
    private val readClipboard: () -> String?,
    private val onNotice: (String) -> Unit,
    private val onDone: () -> Unit,
) {
    private val _ui = MutableStateFlow(FixMatchUi())
    val ui: StateFlow<FixMatchUi> = _ui.asStateFlow()

    fun open(groupKey: String) {
        if (_ui.value.groupKey == groupKey && _ui.value.phase != FixMatchUi.Phase.APPLYING) return
        _ui.value = FixMatchUi(groupKey = groupKey)
        scope.launch {
            val dao = db.library()
            val t = withContext(Dispatchers.IO) { dao.title(groupKey) }
            val file = withContext(Dispatchers.IO) { dao.filesOf(groupKey).firstOrNull() }
            val override = withContext(Dispatchers.IO) { dao.override(groupKey) }
            _ui.update {
                it.copy(
                    title = t, fileName = file?.fileName.orEmpty(),
                    type = override?.tmdbType ?: if (t?.kind == "MOVIE") Tmdb.MOVIE else Tmdb.TV,
                    keyboard = KeyboardState(text = t?.name.orEmpty()),
                )
            }
        }
    }

    /** Returns false when B should leave Fix match. */
    fun onAction(action: PadAction): Boolean {
        val ui = _ui.value
        when (ui.phase) {
            FixMatchUi.Phase.TYPING -> when (action) {
                PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT -> setKeyboard(ui.keyboard.move(action))
                PadAction.A -> press()
                PadAction.X -> setKeyboard(ui.keyboard.delete())
                PadAction.Y -> setKeyboard(ui.keyboard.copy(caps = !ui.keyboard.caps))
                PadAction.L2, PadAction.R2 -> toggleType()
                PadAction.START -> search()
                PadAction.B -> return false
                else -> Unit
            }
            FixMatchUi.Phase.RESULTS -> when (action) {
                PadAction.UP -> _ui.update { it.copy(focus = (it.focus - 1).coerceAtLeast(0)) }
                PadAction.DOWN -> _ui.update { it.copy(focus = (it.focus + 1).coerceAtMost(it.results.size - 1)) }
                PadAction.A -> ui.results.getOrNull(ui.focus)?.let(::apply)
                PadAction.L2, PadAction.R2 -> { toggleType(); search() }
                PadAction.B -> _ui.update { it.copy(phase = FixMatchUi.Phase.TYPING) }
                else -> Unit
            }
            // Busy: buttons wait.
            FixMatchUi.Phase.SEARCHING, FixMatchUi.Phase.APPLYING -> Unit
        }
        return true
    }

    fun onKeyTapped(row: Int, col: Int) {
        setKeyboard(_ui.value.keyboard.focus(row, col))
        press()
    }

    fun onResultTapped(index: Int) {
        if (_ui.value.focus == index) _ui.value.results.getOrNull(index)?.let(::apply) else _ui.update { it.copy(focus = index) }
    }

    fun toggleType() = _ui.update { it.copy(type = if (it.type == Tmdb.MOVIE) Tmdb.TV else Tmdb.MOVIE) }

    private fun setKeyboard(k: KeyboardState) = _ui.update { it.copy(keyboard = k) }

    private fun press() {
        val (k, event) = _ui.value.keyboard.press()
        when (event) {
            KeyboardState.Event.NONE -> setKeyboard(k)
            KeyboardState.Event.PASTE -> readClipboard()?.trim()?.takeIf { it.isNotEmpty() }?.let { setKeyboard(k.paste(it)) }
                ?: onNotice("Nothing to paste.")
            KeyboardState.Event.DONE -> search()
        }
    }

    /** A name searches; digits alone are a TMDB ID. */
    fun search() {
        val ui = _ui.value
        val query = ui.keyboard.text.trim()
        if (query.isEmpty()) return
        val tmdb = online.tmdb() ?: return onNotice("Add your TMDB API key in Settings first (press Start on Browsing).")
        if (!online.isConnected()) return onNotice("No internet connection.")
        _ui.update { it.copy(phase = FixMatchUi.Phase.SEARCHING) }
        scope.launch {
            val hits = try {
                withContext(Dispatchers.IO) {
                    val id = query.toLongOrNull()
                    when {
                        id != null && ui.type == Tmdb.MOVIE -> listOf(tmdb.movie(id).let { TmdbHit(it.id, Tmdb.MOVIE, it.title, it.year, it.overview) })
                        id != null -> listOf(tmdb.show(id).let { TmdbHit(it.id, Tmdb.TV, it.name, it.year, it.overview) })
                        ui.type == Tmdb.MOVIE -> tmdb.searchMovies(query)
                        else -> tmdb.searchShows(query)
                    }
                }
            } catch (e: HttpException) {
                onNotice(if (e.code == 401) "TMDB didn't accept your API key. Check it in Settings." else if (e.code == 404) "No title with that TMDB ID." else "TMDB couldn't be reached.")
                null
            } catch (e: IOException) {
                onNotice("TMDB couldn't be reached.")
                null
            }
            when {
                hits == null -> _ui.update { it.copy(phase = FixMatchUi.Phase.TYPING) }
                hits.isEmpty() -> { onNotice("Nothing found. Try another name, or switch Movie / Series with L2 / R2."); _ui.update { it.copy(phase = FixMatchUi.Phase.TYPING) } }
                else -> _ui.update { it.copy(phase = FixMatchUi.Phase.RESULTS, results = hits.take(20), focus = 0) }
            }
        }
    }

    private fun apply(hit: TmdbHit) {
        val fetch = online.fetchInfo() ?: return onNotice("Add your TMDB API key in Settings first.")
        val key = _ui.value.groupKey
        _ui.update { it.copy(phase = FixMatchUi.Phase.APPLYING) }
        onNotice("Fetching ${hit.title}…")
        scope.launch {
            try {
                fetch.fix(key, MatchOverride(key, hit.id, hit.type))
                onNotice("Matched to ${hit.title}" + (hit.year?.let { " ($it)" } ?: "") + ".")
                _ui.value = FixMatchUi()
                onDone()
            } catch (e: IOException) {
                onNotice(if (e is KeyRejected) "TMDB didn't accept your API key." else "The fix is saved; its info will come with the next Fetch info.")
                _ui.update { it.copy(phase = FixMatchUi.Phase.RESULTS) }
            }
        }
    }
}
