package com.msharwaan.exoplayer.online

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.msharwaan.exoplayer.MainActivity
import com.msharwaan.exoplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Work you started that should finish even if you leave the app (D48): Fetch info. It runs
 * in the app rather than a screen, with a foreground service and its notification keeping
 * it alive, and a wake lock so it carries on with the screen off. Nothing here starts by
 * itself (D32): only a button press calls [start], and all of it ends with the work.
 */
class BackgroundWork(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _progress = MutableStateFlow<String?>(null)
    /** What's running right now ("Fetching info… 3 of 12"), or null when nothing is. */
    val progress: StateFlow<String?> = _progress.asStateFlow()

    private val _finished = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** The message each run ends with. */
    val finished: SharedFlow<String> = _finished.asSharedFlow()

    /** Set by the activity: when the app is on screen, the end is shown there, not as a notification. */
    @Volatile var appVisible = false

    /** Starts [block]; false when something is already running. [block] reports progress and returns its final message. */
    fun start(label: String, block: suspend (progress: (String) -> Unit) -> String): Boolean {
        if (_progress.value != null) return false
        _progress.value = label
        WorkService.show(context, label)
        scope.launch {
            val wake = context.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ExoPlayer:fetch-info")
                ?.apply { setReferenceCounted(false); acquire(MAX_RUN_MS) }
            var message = "Stopped."
            try {
                message = block { text ->
                    _progress.value = text
                    WorkService.show(context, text)
                }
            } finally {
                runCatching { if (wake?.isHeld == true) wake.release() }
                _progress.value = null
                WorkService.stop(context)
                _finished.tryEmit(message)
                if (!appVisible) WorkService.notifyDone(context, message)
            }
        }
        return true
    }

    private companion object {
        /** A wake lock never outlives this, whatever happens. */
        const val MAX_RUN_MS = 60 * 60 * 1000L
    }
}

/** Holds the app in the foreground while [BackgroundWork] runs; shows its progress. */
class WorkService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Working…"
        ServiceCompat.startForeground(
            this, ONGOING_ID, notification(this, text, ongoing = true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        // Not restarted by Android if it's ever stopped: the work belongs to this run of the app.
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "work"
        private const val ONGOING_ID = 1
        private const val DONE_ID = 2
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TEXT = "text"

        fun show(context: Context, text: String) {
            ContextCompat.startForegroundService(context, Intent(context, WorkService::class.java).putExtra(EXTRA_TEXT, text))
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, WorkService::class.java).setAction(ACTION_STOP)) }
        }

        /** "Fetched 9 titles." when the app isn't on screen; tapping it opens the app. */
        fun notifyDone(context: Context, text: String) {
            context.getSystemService(NotificationManager::class.java)?.notify(DONE_ID, notification(context, text, ongoing = false))
        }

        private fun notification(context: Context, text: String, ongoing: Boolean): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(CHANNEL) == null) {
                nm?.createNotificationChannel(NotificationChannel(CHANNEL, "Fetch info", NotificationManager.IMPORTANCE_LOW))
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(if (ongoing) "Exo Player · Fetch info" else "Exo Player")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setAutoCancel(!ongoing)
                .setContentIntent(open)
                .setProgress(0, 0, ongoing)
                .build()
        }
    }
}
