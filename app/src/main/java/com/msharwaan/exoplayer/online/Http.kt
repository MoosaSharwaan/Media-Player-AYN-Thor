package com.msharwaan.exoplayer.online

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A request that came back with an error, or that couldn't be read. */
class HttpException(val code: Int, message: String) : IOException(message)

/**
 * The only way the app talks to the internet, and only when you press Fetch info or Find
 * subtitles (D32). Blocking calls: run them on Dispatchers.IO.
 *
 * "Too many requests" (429) and "busy" (503) are retried up to three times, waiting as long
 * as the service asks (Retry-After), so a big first Fetch info stays within each service's limits.
 */
class Http(
    private val client: OkHttpClient = defaultClient(),
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(text(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build()))

    fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(
            text(
                Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
                    .post(body.toString().toRequestBody(JSON)).build(),
            ),
        )

    fun bytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        call(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build()) { it.body?.bytes() ?: ByteArray(0) }

    private fun text(request: Request): String = call(request) { it.body?.string().orEmpty() }

    private fun <T> call(request: Request, read: (Response) -> T): T {
        var attempt = 0
        while (true) {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return read(response)
                val retryable = response.code == 429 || response.code == 503
                if (!retryable || attempt >= RETRIES) {
                    throw HttpException(response.code, "${request.url.host} answered ${response.code}: ${response.body?.string().orEmpty().take(200)}")
                }
                val wait = response.header("Retry-After")?.toLongOrNull()?.times(1000) ?: (1000L shl attempt)
                sleep(wait.coerceAtMost(MAX_WAIT_MS))
                attempt++
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val RETRIES = 3
        private const val MAX_WAIT_MS = 10_000L
        const val USER_AGENT = "ExoPlayer-Thor v1.0"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
            .build()
    }
}

/** org.json returns "" or "null" for missing strings; this returns null instead. */
internal fun JSONObject.str(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() && it != "null" }

internal fun JSONObject.intOrNull(name: String): Int? = if (isNull(name) || !has(name)) null else optInt(name)

/** "2008-01-20" → 2008. */
internal fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()
