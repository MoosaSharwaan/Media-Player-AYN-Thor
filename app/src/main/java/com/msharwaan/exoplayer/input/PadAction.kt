package com.msharwaan.exoplayer.input

import android.view.KeyEvent

/**
 * What a button means to the app, independent of which key code produced it.
 *
 * The Thor's controller reports standard Android gamepad key codes. Keyboards and the
 * system Back key map onto the same actions, so the app also works on a phone.
 */
enum class PadAction {
    /** One step forward: open, play, confirm (decision D20). */
    A,

    /** One step back: leave, close, stop (decision D20). */
    B,
    X, Y,
    UP, DOWN, LEFT, RIGHT,
    L1, R1, L2, R2, L3, R3,
    START, SELECT;

    companion object {
        /** Returns the action for [keyCode], or null for keys the app leaves to the system. */
        fun fromKeyCode(keyCode: Int): PadAction? = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> A
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> B
            KeyEvent.KEYCODE_BUTTON_X -> X
            KeyEvent.KEYCODE_BUTTON_Y -> Y
            KeyEvent.KEYCODE_DPAD_UP -> UP
            KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
            KeyEvent.KEYCODE_BUTTON_L1 -> L1
            KeyEvent.KEYCODE_BUTTON_R1 -> R1
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_PAGE_UP -> L2
            KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_PAGE_DOWN -> R2
            KeyEvent.KEYCODE_BUTTON_THUMBL -> L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> R3
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> START
            KeyEvent.KEYCODE_BUTTON_SELECT -> SELECT
            else -> null
        }

        /** Actions that repeat while the button is held (moving through lists). */
        fun repeats(action: PadAction): Boolean =
            action == UP || action == DOWN || action == LEFT || action == RIGHT
    }
}
