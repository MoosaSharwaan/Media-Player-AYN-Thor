package com.msharwaan.exoplayer.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msharwaan.exoplayer.library.Card
import com.msharwaan.exoplayer.player.clock
import com.msharwaan.exoplayer.ui.Artwork
import com.msharwaan.exoplayer.ui.KeyboardView
import com.msharwaan.exoplayer.ui.Legend
import com.msharwaan.exoplayer.ui.theme.Palette

// ---- Search ----

/** Top screen: the matches as you type, or the focused match once you're choosing. */
@Composable
fun SearchTop(model: SearchModel) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val focused = ui.results.getOrNull(ui.focus).takeIf { ui.choosing }
    if (focused != null) { FocusedTitle(focused); return }
    Column(Modifier.fillMaxSize().background(Palette.Black).padding(horizontal = 64.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("SEARCH", color = Palette.Accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Text(ui.keyboard.text.ifEmpty { "Type a title, a year or a genre" }, color = if (ui.keyboard.text.isEmpty()) Palette.Muted else Palette.Ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (ui.keyboard.text.isNotEmpty()) {
            Text(if (ui.results.isEmpty()) "No matches" else if (ui.results.size == 1) "1 match" else "${ui.results.size} matches", color = Palette.Muted, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ui.results.take(7).forEach { c ->
                    Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Artwork(c.posterPath, c.groupKey, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)), label = c.name)
                        Text(c.name, color = Palette.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Bottom screen: the keyboard, or the matches as cards. */
@Composable
fun SearchBottom(model: SearchModel, onOpen: (Card) -> Unit) {
    val ui by model.ui.collectAsStateWithLifecycle()
    if (ui.choosing) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("“${ui.keyboard.text.trim()}”", color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Legend("A" to "Open", "B" to "Edit")
            }
            val grid = rememberLazyGridState()
            LaunchedEffect(ui.focus) { grid.animateScrollToItem(ui.focus) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(BrowseModel.COLUMNS), state = grid, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(ui.results, key = { _, c -> c.groupKey }) { i, c ->
                    PosterCard(c, focused = i == ui.focus) { (model.onResultTapped(i) as? SearchModel.Result.Open)?.let { onOpen(it.card) } }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SEARCH YOUR LIBRARY", color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Palette.Panel, shape).border(2.dp, Palette.Accent, shape).padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text(ui.keyboard.text + "▏", color = Palette.Ink, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        KeyboardView(ui.keyboard, onTap = model::onKeyTapped)
        Box(Modifier.weight(1f))
        Legend("A" to "Type", "X" to "Delete", "▷" to "Show matches", "B" to "Back")
    }
}

// ---- Folders ----

/** Top screen: where you are, and the focused folder or video. */
@Composable
fun FoldersTop(model: FoldersModel, path: String) {
    val focus by model.focus.collectAsStateWithLifecycle()
    val data by model.files.collectAsStateWithLifecycle()
    val entries = FoldersModel.entriesOf(data.first, data.second, path)
    val e = entries.getOrNull(focus[path] ?: 0)
    Column(Modifier.fillMaxSize().background(Palette.Black).padding(horizontal = 64.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Text("FOLDERS", color = Palette.Accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Text(crumbs(path), color = Palette.Muted, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        if (e != null) {
            Text(e.name, color = Palette.Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.file != null) listOfNotNull(
                    e.file.durationMs.takeIf { it > 0 }?.let { clock(it) },
                    "%.1f GB".format(e.file.size / 1e9).takeIf { e.file.size > 0 },
                    "Watched".takeIf { e.watched },
                ).joinToString(" · ") else if (e.count == 1) "1 video" else "${e.count} videos",
                color = Palette.Muted, fontSize = 18.sp,
            )
        } else {
            Text("No videos here.", color = Palette.Ink, fontSize = 28.sp)
        }
    }
}

@Composable
fun FoldersBottom(model: FoldersModel, path: String, onResult: (FoldersModel.Result) -> Unit) {
    val focus by model.focus.collectAsStateWithLifecycle()
    val data by model.files.collectAsStateWithLifecycle()
    val entries = FoldersModel.entriesOf(data.first, data.second, path)
    val f = focus[path] ?: 0
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(path.substringAfterLast('/').ifEmpty { "Your video folder" }, color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Legend("A" to "Open", "B" to "Up")
        }
        val list = rememberLazyListState()
        LaunchedEffect(f, path) { list.animateScrollToItem((f - 2).coerceAtLeast(0)) }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(entries) { i, e ->
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Palette.Panel, shape)
                        .border(2.dp, if (i == f) Palette.Accent else Palette.Line, shape)
                        .clickable { onResult(model.onTapped(path, i)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (e.path != null) "▸" else if (e.watched) "✓" else "▶", color = if (e.path != null) Palette.Accent else Palette.Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(e.name, color = Palette.Ink, fontSize = 15.sp, fontWeight = if (e.path != null) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (e.path != null) Text("${e.count}", color = Palette.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (e.progress > 0f) ProgressBar(e.progress, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun crumbs(path: String) = (listOf("Video folder") + path.split('/').filter { it.isNotEmpty() }).joinToString(" › ")
