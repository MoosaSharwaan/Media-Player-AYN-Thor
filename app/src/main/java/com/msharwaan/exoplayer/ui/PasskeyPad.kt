package com.msharwaan.exoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.library.Passkey
import com.msharwaan.exoplayer.library.PasskeyEntry
import com.msharwaan.exoplayer.ui.theme.Palette

/**
 * The bottom screen while a passkey is entered (D56): a dot per press, never the presses
 * themselves. The pad's buttons work, and so do the ones drawn here.
 */
@Composable
fun PasskeyPad(title: String, hint: String, entry: PasskeyEntry, extra: Pair<String, String>? = null, onPress: (PadAction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(entry.error ?: hint, color = if (entry.error != null) Palette.Accent else Palette.Muted, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            repeat(Passkey.MAX) { i ->
                val filled = i < entry.presses.size
                Box(
                    Modifier.size(18.dp)
                        .background(if (filled) Palette.Accent else Palette.Black, CircleShape)
                        .border(2.dp, if (filled) Palette.Accent else if (i < Passkey.MIN) Palette.Muted else Palette.Line, CircleShape),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Passkey.BUTTONS.forEach { b -> PanelButton(symbol(b), Modifier.weight(1f)) { onPress(b) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton("⌫ Back", Modifier.weight(1f)) { onPress(PadAction.B) }
            PanelButton("Done", Modifier.weight(1f), primary = true) { onPress(PadAction.START) }
        }
        Box(Modifier.weight(1f))
        Legend(*listOfNotNull("✛" to "+ A X Y: press", "▷" to "Done", "B" to "Back", extra).toTypedArray())
    }
}

private fun symbol(b: PadAction) = when (b) {
    PadAction.UP -> "↑"
    PadAction.DOWN -> "↓"
    PadAction.LEFT -> "←"
    PadAction.RIGHT -> "→"
    else -> b.name
}

/** The top screen while a library asks for its passkey. */
@Composable
fun PasskeyTop(name: String, text: String) {
    Column(Modifier.fillMaxSize().background(Palette.Black).padding(horizontal = 64.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("LOCKED LIBRARY", color = Palette.Accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Text("🔒 $name", color = Palette.Ink, fontSize = 46.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Palette.Muted, fontSize = 18.sp, lineHeight = 26.sp)
    }
}
