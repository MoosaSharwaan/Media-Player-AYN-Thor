package com.msharwaan.exoplayer.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import com.msharwaan.exoplayer.data.SubtitleFile
import com.msharwaan.exoplayer.data.WatchSession
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.library.Library
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything both screens show while a movie or episode is open. */
data class PlayerUi(
    val fileId: Long = 0,
    val groupKey: String = "",
    val isEpisode: Boolean = false,
    val title: String = "",
    val meta: String = "",
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    /** Playing, or about to: false only while buffering, so a stall never flashes the paused screen. */
    val playing: Boolean = false,
    /** Paused by the user: the top screen shows the title's art instead of the picture (D13). */
    val paused: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val audioLabel: String = "—",
    val subtitleLabel: String = "Off",
    /** No subtitles in the file or next to it: the block offers Find subtitles (D8, D9). */
    val subtitleMissing: Boolean = false,
    val subtitleOn: Boolean = false,
    val subtitleDelayMs: Int = 0,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    val focus: PlayerButton = PlayerButton.RESUME,
    val upNext: UpNext? = null,
    /** Already watched: the button offers Mark unwatched. */
    val watched: Boolean = false,
    /** Playback settings open (Start), with this row focused; null when closed. */
    val panelRow: Int? = null,
)

/** The Up next card at the end of an episode: 10 s, then the next one plays (D23). */
data class UpNext(val fileId: Long, val title: String, val label: String, val secondsLeft: Int)

/** Bottom-screen buttons in D-pad order, row by row. */
enum class PlayerButton(val row: Int) {
    PREV(0), RESUME(0), NEXT(0),
    VOLUME_DOWN(1), VOLUME_UP(1),
    FROM_START(2), MARK_WATCHED(2),
    AUDIO(3), SUBTITLES(3),
    DELAY_MINUS(4), DELAY_PLUS(4),
}

/**
 * One player screen for movies and episodes (decisions D8, D13, D17).
 *
 * Saves the position on pause, stop, episode change and once a minute (battery rule 8);
 * marks a file watched at 90 %; never touches the network.
 */
@OptIn(UnstableApi::class)
class PlayerModel(
    private val context: Context,
    private val scope: CoroutineScope,
    private val db: AppDatabase,
    private val holder: PlaybackHolder,
    /** Database writes go here: an app-wide scope, so the last save still runs while the app closes. */
    private val writeScope: CoroutineScope,
    private val onNotice: (String) -> Unit,
    private val onFinished: () -> Unit,
) {
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    /** The subtitle line to draw on the top screen, or null. Changes only when a line changes. */
    private val _subtitle = MutableStateFlow<String?>(null)
    val subtitle: StateFlow<String?> = _subtitle.asStateFlow()

    val player get() = holder.player

    private val prefs = PlaybackPrefs(context)
    private val _settings = MutableStateFlow(prefs.load())
    /** Speed, picture, audio delay, subtitle look, brightness (Start opens them). */
    val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

    /** The volume bar: media volume, then a boost to 200 %. */
    val volume = VolumeControl(context) { player.audioSessionId }

    private var file: MediaFile? = null

    /** The file open right now, or null once the player screen has been left. */
    val openFileId: Long? get() = file?.id
    private var siblings: List<MediaFile> = emptyList()
    private var subtitleChoices: List<SubtitleChoice> = emptyList()
    private var subtitleIndex = 0
    private var ownCues: List<Cue> = emptyList()
    private var mediaCueText: String? = null
    private var tickJob: Job? = null
    private var subtitleJob: Job? = null
    private var sessionStart = 0L
    private var lastSaved = 0L
    private var upNextCancelled = false

    /** A subtitle the user can pick: a file we draw ourselves, a track ExoPlayer decodes, or off. */
    private sealed interface SubtitleChoice {
        val label: String
        data object Off : SubtitleChoice { override val label = "Off" }
        data class OwnFile(val sub: SubtitleFile) : SubtitleChoice { override val label = labelOf(sub) }
        data class Track(val group: Tracks.Group, val index: Int, override val label: String) : SubtitleChoice
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.update { it.copy(playing = isPlaying) }
            if (isPlaying) startTicking() else { tick(); save() }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) = volume.onAudioSessionChanged()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _ui.update { it.copy(paused = !playWhenReady) }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                val d = player.duration
                if (d > 0) {
                    _ui.update { it.copy(durationMs = d) }
                    file?.let { f -> if (f.durationMs != d) scope.launch(Dispatchers.IO) { db.library().setDuration(f.id, d) } }
                }
            }
            if (state == Player.STATE_ENDED) onEnded()
        }

        override fun onTracksChanged(tracks: Tracks) {
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audio.isNotEmpty() && audio.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }) {
                onNotice("This file's audio format can't be played on this device.")
            }
            refreshAudioLabel(tracks)
            refreshSubtitleChoices(tracks)
        }

        override fun onCues(cueGroup: CueGroup) {
            mediaCueText = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n").ifEmpty { null }
            if (subtitleChoices.getOrNull(subtitleIndex) is SubtitleChoice.Track) showMediaCue()
        }

        override fun onPlayerError(error: PlaybackException) {
            onNotice("Can't play this file: ${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}.")
        }
    }

    private val analytics = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long,
        ) {
            // Plan, battery rule 1: say so when a file needs software decoding.
            if (decoderName.startsWith("c2.android") || decoderName.startsWith("OMX.google")) {
                onNotice("Software decoding for this file: it uses more battery.")
            }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime, format: androidx.media3.common.Format, decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) = Unit
    }

    init {
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
    }

    /** Opens [fileId] and plays it from where you stopped (D8). */
    fun open(fileId: Long) {
        if (file?.id == fileId) { player.play(); return }
        scope.launch {
            saveNow()
            val dao = db.library()
            val f = withContext(Dispatchers.IO) { dao.file(fileId) } ?: return@launch
            val title = withContext(Dispatchers.IO) { dao.title(f.groupKey) }
            val group = withContext(Dispatchers.IO) { dao.filesOf(f.groupKey) }
            val state = withContext(Dispatchers.IO) { dao.playback(f.id) }
            val subs = withContext(Dispatchers.IO) { dao.subtitlesOf(f.id) }
            file = f
            upNextCancelled = false
            siblings = if (f.kind == "EPISODE") group.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }, { it.fileName })) else listOf(f)
            val index = siblings.indexOfFirst { it.id == f.id }
            // A show with one episode gets the movie screen (D22).
            val isEpisode = f.kind == "EPISODE" && siblings.size > 1
            val episodeTitle = if (isEpisode) Library.episodeLabel(f) else null
            _ui.value = PlayerUi(
                fileId = f.id,
                groupKey = f.groupKey,
                isEpisode = isEpisode,
                title = if (isEpisode) (title?.name ?: f.title) + " · " + episodeTitle else title?.name ?: f.title,
                meta = listOfNotNull(title?.year?.toString(), title?.genres, title?.runtimeMin?.let(Library::formatRuntime)).joinToString(" · "),
                overview = title?.overview,
                posterPath = title?.posterPath,
                backdropPath = title?.backdropPath,
                durationMs = f.durationMs,
                hasPrev = isEpisode && index > 0,
                hasNext = isEpisode && index < siblings.size - 1,
                subtitleDelayMs = state?.subtitleDelayMs ?: 0,
                watched = state?.watched == true,
            )
            // Audio delay is kept per file; speed carries over from the last film.
            applySettings(_settings.value.copy(audioDelayMs = prefs.audioDelay(f.id)), save = false)
            // Own-drawn files: .srt and .vtt. ExoPlayer decodes .ass/.ssa and subtitles inside the file.
            val sideloaded = subs.filter { it.fileName.endsWith(".ass", true) || it.fileName.endsWith(".ssa", true) }.map { s ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.uri))
                    .setMimeType(MimeTypes.TEXT_SSA).setLanguage(s.language).setLabel(labelOf(s)).build()
            }
            ownSubtitleFiles = subs.filter { it.fileName.endsWith(".srt", true) || it.fileName.endsWith(".vtt", true) }
            ownCues = emptyList()
            subtitleChoices = emptyList()
            subtitleIndex = 0
            val startAt = if (state != null && !state.watched && state.positionMs > 0) state.positionMs else 0L
            player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(f.uri)).setSubtitleConfigurations(sideloaded).build(), startAt)
            player.prepare()
            player.play()
            sessionStart = System.currentTimeMillis()
            refreshSubtitleChoices(player.currentTracks)
        }
    }

    private var ownSubtitleFiles: List<SubtitleFile> = emptyList()

    /** Find subtitles (D9): set by the app, which opens the search. */
    var onFindSubtitles: (Long) -> Unit = {}

    /** A subtitle just downloaded for the open file: add it and switch it on. */
    fun addSubtitle(sub: SubtitleFile) {
        if (sub.mediaFileId != file?.id) return
        ownSubtitleFiles = ownSubtitleFiles + sub
        refreshSubtitleChoices(player.currentTracks)
        val index = subtitleChoices.indexOfFirst { it is SubtitleChoice.OwnFile && it.sub.uri == sub.uri }
        if (index >= 0) { userChoseOff = false; selectSubtitle(index) }
    }

    fun onAction(action: PadAction): Boolean {
        val upNext = _ui.value.upNext
        if (upNext != null) {
            when (action) {
                PadAction.A -> { playNow(upNext.fileId); return true }
                PadAction.B -> { cancelUpNext(); return true }
                else -> Unit
            }
        }
        val panel = _ui.value.panelRow
        if (panel != null) {
            val rows = PlaybackSettings.Row.entries
            when (action) {
                PadAction.UP -> _ui.update { it.copy(panelRow = (panel - 1).coerceAtLeast(0)) }
                PadAction.DOWN -> _ui.update { it.copy(panelRow = (panel + 1).coerceAtMost(rows.size - 1)) }
                PadAction.LEFT -> changeSetting(rows[panel], -1)
                PadAction.RIGHT, PadAction.A -> changeSetting(rows[panel], 1)
                PadAction.L2 -> seekBy(-10_000)
                PadAction.R2 -> seekBy(10_000)
                PadAction.B, PadAction.START -> closeSettings()
                else -> Unit
            }
            return true
        }
        when (action) {
            PadAction.START -> openSettings()
            PadAction.L2 -> seekBy(-10_000)
            PadAction.R2 -> seekBy(10_000)
            PadAction.A -> press(_ui.value.focus)
            PadAction.X -> cycleSubtitles()
            PadAction.Y -> markWatched()
            PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT -> moveFocus(action)
            PadAction.B -> { stop(); return false } // B steps back: the app pops the route
            else -> return false
        }
        return true
    }

    fun press(button: PlayerButton) {
        _ui.update { it.copy(focus = button) }
        when (button) {
            PlayerButton.RESUME -> if (player.playWhenReady) player.pause() else player.play()
            PlayerButton.PREV -> stepEpisode(-1)
            PlayerButton.NEXT -> stepEpisode(1)
            PlayerButton.FROM_START -> { player.seekTo(0); player.play() }
            PlayerButton.MARK_WATCHED -> markWatched()
            PlayerButton.AUDIO -> cycleAudio()
            PlayerButton.SUBTITLES -> if (_ui.value.subtitleMissing) onFindSubtitles(_ui.value.fileId) else cycleSubtitles()
            PlayerButton.DELAY_MINUS -> shiftSubtitles(-50)
            PlayerButton.DELAY_PLUS -> shiftSubtitles(50)
            PlayerButton.VOLUME_DOWN -> volume.down()
            PlayerButton.VOLUME_UP -> volume.up()
        }
    }

    private fun moveFocus(action: PadAction) {
        val ui = _ui.value
        val visible = PlayerButton.entries.filter { b ->
            when (b) {
                PlayerButton.PREV, PlayerButton.NEXT -> ui.isEpisode
                PlayerButton.DELAY_MINUS, PlayerButton.DELAY_PLUS -> ui.subtitleOn
                else -> true
            }
        }
        val current = ui.focus
        val target = when (action) {
            PadAction.LEFT -> visible.lastOrNull { it.row == current.row && it.ordinal < current.ordinal }
            PadAction.RIGHT -> visible.firstOrNull { it.row == current.row && it.ordinal > current.ordinal }
            PadAction.UP -> visible.filter { it.row < current.row }.maxByOrNull { it.row }?.let { r -> visible.first { it.row == r.row } }
            PadAction.DOWN -> visible.filter { it.row > current.row }.minByOrNull { it.row }?.let { r -> visible.first { it.row == r.row } }
            else -> null
        } ?: return
        _ui.update { it.copy(focus = target) }
    }

    private fun seekBy(deltaMs: Long) {
        val d = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, d))
        tick()
    }

    private fun stepEpisode(step: Int) {
        val index = siblings.indexOfFirst { it.id == file?.id }
        siblings.getOrNull(index + step)?.let { open(it.id) }
    }

    /** Mark watched, or unwatched when it already is (Y, or the button). */
    private fun markWatched() {
        val f = file ?: return
        val watched = !_ui.value.watched
        _ui.update { it.copy(watched = watched) }
        writeScope.launch {
            val old = db.library().playback(f.id) ?: PlaybackState(f.id)
            db.library().upsertPlayback(
                if (watched) old.copy(watched = true, finishedCount = old.finishedCount + 1, lastPlayed = System.currentTimeMillis())
                else old.copy(watched = false, lastPlayed = System.currentTimeMillis()),
            )
        }
        onNotice(if (watched) "Marked watched." else "Marked unwatched.")
    }

    // ---- playback settings (Start) ----

    fun openSettings() = _ui.update { it.copy(panelRow = 0) }

    fun closeSettings() = _ui.update { it.copy(panelRow = null) }

    fun changeSetting(row: PlaybackSettings.Row, dir: Int) {
        _ui.update { it.copy(panelRow = PlaybackSettings.Row.entries.indexOf(row)) }
        applySettings(_settings.value.step(row, dir), save = true)
    }

    private fun applySettings(s: PlaybackSettings, save: Boolean) {
        _settings.value = s
        player.setPlaybackSpeed(s.speed)
        holder.audioDelayUs = s.audioDelayMs * 1000L
        if (save) {
            prefs.save(s)
            file?.let { prefs.saveAudioDelay(it.id, s.audioDelayMs) }
        }
    }

    /** A tap or drag on the progress bar: jump to that point. */
    fun seekToFraction(fraction: Float) {
        val d = player.duration.takeIf { it > 0 } ?: return
        player.seekTo((d * fraction.coerceIn(0f, 1f)).toLong())
        tick()
    }

    // ---- audio and subtitles ----

    private fun refreshAudioLabel(tracks: Tracks) {
        val selected = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .firstNotNullOfOrNull { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
        _ui.update { it.copy(audioLabel = selected?.let(::audioLabel) ?: "—") }
    }

    private fun cycleAudio() {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val all = groups.flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }
        if (all.size < 2) return
        val current = all.indexOfFirst { (g, i) -> g.isTrackSelected(i) }
        val (g, i) = all[(current + 1) % all.size]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
    }

    private fun refreshSubtitleChoices(tracks: Tracks) {
        val embedded = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { g ->
            (0 until g.length).filter { g.isTrackSupported(it) }.map { i -> SubtitleChoice.Track(g, i, trackLabel(g.getTrackFormat(i))) }
        }
        val previous = subtitleChoices.getOrNull(subtitleIndex)
        subtitleChoices = listOf(SubtitleChoice.Off) + ownSubtitleFiles.map { SubtitleChoice.OwnFile(it) } + embedded
        if (previous == null || previous == SubtitleChoice.Off && subtitleChoices.size > 1 && _subtitle.value == null && !userChoseOff) {
            // First time: pick the first subtitle there is.
            selectSubtitle(if (subtitleChoices.size > 1) 1 else 0)
        } else {
            subtitleIndex = subtitleChoices.indexOfFirst { it.label == previous.label }.coerceAtLeast(0)
            updateSubtitleUi()
        }
    }

    private var userChoseOff = false

    private fun cycleSubtitles() {
        if (subtitleChoices.size <= 1) return
        val next = (subtitleIndex + 1) % subtitleChoices.size
        userChoseOff = next == 0
        selectSubtitle(next)
    }

    private fun selectSubtitle(index: Int) {
        subtitleIndex = index
        val choice = subtitleChoices.getOrNull(index) ?: SubtitleChoice.Off
        val params = player.trackSelectionParameters.buildUpon()
        if (choice is SubtitleChoice.Track) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.index))
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        }
        player.trackSelectionParameters = params.build()
        ownCues = emptyList()
        if (choice is SubtitleChoice.OwnFile) {
            scope.launch {
                ownCues = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(Uri.parse(choice.sub.uri))?.use { SubtitleText.parse(it.readBytes().decodeToString()) }
                    }.getOrNull().orEmpty()
                }
                runSubtitleClock()
            }
        }
        updateSubtitleUi()
        runSubtitleClock()
    }

    private fun updateSubtitleUi() {
        val choice = subtitleChoices.getOrNull(subtitleIndex) ?: SubtitleChoice.Off
        val none = subtitleChoices.size <= 1
        _ui.update {
            it.copy(
                subtitleLabel = if (none) "None in file · Find subtitles" else choice.label,
                subtitleMissing = none,
                subtitleOn = choice != SubtitleChoice.Off,
            )
        }
        if (choice == SubtitleChoice.Off) _subtitle.value = null
    }

    private fun shiftSubtitles(stepMs: Int) {
        if (!_ui.value.subtitleOn) return
        val choice = subtitleChoices.getOrNull(subtitleIndex)
        val next = _ui.value.subtitleDelayMs + stepMs
        if (choice is SubtitleChoice.Track && next < 0) {
            onNotice("Earlier timing works with subtitle files (.srt, .vtt). This one can only be moved later.")
            return
        }
        _ui.update { it.copy(subtitleDelayMs = next) }
        runSubtitleClock()
        save()
    }

    /** Redraws own-file subtitles only when the line changes, not every frame (battery rule 6). */
    private fun runSubtitleClock() {
        subtitleJob?.cancel()
        val choice = subtitleChoices.getOrNull(subtitleIndex)
        if (choice !is SubtitleChoice.OwnFile) { if (choice is SubtitleChoice.Track) showMediaCue(); return }
        subtitleJob = scope.launch {
            while (isActive) {
                val delayMs = _ui.value.subtitleDelayMs.toLong()
                val pos = player.currentPosition
                _subtitle.value = SubtitleText.at(ownCues, pos, delayMs)
                val wait = if (player.isPlaying) (SubtitleText.nextChange(ownCues, pos, delayMs) ?: 1_000L).coerceIn(20L, 1_000L) else 500L
                delay(wait)
            }
        }
    }

    /** Subtitles decoded by ExoPlayer, shown after the chosen delay (later only). */
    private fun showMediaCue() {
        val text = mediaCueText
        val delayMs = _ui.value.subtitleDelayMs.toLong()
        if (delayMs <= 0) { _subtitle.value = text; return }
        scope.launch { delay(delayMs); if (subtitleChoices.getOrNull(subtitleIndex) is SubtitleChoice.Track) _subtitle.value = text }
    }

    // ---- clock, saving and the end of a file ----

    /** Updates the bottom screen once a second, never every frame (battery rule 5). */
    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive && player.isPlaying) {
                tick()
                if (System.currentTimeMillis() - lastSaved > 60_000) save()
                checkUpNext()
                delay(1_000)
            }
        }
    }

    private fun tick() {
        _ui.update { it.copy(positionMs = player.currentPosition, durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs) }
    }

    private fun checkUpNext() {
        val ui = _ui.value
        if (!ui.hasNext || upNextCancelled || ui.durationMs <= 0) return
        val left = ui.durationMs - ui.positionMs
        if (left > UP_NEXT_BEFORE_END_MS) return
        if (ui.upNext == null) {
            val index = siblings.indexOfFirst { it.id == ui.fileId }
            val next = siblings.getOrNull(index + 1) ?: return
            _ui.update { it.copy(upNext = UpNext(next.id, next.title, Library.episodeLabel(next), 10)) }
        } else {
            val s = ui.upNext.secondsLeft - 1
            if (s <= 0) playNow(ui.upNext.fileId) else _ui.update { it.copy(upNext = ui.upNext.copy(secondsLeft = s)) }
        }
    }

    /** Up next: Play now (A). */
    fun playNow(fileId: Long) {
        _ui.update { it.copy(upNext = null) }
        open(fileId)
    }

    /** Up next: Cancel (B). The episode plays to its end and the player stays open. */
    fun cancelUpNext() {
        upNextCancelled = true
        _ui.update { it.copy(upNext = null) }
    }

    private fun onEnded() {
        save()
        val upNext = _ui.value.upNext
        when {
            upNext != null -> playNow(upNext.fileId)
            _ui.value.hasNext && !upNextCancelled -> stepEpisode(1)
            else -> onFinished()
        }
    }

    /** Pauses and saves; the Player route is being left. */
    fun stop() {
        player.pause()
        saveNow()
        file = null
    }

    /** Leaving the app, the screen going off or the Thor sleeping: pause and save straight away. */
    fun pause() {
        if (player.playWhenReady) player.pause()
        saveNow()
    }

    private fun save() = saveNow()

    /** The app is closing for good. */
    fun release() = volume.release(context)

    private fun saveNow() {
        val f = file ?: return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it > 0 } ?: f.durationMs
        val now = System.currentTimeMillis()
        val delayMs = _ui.value.subtitleDelayMs
        val watchedNow = dur > 0 && pos >= dur * Library.WATCHED_AT
        val session = if (sessionStart > 0) WatchSession(mediaFileId = f.id, started = sessionStart, ended = now, secondsWatched = (now - sessionStart) / 1000) else null
        sessionStart = if (player.isPlaying) now else 0
        lastSaved = now
        writeScope.launch {
            val dao = db.library()
            val old = dao.playback(f.id) ?: PlaybackState(f.id)
            dao.upsertPlayback(
                old.copy(
                    positionMs = pos, durationMs = dur, lastPlayed = now, subtitleDelayMs = delayMs,
                    watched = old.watched || watchedNow,
                    finishedCount = if (watchedNow && !old.watched) old.finishedCount + 1 else old.finishedCount,
                ),
            )
            if (session != null && session.secondsWatched > 0) dao.insertSession(session)
        }
    }

    companion object {
        /** The Up next card appears this long before the end, if no credits marker is known. */
        const val UP_NEXT_BEFORE_END_MS = 30_000L

        private fun labelOf(s: SubtitleFile): String =
            listOfNotNull(s.language?.uppercase(), s.fileName.substringAfterLast('.').uppercase()).joinToString(" · ")

        private fun trackLabel(f: androidx.media3.common.Format): String =
            listOfNotNull(f.label, f.language?.uppercase()).distinct().joinToString(" · ").ifEmpty { "Embedded" }

        private fun audioLabel(f: androidx.media3.common.Format): String {
            val lang = f.language?.uppercase()
            val ch = when (f.channelCount) {
                1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"
                else -> if (f.channelCount > 0) "${f.channelCount} ch" else null
            }
            return listOfNotNull(lang, ch).joinToString(" · ").ifEmpty { "Audio" }
        }
    }
}
