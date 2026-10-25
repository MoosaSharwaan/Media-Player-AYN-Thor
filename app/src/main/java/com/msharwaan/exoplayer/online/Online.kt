package com.msharwaan.exoplayer.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.settings.Secret
import com.msharwaan.exoplayer.settings.Secrets
import java.io.File

/**
 * Everything online, built from the keys in Settings at the moment you press a button, so a
 * key entered a minute ago is used straight away. Nothing here runs by itself (D32).
 */
class Online(private val context: Context, private val db: AppDatabase, private val secrets: Secrets) {
    val http by lazy { Http() }
    val images by lazy { ImageStore(File(context.filesDir, "art")) }

    fun isConnected(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Null until a TMDB key is entered in Settings. */
    fun tmdb(): Tmdb? = secrets[Secret.TMDB_KEY]?.let { Tmdb(http, it) }

    fun fetchInfo(): FetchInfo? {
        val tmdb = tmdb() ?: return null
        return FetchInfo(db, http, images, tmdb, secrets[Secret.FANART_KEY]?.let { Fanart(http, it) }, AniList(http))
    }

    /** Null until the OpenSubtitles API key is entered. */
    fun openSubtitles(): OpenSubtitles? {
        val key = secrets[Secret.OPENSUBTITLES_KEY] ?: return null
        return OpenSubtitles(http, key, secrets[Secret.OPENSUBTITLES_USER], secrets[Secret.OPENSUBTITLES_PASSWORD])
    }
}
