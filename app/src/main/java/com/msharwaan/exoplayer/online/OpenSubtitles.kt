package com.msharwaan.exoplayer.online

import android.content.ContentResolver
import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One subtitle OpenSubtitles offers for a video. */
data class SubtitleHit(
    val fileId: Long,
    val language: String,
    val release: String,
    val downloads: Int,
    val hearingImpaired: Boolean,
    /** Made for exactly this file (matched by its hash), so the timing should already fit. */
    val exactMatch: Boolean,
)

/** A downloaded subtitle and how many downloads are left today. */
data class SubtitleDownload(val bytes: ByteArray, val fileName: String, val remaining: Int?, val resetsAt: String?)

/**
 * OpenSubtitles (opensubtitles.com REST API), for Find subtitles (D9). Searching is free;
 * each download counts against the daily allowance, which a logged-in account raises.
 */
class OpenSubtitles(
    private val http: Http,
    private val apiKey: String,
    private val username: String?,
    private val password: String?,
    private var base: String = "https://api.opensubtitles.com/api/v1/",
) {
    private var token: String? = null

    /** Downloads allowed per day for this account, once logged in. */
    var allowedPerDay: Int? = null
        private set

    private fun headers(): Map<String, String> = buildMap {
        put("Api-Key", apiKey)
        put("Accept", "application/json")
        token?.let { put("Authorization", "Bearer $it") }
    }

    /** Logs in when a username and password are set; the account may live on another server (base_url). */
    fun login() {
        if (token != null || username.isNullOrBlank() || password.isNullOrBlank()) return
        val o = http.postJson(base + "login", JSONObject().put("username", username).put("password", password), headers())
        token = o.str("token")
        allowedPerDay = o.optJSONObject("user")?.intOrNull("allowed_downloads")
        o.str("base_url")?.let { host -> if (!base.contains(host)) base = "https://$host/api/v1/" }
    }

    /**
     * Subtitles for a file: by its [hash] (exact matches) and by name, in [languages]
     * (two-letter codes). Exact matches first, then your languages in order, then the most downloaded.
     */
    fun search(hash: String?, query: String, season: Int?, episode: Int?, languages: List<String>): List<SubtitleHit> {
        login()
        // OpenSubtitles asks for parameters in alphabetical order and lower case, or it redirects.
        val params = sortedMapOf<String, String>()
        params["languages"] = languages.joinToString(",")
        hash?.let { params["moviehash"] = it }
        params["query"] = query.lowercase()
        season?.let { params["season_number"] = it.toString() }
        episode?.let { params["episode_number"] = it.toString() }
        val url = (base + "subtitles").toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build().toString()
        val hits = http.getJson(url, headers()).optJSONArray("data").objects().mapNotNull { item ->
            val a = item.optJSONObject("attributes") ?: return@mapNotNull null
            val file = a.optJSONArray("files").objects().firstOrNull() ?: return@mapNotNull null
            SubtitleHit(
                fileId = file.getLong("file_id"),
                language = a.str("language") ?: "?",
                release = a.str("release") ?: file.str("file_name") ?: "",
                downloads = a.optInt("download_count"),
                hearingImpaired = a.optBoolean("hearing_impaired"),
                exactMatch = a.optBoolean("moviehash_match"),
            )
        }
        return hits.sortedWith(
            compareByDescending<SubtitleHit> { it.exactMatch }
                .thenBy { languages.indexOf(it.language).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .thenByDescending { it.downloads },
        )
    }

    /** Uses one of today's downloads. Asks for .srt so the app can draw it and move its timing both ways. */
    fun download(fileId: Long): SubtitleDownload {
        login()
        val o = http.postJson(base + "download", JSONObject().put("file_id", fileId).put("sub_format", "srt"), headers())
        val link = o.str("link") ?: throw HttpException(500, o.str("message") ?: "No download link")
        return SubtitleDownload(http.bytes(link), o.str("file_name") ?: "subtitle.srt", o.intOrNull("remaining"), o.str("reset_time"))
    }
}

/**
 * OpenSubtitles' file hash: the file size plus the sums of the first and last 64 KB, read as
 * 64-bit little-endian numbers. It identifies the exact release, so a matching subtitle fits.
 */
object MovieHash {
    private const val CHUNK = 64 * 1024

    fun of(resolver: ContentResolver, uri: Uri, size: Long): String? = runCatching {
        if (size < CHUNK * 2) return null
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                val head = ByteBuffer.allocate(CHUNK)
                ch.read(head, 0)
                val tail = ByteBuffer.allocate(CHUNK)
                ch.read(tail, size - CHUNK)
                compute(head.array(), tail.array(), size)
            }
        }
    }.getOrNull()

    fun compute(head: ByteArray, tail: ByteArray, size: Long): String {
        var hash = size
        for (chunk in listOf(head, tail)) {
            val b = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
            while (b.hasRemaining()) hash += b.get()
        }
        return "%016x".format(hash)
    }
}
