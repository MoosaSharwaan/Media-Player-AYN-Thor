package com.msharwaan.exoplayer

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Display

/**
 * The app icon's entry point (D55): invisible, it opens the player on the top screen and steps
 * aside. The Thor's home screen and frontends can start an app on the bottom screen; the player
 * itself must never close and reopen itself there, because Android hands a single-task app's own
 * relaunch back to the copy that is closing, and the app vanishes into recent apps.
 *
 * This runs in a task of its own, so the player's task is created (or brought back) on the top
 * screen, display 0, wherever the icon was tapped (research: Azahar #1437).
 */
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(playerIntent(), topScreen())
        finish()
    }

    private fun playerIntent(): Intent =
        Intent(intent).setClass(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Display 0 when tapped anywhere else; nothing to ask for when already there. */
    private fun topScreen(): Bundle? {
        val here = runCatching { display?.displayId }.getOrNull() ?: Display.DEFAULT_DISPLAY
        if (here == Display.DEFAULT_DISPLAY) return null
        return ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
    }
}
