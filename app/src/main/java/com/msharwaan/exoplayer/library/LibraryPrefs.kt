package com.msharwaan.exoplayer.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit

/** The one folder you picked for your videos (usually on the SD card). */
class LibraryPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    var folder: Uri?
        get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
        private set(value) = prefs.edit { putString(KEY_FOLDER, value?.toString()) }

    /** Keeps read access to the folder across restarts. */
    fun choose(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        folder = uri
    }

    private companion object {
        const val KEY_FOLDER = "folder"
    }
}
