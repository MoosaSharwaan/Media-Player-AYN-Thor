package com.msharwaan.exoplayer.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.msharwaan.exoplayer.ui.theme.ExoTheme
import com.msharwaan.exoplayer.ui.theme.Palette
import java.io.File

/** The activity Compose tests run in; screens are drawn straight from its window. */
typealias Rule = AndroidComposeTestRule<*, ComponentActivity>

/** Renders a screen at the Thor's real pixel sizes and saves it under app/build/screenshots. */
object Screens {
    /** Top screen 1920 × 1080 px, bottom screen 1240 × 1080 px, both drawn at density 2. */
    enum class Panel(val widthDp: Int, val heightDp: Int) { TOP(960, 540), BOTTOM(620, 540) }

    /**
     * Robolectric's native library caches Java classes (JniConstants) the first time a buffer
     * crosses into it; done from a background thread (the app decodes artwork and Room queries
     * off the main thread) it can't find them and aborts the process. Copying a bitmap's pixels
     * into a buffer here builds that cache on the test's main thread first.
     */
    fun warmUpGraphics() {
        val b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        b.copyPixelsToBuffer(java.nio.ByteBuffer.allocate(4))
        b.recycle()
    }

    fun shoot(rule: Rule, name: String, panel: Panel, content: @Composable () -> Unit) {
        warmUpGraphics()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                ExoTheme { Box(Modifier.size(panel.widthDp.dp, panel.heightDp.dp).background(Palette.Black)) { content() } }
            }
        }
        rule.waitForIdle()
        save(rule, name, panel)
    }

    // captureToImage waits for a frame callback Robolectric never sends, so draw the window instead.
    fun save(rule: Rule, name: String, panel: Panel) {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(panel.widthDp * 2, panel.heightDp * 2, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Waits for data that Room delivers on its own thread: Compose's waitUntil doesn't give it
     * real time, so this runs the main looper and sleeps until [ready] or five seconds pass.
     */
    fun waitFor(ready: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!ready() && System.currentTimeMillis() < until) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    /** Sample artwork: the CC-BY open-movie posters kept in the repo for development. */
    fun art(kind: String, name: String): String = File("../docs/mockups/art/$kind/$name.jpg").absolutePath
}
