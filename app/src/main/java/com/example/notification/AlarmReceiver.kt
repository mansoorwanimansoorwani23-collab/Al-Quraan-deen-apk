package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.audio.AdhanPlaybackService
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.calculator.PrayerTimesCalculator
import com.example.domain.calculator.PrayerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_STOP_ADHAN) {
            AdhanPlaybackService.stopAdhan(context)
            return
        }

        val type = intent.getStringExtra("EXTRA_TYPE") ?: "PRAYER"
        val prayerKey = intent.getStringExtra("EXTRA_PRAYER_KEY") ?: "FAJR"
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Prayer Time — Hidayah"
        val message = intent.getStringExtra("EXTRA_MESSAGE") ?: "Come to prayer, come to success."
        val soundName = intent.getStringExtra("EXTRA_SOUND_NAME") ?: "Makkah Adhan"

        if (type == "PRAYER") {
            AdhanPlaybackService.startAdhan(
                context = context,
                prayerKey = prayerKey,
                title = title,
                message = message,
                soundName = soundName
            )
        } else {
            val notifId = (System.currentTimeMillis() % 100000).toInt()
            NotificationHelper.showNotification(
                context = context,
                notificationId = notifId,
                channelId = NotificationHelper.CHANNEL_REMINDER_ID,
                title = title,
                message = message
            )
        }

        // Schedule next day's upcoming prayer alarms
        PrayerAlarmScheduler.scheduleAllAlarms(context)
    }
}

class PrayerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            NotificationHelper.createNotificationChannels(context)
            PrayerAlarmScheduler.scheduleAllAlarms(context)
        }
    }
}

object PrayerAlarmScheduler {

    fun scheduleAllAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val appContext = context.applicationContext
            val userPrefs = UserPreferencesRepository(appContext)
            val settings = userPrefs.userSettingsFlow.first()

            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Cancel previous slots cleanly first so disabled prayers never fire stale alarms
            for (dayOffset in 0..1) {
                for (index in 0..4) {
                    val requestCode = (dayOffset * 10) + index + 100
                    val cancelIntent = Intent(appContext, AlarmReceiver::class.java)
                    val cancelPi = PendingIntent.getBroadcast(
                        appContext,
                        requestCode,
                        cancelIntent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (cancelPi != null) {
                        alarmManager.cancel(cancelPi)
                        cancelPi.cancel()
                    }
                }
            }

            if (!settings.notificationsEnabled) return@launch

            val timeZone = TimeZone.getTimeZone(settings.timeZoneId)
            val now = System.currentTimeMillis()

            // Schedule for Today and Tomorrow across all 5 obligatory prayers
            for (dayOffset in 0..1) {
                val cal = Calendar.getInstance(timeZone).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }

                val rawResult = PrayerTimesCalculator.calculate(
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    date = cal,
                    method = settings.calculationMethod,
                    madhhab = settings.madhhab,
                    highLatRule = settings.highLatitudeRule,
                    timeZone = timeZone
                )

                val result = PrayerTimesCalculator.applyCustomTimes(
                    calculated = rawResult,
                    useCustom = settings.useCustomPrayerTimes,
                    customFajr = settings.customFajrTime,
                    customDhuhr = settings.customDhuhrTime,
                    customAsr = settings.customAsrTime,
                    customMaghrib = settings.customMaghribTime,
                    customIsha = settings.customIshaTime,
                    timeZone = timeZone,
                    baseDate = cal
                )

                val prayers = listOf(
                    Triple(PrayerType.FAJR, result.fajr.time, settings.fajrNotification),
                    Triple(PrayerType.DHUHR, result.dhuhr.time, settings.dhuhrNotification),
                    Triple(PrayerType.ASR, result.asr.time, settings.asrNotification),
                    Triple(PrayerType.MAGHRIB, result.maghrib.time, settings.maghribNotification),
                    Triple(PrayerType.ISHA, result.isha.time, settings.ishaNotification)
                )

                prayers.forEachIndexed { index, (prayerType, timeMillis, isEnabled) ->
                    if (isEnabled && timeMillis > now) {
                        val requestCode = (dayOffset * 10) + index + 100
                        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
                            putExtra("EXTRA_TYPE", "PRAYER")
                            putExtra("EXTRA_PRAYER_KEY", prayerType.name)
                            putExtra("EXTRA_TITLE", "${prayerType.displayName} Adhan (${prayerType.arabicName})")
                            putExtra(
                                "EXTRA_MESSAGE",
                                "Time for ${prayerType.displayName} in ${settings.cityName}. Hayya 'alas-Salah."
                            )
                            putExtra("EXTRA_SOUND_NAME", settings.adhanSoundName)
                        }
                        val pendingIntent = PendingIntent.getBroadcast(
                            appContext,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        try {
                            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                alarmManager.canScheduleExactAlarms()
                            } else {
                                true
                            }
                            if (canScheduleExact) {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                            } else {
                                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                            }
                        } catch (_: Exception) {
                            alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                        }
                    }
                }
            }
        }
    }
}
