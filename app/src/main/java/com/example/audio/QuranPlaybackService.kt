package com.example.audio

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.notification.NotificationHelper

/**
 * Shared bridge so QuranPlaybackService lock-screen/notification media controls
 * communicate directly with the active QuranAudioPlayerManager instance.
 */
object QuranAudioControllerHolder {
    @Volatile
    var manager: QuranAudioPlayerManager? = null
}

/**
 * Foreground Media Playback Service for Online Quran Audio.
 * Supports background playback and lock-screen / notification media controls (Play/Pause, Previous, Next, Stop).
 */
class QuranPlaybackService : Service() {

    companion object {
        const val ACTION_UPDATE = "com.example.hidayah.QURAN_MEDIA_UPDATE"
        const val ACTION_PLAY_PAUSE = "com.example.hidayah.QURAN_MEDIA_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.hidayah.QURAN_MEDIA_NEXT"
        const val ACTION_PREV = "com.example.hidayah.QURAN_MEDIA_PREV"
        const val ACTION_STOP = "com.example.hidayah.QURAN_MEDIA_STOP"

        private const val EXTRA_TITLE = "EXTRA_TITLE"
        private const val EXTRA_SUBTITLE = "EXTRA_SUBTITLE"
        private const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        private const val EXTRA_POSITION_MS = "EXTRA_POSITION_MS"
        private const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"

        private const val QURAN_NOTIFICATION_ID = 7702

        fun startOrUpdate(
            context: Context,
            title: String,
            subtitle: String,
            isPlaying: Boolean,
            positionMs: Int,
            durationMs: Int
        ) {
            val intent = Intent(context, QuranPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_POSITION_MS, positionMs)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun stopService(context: Context) {
            try {
                context.stopService(Intent(context, QuranPlaybackService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        mediaSession = MediaSession(this, "HidayahQuranMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    QuranAudioControllerHolder.manager?.togglePlayPause()
                }

                override fun onPause() {
                    QuranAudioControllerHolder.manager?.togglePlayPause()
                }

                override fun onSkipToNext() {
                    QuranAudioControllerHolder.manager?.playNextAyah()
                }

                override fun onSkipToPrevious() {
                    QuranAudioControllerHolder.manager?.playPreviousAyah()
                }

                override fun onStop() {
                    QuranAudioControllerHolder.manager?.stopAndSave()
                    stopForegroundAndSelf()
                }
            })
            isActive = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                QuranAudioControllerHolder.manager?.togglePlayPause()
                return START_STICKY
            }
            ACTION_NEXT -> {
                QuranAudioControllerHolder.manager?.playNextAyah()
                return START_STICKY
            }
            ACTION_PREV -> {
                QuranAudioControllerHolder.manager?.playPreviousAyah()
                return START_STICKY
            }
            ACTION_STOP -> {
                QuranAudioControllerHolder.manager?.stopAndSave()
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Online Quran Audio"
                val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: "Hidayah Recitation"
                val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true
                val positionMs = intent?.getIntExtra(EXTRA_POSITION_MS, 0) ?: 0
                val durationMs = intent?.getIntExtra(EXTRA_DURATION_MS, 0) ?: 0

                updateMediaSession(title, subtitle, isPlaying, positionMs, durationMs)
                val notification = buildMediaNotification(title, subtitle, isPlaying)

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            QURAN_NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(QURAN_NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(QURAN_NOTIFICATION_ID, notification)
                }

                return START_STICKY
            }
        }
    }

    private fun updateMediaSession(
        title: String,
        subtitle: String,
        isPlaying: Boolean,
        positionMs: Int,
        durationMs: Int
    ) {
        val session = mediaSession ?: return
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Hidayah • Online Quran Audio")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.toLong())
            .build()
        session.setMetadata(metadata)

        val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_STOP

        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(stateCode, positionMs.toLong(), if (isPlaying) 1f else 0f)
            .build()
        session.setPlaybackState(playbackState)
    }

    private fun buildMediaNotification(
        title: String,
        subtitle: String,
        isPlaying: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            7702,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPendingIntent = PendingIntent.getService(
            this,
            7710,
            Intent(this, QuranPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPausePendingIntent = PendingIntent.getService(
            this,
            7711,
            Intent(this, QuranPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextPendingIntent = PendingIntent.getService(
            this,
            7712,
            Intent(this, QuranPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            7713,
            Intent(this, QuranPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NotificationHelper.CHANNEL_QURAN_MEDIA_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { token ->
            mediaStyle.setMediaSession(token)
        }

        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setContentIntent(contentIntent)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent).build())
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    playPausePendingIntent
                ).build()
            )
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", nextPendingIntent).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent).build())
            .setStyle(mediaStyle)
            .build()
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
