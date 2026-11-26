package com.msharwaan.exoplayer.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.msharwaan.exoplayer.input.PadAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A library: a name, the folders it reads (D52), and its passkey's hash when it has one (D56). */
data class LibrarySpec(val id: Long, val name: String, val folders: List<Uri>, val lock: String? = null) {
    val locked: Boolean get() = lock != null
}

/**
 * The libraries ("Entertainment", "Work"…) and which one Browsing shows (D52). Each reads its own
 * folders and has its own titles, Continue watching and watched marks; one is shown at a time.
 * Library 1 is the one from before libraries existed, so its data needed no change.
 */
class LibraryPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    private val _libraries = MutableStateFlow(load())
    val libraries: StateFlow<List<LibrarySpec>> = _libraries.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getLong(KEY_ACTIVE, _libraries.value.firstOrNull()?.id ?: FIRST_ID))
    /** The library Browsing shows. */
    val activeId: StateFlow<Long> = _activeId.asStateFlow()

    init {
        relock()
    }

    val active: LibrarySpec? get() = _libraries.value.firstOrNull { it.id == _activeId.value } ?: _libraries.value.firstOrNull()

    fun library(id: Long): LibrarySpec? = _libraries.value.firstOrNull { it.id == id }

    /** A new, empty library; the first one is "Library", the rest "Library 2", "Library 3"… */
    fun create(name: String? = null): LibrarySpec {
        val id = (_libraries.value.maxOfOrNull { it.id } ?: 0) + 1
        val spec = LibrarySpec(id, name ?: if (id == FIRST_ID) "Library" else "Library $id", emptyList())
        save(_libraries.value + spec)
        if (_libraries.value.size == 1) setActive(id)
        return spec
    }

    fun rename(id: Long, name: String) = update(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    /**
     * Adds a folder Android's folder picker gave access to, keeping that access across restarts:
     * read for scanning, write so downloaded subtitles can be saved next to their video (D37).
     */
    fun addFolder(id: Long, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        update(id) { if (uri in it.folders) it else it.copy(folders = it.folders + uri) }
    }

    fun removeFolder(id: Long, uri: Uri) = update(id) { it.copy(folders = it.folders - uri) }

    /** The first library without a passkey: one always stays (D56). */
    fun firstOpen(): LibrarySpec? = _libraries.value.firstOrNull { !it.locked }

    /** A passkey can go on [id] only while another library stays open. */
    fun canLock(id: Long): Boolean = _libraries.value.any { it.id != id && !it.locked }

    fun setLock(id: Long, presses: List<PadAction>) {
        if (library(id)?.locked == true || canLock(id)) update(id) { it.copy(lock = Passkey.make(presses)) }
    }

    fun clearLock(id: Long) = update(id) { it.copy(lock = null) }

    fun unlocks(id: Long, presses: List<PadAction>): Boolean = library(id)?.lock?.let { Passkey.matches(it, presses) } ?: true

    /**
     * Leaving the app, or starting it: a locked library on Browsing gives way to the first open
     * one. Returns true when it did.
     */
    fun relock(): Boolean {
        if (active?.locked != true) return false
        val open = firstOpen() ?: return false
        setActive(open.id)
        return true
    }

    /**
     * Forgets a library (its files stay where they are on the card). The last one can't go, nor
     * the last one without a passkey (D56).
     */
    fun delete(id: Long): Boolean {
        if (_libraries.value.size <= 1) return false
        if (library(id)?.locked == false && !canLock(id)) return false
        save(_libraries.value.filter { it.id != id })
        if (_activeId.value == id) setActive((firstOpen() ?: _libraries.value.first()).id)
        return true
    }

    fun setActive(id: Long) {
        prefs.edit { putLong(KEY_ACTIVE, id) }
        _activeId.value = id
    }

    /** The next library round in a circle (L2 + R3 on Browsing); null when there's only one. */
    fun next(): LibrarySpec? = after(_activeId.value)?.also { setActive(it.id) }

    /** The library after [id] round in a circle, without switching; null when there's only one. */
    fun after(id: Long): LibrarySpec? {
        val all = _libraries.value
        if (all.size < 2) return null
        val i = all.indexOfFirst { it.id == id }
        return all[(i + 1) % all.size]
    }

    private fun update(id: Long, change: (LibrarySpec) -> LibrarySpec) = save(_libraries.value.map { if (it.id == id) change(it) else it })

    private fun save(list: List<LibrarySpec>) {
        write(list)
        _libraries.value = list
    }

    private fun write(list: List<LibrarySpec>) {
        val json = JSONArray(list.map { l -> JSONObject().put("id", l.id).put("name", l.name).put("folders", JSONArray(l.folders.map(Uri::toString))).apply { l.lock?.let { put("lock", it) } } })
        prefs.edit { putString(KEY_LIBRARIES, json.toString()) }
    }

    private fun load(): List<LibrarySpec> {
        prefs.getString(KEY_LIBRARIES, null)?.let { text ->
            return runCatching {
                val a = JSONArray(text)
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    val f = o.getJSONArray("folders")
                    LibrarySpec(o.getLong("id"), o.getString("name"), (0 until f.length()).map { Uri.parse(f.getString(it)) }, o.optString("lock").ifEmpty { null })
                }
            }.getOrDefault(emptyList())
        }
        // Before libraries: the one folder becomes library 1, "Library".
        val old = prefs.getString(KEY_OLD_FOLDER, null)?.let(Uri::parse) ?: return emptyList()
        return listOf(LibrarySpec(FIRST_ID, "Library", listOf(old))).also { write(it) }
    }

    companion object {
        /** The library that existed before libraries did; its group keys carry no prefix. */
        const val FIRST_ID = 1L
        private const val KEY_LIBRARIES = "libraries"
        private const val KEY_ACTIVE = "active"
        private const val KEY_OLD_FOLDER = "folder"
    }
}
