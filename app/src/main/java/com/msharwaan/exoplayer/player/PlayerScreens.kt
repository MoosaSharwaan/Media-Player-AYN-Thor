package com.msharwaan.exoplayer.player

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import com.msharwaan.exoplayer.browse.ProgressBar
import com.msharwaan.exoplayer.ui.Artwork
import com.msharwaan.exoplayer.ui.KeyHint
import com.msharwaan.exoplayer.ui.Legend
import com.msharwaan.exoplayer.ui.PanelButton
import com.msharwaan.exoplayer.ui.theme.Palette

/**
 * The top screen while a movie or episode is open (D13).
 *
 * Playing: the picture and its subtitles, nothing else (rule R1). Paused: the title's art and
 * details, never a progress bar. [video] is the picture itself, passed in so screenshots can
 * leave it out.
 */
@Composable
fun PlayerTop(ui: PlayerUi, subtitle: String?, video: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Black)) {
        video()
        if (ui.paused) {
            PausedArt(ui)
        } else if (subtitle != null) {
            Text(
                subtitle,
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), blurRadius = 6f)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 64.dp, end = 64.dp, bottom = 36.dp),
            )
        }
    }
}

/** The paused top screen: art across the screen, the poster and the details on the left. */
@Composable
private fun PausedArt(ui: PlayerUi) {
    Box(Modifier.fillMaxSize()) {
        Artwork(ui.backdropPath ?: ui.posterPath, ui.groupKey, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF2000000), 0.45f to Color(0xC0000000), 0.75f to Color(0x40000000), 1f to Color.Transparent),
            ),
        )
        Row(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 48.dp).fillMaxWidth(0.72f),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.posterPath != null) {
                Artwork(ui.posterPath, ui.groupKey, Modifier.width(190.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ui.title, color = Palette.Ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp)
                if (ui.meta.isNotEmpty()) Text(ui.meta, color = Palette.Muted, fontSize = 16.sp)
                ui.overview?.let { Text(it, color = Color(0xFFD6D8DC), fontSize = 16.sp, lineHeight = 23.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** The picture: a SurfaceView, the cheapest way to show video (battery rule 2), at the video's own shape. */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player) {
    var ratio by remember { mutableFloatStateOf(16f / 9f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
        listener.onVideoSizeChanged(player.videoSize)
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { SurfaceView(it).also(player::setVideoSurfaceView) },
            onRelease = player::clearVideoSurfaceView,
            modifier = Modifier.aspectRatio(ratio),
        )
    }
}

/**
 * The bottom screen while a movie or episode is open (D8, D17): progress, Resume / Pause
 * (with ‹ Prev and Next › for episodes), Play from start and Mark watched, Audio and
 * Subtitles, then subtitle timing. The corner buttons are drawn by the bottom root.
 */
@Composable
fun PlayerBottom(ui: PlayerUi, volume: Int, onPress: (PlayerButton) -> Unit, onPlayNow: (Long) -> Unit, onCancelUpNext: () -> Unit) {
    val upNext = ui.upNext
    if (upNext != null) {
        UpNextCard(ui, upNext, onPlayNow, onCancelUpNext)
        return
    }
    fun focused(b: PlayerButton) = ui.focus == b
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressBar(if (ui.durationMs > 0) ui.positionMs.toFloat() / ui.durationMs else 0f, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(ui.positionMs), color = Palette.Ink, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (ui.durationMs > 0) "−" + clock(ui.durationMs - ui.positionMs) else "",
                        color = Palette.Muted, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ui.isEpisode) {
                    PanelButton("‹ Prev", Modifier.width(96.dp).alpha(if (ui.hasPrev) 1f else 0.35f), focused = focused(PlayerButton.PREV), enabled = ui.hasPrev) {
                        onPress(PlayerButton.PREV)
                    }
                }
                PanelButton(
                    if (ui.paused) "▶  Resume" else "❚❚  Pause",
                    Modifier.weight(1f), primary = true, focused = focused(PlayerButton.RESUME), keyHint = "A",
                ) { onPress(PlayerButton.RESUME) }
                if (ui.isEpisode) {
                    PanelButton("Next ›", Modifier.width(96.dp).alpha(if (ui.hasNext) 1f else 0.35f), focused = focused(PlayerButton.NEXT), enabled = ui.hasNext) {
                        onPress(PlayerButton.NEXT)
                    }
                }
            }
            VolumeRow(volume, focused(PlayerButton.VOLUME_DOWN), focused(PlayerButton.VOLUME_UP), onPress)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PanelButton("Play from start", Modifier.weight(1f), focused = focused(PlayerButton.FROM_START)) { onPress(PlayerButton.FROM_START) }
                PanelButton("Mark watched", Modifier.weight(1f), focused = focused(PlayerButton.MARK_WATCHED), keyHint = "Y") { onPress(PlayerButton.MARK_WATCHED) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelledButton("Audio", ui.audioLabel, Modifier.weight(1f), focused(PlayerButton.AUDIO)) { onPress(PlayerButton.AUDIO) }
                LabelledButton(
                    "Subtitles", ui.subtitleLabel, Modifier.weight(1f), focused(PlayerButton.SUBTITLES),
                    highlight = ui.subtitleMissing, keyHint = "X",
                ) { onPress(PlayerButton.SUBTITLES) }
            }
            Row(
                Modifier.alpha(if (ui.subtitleOn) 1f else 0.35f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelButton("− 50 ms", Modifier.width(120.dp), focused = focused(PlayerButton.DELAY_MINUS), enabled = ui.subtitleOn) { onPress(PlayerButton.DELAY_MINUS) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SUBTITLE TIMING", color = Palette.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text(
                        (if (ui.subtitleDelayMs > 0) "+" else "") + "${ui.subtitleDelayMs} ms",
                        color = Palette.Ink, fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    )
                }
                PanelButton("+ 50 ms", Modifier.width(120.dp), focused = focused(PlayerButton.DELAY_PLUS), enabled = ui.subtitleOn) { onPress(PlayerButton.DELAY_PLUS) }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 30.dp)) {
            Legend("L2" to "−10 s", "R2" to "+10 s", "B" to "Back")
        }
    }
}

/**
 * − [bar] +. The first 80 % of the bar is the media volume up to 100 %; the last 20 % is the
 * boost to 200 %, in a darker accent past a tick, so it's clear when the sound is boosted.
 */
@Composable
private fun VolumeRow(percent: Int, minusFocused: Boolean, plusFocused: Boolean, onPress: (PlayerButton) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PanelButton("−", Modifier.width(64.dp), focused = minusFocused) { onPress(PlayerButton.VOLUME_DOWN) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (percent > 100) "VOLUME · BOOST" else "VOLUME", color = if (percent > 100) Palette.Accent else Palette.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                Text("$percent %", color = Palette.Ink, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            val normal = (percent.coerceAtMost(100) / 100f) * 0.8f
            val boost = ((percent - 100).coerceAtLeast(0) / 100f) * 0.2f
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Line)) {
                Row(Modifier.fillMaxSize()) {
                    if (normal > 0f) Box(Modifier.fillMaxHeight().weight(normal).background(Palette.Accent))
                    if (boost > 0f) Box(Modifier.fillMaxHeight().weight(boost).background(Color(0xFFC77B12)))
                    if (normal + boost < 1f) Box(Modifier.fillMaxHeight().weight(1f - normal - boost))
                }
                // 100 % mark
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.8f)) { Box(Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight().background(Palette.Ink)) }
            }
        }
        PanelButton("+", Modifier.width(64.dp), focused = plusFocused) { onPress(PlayerButton.VOLUME_UP) }
    }
}

/** A button with a small heading over its value: Audio and Subtitles. */
@Composable
private fun LabelledButton(
    heading: String,
    value: String,
    modifier: Modifier,
    focused: Boolean,
    highlight: Boolean = false,
    keyHint: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .background(Palette.Panel, shape)
            .border(2.dp, if (focused || highlight) Palette.Accent else Palette.Line, shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(heading.uppercase(), color = Palette.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(value, color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (keyHint != null) KeyHint(keyHint)
    }
}

/** Up next, near the end of an episode: the next one plays when the ring runs out (D23). */
@Composable
private fun UpNextCard(ui: PlayerUi, upNext: UpNext, onPlayNow: (Long) -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("UP NEXT", color = Palette.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(ui.backdropPath ?: ui.posterPath, ui.groupKey, Modifier.width(260.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(upNext.label, color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(upNext.title, color = Palette.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Countdown(upNext.secondsLeft, total = 10)
            PanelButton("Play now", Modifier.weight(1f), primary = true, keyHint = "A") { onPlayNow(upNext.fileId) }
            PanelButton("Cancel", Modifier.weight(1f), keyHint = "B") { onCancel() }
        }
    }
}

@Composable
private fun Countdown(secondsLeft: Int, total: Int) {
    Box(
        Modifier.size(64.dp).drawBehind {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(Palette.Line, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(Palette.Accent, -90f, 360f * secondsLeft / total, false, Offset(inset, inset), arcSize, style = stroke)
        },
        contentAlignment = Alignment.Center,
    ) {
        Text("$secondsLeft", color = Palette.Ink, fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

/** 1:02:05 or 4:09. */
internal fun clock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
