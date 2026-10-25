package com.msharwaan.exoplayer.player

import com.msharwaan.exoplayer.player.PlaybackSettings.Row
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSettingsTest {

    @Test
    fun speedStepsAndStopsAtTheEnds() {
        var s = PlaybackSettings()
        s = s.step(Row.SPEED, 1)
        assertEquals("1.25×", s.valueOf(Row.SPEED))
        repeat(10) { s = s.step(Row.SPEED, 1) }
        assertEquals("2×", s.valueOf(Row.SPEED))
        repeat(10) { s = s.step(Row.SPEED, -1) }
        assertEquals("0.5×", s.valueOf(Row.SPEED))
    }

    @Test
    fun audioDelayIn50msStepsWithinTwoSeconds() {
        var s = PlaybackSettings().step(Row.AUDIO_DELAY, -1)
        assertEquals("-50 ms", s.valueOf(Row.AUDIO_DELAY))
        repeat(100) { s = s.step(Row.AUDIO_DELAY, 1) }
        assertEquals("+2000 ms", s.valueOf(Row.AUDIO_DELAY))
    }

    @Test
    fun pictureGoesRoundAndBrightnessStartsAtSystem() {
        var s = PlaybackSettings()
        repeat(5) { s = s.step(Row.PICTURE, 1) }
        assertEquals(Picture.FIT, s.picture)
        assertEquals("System", s.valueOf(Row.BRIGHTNESS))
        s = s.step(Row.BRIGHTNESS, 1)
        assertEquals("10 %", s.valueOf(Row.BRIGHTNESS))
        s = s.step(Row.BRIGHTNESS, -1)
        assertEquals("System", s.valueOf(Row.BRIGHTNESS))
    }
}
