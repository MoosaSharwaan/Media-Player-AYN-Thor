package com.msharwaan.exoplayer.online

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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

/** Top screen: which file is being fixed and, in the results, the focused title's details. */
@Composable
fun FixMatchTop(model: FixMatchModel) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val hit = ui.results.getOrNull(ui.focus).takeIf { ui.phase == FixMatchUi.Phase.RESULTS }
    Box(Modifier.fillMaxSize().background(Palette.Black).padding(horizontal = 64.dp), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.fillMaxWidth(0.66f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("FIX MATCH", color = Palette.Accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            if (hit != null) {
                Text(hit.title, color = Palette.Ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp)
                Text(listOfNotNull(hit.year?.toString(), if (hit.type == Tmdb.MOVIE) "Movie" else "Series", "TMDB ${hit.id}").joinToString(" · "), color = Palette.Muted, fontSize = 16.sp)
                hit.overview?.let { Text(it, color = Palette.Ink, fontSize = 17.sp, lineHeight = 25.sp, maxLines = 7, overflow = TextOverflow.Ellipsis) }
            } else {
                Text("Which title is this?", color = Palette.Ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp)
                Text(ui.fileName, color = Palette.Muted, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                ui.title?.let { Text("Now: ${it.name}" + (it.year?.let { y -> " ($y)" } ?: ""), color = Palette.Ink, fontSize = 17.sp) }
                Text(
                    "Search by name, or type the number from the title's TMDB page (themoviedb.org/movie/603 → 603). " +
                        "Your choice is kept, even when the folder is scanned again.",
                    color = Palette.Muted, fontSize = 16.sp, lineHeight = 24.sp,
                )
            }
        }
    }
}

/** Bottom screen: the search with the keyboard, or the results. */
@Composable
fun FixMatchBottom(model: FixMatchModel) {
    val ui by model.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SEARCH TMDB FOR A", color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            TypeChip("Movie", ui.type == Tmdb.MOVIE) { if (ui.type != Tmdb.MOVIE) model.toggleType() }
            TypeChip("Series", ui.type == Tmdb.TV) { if (ui.type != Tmdb.TV) model.toggleType() }
            Text("L2 / R2", color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        when (ui.phase) {
            FixMatchUi.Phase.TYPING -> {
                Field(ui.keyboard.text)
                KeyboardView(ui.keyboard, onTap = model::onKeyTapped)
                Box(Modifier.weight(1f))
                Legend("A" to "Type", "X" to "Delete", "▷" to "Search", "B" to "Back")
            }
            FixMatchUi.Phase.SEARCHING, FixMatchUi.Phase.APPLYING -> {
                Field(ui.keyboard.text)
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (ui.phase == FixMatchUi.Phase.SEARCHING) "Searching…" else "Fetching its info…", color = Palette.Muted, fontSize = 16.sp)
                }
            }
            FixMatchUi.Phase.RESULTS -> {
                val list = rememberLazyListState()
                LaunchedEffect(ui.focus) { list.animateScrollToItem((ui.focus - 2).coerceAtLeast(0)) }
                Text("${ui.results.size} results for “${ui.keyboard.text.trim()}”", color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(ui.results) { i, hit ->
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Palette.Panel, shape)
                                .border(2.dp, if (i == ui.focus) Palette.Accent else Palette.Line, shape)
                                .clickable { model.onResultTapped(i) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(hit.title, color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(hit.year?.toString() ?: "—", color = Palette.Muted, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                Legend("A" to "This one", "L2" to "Movie / Series", "B" to "Edit search")
            }
        }
    }
}

@Composable
private fun Field(text: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Palette.Panel, shape).border(2.dp, Palette.Accent, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text("$text▏", color = Palette.Ink, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        label, color = if (selected) Palette.Black else Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(if (selected) Palette.Ink else Palette.Black, shape).border(2.dp, if (selected) Palette.Ink else Palette.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
