package com.msharwaan.exoplayer.browse

import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.library.Card
import com.msharwaan.exoplayer.library.Library
import com.msharwaan.exoplayer.ui.KeyboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SearchUi(
    val keyboard: KeyboardState = KeyboardState(),
    val results: List<Card> = emptyList(),
    val choosing: Boolean = false,
    val focus: Int = 0,
)

/**
 * Search (X on Browsing): type on the app's keyboard (D25); matches show on the top screen
 * as you type. Done moves to the matches as cards; A opens one. Offline: it searches your library.
 */
class SearchModel(private val all: () -> List<Card>, private val readClipboard: () -> String?, private val onNotice: (String) -> Unit) {
    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui.asStateFlow()

    sealed interface Result {
        data object Used : Result
        data object NotUsed : Result
        data class Open(val card: Card) : Result
    }

    fun open() { _ui.value = SearchUi() }

    fun onAction(action: PadAction): Result {
        val ui = _ui.value
        if (ui.choosing) {
            val n = ui.results.size
            when (action) {
                PadAction.LEFT -> move(-1, n)
                PadAction.RIGHT -> move(1, n)
                PadAction.UP -> move(-BrowseModel.COLUMNS, n)
                PadAction.DOWN -> move(BrowseModel.COLUMNS, n)
                PadAction.A -> return ui.results.getOrNull(ui.focus)?.let { Result.Open(it) } ?: Result.Used
                PadAction.B -> _ui.update { it.copy(choosing = false) }
                else -> Unit
            }
            return Result.Used
        }
        when (action) {
            PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT -> _ui.update { it.copy(keyboard = it.keyboard.move(action)) }
            PadAction.A -> press()
            PadAction.X -> type(ui.keyboard.delete())
            PadAction.Y -> _ui.update { it.copy(keyboard = it.keyboard.copy(caps = !it.keyboard.caps)) }
            PadAction.START -> choose()
            PadAction.B -> return Result.NotUsed
            else -> Unit
        }
        return Result.Used
    }

    fun onKeyTapped(row: Int, col: Int) {
        _ui.update { it.copy(keyboard = it.keyboard.focus(row, col)) }
        press()
    }

    /** A tap on a result: the first focuses it, a second opens it. */
    fun onResultTapped(index: Int): Result {
        if (_ui.value.choosing && _ui.value.focus == index) return _ui.value.results.getOrNull(index)?.let { Result.Open(it) } ?: Result.Used
        _ui.update { it.copy(choosing = true, focus = index) }
        return Result.Used
    }

    private fun press() {
        val (k, event) = _ui.value.keyboard.press()
        when (event) {
            KeyboardState.Event.NONE -> type(k)
            KeyboardState.Event.PASTE -> readClipboard()?.trim()?.takeIf { it.isNotEmpty() }?.let { type(k.paste(it)) } ?: onNotice("Nothing to paste.")
            KeyboardState.Event.DONE -> choose()
        }
    }

    private fun type(k: KeyboardState) = _ui.update { it.copy(keyboard = k, results = Library.search(all(), k.text)) }

    private fun choose() {
        if (_ui.value.results.isEmpty()) onNotice("Nothing in your library matches.")
        else _ui.update { it.copy(choosing = true, focus = 0) }
    }

    private fun move(step: Int, n: Int) {
        val next = _ui.value.focus + step
        if (next in 0 until n) _ui.update { it.copy(focus = next) }
    }
}
