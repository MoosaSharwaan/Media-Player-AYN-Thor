package com.msharwaan.exoplayer.library

import android.content.Context
import android.net.Uri
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.online.ImageStore
import java.io.File

/**
 * Lets go of everything that belonged to files that are gone (D54): their titles, season and
 * episode details, artwork, subtitle links and subtitles kept in the app, and where you stopped.
 * Fix match choices stay, so a file that comes back is matched the same way. Never touches the
 * card: subtitles saved next to a video are the user's files.
 */
object Cleanup {

    data class Freed(val bytes: Long, val rows: Int)

    suspend fun run(context: Context, db: AppDatabase): Freed {
        val dao = db.library()
        dao.deleteOrphanTitles()
        var rows = dao.deleteOrphanSeasons() + dao.deleteOrphanEpisodes()
        var bytes = 0L
        val appFiles = context.filesDir.canonicalFile
        for (sub in dao.orphanSubtitles()) {
            val uri = Uri.parse(sub.uri)
            val file = uri.path?.takeIf { uri.scheme == "file" }?.let { File(it).canonicalFile } ?: continue
            // Only subtitles the app kept itself, in its own storage.
            if (file.isFile && file.path.startsWith(appFiles.path + File.separator)) {
                bytes += file.length()
                file.delete()
            }
        }
        rows += dao.deleteOrphanSubtitles() + dao.deleteOrphanPlayback() + dao.deleteOrphanSessions()
        bytes += ImageStore(ImageStore.rootOf(context)).keepOnly(dao.titleKeys())
        return Freed(bytes, rows)
    }

    /** "84 MB": artwork plus subtitles kept in the app. */
    fun storageUsed(context: Context): Long =
        ImageStore.sizeOf(ImageStore.rootOf(context)) + ImageStore.sizeOf(File(context.filesDir, "subtitles"))
}
