package com.msharwaan.exoplayer.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msharwaan.exoplayer.library.Card

/**
 * The moving picture behind a focused movie or episode (D51). Draw it first: it sits behind the
 * window, so the art drawn after it covers it until [artAlpha] says the video is showing; the
 * details stay on top throughout. Stops when the app leaves the screen or this goes away.
 *
 * Returns how visible the art over it should be: 1 before the first frame, then fading to 0.
 */
@Composable
fun PreviewVideo(preview: Preview?, fileId: Long?): Float {
    if (preview == null) return 1f
    val enabled by preview.prefs.enabled.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val showing = state.isAtLeast(Lifecycle.State.STARTED)
    val target = fileId.takeIf { enabled && showing }
    LaunchedEffect(target) { preview.show(target) }
    DisposableEffect(preview) { onDispose { preview.show(null) } }
    val ready by preview.ready.collectAsStateWithLifecycle()
    val artAlpha by animateFloatAsState(if (target != null && ready == target) 0f else 1f, tween(500), label = "preview")
    if (target != null) VideoSurface(preview.player, Picture.ZOOM)
    return artAlpha
}

/** The file a card's preview plays: movies, Others and Continue watching, not a whole series. */
val Card.previewFileId: Long?
    get() = playFileId.takeIf { kind != "SHOW" || resumeNote != null }
