package com.msharwaan.exoplayer.online

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/**
 * Artwork on the device, one folder per title, each image saved at the size its screen
 * shows (D7): decoding a 342-pixel poster is far lighter than a full-size one while scrolling.
 */
class ImageStore(private val root: File) {

    /** True when [path] is an image already on the device. */
    fun has(path: String?): Boolean = path != null && File(path).let { it.isFile && it.length() > 0 }

    /** Downloads [url], shrinks it to at most [maxWidth] pixels wide, and returns where it was saved. */
    fun save(http: Http, groupKey: String, name: String, url: String, maxWidth: Int): String? {
        val bytes = runCatching { http.bytes(url) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val file = File(File(root, folderOf(groupKey)).apply { mkdirs() }, "$name.jpg")
        val scaled = shrink(bytes, maxWidth)
        if (scaled != null) {
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            scaled.recycle()
        } else {
            file.writeBytes(bytes)
        }
        return file.absolutePath
    }

    /** Removes a title's images, for Fix match (the old ones belonged to the wrong title). */
    fun clear(groupKey: String) { File(root, folderOf(groupKey)).deleteRecursively() }

    private fun shrink(bytes: ByteArray, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (bitmap.width <= maxWidth) return bitmap
        val h = (bitmap.height.toLong() * maxWidth / bitmap.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidth, h, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun folderOf(groupKey: String): String =
        MessageDigest.getInstance("SHA-1").digest(groupKey.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    companion object {
        const val POSTER = 342
        const val BACKDROP = 1280
        const val STILL = 300
    }
}
