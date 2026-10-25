package com.msharwaan.exoplayer.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msharwaan.exoplayer.library.Card
import com.msharwaan.exoplayer.library.Tab
import com.msharwaan.exoplayer.ui.Artwork
import com.msharwaan.exoplayer.ui.CornerButton
import com.msharwaan.exoplayer.ui.KeyHint
import com.msharwaan.exoplayer.ui.PanelButton
import com.msharwaan.exoplayer.ui.theme.Palette

/**
 * Top screen for Browsing. Display only (rule R11).
 * Nothing focused: a poster mix of the tab (D4). A card focused: that title's art and
 * details filling the screen, title, year and plot on the left (D6, user feedback).
 */
@Composable
fun BrowseTop(model: BrowseModel) {
    val library by model.library.collectAsStateWithLifecycle()
    val tab by model.tab.collectAsStateWithLifecycle()
    val focus by model.focus.collectAsStateWithLifecycle()
    val hasFolder by model.hasFolder.collectAsStateWithLifecycle()
    val cards = library[tab].orEmpty()
    val card = cards.getOrNull(focus)
    when {
        !hasFolder -> Welcome()
        card != null -> FocusedTitle(card)
        else -> PosterMix(tab, cards)
    }
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center) {
        Text("Exo Player", color = Palette.Ink, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text(
            "Choose the folder that holds your videos (usually on the SD card). Everything stays on the device.",
            color = Palette.Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp).width(520.dp),
        )
    }
}

@Composable
private fun PosterMix(tab: Tab, cards: List<Card>) {
    Box(Modifier.fillMaxSize()) {
        if (cards.isNotEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { row ->
                    Row(
                        Modifier.weight(1f).fillMaxWidth().offset(x = if (row == 1) (-48).dp else (-6).dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(9) { col ->
                            val c = cards[(col + row * 3) % cards.size]
                            Artwork(c.posterPath, c.groupKey, Modifier.fillMaxHeight().aspectRatio(2f / 3f), label = c.name)
                        }
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x4D000000), 0.35f to Color(0x1F000000), 0.65f to Color(0x8C000000), 1f to Color(0xF0000000)),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp)) {
            Text(
                if (cards.isEmpty()) "Nothing here yet" else countLabel(tab, cards.size),
                color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            )
            Text(tab.label, color = Palette.Ink, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun countLabel(tab: Tab, n: Int): String = when (tab) {
    Tab.CONTINUE -> "$n IN PROGRESS"
    Tab.MOVIES -> if (n == 1) "1 MOVIE" else "$n MOVIES"
    Tab.SERIES, Tab.ANIME -> if (n == 1) "1 TITLE" else "$n TITLES"
    Tab.OTHERS -> if (n == 1) "1 ENTRY" else "$n ENTRIES"
}

/** The focused title: art filling the screen, text over a dark fade on the left. */
@Composable
fun FocusedTitle(card: Card) {
    Box(Modifier.fillMaxSize()) {
        Artwork(card.backdropPath ?: card.posterPath, card.groupKey, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF0000000), 0.3f to Color(0xD1000000), 0.5f to Color(0x66000000), 0.72f to Color.Transparent),
            ),
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 48.dp).fillMaxWidth(0.42f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(card.name, color = Palette.Ink, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 54.sp)
            Text(card.meta, color = Palette.Muted, fontSize = 16.sp)
            card.overview?.let { Text(it, color = Color(0xFFD6D8DC), fontSize = 16.sp, lineHeight = 23.sp, maxLines = 5, overflow = TextOverflow.Ellipsis) }
            card.resumeNote?.let { note ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressBar(card.progress, Modifier.width(140.dp))
                    Text(note, color = Palette.Ink, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(5.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Palette.Accent))
    }
}

/** Bottom screen for Browsing: the tab row, the cards, and ⟳ Fetch info in the corner (D36). */
@Composable
fun BrowseBottom(model: BrowseModel, onOpen: (Card) -> Unit, onFetchInfo: () -> Unit, onSettings: () -> Unit) {
    val library by model.library.collectAsStateWithLifecycle()
    val tab by model.tab.collectAsStateWithLifecycle()
    val focus by model.focus.collectAsStateWithLifecycle()
    val hasFolder by model.hasFolder.collectAsStateWithLifecycle()
    val scanning by model.scanning.collectAsStateWithLifecycle()
    val fetching by model.fetching.collectAsStateWithLifecycle()
    val cards = library[tab].orEmpty()

    if (!hasFolder) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            PanelButton("Choose video folder", Modifier.fillMaxWidth(), primary = true, keyHint = "A") { model.chooseFolder() }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabStrip(tab, onSelect = model::selectTab)
            if (cards.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (scanning) "Reading your folder…" else "Nothing in ${tab.label} yet.",
                        color = Palette.Muted, fontSize = 15.sp,
                    )
                }
            } else {
                val grid = rememberLazyGridState()
                LaunchedEffect(focus, tab) { if (focus >= 0) grid.animateScrollToItem(focus) else grid.scrollToItem(0) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(BrowseModel.COLUMNS),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(cards, key = { _, c -> c.groupKey }) { i, c ->
                        PosterCard(c, focused = i == focus) {
                            when (val r = model.onCardTapped(i)) {
                                is BrowseResult.Open -> onOpen(r.card)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(14.dp)) {
            // ⚙ Settings (also Start) next to ⟳ Fetch info.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CornerButton("⚙", onClick = onSettings)
                CornerButton(if (scanning || fetching) "…" else "⟳", onClick = onFetchInfo)
            }
        }
    }
}

@Composable
private fun PosterCard(card: Card, focused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .aspectRatio(2f / 3f)
            .then(if (focused) Modifier.border(3.dp, Palette.Accent, shape) else Modifier)
            .padding(if (focused) 4.dp else 0.dp)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Artwork(card.posterPath, card.groupKey, Modifier.fillMaxSize(), label = card.name, labelSize = 12.sp)
        if (card.resumeNote != null) {
            ProgressBar(card.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp))
        }
    }
}

/**
 * The tab row: one line that runs past both edges, the selected tab in the centre,
 * L2 / R2 going round in a full circle (D5).
 */
@Composable
private fun TabStrip(selected: Tab, onSelect: (Tab) -> Unit) {
    val tabs = Tab.entries
    val n = tabs.size
    // The ring repeated on both sides of the selected tab, so the row never shows an end.
    val ring = (-n..n).map { tabs[((selected.ordinal + it) % n + n) % n] to (it == 0) }
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                ring.forEach { (t, isSelected) ->
                    val shape = RoundedCornerShape(50)
                    Text(
                        t.label,
                        color = if (isSelected) Color.Black else Palette.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(shape)
                            .background(if (isSelected) Palette.Ink else Color.Transparent)
                            .border(2.dp, if (isSelected) Palette.Ink else Palette.Line, shape)
                            .clickable { onSelect(t) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, maxWidth = Int.MAX_VALUE, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            val gap = 10.dp.roundToPx()
            val center = n // index of the selected tab in the ring
            val starts = IntArray(placeables.size)
            for (i in 1 until placeables.size) starts[i] = starts[i - 1] + placeables[i - 1].width + gap
            val shift = constraints.maxWidth / 2 - (starts[center] + placeables[center].width / 2)
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { i, p -> p.place(starts[i] + shift, (constraints.maxHeight - p.height) / 2) }
            }
        }
        // L2 / R2 on dark fades at the edges.
        EdgeKey("L2", Alignment.CenterStart)
        EdgeKey("R2", Alignment.CenterEnd)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EdgeKey(key: String, align: Alignment) {
    val left = align == Alignment.CenterStart
    Box(
        Modifier.align(align).fillMaxHeight().width(96.dp).background(
            // Solid under the key hint, so tab text never shows through it.
            if (left) Brush.horizontalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent)
            else Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color.Black, 1f to Color.Black),
        ),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) { Box(Modifier.padding(horizontal = 10.dp)) { KeyHint(key) } }
}
