package com.msharwaan.exoplayer.show

import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.input.PadAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where you are inside a show: its season cards, or one season's episode cards. */
data class ShowView(
    val level: Level = Level.SEASONS,
    /** Focused season card; -1 = none, so the top screen shows the show itself. */
    val seasonFocus: Int = -1,
    /** The season whose episodes are open (an index into [ShowData.seasons]). */
    val season: Int = 0,
    /** Focused episode card; -1 = none, so the top screen shows the season. */
    val episodeFocus: Int = -1,
) {
    enum class Level { SEASONS, EPISODES }
}

/**
 * The series and anime screens (D14, D15, D21): details → season cards → episode cards → play.
 * A goes one step forward, B one step back (D20).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShowModel(private val scope: CoroutineScope, db: AppDatabase, private val onNotice: (String) -> Unit) {

    private val groupKey = MutableStateFlow<String?>(null)

    val data: StateFlow<ShowData?> = groupKey.flatMapLatest { key ->
        if (key == null) return@flatMapLatest flowOf(null)
        val dao = db.library()
        combine(
            dao.observeTitle(key), dao.observeFilesOf(key), dao.observePlayback(), dao.observeSeasons(key), dao.observeEpisodes(key),
        ) { title, files, playback, seasons, episodes ->
            title?.let { ShowData.build(it, files, playback, seasons, episodes) }
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    private val _view = MutableStateFlow(ShowView())
    val view: StateFlow<ShowView> = _view.asStateFlow()

    /** Set by [open]: once the data arrives, land on the episode to continue. */
    private var pendingFile: Long? = null

    init {
        scope.launch {
            data.collect { d ->
                if (d == null || d.title.groupKey != groupKey.value) return@collect
                val target = pendingFile
                if (target != null && focusFile(d, target)) pendingFile = null else skipSingleSeason(d)
            }
        }
    }

    /** Opens a show. [openEpisodes]: start on the episode cards with the next-up episode focused (D14). */
    fun open(key: String, openEpisodes: Boolean, nextFileId: Long?) {
        if (groupKey.value != key) {
            groupKey.value = key
            _view.value = ShowView()
        }
        if (openEpisodes && nextFileId != null) {
            pendingFile = nextFileId
            data.value?.takeIf { it.title.groupKey == key }?.let { if (focusFile(it, nextFileId)) pendingFile = null }
        } else {
            data.value?.takeIf { it.title.groupKey == key }?.let(::skipSingleSeason)
        }
    }

    /** Back from the player: focus the episode that was playing (Next › may have moved it on). */
    fun returnedFrom(fileId: Long) {
        val d = data.value ?: return
        focusFile(d, fileId)
    }

    private fun focusFile(d: ShowData, fileId: Long): Boolean {
        d.seasons.forEachIndexed { si, s ->
            val ei = s.episodes.indexOfFirst { it.fileId == fileId }
            if (ei >= 0) {
                _view.value = ShowView(ShowView.Level.EPISODES, seasonFocus = si, season = si, episodeFocus = ei)
                return true
            }
        }
        return false
    }

    private fun skipSingleSeason(d: ShowData) {
        if (d.singleSeason && _view.value.level == ShowView.Level.SEASONS) _view.update { it.copy(level = ShowView.Level.EPISODES, season = 0) }
    }

    /** Returns the file A plays, [Result.Used], or [Result.NotUsed] (B on the first screen: leave the show). */
    fun onAction(action: PadAction): Result {
        val d = data.value ?: return if (action == PadAction.B) Result.NotUsed else Result.Used
        skipSingleSeason(d)
        val v = _view.value
        return when (v.level) {
            ShowView.Level.SEASONS -> seasonsAction(d, v, action)
            ShowView.Level.EPISODES -> episodesAction(d, v, action)
        }
    }

    private fun seasonsAction(d: ShowData, v: ShowView, action: PadAction): Result {
        val n = d.seasons.size
        when (action) {
            PadAction.LEFT -> moveSeason(v, -1, n)
            PadAction.RIGHT -> moveSeason(v, 1, n)
            PadAction.UP -> moveSeason(v, -SEASON_COLUMNS, n)
            PadAction.DOWN -> moveSeason(v, SEASON_COLUMNS, n)
            PadAction.A -> if (v.seasonFocus < 0) moveSeason(v, 0, n) else openSeason(v.seasonFocus)
            PadAction.B -> if (v.seasonFocus >= 0) _view.update { it.copy(seasonFocus = -1) } else return Result.NotUsed
            else -> return Result.NotUsed
        }
        return Result.Used
    }

    private fun episodesAction(d: ShowData, v: ShowView, action: PadAction): Result {
        val episodes = d.seasons.getOrNull(v.season)?.episodes.orEmpty()
        val n = episodes.size
        when (action) {
            PadAction.LEFT -> moveEpisode(v, -1, n)
            PadAction.RIGHT -> moveEpisode(v, 1, n)
            PadAction.UP -> moveEpisode(v, -EPISODE_COLUMNS, n)
            PadAction.DOWN -> moveEpisode(v, EPISODE_COLUMNS, n)
            PadAction.A -> {
                if (v.episodeFocus < 0) { moveEpisode(v, 0, n); return Result.Used }
                return play(episodes.getOrNull(v.episodeFocus))
            }
            PadAction.B -> when {
                v.episodeFocus >= 0 && d.singleSeason -> _view.update { it.copy(episodeFocus = -1) }
                // Several seasons: back to the season cards, with this season still focused.
                !d.singleSeason -> _view.value = ShowView(ShowView.Level.SEASONS, seasonFocus = v.season)
                else -> return Result.NotUsed
            }
            else -> return Result.NotUsed
        }
        return Result.Used
    }

    /** A tap on a season card: the first tap focuses it, a second tap opens it. */
    fun onSeasonTapped(index: Int) {
        if (_view.value.seasonFocus == index) openSeason(index) else _view.update { it.copy(seasonFocus = index) }
    }

    /** A tap on an episode card: the first tap focuses it, a second tap plays it. */
    fun onEpisodeTapped(index: Int): Result {
        val v = _view.value
        if (v.episodeFocus != index) { _view.update { it.copy(episodeFocus = index) }; return Result.Used }
        return play(data.value?.seasons?.getOrNull(v.season)?.episodes?.getOrNull(index))
    }

    private fun play(e: EpisodeEntry?): Result {
        val id = e?.fileId
        if (id == null) {
            if (e != null) onNotice("That episode isn't on the SD card.")
            return Result.Used
        }
        return Result.Play(id)
    }

    private fun openSeason(index: Int) {
        _view.update { it.copy(level = ShowView.Level.EPISODES, season = index, seasonFocus = index, episodeFocus = -1) }
    }

    private fun moveSeason(v: ShowView, step: Int, n: Int) {
        if (n == 0) return
        if (v.seasonFocus < 0) { _view.update { it.copy(seasonFocus = 0) }; return }
        val next = v.seasonFocus + step
        if (next in 0 until n) _view.update { it.copy(seasonFocus = next) }
    }

    private fun moveEpisode(v: ShowView, step: Int, n: Int) {
        if (n == 0) return
        if (v.episodeFocus < 0) { _view.update { it.copy(episodeFocus = 0) }; return }
        val next = v.episodeFocus + step
        if (next in 0 until n) _view.update { it.copy(episodeFocus = next) }
    }

    sealed interface Result {
        data object Used : Result
        data object NotUsed : Result
        data class Play(val fileId: Long) : Result
    }

    companion object {
        const val SEASON_COLUMNS = 4
        /** Episode cards 3 across (D15). */
        const val EPISODE_COLUMNS = 3
    }
}
