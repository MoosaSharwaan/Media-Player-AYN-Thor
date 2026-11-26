package com.msharwaan.exoplayer.library

import com.msharwaan.exoplayer.input.PadAction
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A library's passkey: a combo of 4 to 8 presses on the D-pad and A, X, Y (D56). Only a salted
 * hash is kept. It keeps a library out of sight on the Thor; the videos themselves are ordinary
 * files on the card.
 */
object Passkey {
    val BUTTONS = listOf(PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT, PadAction.A, PadAction.X, PadAction.Y)
    const val MIN = 4
    const val MAX = 8

    /** "salt:hash", as kept in the library's settings. */
    fun make(presses: List<PadAction>): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        return "$salt:${hash(salt, presses)}"
    }

    fun matches(stored: String, presses: List<PadAction>): Boolean {
        val salt = stored.substringBefore(':', "")
        val expected = stored.substringAfter(':', "")
        if (salt.isEmpty() || expected.isEmpty()) return false
        return MessageDigest.isEqual(hash(salt, presses).toByteArray(), expected.toByteArray())
    }

    private fun hash(salt: String, presses: List<PadAction>): String =
        MessageDigest.getInstance("SHA-256").digest("$salt|${presses.joinToString(",") { it.name }}".toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

/** Entering a passkey on the pad: [first] is the combo being confirmed when setting one. */
data class PasskeyEntry(
    val presses: List<PadAction> = emptyList(),
    val first: List<PadAction>? = null,
    val error: String? = null,
) {
    sealed interface Result {
        data object Typing : Result
        data class Done(val presses: List<PadAction>) : Result
        data object Cancel : Result
    }

    /** D-pad, A, X, Y add a press; Start finishes; B takes one back, or cancels when empty. */
    fun press(action: PadAction): Pair<PasskeyEntry, Result> = when (action) {
        in Passkey.BUTTONS -> (if (presses.size < Passkey.MAX) copy(presses = presses + action, error = null) else this) to Result.Typing
        PadAction.START -> if (presses.size < Passkey.MIN) copy(error = "At least ${Passkey.MIN} presses, then ▷ Start.") to Result.Typing
            else this to Result.Done(presses)
        PadAction.B -> if (presses.isEmpty()) this to Result.Cancel else copy(presses = presses.dropLast(1), error = null) to Result.Typing
        else -> this to Result.Typing
    }
}
