package com.msharwaan.exoplayer

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.msharwaan.exoplayer.display.BottomPresentation
import com.msharwaan.exoplayer.display.DisplayMonitor
import com.msharwaan.exoplayer.input.PadAction
import com.msharwaan.exoplayer.nav.Route
import com.msharwaan.exoplayer.settings.DisplayPrefs
import com.msharwaan.exoplayer.ui.BottomRoot
import com.msharwaan.exoplayer.ui.TopRoot
import com.msharwaan.exoplayer.ui.theme.ExoTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The top screen, and the owner of the bottom screen's window.
 *
 * The activity always lives on the default display (the Thor's top screen). If a frontend
 * started it on the bottom screen, it moves itself to the top first: an app on the bottom
 * screen can't put a window on the top one (research: Azahar #1437).
 */
class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var bottom: BottomPresentation? = null
    private var bottomDisplayId: Int? = null

    /** Android's folder picker: read access to one folder, no broad storage permission. */
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderChosen(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (moveToTopScreenIfNeeded()) return

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent { ExoTheme { TopRoot(vm) } }
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        screenOffRegistered = true

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.displaySetup.collect { showBottomScreen(it.bottomDisplayId) } }
                launch { vm.browse.folderRequests.collect { pickFolder.launch(null) } }
                launch {
                    vm.diagnostics.bottomBrightness.collect { level ->
                        bottom?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = level } }
                    }
                }
                launch {
                    // The screen stays on only while a film is actually playing.
                    vm.player.ui.collect { ui ->
                        if (ui.fileId != 0L && !ui.paused && vm.navigator.current is Route.Player) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
                launch {
                    // The rate chosen in Settings; the diagnostics mode test overrides it while it runs.
                    combine(vm.diagnostics.topModeId, displayPrefs.top) { testMode, hz ->
                        if (testMode != 0) testMode else display?.let { DisplayPrefs.modeIdFor(it, hz) } ?: 0
                    }.collect { modeId ->
                        window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
                    }
                }
                launch { displayPrefs.bottom.collect { applyBottomRate() } }
            }
        }
    }

    /** Relaunches on the default display when started elsewhere. Returns true if it did. */
    private fun moveToTopScreenIfNeeded(): Boolean {
        val here = display?.displayId ?: Display.DEFAULT_DISPLAY
        if (here == Display.DEFAULT_DISPLAY) return false
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
        finish()
        return true
    }

    private val displayPrefs get() = (application as ExoApp).displayPrefs

    /** Asks the bottom screen for the rate chosen in Settings (0 = Auto). */
    private fun applyBottomRate() {
        val window = bottom?.window ?: return
        val modeId = bottom?.display?.let { DisplayPrefs.modeIdFor(it, displayPrefs.bottom.value) } ?: 0
        window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
    }

    /** Shows, moves or removes the bottom screen's window as displays come and go. */
    private fun showBottomScreen(displayId: Int?) {
        if (displayId == bottomDisplayId && bottom?.isShowing == true) return
        bottom?.let { runCatching { it.dismiss() } }
        bottom = null
        bottomDisplayId = displayId
        val display = displayId?.let { DisplayMonitor(this).display(it) } ?: return
        val presentation = BottomPresentation(
            outerContext = this,
            display = display,
            viewModelStoreOwner = this,
            onKey = { handleKey(it, "bottom") },
            onTouch = { vm.touched() },
        ) { ExoTheme { BottomRoot(vm) } }
        // The display can disappear between finding it and showing the window.
        runCatching { presentation.show(); bottom = presentation; applyBottomRate() }
            .onFailure { if (it !is WindowManager.BadTokenException && it !is WindowManager.InvalidDisplayException) throw it }
    }

    override fun onStart() {
        super.onStart()
        // Reading the SD card is offline, so it still runs by itself (D32).
        vm.browse.rescan()
    }

    /**
     * The power button or the lid turning the screens off. onStop normally covers it too; this
     * makes sure, since a window on the bottom screen can keep the activity counted as visible.
     */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { vm.player.pause() }
    }

    override fun onStop() {
        // Leaving the app (Home, a frontend, the screen going off) pauses and saves (D8).
        vm.player.pause()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        handleKey(event, "top") || super.dispatchKeyEvent(event)

    private fun handleKey(event: KeyEvent, window: String): Boolean {
        vm.diagnostics.logKey(event, window)
        // While a film is open, the volume buttons move the app's volume bar instead of
        // Android's pop-up, which would cover the picture (rule R1).
        if (vm.navigator.current is Route.Player && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                vm.touched()
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) vm.player.volume.up() else vm.player.volume.down()
            }
            return true
        }
        val action = PadAction.fromKeyCode(event.keyCode) ?: return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) {
                    // Held buttons: only list movement repeats; nothing else fires twice.
                    if (event.keyCode !in usedKeys) return false
                    if (PadAction.repeats(action)) vm.onAction(action)
                    return true
                }
                val used = vm.onAction(action)
                if (used) usedKeys += event.keyCode else usedKeys -= event.keyCode
                used
            }
            // A release belongs to whoever took the press; otherwise Back would reach the system
            // after B had already stepped back, and close the app.
            KeyEvent.ACTION_UP -> { vm.onButtonUp(action); usedKeys.remove(event.keyCode) }
            else -> false
        }
    }

    private val usedKeys = mutableSetOf<Int>()

    private var screenOffRegistered = false

    override fun onDestroy() {
        if (screenOffRegistered) unregisterReceiver(screenOff)
        // Never leave a window stuck on the bottom screen (research: iiSU #265).
        bottom?.let { runCatching { it.dismiss() } }
        bottom = null
        super.onDestroy()
    }
}
