package com.msharwaan.exoplayer.player

import androidx.media3.common.text.Cue
import kotlin.math.abs

/** A sign's translation, placed where the subtitle file puts it in the frame (fractions of it). */
data class SignCue(
    val text: String,
    /** The point the text hangs from. */
    val x: Float,
    val y: Float,
    /** 0 left / top, 0.5 middle, 1 right / bottom: which part of the text sits on the point. */
    val anchorX: Float,
    val anchorY: Float,
    /** Text height as a fraction of the frame's, when the file says. */
    val size: Float?,
)

/**
 * Text subtitles from the file, sorted for the top screen. ASS files (most anime) also carry:
 * drawings, shapes written as numbers ("m 830 203 l 848 177…") that a full renderer paints and
 * that would otherwise show as those numbers, so they're dropped; signs, translations of writing
 * in the picture placed with \pos, shown where they belong; and each sign two or three times over
 * for its outline and fill, shown once.
 */
object TextCues {
    class Sorted(val dialogue: String?, val signs: List<SignCue>)

    fun sort(cues: List<Cue>): Sorted {
        val dialogue = mutableListOf<String>()
        val signs = mutableListOf<SignCue>()
        for (cue in cues) {
            val text = cue.text?.toString()?.trim().orEmpty()
            if (text.isEmpty() || isDrawing(text)) continue
            val sign = signOf(cue, text)
            if (sign == null) {
                if (text !in dialogue) dialogue += text
            } else if (signs.none { it.text == text && abs(it.x - sign.x) < 0.02f && abs(it.y - sign.y) < 0.02f }) {
                signs += sign
            }
        }
        return Sorted(dialogue.joinToString("\n").ifEmpty { null }, signs)
    }

    private val drawing = Regex("""^[mn]\s+-?\d[\d\s.\-mnlbspcMNLBSPC]*$""")

    /** Only drawing commands and numbers. */
    fun isDrawing(text: String): Boolean = drawing.matches(text)

    /**
     * Null for ordinary lines: those with no place of their own, or at the default bottom middle,
     * which the top screen shows in the size and spot chosen in Settings.
     */
    private fun signOf(cue: Cue, text: String): SignCue? {
        if (cue.line == Cue.DIMEN_UNSET || cue.lineType != Cue.LINE_TYPE_FRACTION || cue.position == Cue.DIMEN_UNSET) return null
        val bottomMiddle = cue.line >= 0.85f && abs(cue.position - 0.5f) < 0.05f &&
            cue.lineAnchor == Cue.ANCHOR_TYPE_END && cue.positionAnchor != Cue.ANCHOR_TYPE_START && cue.positionAnchor != Cue.ANCHOR_TYPE_END
        if (bottomMiddle) return null
        return SignCue(
            text, cue.position, cue.line,
            anchorX = anchor(cue.positionAnchor), anchorY = anchor(cue.lineAnchor),
            size = cue.textSize.takeIf { it != Cue.DIMEN_UNSET && cue.textSizeType == Cue.TEXT_SIZE_TYPE_FRACTIONAL },
        )
    }

    private fun anchor(type: Int) = when (type) {
        Cue.ANCHOR_TYPE_START -> 0f
        Cue.ANCHOR_TYPE_END -> 1f
        else -> 0.5f
    }
}
