package com.example.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ActiveTimerManager

class TimerService : Service() {

    private val shownNotificationIds = mutableSetOf<Int>()

    companion object {
        const val CHANNEL_ID = "com.example.ui.TIMER_CHANNEL"
        const val FOREGROUND_NOTIF_ID = 9999

        const val ACTION_START = "com.example.ui.START"
        const val ACTION_UPDATE = "com.example.ui.UPDATE"
        const val ACTION_STOP = "com.example.ui.STOP"
        const val ACTION_ADD_1_MIN = "com.example.ui.ADD_1_MIN"
        const val ACTION_DISMISS = "com.example.ui.DISMISS"

        const val EXTRA_TIMER_ID = "com.example.ui.TIMER_ID"

        fun startService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_UPDATE
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_UPDATE
        val timerId = intent?.getStringExtra(EXTRA_TIMER_ID)

        when (action) {
            ACTION_START, ACTION_UPDATE -> {
                showForegroundAndNotifications()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_ADD_1_MIN -> {
                timerId?.let { id ->
                    ActiveTimerManager.addMinutes(id, 1)
                }
            }
            ACTION_DISMISS -> {
                timerId?.let { id ->
                    ActiveTimerManager.removeTimer(id)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun showForegroundAndNotifications() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val timers = ActiveTimerManager.activeTimers.value

        val currentNotificationIds = timers.map { it.id.hashCode() }.toSet()
        shownNotificationIds.forEach { id ->
            if (id !in currentNotificationIds) {
                manager.cancel(id)
            }
        }
        shownNotificationIds.clear()
        shownNotificationIds.addAll(currentNotificationIds)

        if (timers.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val runningCount = timers.count { it.status == TimerStatus.RUNNING }
        val finishedCount = timers.count { it.status == TimerStatus.FINISHED }

        val contentText = when {
            finishedCount > 0 && runningCount > 0 -> "$finishedCount finished, $runningCount active countdowns"
            finishedCount > 0 -> "$finishedCount timer countdown complete!"
            runningCount > 0 -> "$runningCount running"
            else -> "Active"
        }

        // 1. Create main foreground summary notification required by OS
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MultiTimer Running")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setLocalOnly(true)
            .build()

        // Bind Foreground Service immediately (required within limits)
        startForeground(FOREGROUND_NOTIF_ID, mainNotification)

        // 2. Generate/Update individual notifications per active or finished timer
        timers.forEach { timer ->
            val notificationId = timer.id.hashCode()

            // Setup customized local actions for this specific notification
            val addIntent = Intent(this, TimerService::class.java).apply {
                action = ACTION_ADD_1_MIN
                putExtra(EXTRA_TIMER_ID, timer.id)
            }
            val addPendingIntent = PendingIntent.getService(
                this,
                notificationId * 3 + 1,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(this, TimerService::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_TIMER_ID, timer.id)
            }
            val dismissPendingIntent = PendingIntent.getService(
                this,
                notificationId * 3 + 2,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(timer.label.uppercase())
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(false)
                .setLocalOnly(true)

            if (timer.status == TimerStatus.FINISHED) {
                builder.setContentText("Finished / Click Dismiss or Add a Min")
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setColor(0xFFFF2A85.toInt()) // highlight with Geometric pulsing secondary brand color
            } else if (timer.status == TimerStatus.PAUSED) {
                builder.setContentText("Paused: ${formatSeconds(timer.remainingSeconds)}")
                    .setOngoing(false)
            } else {
                // Running countdown showing real-time system tick!
                val remainingMillis = timer.remainingSeconds * 1000L
                builder.setContentText("Running countdown")
                    .setWhen(System.currentTimeMillis() + remainingMillis)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setOngoing(true)
            }

            // Options: Dismiss and Add +1 Min
            builder.addAction(android.R.drawable.ic_input_add, "+1 Min", addPendingIntent)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)

            manager.notify(notificationId, builder.build())
        }

        // Clean up any stale notifications if list changed
        // (Not strictly required for most triggers but keeps notification trays exceptionally tidy!)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Countdowns",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Provides notifications and action buttons for active multitimer counters."
                setShowBadge(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun formatSeconds(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shownNotificationIds.forEach { id ->
            manager.cancel(id)
        }
        shownNotificationIds.clear()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
