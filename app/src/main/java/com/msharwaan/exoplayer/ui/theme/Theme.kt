package com.msharwaan.exoplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Pure black backgrounds (rule R2) and one accent for focus and the main action (rule R8). */
object Palette {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFFF1F2F4)
    val Muted = Color(0xFF8D929B)
    val Dim = Color(0xFF4A4E55)
    val Line = Color(0xFF24272C)
    val Panel = Color(0xFF15171B)
    val Accent = Color(0xFFF2B33D)
    val OnAccent = Color(0xFF1A1204)
}

@Composable
fun ExoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.Black,
            surface = Palette.Black,
            primary = Palette.Accent,
            onPrimary = Palette.OnAccent,
            onBackground = Palette.Ink,
            onSurface = Palette.Ink,
        ),
        content = content,
    )
}
