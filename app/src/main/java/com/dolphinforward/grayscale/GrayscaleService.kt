package com.dolphinforward.grayscale

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service that keeps the phone in grayscale.
 *
 * It applies grayscale on start and watches the display-daltonizer secure
 * settings. When it sees grayscale was turned off (by the user or anything
 * else), it schedules an exact alarm for the configured number of minutes;
 * when that alarm fires, [ReapplyReceiver] turns grayscale back on. Turning
 * grayscale back on before the alarm fires cancels the pending alarm.
 */
class GrayscaleService : Service() {

    private lateinit var prefs: Prefs
    private var observer: ContentObserver? = null
    private var reapplyScheduled = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()

        if (!prefs.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Apply immediately and start watching for changes.
        applyGrayscale()
        registerObserver()
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        cancelReapplyAlarm(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyGrayscale() {
        if (GrayscaleController.enableGrayscale(this)) {
            reapplyScheduled = false
            cancelReapplyAlarm(this)
        }
    }

    private fun registerObserver() {
        if (observer != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!prefs.enabled) return
                if (GrayscaleController.isGrayscaleOn(this@GrayscaleService)) {
                    // Back to grayscale - drop any pending re-apply.
                    reapplyScheduled = false
                    cancelReapplyAlarm(this@GrayscaleService)
                } else if (!reapplyScheduled) {
                    // Grayscale was switched off; re-enable after the delay.
                    scheduleReapply()
                }
            }
        }
        contentResolver.registerContentObserver(
            GrayscaleController.enabledUri, false, obs
        )
        contentResolver.registerContentObserver(
            GrayscaleController.modeUri, false, obs
        )
        observer = obs
    }

    private fun scheduleReapply() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() +
            prefs.delayMinutes * 60_000L
        val pi = reapplyPendingIntent(this)
        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi
            )
            reapplyScheduled = true
        } catch (e: SecurityException) {
            // No exact-alarm permission; fall back to an inexact alarm.
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            reapplyScheduled = true
        }
    }

    private fun startInForeground() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_running))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notif_channel_desc) }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "grayscale_service"
        private const val NOTIF_ID = 1
        private const val ALARM_REQ = 7

        fun start(context: Context) {
            val intent = Intent(context, GrayscaleService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GrayscaleService::class.java))
        }

        fun reapplyPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ReapplyReceiver::class.java)
            return PendingIntent.getBroadcast(
                context, ALARM_REQ, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun cancelReapplyAlarm(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(reapplyPendingIntent(context))
        }
    }
}
