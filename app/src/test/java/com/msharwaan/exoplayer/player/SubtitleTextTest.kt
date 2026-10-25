package com.msharwaan.exoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleTextTest {

    private val srt = """
        1
        00:00:01,000 --> 00:00:03,500
        <i>Hello there.</i>

        2
        00:00:04,000 --> 00:00:06,000
        Two lines,
        on screen.
    """.trimIndent()

    private val vtt = """
        WEBVTT

        00:01.000 --> 00:02.000 align:start
        Short form.

        01:00:00.250 --> 01:00:01.000
        An hour in.
    """.trimIndent()

    @Test
    fun readsSrt() {
        val cues = SubtitleText.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(Cue(1000, 3500, "Hello there."), cues[0])
        assertEquals("Two lines,\non screen.", cues[1].text)
    }

    @Test
    fun readsVtt() {
        val cues = SubtitleText.parse(vtt)
        assertEquals(listOf(1000L, 3_600_250L), cues.map { it.startMs })
    }

    @Test
    fun shiftsTimingBothWays() {
        val cues = SubtitleText.parse(srt)
        assertEquals("Hello there.", SubtitleText.at(cues, 2000, 0))
        // 500 ms later: at 1.2 s the first line hasn't appeared yet.
        assertNull(SubtitleText.at(cues, 1200, 500))
        // 500 ms earlier: at 0.6 s it already shows.
        assertEquals("Hello there.", SubtitleText.at(cues, 600, -500))
    }

    @Test
    fun knowsWhenTheNextChangeIs() {
        val cues = SubtitleText.parse(srt)
        assertEquals(1000L, SubtitleText.nextChange(cues, 0, 0))
        assertEquals(1500L, SubtitleText.nextChange(cues, 2000, 0))
        assertNull(SubtitleText.nextChange(cues, 7000, 0))
    }
}
