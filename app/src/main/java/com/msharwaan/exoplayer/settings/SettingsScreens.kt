package com.msharwaan.exoplayer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msharwaan.exoplayer.ui.KeyboardView
import com.msharwaan.exoplayer.ui.Legend
import com.msharwaan.exoplayer.ui.theme.Palette

/** Top screen: what the focused setting is for and where to get it. Never shows a key (display only, R11). */
@Composable
fun SettingsTop(model: SettingsModel) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val row = ui.editing?.row ?: SettingRow.entries[ui.focus]
    Box(Modifier.fillMaxSize().background(Palette.Black).padding(horizontal = 64.dp), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (ui.editing != null) "EDITING" else "SETTINGS · " + row.group.uppercase(),
                color = Palette.Accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp,
            )
            Text(row.title, color = Palette.Ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp)
            Text(row.help, color = Palette.Muted, fontSize = 18.sp, lineHeight = 27.sp)
            if (ui.editing != null) {
                Text(
                    "Type on the bottom screen, or copy the key in a browser and press Paste.",
                    color = Palette.Ink, fontSize = 16.sp, lineHeight = 24.sp,
                )
            } else {
                ui.summaries[row]?.takeIf { it.isNotEmpty() }?.let { Text(it, color = Palette.Ink, fontSize = 18.sp) }
            }
        }
    }
}

/** Bottom screen: the list of settings, or the editor with the keyboard. */
@Composable
fun SettingsBottom(model: SettingsModel) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val e = ui.editing
    if (e != null) Editor(model, e) else SettingsList(model, ui)
}

@Composable
private fun SettingsList(model: SettingsModel, ui: SettingsUi) {
    val list = rememberLazyListState()
    LaunchedEffect(ui.focus) { list.animateScrollToItem((ui.focus - 2).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Legend("A" to "Open", "X" to "Clear", "B" to "Back")
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(SettingRow.entries) { i, row ->
                if (i == 0 || SettingRow.entries[i - 1].group != row.group) {
                    Text(
                        row.group.uppercase(), color = Palette.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = if (i == 0) 0.dp else 10.dp, bottom = 2.dp),
                    )
                }
                val focused = i == ui.focus
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(Palette.Panel, shape)
                        .border(2.dp, if (focused) Palette.Accent else Palette.Line, shape)
                        .clickable { model.onRowTapped(i) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.title, color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val summary = ui.summaries[row].orEmpty()
                    Text(
                        summary,
                        color = if (summary.startsWith("Not")) Palette.Accent else Palette.Muted,
                        fontSize = 14.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp).width(210.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Editor(model: SettingsModel, e: SettingsUi.Editing) {
    val password = e.row == SettingRow.OPENSUBTITLES_PASSWORD
    val text = e.keyboard.text
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(e.row.title.uppercase(), color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Palette.Panel, shape).border(2.dp, Palette.Accent, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                (if (password && !e.reveal) "•".repeat(text.length) else text) + "▏",
                color = Palette.Ink, fontSize = 18.sp, fontFamily = FontFamily.Monospace, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        KeyboardView(e.keyboard, onTap = model::onKeyTapped)
        Box(Modifier.weight(1f))
        Legend(
            *listOfNotNull(
                "A" to "Type", "X" to "Delete", "Y" to "Caps",
                ("▢" to if (e.reveal) "Hide" else "Show").takeIf { password },
                "▷" to "Save", "B" to "Cancel",
            ).toTypedArray(),
        )
    }
}
