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
    data object Delete : LibraryRow
}

data class LibrariesUi(
    /** Null: the list of libraries. Otherwise the library whose page is open. */
    val open: Long? = null,
    val focus: Int = 0,
    /** Typing a library's name. */
    val naming: KeyboardState? = null,
    /** Delete was pressed once: the next A deletes. */
    val confirmDelete: Boolean = false,
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

    /** Opening Libraries starts on the list, on the library Browsing shows. */
    fun reset() {
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
            LibraryRow.Delete.takeIf { prefs.libraries.value.size > 1 },
        )
    }

    /** Returns false when the button isn't used here: B on the list leaves Libraries. */
    fun onAction(action: PadAction): Boolean {
        val ui = _ui.value
        ui.naming?.let { return nameAction(ui, it, action) }
        val open = ui.open
        if (open == null) {
            val n = prefs.libraries.value.size + 1 // + "New library"
            when (action) {
                PadAction.UP -> _ui.update { it.copy(focus = (it.focus - 1).coerceAtLeast(0)) }
                PadAction.DOWN -> _ui.update { it.copy(focus = (it.focus + 1).coerceAtMost(n - 1)) }
                PadAction.A -> activateInList(ui.focus)
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
            LibraryRow.Delete -> if (!_ui.value.confirmDelete) {
                _ui.update { it.copy(confirmDelete = true) }
            } else {
                val name = prefs.library(id)?.name
                if (prefs.delete(id)) {
                    scope.launch {
                        db.library().deleteFilesIn(id)
                        db.library().deleteOrphanTitles()
                    }
                    onNotice("$name deleted. Its videos are still on the card.")
                    reset()
                }
            }
        }
    }

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
                    "Browsing shows one at a time: press L2 there to switch. Deleting a library never touches the videos on the card.",
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
    val open = ui.open?.let { id -> libraries.firstOrNull { it.id == id } }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(open?.name ?: "Libraries", color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Legend(*listOfNotNull("A" to "Open", ("X" to "Remove folder").takeIf { open != null }, "B" to "Back").toTypedArray())
        }
        val labels: List<Pair<String, String?>> = if (open == null) {
            libraries.map { it.name to listOfNotNull("Shown".takeIf { _ -> it.id == activeId }, countText(counts[it.id] ?: 0)).joinToString(" · ") } +
                ("＋ New library" to null)
        } else {
            model.rowsOf(open.id).map { row ->
                when (row) {
                    LibraryRow.Name -> "Name" to open.name
                    LibraryRow.Use -> "Show this library on Browsing" to null
                    is LibraryRow.Folder -> SettingsModel.folderName(row.uri) to "Folder"
                    LibraryRow.AddFolder -> "＋ Add folder" to null
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
