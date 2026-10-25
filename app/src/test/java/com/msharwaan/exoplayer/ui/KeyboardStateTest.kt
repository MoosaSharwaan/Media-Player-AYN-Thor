package com.msharwaan.exoplayer.ui

import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.ui.KeyboardState.Event
import com.msharwaan.exoplayer.ui.KeyboardState.Key
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardStateTest {

    private fun KeyboardState.go(vararg a: PadAction) = a.fold(this) { s, x -> s.move(x) }

    @Test
    fun typesTheFocusedKey() {
        // Starts on "q"; right twice is "e".
        val (s, event) = KeyboardState().go(PadAction.RIGHT, PadAction.RIGHT).press()
        assertEquals("e", s.text)
        assertEquals(Event.NONE, event)
    }

    @Test
    fun capsAndSymbols() {
        val caps = KeyboardState(caps = true).press().first
        assertEquals("Q", caps.text)
        val sym = KeyboardState(symbols = true).press().first
        assertEquals("!", sym.text)
    }

    @Test
    fun wideKeysMoveAsOne() {
        // Bottom row: #+= (2 wide), Space (4), Paste (2), Done (2).
        var s = KeyboardState(row = 4, col = 0)
        assertEquals(Key.Mode, s.focusedKey)
        s = s.go(PadAction.RIGHT)
        assertEquals(Key.Space, s.focusedKey)
        s = s.go(PadAction.RIGHT)
        assertEquals(Key.Paste, s.focusedKey)
        s = s.go(PadAction.RIGHT, PadAction.RIGHT)
        assertEquals(Key.Done, s.focusedKey)
        s = s.go(PadAction.LEFT)
        assertEquals(Key.Paste, s.focusedKey)
        assertEquals(Event.PASTE, s.press().second)
    }

    @Test
    fun upFromAWideKeyLandsAboveIt() {
        val s = KeyboardState(row = 4, col = 2).go(PadAction.UP)
        assertEquals(Key.Char('x'), s.focusedKey)
    }

    @Test
    fun deleteAndPaste() {
        val s = KeyboardState(text = "ab").delete().paste("  3f9a  ")
        assertEquals("a3f9a", s.text)
    }

    @Test
    fun staysInsideTheEdges() {
        val s = KeyboardState(row = 0, col = 0).go(PadAction.UP, PadAction.LEFT)
        assertEquals(0 to 0, s.row to s.col)
        val e = KeyboardState(row = 4, col = 8).go(PadAction.RIGHT, PadAction.DOWN)
        assertEquals(Key.Done, e.focusedKey)
    }
}
