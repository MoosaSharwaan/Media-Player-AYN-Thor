package com.msharwaan.exoplayer.player

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VolumeControlTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    @Test
    fun goesPastFullVolumeIntoBoostAndBack() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max - 1, 0)
        val v = VolumeControl(context) { 0 }
        v.up()
        assertEquals(100, v.percent.value)
        v.up()
        assertEquals(125, v.percent.value)
        repeat(5) { v.up() }
        assertEquals(200, v.percent.value) // the boost stops at 200 %
        repeat(4) { v.down() }
        assertEquals(100, v.percent.value) // the boost comes off before the media volume
        v.down()
        assertEquals(max - 1, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        v.release(context)
    }

    @Test
    fun boostEndsWhenTheVolumeIsTurnedDownElsewhere() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        val v = VolumeControl(context) { 0 }
        v.up()
        assertEquals(125, v.percent.value)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max / 2, 0)
        v.refresh()
        assertEquals(Math.round((max / 2) * 100f / max), v.percent.value)
        v.release(context)
    }
}
