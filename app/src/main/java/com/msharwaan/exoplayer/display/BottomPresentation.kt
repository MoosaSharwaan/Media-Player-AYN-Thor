package com.msharwaan.exoplayer.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The bottom screen: a Compose window on the Thor's second display.
 *
 * Built the way melonDS, Azahar and Loki do it on the Thor:
 * - FLAG_NOT_FOCUSABLE: touching this window never takes the controller away from the
 *   top screen, so buttons keep working after a tap (decision D1, research F3);
 * - its own lifecycle, resumed while showing: borrowing the activity's froze this window
 *   whenever the activity stopped (a lesson from Loki);
 * - Back and Escape are swallowed so the window is never dismissed by them;
 * - any key that does reach it is forwarded to the same handler as the activity.
 */
class BottomPresentation(
    outerContext: Context,
    display: Display,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val onKey: (KeyEvent) -> Boolean,
    private val onTouch: () -> Unit,
    private val content: @Composable () -> Unit,
) : Presentation(outerContext, display) {

    private val host = WindowHost()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setBackgroundDrawableResource(android.R.color.black)
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            )
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            decorView.setViewTreeLifecycleOwner(host)
            decorView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            decorView.setViewTreeSavedStateRegistryOwner(host)
        }
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(host)
            setContent(content)
        }
        setContentView(view)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (onKey(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) onTouch()
        return super.dispatchTouchEvent(ev)
    }

    override fun show() {
        super.show()
        host.resume()
    }

    override fun dismiss() {
        host.destroy()
        super.dismiss()
    }

    /** This window's own lifecycle and saved state: resumed while showing, destroyed on dismiss. */
    private class WindowHost : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        init {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun resume() { registry.currentState = Lifecycle.State.RESUMED }

        fun destroy() {
            if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
