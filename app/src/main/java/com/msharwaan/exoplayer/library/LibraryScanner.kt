package com.msharwaan.exoplayer.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.SubtitleFile
import com.msharwaan.exoplayer.data.TitleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds every video in the chosen folder and its subfolders. Offline: no network (D32).
 *
 * Reads the folder through Android's folder picker grant (no broad storage permission).
 * New files are added, changed ones (size or date) re-read, missing ones removed.
 * Subtitle files next to a video (.srt, .ass, .ssa, .vtt) are matched by name.
 */
class LibraryScanner(private val context: Context, private val db: AppDatabase) {

    data class Result(val added: Int, val changed: Int, val removed: Int, val total: Int)

    private data class Entry(val uri: Uri, val name: String, val size: Long, val modified: Long, val folders: List<String>)

    suspend fun scan(tree: Uri): Result = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Entry>()
        val subtitles = mutableListOf<Entry>()
        walk(tree) { entry ->
            when {
                FileNameParser.isVideo(entry.name) -> videos += entry
                entry.name.substringAfterLast('.', "").lowercase() in SUBTITLE_EXTENSIONS -> subtitles += entry
            }
        }

        val dao = db.library()
        val known = dao.allFiles().associateBy { it.uri }
        var added = 0
        var changed = 0
        val seen = mutableSetOf<String>()
        for (v in videos) {
            val key = v.uri.toString()
            seen += key
            val old = known[key]
            if (old != null && old.size == v.size && old.modified == v.modified) {
                linkSubtitles(old.id, v, subtitles)
                continue
            }
            val parsed = FileNameParser.parse(v.name, v.folders)
            val file = MediaFile(
                id = old?.id ?: 0,
                uri = key,
                folderPath = v.folders.joinToString("/"),
                fileName = v.name,
                size = v.size,
                modified = v.modified,
                kind = parsed.kind.name,
                groupKey = groupKeyOf(parsed, v),
                title = parsed.title,
                year = parsed.year,
                season = parsed.season,
                episode = parsed.episode,
                anime = parsed.animeHint,
                durationMs = old?.durationMs ?: 0,
            )
            val id = dao.insertFile(file)
            if (old == null) added++ else changed++
            ensureTitle(file)
            linkSubtitles(if (old != null) old.id else id, v, subtitles)
        }
        val gone = known.values.filter { it.uri !in seen }.map { it.id }
        if (gone.isNotEmpty()) dao.deleteFiles(gone)
        dao.deleteOrphanTitles()
        Result(added, changed, gone.size, videos.size)
    }

    /** Creates the card's entry the first time a title is seen; Fetch info fills it in later. */
    private suspend fun ensureTitle(file: MediaFile) {
        val dao = db.library()
        if (dao.title(file.groupKey) != null) return
        val kind = when (file.kind) {
            ParsedName.Kind.EPISODE.name -> "SHOW"
            ParsedName.Kind.MOVIE.name -> "MOVIE"
            else -> "OTHER"
        }
        val tab = when {
            kind == "OTHER" -> "OTHERS"
            file.anime -> "ANIME"
            kind == "SHOW" -> "SERIES"
            else -> "MOVIES"
        }
        dao.upsertTitle(TitleInfo(groupKey = file.groupKey, kind = kind, tab = tab, name = displayName(file), year = file.year))
    }

    private suspend fun linkSubtitles(fileId: Long, video: Entry, all: List<Entry>) {
        val base = video.name.substringBeforeLast('.')
        val matches = all.filter { it.folders == video.folders && it.name.startsWith(base) }
        db.library().replaceSubtitles(
            fileId,
            matches.map { SubtitleFile(mediaFileId = fileId, uri = it.uri.toString(), fileName = it.name, language = languageOf(it.name, base)) },
        )
    }

    /** Walks the folder tree with DocumentsContract queries (much faster than DocumentFile). */
    private fun walk(tree: Uri, onFile: (Entry) -> Unit) {
        val resolver = context.contentResolver
        val queue = ArrayDeque<Pair<String, List<String>>>()
        queue += DocumentsContract.getTreeDocumentId(tree) to emptyList()
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )
        while (queue.isNotEmpty()) {
            val (docId, folders) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            resolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    if (c.getString(2) == Document.MIME_TYPE_DIR) {
                        queue += id to (folders + name)
                    } else {
                        onFile(Entry(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, c.getLong(3), c.getLong(4), folders))
                    }
                }
            }
        }
    }

    companion object {
        private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")

        /** Normalised name for grouping: "The Dark Knight" and "the.dark.knight" are one title. */
        fun normalise(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

        private fun groupKeyOf(p: ParsedName, e: Entry): String = when (p.kind) {
            ParsedName.Kind.EPISODE -> "s:" + normalise(p.title)
            ParsedName.Kind.MOVIE -> "m:" + normalise(p.title) + ":" + (p.year ?: "")
            ParsedName.Kind.OTHER -> "o:" + normalise((e.folders + e.name).joinToString("/"))
        }

        private fun displayName(file: MediaFile): String =
            file.title.split(' ').joinToString(" ") { w -> if (w.length > 1 && w == w.lowercase()) w.replaceFirstChar(Char::uppercase) else w }

        /** "Movie.en.srt" → "en"; "Movie.srt" → null. */
        fun languageOf(fileName: String, videoBase: String): String? =
            fileName.removePrefix(videoBase).substringBeforeLast('.').trim('.', ' ', '_', '-').ifEmpty { null }
    }
}
