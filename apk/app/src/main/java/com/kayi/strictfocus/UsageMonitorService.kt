package com.kayi.strictfocus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Best-effort app monitor that uses Usage Access instead of Accessibility.
 * Android and some manufacturers may delay or stop background monitoring,
 * so this cannot guarantee instant blocking on every device.
 */
class UsageMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastKick = 0L
    private val poll = object : Runnable {
        override fun run() {
            if (!BlockerPrefs.isLocked(this@UsageMonitorService) && !BlockerPrefs.hasSchedule(this@UsageMonitorService)) {
                stopSelf()
                return
            }
            checkForegroundApp()
            // Poll more frequently to reduce the app-switch detection delay.
            handler.postDelayed(this, 300L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, getString(R.string.monitor_channel))
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.monitor_notification))
            .setOngoing(true)
            .build()
        startForeground(7301, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_STICKY
    }

    private fun checkForegroundApp() {
        val blocked = BlockerPrefs.blockedNow(this)
        if (blocked.isEmpty()) return
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // A short window avoids repeatedly acting on old foreground events.
        val events = manager.queryEvents(now - 3_000L, now)
        val event = UsageEvents.Event()
        var foreground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)) {
                foreground = event.packageName
            }
        }
        if (foreground != null && foreground in blocked) {
            val elapsed = android.os.SystemClock.elapsedRealtime()
            if (elapsed - lastKick > 500L) {
                lastKick = elapsed
                val home = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { startActivity(home) } catch (_: Exception) { }
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                getString(R.string.monitor_channel),
                getString(R.string.monitor_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }
}