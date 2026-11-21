package com.msharwaan.exoplayer.ui

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android's battery broadcast, read the way the bottom screen shows it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BatteryTest {

    private fun broadcast(level: Int, scale: Int = 100, status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING) =
        Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, level)
            .putExtra(BatteryManager.EXTRA_SCALE, scale)
            .putExtra(BatteryManager.EXTRA_STATUS, status)

    @Test
    fun percentOfTheScale() {
        assertEquals(Battery(64, charging = false), Battery.from(broadcast(64)))
        assertEquals(Battery(50, charging = false), Battery.from(broadcast(128, scale = 256)))
    }

    @Test
    fun chargingAndFullCountAsCharging() {
        assertEquals(true, Battery.from(broadcast(87, status = BatteryManager.BATTERY_STATUS_CHARGING))?.charging)
        assertEquals(true, Battery.from(broadcast(100, status = BatteryManager.BATTERY_STATUS_FULL))?.charging)
        assertEquals(false, Battery.from(broadcast(87, status = BatteryManager.BATTERY_STATUS_NOT_CHARGING))?.charging)
    }

    @Test
    fun noLevelNoBattery() {
        assertNull(Battery.from(Intent(Intent.ACTION_BATTERY_CHANGED)))
        assertNull(Battery.from(null))
    }
}
