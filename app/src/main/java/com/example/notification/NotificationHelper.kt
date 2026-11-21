package com.example.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    // Fresh channel ID with silent channel audio so Android never overlays a generic phone alarm over the real Adhan
    const val CHANNEL_PRAYER_ID = "hidayah_adhan_channel_v2"
    const val CHANNEL_QURAN_MEDIA_ID = "hidayah_quran_audio_channel"
    const val CHANNEL_REMINDER_ID = "hidayah_reminder_channel"
    const val ACTION_STOP_ADHAN = "com.example.ACTION_STOP_ADHAN"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val prayerChannel = NotificationChannel(
                CHANNEL_PRAYER_ID,
                "Prayer Times & Real Adhan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lock-screen alerts and real Adhan playback for Fajr, Dhuhr, Asr, Maghrib, and Isha"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(null, null) // Sound is played strictly by Hidayah's real Adhan audio player
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val quranMediaChannel = NotificationChannel(
                CHANNEL_QURAN_MEDIA_ID,
                "Online Quran Audio Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background & lock-screen media controls for Online Quran recitation"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER_ID,
                "Daily Islamic Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Morning/Evening Azkar and Daily Ayah/Hadith"
            }

            notificationManager.createNotificationChannel(prayerChannel)
            notificationManager.createNotificationChannel(quranMediaChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    fun buildAdhanNotification(
        context: Context,
        title: String,
        message: String
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            7701,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_STOP_ADHAN
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            9999,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_PRAYER_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Adhan", stopPendingIntent)
            .build()
    }

    fun showAdhanNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        createNotificationChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, buildAdhanNotification(context, title, message))
    }

    fun showNotification(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        message: String,
        subText: String? = null
    ) {
        createNotificationChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (subText != null) {
            builder.setSubText(subText)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }
}
