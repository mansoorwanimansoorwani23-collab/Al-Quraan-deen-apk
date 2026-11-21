package com.example.audio

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.notification.NotificationHelper

/**
 * Foreground Service for playing the real Adhan at prayer times with lock-screen
 * notification and Stop Adhan action. Complies with Android 14+ mediaPlayback FGS type.
 */
class AdhanPlaybackService : Service() {

    companion object {
        const val ACTION_START_ADHAN = "com.example.hidayah.ACTION_START_ADHAN"
        const val ACTION_STOP_ADHAN = "com.example.hidayah.ACTION_STOP_ADHAN"
        const val EXTRA_PRAYER_KEY = "EXTRA_PRAYER_KEY"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        const val EXTRA_SOUND_NAME = "EXTRA_SOUND_NAME"
        private const val ADHAN_FGS_NOTIFICATION_ID = 7701

        fun startAdhan(
            context: Context,
            prayerKey: String,
            title: String,
            message: String,
            soundName: String
        ) {
            val intent = Intent(context, AdhanPlaybackService::class.java).apply {
                action = ACTION_START_ADHAN
                putExtra(EXTRA_PRAYER_KEY, prayerKey)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_SOUND_NAME, soundName)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Fallback if OS temporarily restricts background FGS start
                NotificationHelper.showAdhanNotification(context, ADHAN_FGS_NOTIFICATION_ID, title, message)
                AdhanAudioPlayer.playAdhanSound(context, soundName, prayerKey)
            }
        }

        fun stopAdhan(context: Context) {
            AdhanAudioPlayer.stopAdhan()
            val intent = Intent(context, AdhanPlaybackService::class.java).apply {
                action = ACTION_STOP_ADHAN
            }
            try {
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADHAN) {
            stopAdhanAndSelf()
            return START_NOT_STICKY
        }

        val prayerKey = intent?.getStringExtra(EXTRA_PRAYER_KEY) ?: "FAJR"
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Prayer Time — Hidayah"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "Come to prayer, come to success."
        val soundName = intent?.getStringExtra(EXTRA_SOUND_NAME) ?: "Makkah Adhan"

        NotificationHelper.createNotificationChannels(this)
        val notification = NotificationHelper.buildAdhanNotification(this, title, message)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ADHAN_FGS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(ADHAN_FGS_NOTIFICATION_ID, notification)
        }

        acquireWakeLock()

        AdhanAudioPlayer.playAdhanSound(
            context = this,
            soundName = soundName,
            prayerKey = prayerKey,
            onComplete = {
                stopAdhanAndSelf()
            }
        )

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hidayah:AdhanWakeLock").apply {
                acquire(5 * 60 * 1000L) // Max 5 minutes safety timeout
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun stopAdhanAndSelf() {
        AdhanAudioPlayer.stopAdhan()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        AdhanAudioPlayer.stopAdhan()
        releaseWakeLock()
        super.onDestroy()
    }
}
