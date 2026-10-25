package com.msharwaan.exoplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msharwaan.exoplayer.AppViewModel
import com.msharwaan.exoplayer.browse.BrowseBottom
import com.msharwaan.exoplayer.browse.BrowseTop
import com.msharwaan.exoplayer.diagnostics.DiagnosticsBottom
import com.msharwaan.exoplayer.diagnostics.DiagnosticsTop
import com.msharwaan.exoplayer.nav.Route
import com.msharwaan.exoplayer.online.FindSubtitlesBottom
import com.msharwaan.exoplayer.online.FixMatchBottom
import com.msharwaan.exoplayer.online.FixMatchTop
import com.msharwaan.exoplayer.player.PlayerBottom
import com.msharwaan.exoplayer.player.PlayerTop
import com.msharwaan.exoplayer.player.VideoSurface
import com.msharwaan.exoplayer.settings.SettingsBottom
import com.msharwaan.exoplayer.settings.SettingsTop
import com.msharwaan.exoplayer.show.ShowBottom
import com.msharwaan.exoplayer.show.ShowTop
import com.msharwaan.exoplayer.ui.theme.Palette
import kotlinx.coroutines.delay

/** Screens that play video: the corner holds ✎ and ⏻ there (decision D12). */
private fun Route.isVideo() = this is Route.Player

/**
 * The top screen. Display only (rule R11): nothing here takes input.
 *
 * With no bottom screen at all (a phone, or a Thor whose second display isn't detected),
 * the bottom screen's content shows as a panel on the right instead (decisions D19, D29).
 */
@Composable
fun TopRoot(vm: AppViewModel) {
    val stack by vm.navigator.stack.collectAsStateWithLifecycle()
    val setup by vm.displaySetup.collectAsStateWithLifecycle()
    val route = stack.last()
    Box(Modifier.fillMaxSize().background(Palette.Black)) {
        TopContent(vm, route)
        if (setup.bottomDisplayId == null && oneScreenPanelShown(vm, route)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(maxWidth * 0.42f)
                        .background(Color(0xE6000000)),
                ) { BottomContent(vm, route, oneScreen = true) }
            }
        }
    }
}

/**
 * With one screen, the controls cover part of the picture, so during playback they show
 * only while paused or for a few seconds after a button press.
 */
@Composable
private fun oneScreenPanelShown(vm: AppViewModel, route: Route): Boolean {
    if (!route.isVideo()) return true
    val ui by vm.player.ui.collectAsStateWithLifecycle()
    val last by vm.lastInteraction.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(last) {
        now = System.currentTimeMillis()
        delay(PANEL_HIDE_AFTER_MS)
        now = System.currentTimeMillis()
    }
    return ui.paused || ui.upNext != null || now - last < PANEL_HIDE_AFTER_MS
}

private const val PANEL_HIDE_AFTER_MS = 4_000L

/** The bottom screen, shown in the Presentation on the Thor's second display. */
@Composable
fun BottomRoot(vm: AppViewModel) {
    val stack by vm.navigator.stack.collectAsStateWithLifecycle()
    val off by vm.bottomOff.collectAsStateWithLifecycle()
    val route = stack.last()
    Box(Modifier.fillMaxSize().background(Palette.Black)) {
        if (off && route.isVideo()) {
            // Pure black with only ⏻ left; only ⏻ turns it back on (decisions D10, D18).
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                CornerButton("⏻", dim = true) { vm.toggleBottomScreen() }
            }
        } else {
            BottomContent(vm, route, oneScreen = false)
            if (route.isVideo()) {
                // ✎ Fix match next to ⏻ in the bottom-right corner (decisions D11, D12).
                Row(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CornerButton("✎") { vm.openFixMatch() }
                    CornerButton("⏻") { vm.toggleBottomScreen() }
                }
            }
            DimLayer(vm, enabled = route.isVideo())
        }
    }
}

@Composable
private fun TopContent(vm: AppViewModel, route: Route) {
    when (route) {
        Route.Browse -> BrowseTop(vm.browse)
        Route.Diagnostics -> DiagnosticsTop(vm.diagnostics)
        is Route.Show -> ShowTop(vm.show)
        Route.Settings -> SettingsTop(vm.settings)
        // Finding subtitles keeps the film on the top screen.
        is Route.Player, is Route.FindSubtitles -> {
            val ui by vm.player.ui.collectAsStateWithLifecycle()
            val subtitle by vm.player.subtitle.collectAsStateWithLifecycle()
            val settings by vm.player.settings.collectAsStateWithLifecycle()
            PlayerTop(ui, subtitle, settings) { VideoSurface(vm.player.player, settings.picture) }
        }
        is Route.FixMatch -> FixMatchTop(vm.fixMatch)
    }
}

@Composable
private fun BottomContent(vm: AppViewModel, route: Route, oneScreen: Boolean) {
    Box(Modifier.fillMaxSize()) {
        when (route) {
            Route.Browse -> BrowseBottom(vm.browse, onOpen = vm::open, onFetchInfo = vm::fetchInfo, onSettings = vm::openSettings)
            Route.Diagnostics -> DiagnosticsBottom(vm.diagnostics)
            is Route.Show -> {
                ShowBottom(vm.show) { vm.navigator.push(Route.Player(it)) }
                Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { CornerButton("✎") { vm.openFixMatch() } }
            }
            is Route.FixMatch -> FixMatchBottom(vm.fixMatch)
            is Route.FindSubtitles -> FindSubtitlesBottom(vm.findSubtitles)
            Route.Settings -> SettingsBottom(vm.settings)
            is Route.Player -> {
                val ui by vm.player.ui.collectAsStateWithLifecycle()
                val volume by vm.player.volume.percent.collectAsStateWithLifecycle()
                val settings by vm.player.settings.collectAsStateWithLifecycle()
                PlayerBottom(
                    ui, volume, onPress = vm.player::press, onPlayNow = vm.player::playNow, onCancelUpNext = vm.player::cancelUpNext,
                    settings = settings, onSeek = vm.player::seekToFraction, onSetting = vm.player::changeSetting, onCloseSettings = vm.player::closeSettings,
                )
                // One screen: no ⏻ (there is no bottom screen to turn off), ✎ stays.
                if (oneScreen) Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { CornerButton("✎") { vm.openFixMatch() } }
            }
        }
        Notice(vm)
    }
}

/**
 * Dims the bottom screen with a black layer after 10 s untouched during playback (rule R4).
 * Any touch or button wakes it.
 */
@Composable
private fun DimLayer(vm: AppViewModel, enabled: Boolean) {
    val last by vm.lastInteraction.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(last, enabled) {
        now = System.currentTimeMillis()
        if (enabled) {
            delay(DIM_AFTER_MS)
            now = System.currentTimeMillis()
        }
    }
    val dimmed = enabled && now - last >= DIM_AFTER_MS
    val alpha by animateFloatAsState(if (dimmed) 0.82f else 0f, label = "dim")
    if (alpha > 0f) Box(Modifier.fillMaxSize().alpha(alpha).background(Palette.Black))
}

private const val DIM_AFTER_MS = 10_000L

/** A short message along the top of the bottom screen; it goes after a few seconds. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.Notice(vm: AppViewModel) {
    val notice by vm.notice.collectAsStateWithLifecycle()
    val text = notice ?: return
    androidx.compose.material3.Text(
        text,
        color = Palette.Ink,
        fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(12.dp)
            .background(Palette.Panel, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
