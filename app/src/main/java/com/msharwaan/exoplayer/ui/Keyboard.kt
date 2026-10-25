package com.msharwaan.exoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.ui.theme.Palette

/**
 * The app's own keyboard on the bottom screen (D25, rule R10): Android's keyboard isn't
 * reliable there. D-pad moves, A types, X deletes, Y switches case, Start is Done.
 * Plain Kotlin state, so the logic is unit tested; [KeyboardView] draws it.
 */
data class KeyboardState(
    val text: String = "",
    val caps: Boolean = false,
    val symbols: Boolean = false,
    val row: Int = 1,
    val col: Int = 0,
) {
    sealed interface Key {
        val span: Int
        data class Char(val c: kotlin.Char) : Key { override val span = 1 }
        data object Shift : Key { override val span = 1 }
        data object Delete : Key { override val span = 1 }
        data object Mode : Key { override val span = 2 }
        data object Space : Key { override val span = 4 }
        data object Paste : Key { override val span = 2 }
        data object Done : Key { override val span = 2 }
    }

    /** What a key press asks of the screen around the keyboard. */
    enum class Event { NONE, PASTE, DONE }

    val rows: List<List<Key>> get() = if (symbols) SYMBOLS else if (caps) LETTERS_UPPER else LETTERS

    /** The focused key: the one whose columns cover [col]. */
    val focusedKey: Key get() = keyAt(row, col)

    private fun keyAt(r: Int, c: Int): Key {
        var start = 0
        for (k in rows[r]) {
            if (c < start + k.span) return k
            start += k.span
        }
        return rows[r].last()
    }

    /** First column of the key at [c], so moving right from a wide key skips its whole width. */
    private fun startOf(r: Int, c: Int): Int {
        var start = 0
        for (k in rows[r]) {
            if (c < start + k.span) return start
            start += k.span
        }
        return start
    }

    fun move(action: PadAction): KeyboardState = when (action) {
        PadAction.UP -> if (row > 0) copy(row = row - 1) else this
        PadAction.DOWN -> if (row < rows.size - 1) copy(row = row + 1) else this
        PadAction.LEFT -> { val s = startOf(row, col); if (s > 0) copy(col = startOf(row, s - 1)) else this }
        PadAction.RIGHT -> { val s = startOf(row, col) + keyAt(row, col).span; if (s < COLUMNS) copy(col = s) else this }
        else -> this
    }

    /** Presses the focused key (A, or a tap after [focus]). */
    fun press(): Pair<KeyboardState, Event> = when (val k = focusedKey) {
        is Key.Char -> type(k.c) to Event.NONE
        Key.Shift -> copy(caps = !caps) to Event.NONE
        Key.Delete -> delete() to Event.NONE
        Key.Mode -> copy(symbols = !symbols) to Event.NONE
        Key.Space -> type(' ') to Event.NONE
        Key.Paste -> this to Event.PASTE
        Key.Done -> this to Event.DONE
    }

    fun focus(r: Int, c: Int) = copy(row = r, col = c)
    fun type(c: kotlin.Char) = copy(text = text + c)
    fun delete() = copy(text = text.dropLast(1))
    fun paste(s: String) = copy(text = text + s.trim())

    companion object {
        const val COLUMNS = 10

        private fun chars(s: String) = s.map { Key.Char(it) }
        private val BOTTOM = listOf(Key.Mode, Key.Space, Key.Paste, Key.Done)
        private fun letters(upper: Boolean): List<List<Key>> {
            fun c(s: String) = chars(if (upper) s.uppercase() else s)
            return listOf(
                chars("1234567890"),
                c("qwertyuiop"),
                c("asdfghjkl") + Key.Char('-'),
                listOf(Key.Shift) + c("zxcvbnm") + Key.Char('.') + Key.Delete,
                BOTTOM,
            )
        }
        val LETTERS = letters(false)
        val LETTERS_UPPER = letters(true)
        val SYMBOLS = listOf(
            chars("1234567890"),
            chars("!@#$%^&*()"),
            chars("~`+=[]{}\\|"),
            listOf(Key.Char('_')) + chars("?/;:'\",<") + Key.Delete,
            BOTTOM,
        )
    }
}

/** Draws [state]; a tap focuses and presses a key, like A. */
@Composable
fun KeyboardView(state: KeyboardState, onTap: (row: Int, col: Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.rows.forEachIndexed { r, keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                var start = 0
                keys.forEach { key ->
                    val c = start
                    val focused = r == state.row && c == startCol(state, r)
                    val shape = RoundedCornerShape(8.dp)
                    val action = key is KeyboardState.Key.Done
                    Box(
                        Modifier
                            .weight(key.span.toFloat())
                            .height(44.dp)
                            .background(if (action) Palette.Accent else Palette.Panel, shape)
                            .border(if (focused && action) 3.dp else 2.dp, if (focused) (if (action) Palette.Ink else Palette.Accent) else Palette.Line, shape)
                            .then(if (focused && !action) Modifier.background(Palette.Accent.copy(alpha = 0.18f), shape) else Modifier)
                            .clickable { onTap(r, c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label(key, state),
                            color = if (action) Palette.OnAccent else Palette.Ink,
                            fontSize = if (key is KeyboardState.Key.Char) 18.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    start += key.span
                }
            }
        }
    }
}

/** Column where the focused key starts, so only that key lights up when a row repeats a key. */
private fun startCol(state: KeyboardState, r: Int): Int {
    var start = 0
    for (k in state.rows[r]) {
        if (state.col < start + k.span) return start
        start += k.span
    }
    return start
}

private fun label(key: KeyboardState.Key, state: KeyboardState): String = when (key) {
    is KeyboardState.Key.Char -> key.c.toString()
    KeyboardState.Key.Shift -> if (state.caps) "⇧ ON" else "⇧"
    KeyboardState.Key.Delete -> "⌫"
    KeyboardState.Key.Mode -> if (state.symbols) "abc" else "#+="
    KeyboardState.Key.Space -> "Space"
    KeyboardState.Key.Paste -> "Paste"
    KeyboardState.Key.Done -> "Done"
}
