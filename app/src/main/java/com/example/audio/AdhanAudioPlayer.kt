package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdhanVoiceOption(
    val name: String,
    val subtitle: String,
    val rawResId: Int
)

/**
 * Plays authentic real Adhan MP3 recordings bundled in res/raw.
 * Never uses generic system alarm or ringtone sounds.
 */
object AdhanAudioPlayer {

    private const val TAG = "AdhanAudioPlayer"

    val ADHAN_VOICES = listOf(
        AdhanVoiceOption("Makkah Adhan", "Masjid Al-Haram Call to Prayer", R.raw.adhan_makkah),
        AdhanVoiceOption("Madinah Adhan", "Al-Masjid An-Nabawi Call to Prayer", R.raw.adhan_madinah),
        AdhanVoiceOption("Al-Aqsa Adhan", "Masjid Al-Aqsa Call to Prayer", R.raw.adhan_aqsa),
        AdhanVoiceOption("Mishary Alafasy Adhan", "Sheikh Mishary Rashid Alafasy", R.raw.adhan_mishary),
        AdhanVoiceOption("Fajr Special Adhan", "Includes As-Salatu Khayrun Minan-Nawm", R.raw.adhan_fajr)
    )

    private var mediaPlayer: MediaPlayer? = null

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    private val _currentAdhanNameFlow = MutableStateFlow<String?>(null)
    val currentAdhanNameFlow: StateFlow<String?> = _currentAdhanNameFlow.asStateFlow()

    fun isPlaying(): Boolean = _isPlayingFlow.value

    fun resolveRawResId(soundName: String, prayerKey: String? = null): Int {
        if (prayerKey.equals("FAJR", ignoreCase = true) && soundName.equals("Fajr Special Adhan", ignoreCase = true)) {
            return R.raw.adhan_fajr
        }
        return when {
            soundName.contains("Madinah", ignoreCase = true) -> R.raw.adhan_madinah
            soundName.contains("Aqsa", ignoreCase = true) -> R.raw.adhan_aqsa
            soundName.contains("Mishary", ignoreCase = true) || soundName.contains("Traditional", ignoreCase = true) -> R.raw.adhan_mishary
            soundName.contains("Fajr", ignoreCase = true) -> R.raw.adhan_fajr
            prayerKey.equals("FAJR", ignoreCase = true) -> R.raw.adhan_fajr
            else -> R.raw.adhan_makkah
        }
    }

    fun playAdhanSound(
        context: Context,
        soundName: String = "Makkah Adhan",
        prayerKey: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stopAdhan()

        val rawResId = resolveRawResId(soundName, prayerKey)
        val appContext = context.applicationContext

        try {
            val afd = appContext.resources.openRawResourceFd(rawResId)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = false
                setOnCompletionListener {
                    stopAdhan()
                    onComplete?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Adhan MediaPlayer error: what=$what, extra=$extra")
                    stopAdhan()
                    onComplete?.invoke()
                    true
                }
                prepare()
                start()
            }

            mediaPlayer = player
            _isPlayingFlow.value = true
            _currentAdhanNameFlow.value = soundName
        } catch (e: Exception) {
            Log.e(TAG, "Error playing real Adhan audio", e)
            stopAdhan()
            onComplete?.invoke()
        }
    }

    fun stopAdhan() {
        _isPlayingFlow.value = false
        _currentAdhanNameFlow.value = null
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }
}
