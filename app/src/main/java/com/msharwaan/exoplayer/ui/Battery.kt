package com.msharwaan.exoplayer.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.msharwaan.exoplayer.ui.theme.Palette

/** The battery as the bottom screen shows it. */
data class Battery(val percent: Int, val charging: Boolean) {
    companion object {
        /** Reads Android's battery broadcast; null when it carries no level. */
        fun from(intent: Intent?): Battery? {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level < 0 || scale <= 0) return null
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            return Battery((level * 100f / scale).toInt().coerceIn(0, 100), charging)
        }
    }
}

/**
 * The battery level, kept current by Android's battery broadcast: it arrives only when the
 * level or charging changes, so nothing polls (battery rule 5).
 */
@Composable
fun rememberBattery(): Battery? {
    val context = LocalContext.current.applicationContext
    var battery by remember { mutableStateOf<Battery?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { battery = Battery.from(intent) ?: battery }
        }
        // The broadcast is sticky, so registering returns the current level at once.
        val current = ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        Battery.from(current)?.let { battery = it }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return battery
}

/** "▭ 87 %": a small battery outline, filled to the level, red at 15 % and below; ⚡ while charging. */
@Composable
fun BatteryLabel(battery: Battery, modifier: Modifier = Modifier) {
    val low = battery.percent <= 15 && !battery.charging
    val fill = when {
        battery.charging -> Color(0xFF6CC070)
        low -> Color(0xFFE5534B)
        else -> Palette.Muted
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
            val tip = 2.dp.toPx()
            val stroke = 1.5.dp.toPx()
            val body = Size(size.width - tip, size.height)
            drawRoundRect(Palette.Muted, Offset.Zero, body, CornerRadius(2.dp.toPx()), style = Stroke(stroke))
            drawRect(Palette.Muted, Offset(body.width, size.height * 0.3f), Size(tip, size.height * 0.4f))
            val inset = stroke + 1.dp.toPx()
            val inner = Size((body.width - 2 * inset) * battery.percent / 100f, body.height - 2 * inset)
            if (inner.width > 0f) drawRect(fill, Offset(inset, inset), inner)
        }
        Text(
            (if (battery.charging) "⚡" else "") + "${battery.percent} %",
            color = if (low) fill else Palette.Muted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
