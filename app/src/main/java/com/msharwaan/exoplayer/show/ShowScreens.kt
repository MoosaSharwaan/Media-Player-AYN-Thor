package com.msharwaan.exoplayer.show

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msharwaan.exoplayer.browse.ProgressBar
import com.msharwaan.exoplayer.library.Library
import com.msharwaan.exoplayer.ui.Artwork
import com.msharwaan.exoplayer.ui.Legend
import com.msharwaan.exoplayer.ui.theme.Palette

/**
 * The top screen for a show (D14): the show's art and details first, a season's art and
 * description while a season is focused or open, an episode's still and description while
 * an episode is focused.
 */
@Composable
fun ShowTop(model: ShowModel) {
    val data by model.data.collectAsStateWithLifecycle()
    val view by model.view.collectAsStateWithLifecycle()
    val d = data ?: return
    val title = d.title
    val season = when (view.level) {
        ShowView.Level.SEASONS -> d.seasons.getOrNull(view.seasonFocus)
        ShowView.Level.EPISODES -> d.seasons.getOrNull(view.season).takeUnless { d.singleSeason }
    }
    val episode = if (view.level == ShowView.Level.EPISODES) d.seasons.getOrNull(view.season)?.episodes?.getOrNull(view.episodeFocus) else null

    when {
        episode != null -> Details(
            art = episode.stillPath ?: title.backdropPath ?: title.posterPath,
            key = title.groupKey,
            heading = episode.title,
            meta = listOfNotNull(
                ShowData.label(episode, d.singleSeason),
                episode.runtimeMin?.let(Library::formatRuntime),
                "Watched".takeIf { episode.watched },
            ).joinToString(" · "),
            overview = episode.overview,
        ) {
            when {
                episode.missing -> Text("Not on the SD card", color = Palette.Muted, fontSize = 14.sp)
                episode.leftNote != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressBar(episode.progress, Modifier.width(140.dp))
                    Text(episode.leftNote, color = Palette.Ink, fontSize = 14.sp)
                }
            }
        }
        season != null -> Details(
            art = title.backdropPath ?: title.posterPath,
            key = title.groupKey,
            heading = season.name,
            meta = listOfNotNull(title.name, season.year?.toString(), episodesText(season)).joinToString(" · "),
            overview = season.overview ?: title.overview,
            poster = season.posterPath ?: title.posterPath,
        )
        else -> Details(
            art = title.backdropPath ?: title.posterPath,
            key = title.groupKey,
            heading = title.name,
            meta = d.meta,
            overview = title.overview,
        ) {
            d.nextUp?.let { next ->
                Text(
                    "Next up: " + listOfNotNull(ShowData.label(next, d.singleSeason).takeIf { next.number != null }, next.title).distinct().joinToString(" · ") +
                        (next.leftNote?.let { ", $it" } ?: ""),
                    color = Palette.Ink, fontSize = 15.sp,
                )
            }
        }
    }
}

private fun episodesText(s: SeasonEntry): String {
    val n = s.episodes.size
    return if (n == 1) "1 episode" else "$n episodes"
}

/** Art across the screen, text over a dark fade on the left, and a poster on the right for seasons. */
@Composable
private fun Details(
    art: String?,
    key: String,
    heading: String,
    meta: String,
    overview: String?,
    poster: String? = null,
    extra: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Palette.Black)) {
        Artwork(art, key, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF0000000), 0.3f to Color(0xD1000000), 0.5f to Color(0x66000000), 0.72f to Color.Transparent),
            ),
        )
        if (poster != null) {
            Artwork(
                poster, key,
                Modifier.align(Alignment.CenterEnd).padding(48.dp).fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 48.dp).fillMaxWidth(0.42f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(heading, color = Palette.Ink, fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Text(meta, color = Palette.Muted, fontSize = 16.sp)
            overview?.let { Text(it, color = Color(0xFFD6D8DC), fontSize = 16.sp, lineHeight = 23.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
            extra()
        }
    }
}

/** The bottom screen for a show: season cards 4 across, or one season's episode cards 3 across (D15). */
@Composable
fun ShowBottom(model: ShowModel, onPlay: (Long) -> Unit) {
    val data by model.data.collectAsStateWithLifecycle()
    val view by model.view.collectAsStateWithLifecycle()
    val d = data ?: return
    val episodes = view.level == ShowView.Level.EPISODES
    val season = d.seasons.getOrNull(view.season)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (!episodes || d.singleSeason) d.title.name else season?.name.orEmpty(),
                color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Legend(if (episodes) "A" to "Play" else "A" to "Open", "B" to "Back")
        }
        val grid = rememberLazyGridState()
        val focus = if (episodes) view.episodeFocus else view.seasonFocus
        LaunchedEffect(focus, view.level, view.season) { if (focus >= 0) grid.animateScrollToItem(focus) else grid.scrollToItem(0) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (episodes) ShowModel.EPISODE_COLUMNS else ShowModel.SEASON_COLUMNS),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (episodes) {
                itemsIndexed(season?.episodes.orEmpty()) { i, e ->
                    EpisodeCard(d, e, focused = i == view.episodeFocus) {
                        (model.onEpisodeTapped(i) as? ShowModel.Result.Play)?.let { onPlay(it.fileId) }
                    }
                }
            } else {
                itemsIndexed(d.seasons) { i, s ->
                    SeasonCard(d, s, focused = i == view.seasonFocus) { model.onSeasonTapped(i) }
                }
            }
        }
    }
}

private fun Modifier.focusRing(focused: Boolean, shape: RoundedCornerShape) =
    then(if (focused) Modifier.border(3.dp, Palette.Accent, shape).padding(4.dp) else Modifier)

@Composable
private fun SeasonCard(d: ShowData, s: SeasonEntry, focused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.aspectRatio(2f / 3f).focusRing(focused, shape).clip(shape).clickable(onClick = onClick)) {
        Artwork(s.posterPath ?: d.title.posterPath, d.title.groupKey + s.number, Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 10.dp),
        ) {
            Text(s.name, color = Palette.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(episodesText(s), color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/** An episode: still, number, ✓ when watched, progress when started, greyed when not on the card. */
@Composable
private fun EpisodeCard(d: ShowData, e: EpisodeEntry, focused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.alpha(if (e.missing) 0.4f else 1f).clickable(onClick = onClick)) {
        Box(Modifier.aspectRatio(16f / 9f).focusRing(focused, shape).clip(shape)) {
            Artwork(e.stillPath, d.title.groupKey + e.season + "x" + e.number, Modifier.fillMaxSize())
            e.number?.let {
                Badge("$it", Palette.Ink, Color(0xA6000000), Modifier.align(Alignment.TopStart))
            }
            if (e.watched) Badge("✓", Palette.OnAccent, Palette.Accent, Modifier.align(Alignment.TopEnd))
            if (e.progress > 0f) ProgressBar(e.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
        }
        Text(
            e.title, color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            if (e.missing) "Not on card" else e.runtimeMin?.let(Library::formatRuntime) ?: ShowData.label(e, d.singleSeason),
            color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun Badge(text: String, color: Color, background: Color, modifier: Modifier) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        modifier = modifier.padding(6.dp).background(background, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
