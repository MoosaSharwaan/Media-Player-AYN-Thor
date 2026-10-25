package com.msharwaan.exoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msharwaan.exoplayer.ui.theme.Palette

/** A small round corner button: ⏻ bottom screen off, ✎ Fix match, ⟳ Fetch info (D11, D12, D36). */
@Composable
fun CornerButton(glyph: String, dim: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Palette.Black, CircleShape)
            .border(2.dp, if (dim) Color(0xFF1C1E22) else Palette.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (dim) Color(0xFF3A3D43) else Palette.Muted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** A bottom-screen button. Touch targets are at least 48 dp tall (rule R6). */
@Composable
fun PanelButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focused: Boolean = false,
    enabled: Boolean = true,
    keyHint: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (primary) Palette.Accent else Palette.Panel, shape)
            .border(2.dp, if (focused) Palette.Accent else if (primary) Palette.Accent else Palette.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            color = when {
                !enabled -> Palette.Dim
                primary -> Palette.OnAccent
                else -> Palette.Ink
            },
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        if (keyHint != null) KeyHint(keyHint, if (primary) Palette.OnAccent else Palette.Muted)
    }
}

/** A button name in a small circle, as on the Thor: A, B, L2… */
@Composable
fun KeyHint(key: String, color: Color = Palette.Muted) {
    Box(
        modifier = Modifier.padding(start = 8.dp).size(22.dp).border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(key, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

/** The button legend along the bottom edge of the bottom screen (rule R9). */
@Composable
fun Legend(vararg items: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyHint(key)
                Text(label, color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
