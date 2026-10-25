package com.msharwaan.exoplayer.browse

import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.PlaybackState
import com.msharwaan.exoplayer.input.PadAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One line in the folder browser: a folder, or a video in it. */
data class FolderEntry(
    val name: String,
    /** The folder's path from the library root, for folders. */
    val path: String? = null,
    /** Videos inside a folder, counting its subfolders. */
    val count: Int = 0,
    val file: MediaFile? = null,
    val progress: Float = 0f,
    val watched: Boolean = false,
)

/**
 * The folder browser (R1 on Browsing): your video folder as it is on the SD card, folders
 * first, then videos. Built from the library, so it needs no extra scan.
 */
class FoldersModel(scope: CoroutineScope, db: AppDatabase) {

    /** Every file and where you stopped in each; the screens watch this. */
    val files: StateFlow<Pair<List<MediaFile>, Map<Long, PlaybackState>>> =
        combine(db.library().observeFiles(), db.library().observePlayback()) { f, p -> f to p.associateBy { it.mediaFileId } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList<MediaFile>() to emptyMap())

    /** Focus per folder, so going back up lands where you were. */
    private val _focus = MutableStateFlow<Map<String, Int>>(emptyMap())
    val focus: StateFlow<Map<String, Int>> = _focus.asStateFlow()

    sealed interface Result {
        data object Used : Result
        data object NotUsed : Result
        data class OpenFolder(val path: String) : Result
        data class Play(val fileId: Long) : Result
    }

    fun entries(path: String): List<FolderEntry> = entriesOf(files.value.first, files.value.second, path)

    fun onAction(path: String, action: PadAction): Result {
        val list = entries(path)
        val f = _focus.value[path] ?: 0
        when (action) {
            PadAction.UP -> setFocus(path, (f - 1).coerceAtLeast(0))
            PadAction.DOWN -> setFocus(path, (f + 1).coerceAtMost((list.size - 1).coerceAtLeast(0)))
            PadAction.A -> return open(list.getOrNull(f))
            PadAction.B -> return Result.NotUsed
            else -> Unit
        }
        return Result.Used
    }

    fun onTapped(path: String, index: Int): Result {
        if ((_focus.value[path] ?: 0) == index) return open(entries(path).getOrNull(index))
        setFocus(path, index)
        return Result.Used
    }

    private fun open(e: FolderEntry?): Result = when {
        e?.path != null -> Result.OpenFolder(e.path)
        e?.file != null -> Result.Play(e.file.id)
        else -> Result.Used
    }

    private fun setFocus(path: String, index: Int) = _focus.update { it + (path to index) }

    companion object {
        /** Plain Kotlin, unit tested. */
        fun entriesOf(files: List<MediaFile>, state: Map<Long, PlaybackState>, path: String): List<FolderEntry> {
            val prefix = if (path.isEmpty()) "" else "$path/"
            // Files in a subfolder of this one, at any depth.
            val deeper = files.filter { it.folderPath != path && (path.isEmpty() || it.folderPath.startsWith(prefix)) }
            val folders = deeper
                .groupBy { it.folderPath.removePrefix(prefix).substringBefore('/') }
                .map { (name, inside) -> FolderEntry(name, path = prefix + name, count = inside.size) }
                .sortedBy { it.name.lowercase() }
            val videos = files.filter { it.folderPath == path }.sortedBy { it.fileName.lowercase() }.map { f ->
                val s = state[f.id]
                FolderEntry(
                    f.fileName, file = f, watched = s?.watched == true,
                    progress = if (s != null && s.durationMs > 0 && !s.watched) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f,
                )
            }
            return folders + videos
        }
    }
}
