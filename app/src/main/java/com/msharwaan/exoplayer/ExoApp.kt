package com.msharwaan.exoplayer

import android.app.Application
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.library.LibraryPrefs
import com.msharwaan.exoplayer.library.LibraryScanner
import com.msharwaan.exoplayer.player.PlaybackHolder

/** The application. Holds nothing that runs in the background (decision D32). */
class ExoApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val prefs: LibraryPrefs by lazy { LibraryPrefs(this) }
    val scanner: LibraryScanner by lazy { LibraryScanner(this, db) }
    val playback: PlaybackHolder by lazy { PlaybackHolder(this) }
}
