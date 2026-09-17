package com.vag.vcdsandroid.logging

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vag.vcdsandroid.ui.MainActivity

/**
 * Keeps the process at foreground priority and the CPU awake while a CSV log is open, so a long
 * drive log survives screen-off / app-switch. It does no I/O itself: polling and writing stay in
 * MainActivity. Failure to start is non-fatal (logging continues, just without the protection).
 */
class RecordingKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Logging", NotificationManager.IMPORTANCE_LOW))
        }
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("VCDS Mobile: log recording")
            .setContentText(intent?.getStringExtra(EXTRA_FILE) ?: "Recording")
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
        }
        if (wakeLock?.isHeld != true) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vcds:recording").apply {
                setReferenceCounted(false)
                acquire(MAX_WAKE_MS)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecordingKeepAlive"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 4711
        private const val EXTRA_FILE = "file"
        private const val MAX_WAKE_MS = 4 * 60 * 60 * 1000L // hard cap; released on stop anyway

        fun start(context: Context, fileName: String) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, RecordingKeepAliveService::class.java).putExtra(EXTRA_FILE, fileName)
                )
            } catch (e: Exception) {
                Log.w(TAG, "start failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RecordingKeepAliveService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "stop failed: ${e.message}")
            }
        }
    }
}
