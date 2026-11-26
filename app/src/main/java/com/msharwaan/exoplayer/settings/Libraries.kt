package com.msharwaan.exoplayer.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.library.LibraryPrefs
import com.msharwaan.exoplayer.library.LibrarySpec
import com.msharwaan.exoplayer.library.Passkey
import com.msharwaan.exoplayer.library.PasskeyEntry
import com.msharwaan.exoplayer.ui.PasskeyPad
import com.msharwaan.exoplayer.ui.KeyboardState
import com.msharwaan.exoplayer.ui.KeyboardView
import com.msharwaan.exoplayer.ui.Legend
import com.msharwaan.exoplayer.ui.PanelButton
import com.msharwaan.exoplayer.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of a library's page. */
sealed interface LibraryRow {
    data object Name : LibraryRow
    data object Use : LibraryRow
    data class Folder(val uri: Uri) : LibraryRow
    data object AddFolder : LibraryRow
    /** Set or change the passkey (D56). */
    data object Passkey : LibraryRow
    data object RemovePasskey : LibraryRow
    data object Delete : LibraryRow
}

/** What a passkey being entered is for. */
enum class PasskeyStep { UNLOCK, SET, CONFIRM }

data class LibrariesUi(
    /** Null: the list of libraries. Otherwise the library whose page is open. */
    val open: Long? = null,
    val focus: Int = 0,
    /** Typing a library's name. */
    val naming: KeyboardState? = null,
    /** Delete was pressed once: the next A deletes. */
    val confirmDelete: Boolean = false,
    /** A passkey being entered for [passkeyFor] (D56). */
    val passkey: PasskeyEntry? = null,
    val passkeyFor: Long? = null,
    val passkeyStep: PasskeyStep = PasskeyStep.UNLOCK,
    /** X was pressed once on a locked library in the list: the next X deletes it. */
    val confirmForget: Boolean = false,
)

/**
 * Settings › Libraries (D52): make libraries ("Entertainment", "Work"…), name them, give them
 * folders, pick the one Browsing shows, or delete one (its files stay on the card).
 */
class LibrariesModel(
    scope: CoroutineScope,
    private val db: AppDatabase,
    private val prefs: LibraryPrefs,
    /** Opens Android's folder picker for this library. */
    private val onAddFolder: (Long) -> Unit,
    /** Makes a library the one Browsing shows; returns its name. */
    private val onUse: (Long) -> Unit,
    /** A library's folders changed: read them again if Browsing shows it. */
    private val onFoldersChanged: (Long) -> Unit,
    private val onNotice: (String) -> Unit,
    /** After a library is deleted: let go of its titles, artwork and history (D54). */
    private val cleanup: suspend () -> Unit = { db.library().deleteOrphanTitles() },
) {
    private val _ui = MutableStateFlow(LibrariesUi())
    val ui: StateFlow<LibrariesUi> = _ui.asStateFlow()

    val libraries: StateFlow<List<LibrarySpec>> get() = prefs.libraries
    val activeId: StateFlow<Long> get() = prefs.activeId

    /** How many videos each library holds, for the list. */
    val counts: StateFlow<Map<Long, Int>> = db.library().observeFiles()
        .map { files -> files.filter { it.kind != "EXTRA" }.groupingBy { it.libraryId }.eachCount() }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val scope = scope

    /** Locked libraries opened here with their passkey; asked again after leaving Libraries. */
    private val unlocked = mutableSetOf<Long>()

    /** The app left the screen: an open locked library's page closes (D56). */
    fun lockAgain() {
        unlocked.clear()
        val ui = _ui.value
        if (ui.passkey != null || ui.open?.let { prefs.library(it)?.locked } == true) reset()
    }

    /** Opening Libraries starts on the list, on the library Browsing shows. */
    fun reset() {
        unlocked.clear()
        _ui.value = LibrariesUi(focus = prefs.libraries.value.indexOfFirst { it.id == prefs.activeId.value }.coerceAtLeast(0))
    }

    /** The rows of a library's page: Use only when it isn't the one shown, Delete only when it isn't the last. */
    fun rowsOf(id: Long): List<LibraryRow> {
        val lib = prefs.library(id) ?: return emptyList()
        return listOfNotNull(
            LibraryRow.Name,
            LibraryRow.Use.takeIf { id != prefs.activeId.value },
        ) + lib.folders.map { LibraryRow.Folder(it) } + listOfNotNull(
            LibraryRow.AddFolder,
            LibraryRow.Passkey,
            LibraryRow.RemovePasskey.takeIf { lib.locked },
            LibraryRow.Delete.takeIf { prefs.libraries.value.size > 1 },
        )
    }

    /** Returns false when the button isn't used here: B on the list leaves Libraries. */
    fun onAction(action: PadAction): Boolean {
        val ui = _ui.value
        ui.naming?.let { return nameAction(ui, it, action) }
        ui.passkey?.let { passkeyAction(ui, it, action); return true }
        val open = ui.open
        if (open == null) {
            val n = prefs.libraries.value.size + 1 // + "New library"
            when (action) {
                PadAction.UP -> _ui.update { it.copy(focus = (it.focus - 1).coerceAtLeast(0), confirmForget = false) }
                PadAction.DOWN -> _ui.update { it.copy(focus = (it.focus + 1).coerceAtMost(n - 1), confirmForget = false) }
                PadAction.A -> activateInList(ui.focus)
                // Forgot the passkey: X twice deletes the library without it; its videos stay (D56).
                PadAction.X -> prefs.libraries.value.getOrNull(ui.focus)?.takeIf { it.locked }?.let { lib ->
                    if (!ui.confirmForget) _ui.update { it.copy(confirmForget = true) } else deleteLibrary(lib.id)
                } ?: return false
                else -> return false
            }
            return true
        }
        val rows = rowsOf(open)
        when (action) {
            PadAction.UP -> _ui.update { it.copy(focus = (it.focus - 1).coerceAtLeast(0), confirmDelete = false) }
            PadAction.DOWN -> _ui.update { it.copy(focus = (it.focus + 1).coerceAtMost(rows.size - 1), confirmDelete = false) }
            PadAction.A -> rows.getOrNull(ui.focus)?.let { activate(open, it) }
            PadAction.X -> (rows.getOrNull(ui.focus) as? LibraryRow.Folder)?.let { removeFolder(open, it.uri) }
            // B steps back from a library's page to the list (D20).
            PadAction.B -> _ui.value = LibrariesUi(focus = prefs.libraries.value.indexOfFirst { it.id == open }.coerceAtLeast(0))
            else -> return false
        }
        return true
    }

    /** A tap: the first focuses a row, a second opens it. */
    fun onRowTapped(index: Int) {
        if (_ui.value.focus != index) { _ui.update { it.copy(focus = index, confirmDelete = false) }; return }
        val open = _ui.value.open
        if (open == null) activateInList(index) else rowsOf(open).getOrNull(index)?.let { activate(open, it) }
    }

    fun onKeyTapped(row: Int, col: Int) {
        val k = _ui.value.naming ?: return
        _ui.update { it.copy(naming = k.focus(row, col)) }
        pressKey()
    }

    private fun activateInList(index: Int) {
        val lib = prefs.libraries.value.getOrNull(index)
        if (lib != null && lib.locked && lib.id !in unlocked) {
            _ui.value = LibrariesUi(focus = index, passkey = PasskeyEntry(), passkeyFor = lib.id, passkeyStep = PasskeyStep.UNLOCK)
            return
        }
        if (lib != null) {
            _ui.value = LibrariesUi(open = lib.id)
            return
        }
        // "New library": made, opened, and named straight away.
        val made = prefs.create()
        _ui.value = LibrariesUi(open = made.id, naming = KeyboardState(text = made.name))
    }

    private fun activate(id: Long, row: LibraryRow) {
        when (row) {
            LibraryRow.Name -> _ui.update { it.copy(naming = KeyboardState(text = prefs.library(id)?.name.orEmpty())) }
            LibraryRow.Use -> {
                onUse(id)
                _ui.update { it.copy(focus = 0) }
            }
            is LibraryRow.Folder -> onNotice("X removes this folder from the library. The videos stay on the card.")
            LibraryRow.AddFolder -> onAddFolder(id)
            LibraryRow.Passkey -> if (prefs.library(id)?.locked == true || prefs.canLock(id)) {
                _ui.update { it.copy(passkey = PasskeyEntry(), passkeyFor = id, passkeyStep = PasskeyStep.SET) }
            } else {
                onNotice("One library always stays without a passkey. Make another library first.")
            }
            LibraryRow.RemovePasskey -> {
                prefs.clearLock(id)
                _ui.update { it.copy(focus = it.focus.coerceAtMost(rowsOf(id).size - 1)) }
                onNotice("Passkey removed. ${prefs.library(id)?.name} opens without one.")
            }
            LibraryRow.Delete -> if (!_ui.value.confirmDelete) {
                _ui.update { it.copy(confirmDelete = true) }
            } else {
                deleteLibrary(id)
            }
        }
    }

    private fun deleteLibrary(id: Long) {
        val name = prefs.library(id)?.name
        if (prefs.delete(id)) {
            scope.launch {
                db.library().deleteFilesIn(id)
                cleanup()
            }
            onNotice("$name deleted. Its videos are still on the card.")
            reset()
        } else {
            onNotice("One library always stays without a passkey, so $name can't go.")
            _ui.update { it.copy(confirmDelete = false, confirmForget = false) }
        }
    }

    /** A button while a passkey is entered: to open a locked library, or to set one. */
    private fun passkeyAction(ui: LibrariesUi, entry: PasskeyEntry, action: PadAction) {
        val id = ui.passkeyFor ?: return
        val (next, result) = entry.press(action)
        when (result) {
            PasskeyEntry.Result.Typing -> _ui.update { it.copy(passkey = next) }
            PasskeyEntry.Result.Cancel -> _ui.update { it.copy(passkey = null, passkeyFor = null) }
            is PasskeyEntry.Result.Done -> when (ui.passkeyStep) {
                PasskeyStep.UNLOCK -> if (prefs.unlocks(id, result.presses)) {
                    unlocked += id
                    _ui.value = LibrariesUi(open = id)
                } else {
                    _ui.update { it.copy(passkey = PasskeyEntry(error = "That's not the passkey. Try again.")) }
                }
                PasskeyStep.SET -> _ui.update { it.copy(passkey = PasskeyEntry(first = result.presses), passkeyStep = PasskeyStep.CONFIRM) }
                PasskeyStep.CONFIRM -> if (result.presses == entry.first) {
                    prefs.setLock(id, result.presses)
                    unlocked += id
                    _ui.update { it.copy(passkey = null, passkeyFor = null) }
                    onNotice("Passkey set. Switching to ${prefs.library(id)?.name} and opening it here will ask for it.")
                } else {
                    _ui.update { it.copy(passkey = PasskeyEntry(error = "The two didn't match. Press the new combo again."), passkeyStep = PasskeyStep.SET) }
                }
            }
        }
    }

    /** A button drawn on the passkey pad was tapped. */
    fun onPasskeyTapped(action: PadAction) { onAction(action) }

    private fun removeFolder(id: Long, uri: Uri) {
        prefs.removeFolder(id, uri)
        val rows = rowsOf(id)
        _ui.update { it.copy(focus = it.focus.coerceAtMost(rows.size - 1)) }
        onFoldersChanged(id)
        onNotice("Folder removed from the library. Its videos stay on the card.")
    }

    private fun nameAction(ui: LibrariesUi, k: KeyboardState, action: PadAction): Boolean {
        when (action) {
            PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT -> _ui.update { it.copy(naming = k.move(action)) }
            PadAction.A -> pressKey()
            PadAction.X -> _ui.update { it.copy(naming = k.delete()) }
            PadAction.Y -> _ui.update { it.copy(naming = k.copy(caps = !k.caps)) }
            PadAction.START -> saveName(ui, k)
            PadAction.B -> _ui.update { it.copy(naming = null) }
            else -> Unit
        }
        return true
    }

    private fun pressKey() {
        val ui = _ui.value
        val k = ui.naming ?: return
        val (next, event) = k.press()
        when (event) {
            KeyboardState.Event.DONE -> saveName(ui, next)
            else -> _ui.update { it.copy(naming = next) }
        }
    }

    private fun saveName(ui: LibrariesUi, k: KeyboardState) {
        val id = ui.open ?: return
        prefs.rename(id, k.text)
        _ui.update { it.copy(naming = null) }
    }
}

/** Top screen: what a library is, or the open library's folders. */
@Composable
fun LibrariesTop(model: LibrariesModel) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val libraries by model.libraries.collectAsStateWithLifecycle()
    val activeId by model.activeId.collectAsStateWithLifecycle()
    val counts by model.counts.collectAsStateWithLifecycle()
    val open = libraries.firstOrNull { it.id == ui.open }
    Column(Modifier.fillMaxSize().background(Palette.Black).padding(horizontal = 64.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("LIBRARIES", color = Palette.Accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        if (open == null) {
            Text("One for films, one for work…", color = Palette.Ink, fontSize = 46.sp, fontWeight = FontWeight.Bold)
            Text(
                "Each library reads its own folders and keeps its own titles, Continue watching and watched marks. " +
                    "Browsing shows one at a time: press L2 + R3 there to switch. A passkey keeps a library private. Deleting a library never touches the videos on the card.",
                color = Palette.Muted, fontSize = 18.sp, lineHeight = 26.sp,
            )
        } else {
            Text(open.name, color = Palette.Ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    "Shown on Browsing".takeIf { open.id == activeId },
                    countText(counts[open.id] ?: 0),
                    if (open.folders.size == 1) "1 folder" else "${open.folders.size} folders",
                ).joinToString(" · "),
                color = Palette.Muted, fontSize = 18.sp,
            )
            if (open.folders.isEmpty()) {
                Text("No folder yet: Add folder picks one on the SD card or the internal storage.", color = Palette.Muted, fontSize = 16.sp)
            }
            open.folders.forEach { Text(SettingsModel.folderName(it), color = Palette.Ink, fontSize = 18.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

private fun countText(n: Int) = if (n == 1) "1 video" else "$n videos"

/** Bottom screen: the list of libraries, or one library's page. */
@Composable
fun LibrariesBottom(model: LibrariesModel) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val libraries by model.libraries.collectAsStateWithLifecycle()
    val activeId by model.activeId.collectAsStateWithLifecycle()
    val counts by model.counts.collectAsStateWithLifecycle()
    ui.naming?.let { NameEditor(model, it); return }
    ui.passkey?.let { entry ->
        val name = libraries.firstOrNull { it.id == ui.passkeyFor }?.name.orEmpty()
        when (ui.passkeyStep) {
            PasskeyStep.UNLOCK -> PasskeyPad("🔒 $name", "Its passkey opens it. Forgot it? B, then X twice on it deletes the library.", entry, onPress = model::onPasskeyTapped)
            PasskeyStep.SET -> PasskeyPad("New passkey · $name", "${Passkey.MIN} to ${Passkey.MAX} presses of the D-pad, A, X and Y, then ▷ Start.", entry, onPress = model::onPasskeyTapped)
            PasskeyStep.CONFIRM -> PasskeyPad("New passkey · $name", "Once more, to be sure.", entry, onPress = model::onPasskeyTapped)
        }
        return
    }
    val open = ui.open?.let { id -> libraries.firstOrNull { it.id == id } }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(open?.name ?: "Libraries", color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val focusedLocked = open == null && libraries.getOrNull(ui.focus)?.locked == true
            Legend(*listOfNotNull("A" to "Open", ("X" to "Remove folder").takeIf { open != null }, ("X" to "Delete").takeIf { focusedLocked }, "B" to "Back").toTypedArray())
        }
        val labels: List<Pair<String, String?>> = if (open == null) {
            libraries.mapIndexed { i, lib ->
                when {
                    lib.locked && ui.confirmForget && i == ui.focus -> "X again deletes ${lib.name}" to "Videos stay on the card"
                    lib.locked -> "🔒 ${lib.name}" to listOfNotNull("Shown".takeIf { lib.id == activeId }, "Passkey").joinToString(" · ")
                    else -> lib.name to listOfNotNull("Shown".takeIf { lib.id == activeId }, countText(counts[lib.id] ?: 0)).joinToString(" · ")
                }
            } +
                ("＋ New library" to null)
        } else {
            model.rowsOf(open.id).map { row ->
                when (row) {
                    LibraryRow.Name -> "Name" to open.name
                    LibraryRow.Use -> "Show this library on Browsing" to null
                    is LibraryRow.Folder -> SettingsModel.folderName(row.uri) to "Folder"
                    LibraryRow.AddFolder -> "＋ Add folder" to null
                    LibraryRow.Passkey -> (if (open.locked) "Change passkey" else "Set passkey") to (if (open.locked) "On" else "Off")
                    LibraryRow.RemovePasskey -> "Remove passkey" to null
                    LibraryRow.Delete -> (if (ui.confirmDelete) "Press A again to delete" else "Delete library") to "Videos stay on the card"
                }
            }
        }
        val list = rememberLazyListState()
        LaunchedEffect(ui.focus, ui.open) { list.animateScrollToItem(ui.focus.coerceAtLeast(0)) }
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(labels) { i, (text, note) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanelButton(text, Modifier.weight(1f), focused = i == ui.focus) { model.onRowTapped(i) }
                    note?.let {
                        Text(it, color = Palette.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NameEditor(model: LibrariesModel, k: KeyboardState) {
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("LIBRARY NAME", color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Palette.Panel, shape).border(2.dp, Palette.Accent, shape).padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(k.text + "▏", color = Palette.Ink, fontSize = 18.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        KeyboardView(k, onTap = model::onKeyTapped)
        Box(Modifier.weight(1f))
        Legend("A" to "Type", "X" to "Delete", "Y" to "Caps", "▷" to "Save", "B" to "Cancel")
    }
}
