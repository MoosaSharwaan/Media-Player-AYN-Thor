package com.msharwaan.exoplayer.settings

import android.net.Uri
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.ui.KeyboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One line in Settings, in order. [secret] rows open the keyboard. */
enum class SettingRow(val title: String, val group: String, val secret: Secret?, val help: String) {
    TMDB_KEY(
        "TMDB API key", "Online", Secret.TMDB_KEY,
        "Needed for Fetch info: posters, backdrops, plots, seasons and episodes. Free: make an account at themoviedb.org, " +
            "then Settings › API › create a key. The API Key (32 letters and numbers) or the longer Read Access Token both work.",
    ),
    FANART_KEY(
        "fanart.tv API key", "Online", Secret.FANART_KEY,
        "Optional. Better logos and backdrops when TMDB's are missing. Free personal key: fanart.tv › your profile › API.",
    ),
    OPENSUBTITLES_KEY(
        "OpenSubtitles API key", "Online", Secret.OPENSUBTITLES_KEY,
        "Needed for Find subtitles. Free: opensubtitles.com › your profile › API consumers › create a consumer, then copy its API key.",
    ),
    OPENSUBTITLES_USER(
        "OpenSubtitles username", "Online", Secret.OPENSUBTITLES_USER,
        "Your opensubtitles.com account. Logged in, you get about 20 subtitle downloads a day instead of about 5.",
    ),
    OPENSUBTITLES_PASSWORD(
        "OpenSubtitles password", "Online", Secret.OPENSUBTITLES_PASSWORD,
        "Stored encrypted on this device only, and sent only to OpenSubtitles when you press Find subtitles.",
    ),
    FOLDER("Video folder", "Library", null, "The folder the library is read from, usually on the SD card. A picks a different one."),
    ABOUT(
        "About", "About", null,
        "Exo Player, a personal video player for the AYN Thor. Licence GPL-3.0. " +
            "This product uses the TMDB API but is not endorsed or certified by TMDB.",
    ),
}

/** Settings as both screens show them. Nothing secret is ever shown in full outside the editor. */
data class SettingsUi(
    val focus: Int = 0,
    /** What each row shows on the right: "Set · …3f9a", "Not set", the folder name. */
    val summaries: Map<SettingRow, String> = emptyMap(),
    val editing: Editing? = null,
) {
    data class Editing(val row: SettingRow, val keyboard: KeyboardState, val reveal: Boolean)
}

/**
 * Settings (opened with Start or ⚙ on Browsing). API keys and the OpenSubtitles login are
 * typed with the app's own keyboard, or pasted, which is quicker for a long key.
 */
class SettingsModel(
    private val secrets: Secrets,
    private val folder: () -> Uri?,
    private val readClipboard: () -> String?,
    private val onChooseFolder: () -> Unit,
    private val onNotice: (String) -> Unit,
) {
    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    init { refresh() }

    /** Re-reads what's saved, e.g. when Settings opens or a folder was picked. */
    fun refresh() {
        _ui.update { it.copy(summaries = SettingRow.entries.associateWith(::summary)) }
    }

    private fun summary(row: SettingRow): String = when (row) {
        SettingRow.FOLDER -> folder()?.let(::folderName) ?: "Not chosen"
        SettingRow.ABOUT -> ""
        SettingRow.OPENSUBTITLES_USER -> secrets[Secret.OPENSUBTITLES_USER] ?: "Not set"
        SettingRow.OPENSUBTITLES_PASSWORD -> if (secrets[Secret.OPENSUBTITLES_PASSWORD] != null) "Set" else "Not set"
        else -> secrets[row.secret!!]?.let { "Set · …" + it.takeLast(4) } ?: "Not set"
    }

    /** Returns false when the button isn't used here: B on the list leaves Settings. */
    fun onAction(action: PadAction): Boolean {
        val editing = _ui.value.editing
        if (editing != null) return editAction(editing, action)
        val n = SettingRow.entries.size
        when (action) {
            PadAction.UP -> _ui.update { it.copy(focus = (it.focus - 1).coerceAtLeast(0)) }
            PadAction.DOWN -> _ui.update { it.copy(focus = (it.focus + 1).coerceAtMost(n - 1)) }
            PadAction.A -> activate(SettingRow.entries[_ui.value.focus])
            PadAction.X -> clear(SettingRow.entries[_ui.value.focus])
            else -> return false
        }
        return true
    }

    /** A tap on a row: the first focuses it, a second opens it. */
    fun onRowTapped(index: Int) {
        if (_ui.value.focus == index) activate(SettingRow.entries[index]) else _ui.update { it.copy(focus = index) }
    }

    /** A tap on a keyboard key types it, like moving there and pressing A. */
    fun onKeyTapped(row: Int, col: Int) {
        val e = _ui.value.editing ?: return
        val k = e.keyboard.focus(row, col)
        _ui.update { it.copy(editing = e.copy(keyboard = k)) }
        press()
    }

    private fun activate(row: SettingRow) {
        when {
            row.secret != null -> {
                // Keys and the username start with what's saved, so a typo can be fixed; the password starts empty.
                val start = if (row == SettingRow.OPENSUBTITLES_PASSWORD) "" else secrets[row.secret].orEmpty()
                _ui.update { it.copy(editing = SettingsUi.Editing(row, KeyboardState(text = start), reveal = false)) }
            }
            row == SettingRow.FOLDER -> onChooseFolder()
        }
    }

    private fun clear(row: SettingRow) {
        val secret = row.secret ?: return
        if (secrets[secret] == null) return
        secrets[secret] = null
        refresh()
        onNotice("${row.title} removed.")
    }

    private fun editAction(e: SettingsUi.Editing, action: PadAction): Boolean {
        when (action) {
            PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT ->
                _ui.update { it.copy(editing = e.copy(keyboard = e.keyboard.move(action))) }
            PadAction.A -> press()
            PadAction.X -> _ui.update { it.copy(editing = e.copy(keyboard = e.keyboard.delete())) }
            PadAction.Y -> _ui.update { it.copy(editing = e.copy(keyboard = e.keyboard.copy(caps = !e.keyboard.caps))) }
            PadAction.SELECT -> if (e.row == SettingRow.OPENSUBTITLES_PASSWORD) _ui.update { it.copy(editing = e.copy(reveal = !e.reveal)) }
            PadAction.START -> save(e)
            // B steps back out of the editor without saving (D20).
            PadAction.B -> _ui.update { it.copy(editing = null) }
            else -> Unit
        }
        // Every button belongs to the editor while it's open.
        return true
    }

    private fun press() {
        val e = _ui.value.editing ?: return
        val (k, event) = e.keyboard.press()
        when (event) {
            KeyboardState.Event.NONE -> _ui.update { it.copy(editing = e.copy(keyboard = k)) }
            KeyboardState.Event.PASTE -> {
                val clip = readClipboard()?.trim()
                if (clip.isNullOrEmpty()) onNotice("Nothing to paste. Copy the key first, for example in the browser.")
                else _ui.update { it.copy(editing = e.copy(keyboard = k.paste(clip))) }
            }
            KeyboardState.Event.DONE -> save(e)
        }
    }

    private fun save(e: SettingsUi.Editing) {
        val text = e.keyboard.text.trim()
        secrets[e.row.secret!!] = text
        _ui.update { it.copy(editing = null) }
        refresh()
        onNotice(if (text.isEmpty()) "${e.row.title} removed." else "${e.row.title} saved.")
    }

    companion object {
        /** "content://…/tree/primary%3AMovies" → "Movies"; SD cards read "SD card › Movies". */
        fun folderName(uri: Uri): String {
            val doc = Uri.decode(uri.lastPathSegment ?: return uri.toString())
            val volume = doc.substringBefore(':')
            val path = doc.substringAfter(':', "").ifEmpty { "(whole card)" }
            return if (volume == "primary") path else "SD card › $path"
        }
    }
}
