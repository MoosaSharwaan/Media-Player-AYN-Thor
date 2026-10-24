package com.msharwaan.exoplayer.player

/** One subtitle line on screen from [startMs] to [endMs]. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Reads .srt and .vtt subtitle files.
 *
 * The app draws these itself so subtitle timing can move both earlier and later in
 * 50 ms steps (decision D8). Plain Kotlin, unit tested.
 */
object SubtitleText {

    private val TIME = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
    private val ARROW = Regex("""\s*-->\s*""")
    private val TAGS = Regex("""<[^>]+>|\{\\[^}]*}""")

    fun parse(text: String): List<Cue> {
        val lines = text.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val cues = mutableListOf<Cue>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (ARROW.containsMatchIn(line)) {
                val (a, b) = line.split(ARROW, limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                val start = time(a)
                val end = time(b.trim().substringBefore(' '))
                i++
                val body = StringBuilder()
                while (i < lines.size && lines[i].isNotBlank()) {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[i].replace(TAGS, ""))
                    i++
                }
                if (start != null && end != null && body.isNotBlank()) cues += Cue(start, end, body.toString().trim())
            } else {
                i++
            }
        }
        return cues.sortedBy { it.startMs }
    }

    /** The lines showing at [positionMs], after shifting subtitles by [delayMs] (positive = later). */
    fun at(cues: List<Cue>, positionMs: Long, delayMs: Long): String? {
        val t = positionMs - delayMs
        val showing = cues.filter { t >= it.startMs && t < it.endMs }
        return if (showing.isEmpty()) null else showing.joinToString("\n") { it.text }
    }

    /** Milliseconds until the subtitle shown changes, so the screen redraws only then. */
    fun nextChange(cues: List<Cue>, positionMs: Long, delayMs: Long): Long? {
        val t = positionMs - delayMs
        val next = cues.asSequence().flatMap { sequenceOf(it.startMs, it.endMs) }.filter { it > t }.minOrNull() ?: return null
        return next - t
    }

    private fun time(s: String): Long? {
        val m = TIME.find(s.trim()) ?: return null
        val h = m.groupValues[1].ifEmpty { "0" }.toLong()
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        val ms = m.groupValues[4].padEnd(3, '0').toLong()
        return ((h * 60 + min) * 60 + sec) * 1000 + ms
    }
}
