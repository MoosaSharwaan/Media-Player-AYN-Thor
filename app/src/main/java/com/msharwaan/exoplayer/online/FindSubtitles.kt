package com.msharwaan.exoplayer.online

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.msharwaan.exoplayer.data.MediaFile
import com.msharwaan.exoplayer.data.SubtitleFile
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.ui.Legend
import com.msharwaan.exoplayer.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

data class FindSubtitlesUi(
    val fileId: Long = 0,
    val label: String = "",
    val phase: Phase = Phase.SEARCHING,
    val results: List<SubtitleHit> = emptyList(),
    val focus: Int = 0,
    val allowedPerDay: Int? = null,
) {
    enum class Phase { SEARCHING, RESULTS, NONE, DOWNLOADING }
}

/**
 * Find subtitles (D9): offered when a video has none. Searches OpenSubtitles by the file's
 * hash and its name; the chosen one is saved next to the video (D37) and switched on.
 */
class FindSubtitlesModel(
    private val context: Context,
    private val scope: CoroutineScope,
    private val db: AppDatabase,
    private val online: Online,
    private val libraryFolder: () -> Uri?,
    private val onNotice: (String) -> Unit,
    private val onAdded: (SubtitleFile) -> Unit,
    private val onDone: () -> Unit,
) {
    private val _ui = MutableStateFlow(FindSubtitlesUi())
    val ui: StateFlow<FindSubtitlesUi> = _ui.asStateFlow()
    private var client: OpenSubtitles? = null

    /** Your language first, then English. */
    private val languages get() = listOf(Locale.getDefault().language.ifEmpty { "en" }, "en").distinct()

    fun open(fileId: Long) {
        if (_ui.value.fileId == fileId && _ui.value.phase != FindSubtitlesUi.Phase.NONE) return
        val os = online.openSubtitles()
        if (os == null) { onNotice("Add your OpenSubtitles API key in Settings first."); onDone(); return }
        if (!online.isConnected()) { onNotice("No internet connection. Find subtitles needs one."); onDone(); return }
        client = os
        _ui.value = FindSubtitlesUi(fileId = fileId)
        scope.launch {
            val dao = db.library()
            val f = withContext(Dispatchers.IO) { dao.file(fileId) } ?: return@launch onDone()
            val t = withContext(Dispatchers.IO) { dao.title(f.groupKey) }
            val name = t?.name ?: f.title
            val episode = f.episode?.let { " · " + (f.season?.let { s -> "S%02dE%02d".format(s, it) } ?: "Episode $it") }.orEmpty()
            _ui.update { it.copy(label = name + episode) }
            val hits = try {
                withContext(Dispatchers.IO) {
                    val hash = MovieHash.of(context.contentResolver, Uri.parse(f.uri), f.size)
                    os.search(hash, if (t?.year != null && f.episode == null) "$name ${t.year}" else name, f.season?.takeIf { f.episode != null }, f.episode, languages)
                }
            } catch (e: HttpException) {
                onNotice(if (e.code == 401 || e.code == 403) "OpenSubtitles didn't accept the API key or login. Check them in Settings." else "OpenSubtitles couldn't be reached.")
                return@launch onDone()
            } catch (e: IOException) {
                onNotice("OpenSubtitles couldn't be reached.")
                return@launch onDone()
            }
            _ui.update {
                it.copy(phase = if (hits.isEmpty()) FindSubtitlesUi.Phase.NONE else FindSubtitlesUi.Phase.RESULTS, results = hits.take(30), allowedPerDay = os.allowedPerDay)
            }
        }
    }

    /** Returns false when B should go back to the player. */
    fun onAction(action: PadAction): Boolean {
        val ui = _ui.value
        if (ui.phase == FindSubtitlesUi.Phase.DOWNLOADING) return true
        when (action) {
            PadAction.UP -> _ui.update { it.copy(focus = (it.focus - 1).coerceAtLeast(0)) }
            PadAction.DOWN -> _ui.update { it.copy(focus = (it.focus + 1).coerceAtMost((it.results.size - 1).coerceAtLeast(0))) }
            PadAction.A -> ui.results.getOrNull(ui.focus)?.let(::download)
            PadAction.B -> { _ui.value = FindSubtitlesUi(); return false }
            else -> Unit
        }
        return true
    }

    fun onTapped(index: Int) {
        if (_ui.value.focus == index) _ui.value.results.getOrNull(index)?.let(::download) else _ui.update { it.copy(focus = index) }
    }

    private fun download(hit: SubtitleHit) {
        val os = client ?: return
        val fileId = _ui.value.fileId
        _ui.update { it.copy(phase = FindSubtitlesUi.Phase.DOWNLOADING) }
        scope.launch {
            try {
                val (sub, nextToVideo, d) = withContext(Dispatchers.IO) {
                    val f = db.library().file(fileId) ?: throw IOException("file gone")
                    val d = os.download(hit.fileId)
                    val saved = saveNextToVideo(f, hit.language, d.bytes)
                    val uri = saved ?: saveInApp(f, hit.language, d.bytes)
                    val name = f.fileName.substringBeforeLast('.') + ".${hit.language}.srt"
                    val row = SubtitleFile(mediaFileId = f.id, uri = uri.toString(), fileName = name, language = hit.language, source = "downloaded")
                    val id = db.library().insertSubtitles(listOf(row)).firstOrNull() ?: 0L
                    Triple(row.copy(id = id), saved != null, d)
                }
                onAdded(sub)
                onNotice(
                    (if (nextToVideo) "Subtitles added next to the video." else "Subtitles added (kept in the app: the folder couldn't be written to).") +
                        (d.remaining?.let { " $it downloads left today." } ?: ""),
                )
                _ui.value = FindSubtitlesUi()
                onDone()
            } catch (e: HttpException) {
                onNotice(
                    when (e.code) {
                        401 -> "Downloading needs your OpenSubtitles username and password in Settings."
                        406, 429 -> "No subtitle downloads left today. They reset at midnight UTC."
                        else -> "The download didn't work. Try another one."
                    },
                )
                _ui.update { it.copy(phase = FindSubtitlesUi.Phase.RESULTS) }
            } catch (e: IOException) {
                onNotice("The download didn't work. Try another one.")
                _ui.update { it.copy(phase = FindSubtitlesUi.Phase.RESULTS) }
            }
        }
    }

    /** Saves "Name.en.srt" in the video's own folder, through the folder access you gave (D37). */
    private fun saveNextToVideo(f: MediaFile, lang: String, bytes: ByteArray): Uri? = runCatching {
        val tree = libraryFolder() ?: return null
        val docId = DocumentsContract.getDocumentId(Uri.parse(f.uri))
        val parentId = docId.substringBeforeLast('/', "").ifEmpty { DocumentsContract.getTreeDocumentId(tree) }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val name = f.fileName.substringBeforeLast('.') + ".$lang.srt"
        val doc = DocumentsContract.createDocument(context.contentResolver, parent, "application/x-subrip", name) ?: return null
        context.contentResolver.openOutputStream(doc)?.use { it.write(bytes) } ?: return null
        doc
    }.getOrNull()

    private fun saveInApp(f: MediaFile, lang: String, bytes: ByteArray): Uri {
        val file = File(File(context.filesDir, "subtitles").apply { mkdirs() }, "${f.id}.$lang.srt")
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }
}

/** Bottom screen for Find subtitles. The top screen keeps showing the film. */
@Composable
fun FindSubtitlesBottom(model: FindSubtitlesModel) {
    val ui by model.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FIND SUBTITLES", color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        Text(ui.label, color = Palette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when (ui.phase) {
            FindSubtitlesUi.Phase.SEARCHING, FindSubtitlesUi.Phase.DOWNLOADING -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (ui.phase == FindSubtitlesUi.Phase.SEARCHING) "Searching OpenSubtitles…" else "Downloading…", color = Palette.Muted, fontSize = 16.sp)
            }
            FindSubtitlesUi.Phase.NONE -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("OpenSubtitles has nothing for this one.", color = Palette.Muted, fontSize = 16.sp)
            }
            FindSubtitlesUi.Phase.RESULTS -> {
                Text(
                    "✓ made for this exact file · each download counts" + (ui.allowedPerDay?.let { " (you have $it a day)" } ?: ""),
                    color = Palette.Muted, fontSize = 13.sp,
                )
                val list = rememberLazyListState()
                LaunchedEffect(ui.focus) { list.animateScrollToItem((ui.focus - 2).coerceAtLeast(0)) }
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(ui.results) { i, hit ->
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Palette.Panel, shape)
                                .border(2.dp, if (i == ui.focus) Palette.Accent else Palette.Line, shape)
                                .clickable { model.onTapped(i) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(hit.language.uppercase(), color = Palette.Ink, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                            Text(hit.release, color = Palette.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (hit.hearingImpaired) Text("HI", color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            if (hit.exactMatch) Text("✓", color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(compact(hit.downloads), color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        Legend("A" to "Download", "B" to "Back to the film")
    }
}

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fk".format(n / 1_000f)
    else -> n.toString()
}
