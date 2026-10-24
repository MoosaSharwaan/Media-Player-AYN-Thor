package com.msharwaan.exoplayer.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Artwork stored on the device, already at display size (decision D7), so it is decoded
 * as is. A small memory cache keeps scrolling smooth.
 */
object ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    suspend fun load(path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        return withContext(Dispatchers.IO) {
            if (!File(path).exists()) return@withContext null
            BitmapFactory.decodeFile(path)?.asImageBitmap()?.also { cache.put(path, it) }
        }
    }
}

/** A colour that stays the same for a title, used until its artwork is fetched. */
fun placeholderBrush(key: String): Brush {
    val hue = (key.hashCode().toLong() and 0xffff).toFloat() % 360f
    return Brush.linearGradient(
        listOf(Color.hsv(hue, 0.45f, 0.55f), Color.hsv(hue, 0.5f, 0.25f), Color.hsv(hue, 0.55f, 0.06f)),
    )
}

/**
 * An image from [path], or a colour block with [label] when there is none yet
 * (before Fetch info, or for videos in Others).
 */
@Composable
fun Artwork(
    path: String?,
    key: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelSize: TextUnit = 14.sp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val image by produceState<ImageBitmap?>(null, path) { value = path?.let { ImageCache.load(it) } }
    Box(modifier.background(placeholderBrush(key))) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = label, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        } else if (label != null) {
            Text(
                label,
                color = Color.White,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}
