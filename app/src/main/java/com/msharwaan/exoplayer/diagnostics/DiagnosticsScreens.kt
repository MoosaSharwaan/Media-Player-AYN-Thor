package com.msharwaan.exoplayer.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msharwaan.exoplayer.ui.Legend
import com.msharwaan.exoplayer.ui.PanelButton
import com.msharwaan.exoplayer.ui.theme.Palette

/** Top screen: what the Thor reports about its displays and decoders. Display only (rule R11). */
@Composable
fun DiagnosticsTop(model: DiagnosticsModel) {
    val displays by model.displays.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Diagnostics", color = Palette.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Mono(model.firmware)
        displays.forEach { d ->
            Text("Display ${d.id} · ${d.name}", color = Palette.Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Mono("flags: ${d.flags} · ${d.state} · now ${d.current}")
            Mono("modes: " + d.modes.joinToString("   ") { it.label })
        }
        Text("Decoders", color = Palette.Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        model.codecs.forEach { c -> Mono("${c.mime.padEnd(22)} ${if (c.hardware) "HARDWARE" else "software"}  ${c.name}") }
    }
}

/** Bottom screen: the tests to run, and which window each key reached. */
@Composable
fun DiagnosticsBottom(model: DiagnosticsModel) {
    val focus by model.focus.collectAsStateWithLifecycle()
    val keys by model.keyLog.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiagnosticsModel.Test.entries.forEachIndexed { i, test ->
            PanelButton(test.label, Modifier.fillMaxWidth(), focused = i == focus) { model.run(test) }
        }
        Text("Keys", color = Palette.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        keys.forEach { Mono(it) }
        Legend("A" to "Run", "B" to "Back")
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
}
