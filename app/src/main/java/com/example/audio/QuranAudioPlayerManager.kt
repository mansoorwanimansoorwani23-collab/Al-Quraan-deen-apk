package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.example.data.local.IslamicDataSource
import com.example.data.model.Ayah
import com.example.data.model.Surah
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.QuranRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class QuranPlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val currentSurah: Surah? = null,
    val currentAyah: Ayah? = null,
    val currentAyahIndex: Int = 0,
    val totalAyahsInSurah: Int = 0,
    val selectedReciter: ReciterVoice = ReciterVoicePacks.DEFAULT_RECITER,
    val playbackProgress: Float = 0f,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null
)

class QuranAudioPlayerManager(
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(context),
    private val quranRepository: QuranRepository = QuranRepository(context)
) {

    private val TAG = "QuranAudioPlayerManager"
    private var mediaPlayer: MediaPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var lastSavedTimestampMs = 0L

    private val _playbackState = MutableStateFlow(QuranPlaybackState())
    val playbackState: StateFlow<QuranPlaybackState> = _playbackState.asStateFlow()

    private var currentAyahsList: List<Ayah> = emptyList()

    init {
        QuranAudioControllerHolder.manager = this
    }

    fun setSurahAyahs(ayahs: List<Ayah>) {
        currentAyahsList = ayahs
    }

    fun selectReciter(reciter: ReciterVoice) {
        _playbackState.value = _playbackState.value.copy(selectedReciter = reciter)
        val currentAyah = _playbackState.value.currentAyah
        val currentSurah = _playbackState.value.currentSurah
        if (currentAyah != null && currentSurah != null && (_playbackState.value.isPlaying || _playbackState.value.isPaused)) {
            playAyah(currentSurah, currentAyah, startPositionMs = 0)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.playbackParams = mediaPlayer!!.playbackParams.setSpeed(speed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing playback speed", e)
        }
    }

    /**
     * Plays a specific Ayah online (with caching) and optionally seeks to an exact saved position (ms).
     */
    fun playAyah(surah: Surah, ayah: Ayah, startPositionMs: Int = 0) {
        loadJob?.cancel()
        prefetchJob?.cancel()
        stopProgressTracker()
        releasePlayer()

        loadJob = coroutineScope.launch {
            if (currentAyahsList.isEmpty() || currentAyahsList.none { it.surahNumber == surah.number && it.numberInSurah == ayah.numberInSurah }) {
                currentAyahsList = quranRepository.getAyahsForSurah(surah)
            }

            val resolvedAyah = currentAyahsList.find {
                it.surahNumber == surah.number && it.numberInSurah == ayah.numberInSurah
            } ?: ayah

            val indexInList = currentAyahsList.indexOfFirst {
                it.surahNumber == surah.number && it.numberInSurah == resolvedAyah.numberInSurah
            }.let { if (it >= 0) it else (resolvedAyah.numberInSurah - 1).coerceIn(0, (surah.numberOfAyahs - 1).coerceAtLeast(0)) }

            val totalCount = if (currentAyahsList.isNotEmpty()) currentAyahsList.size else surah.numberOfAyahs

            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isPaused = false,
                isLoading = true,
                currentSurah = surah,
                currentAyah = resolvedAyah,
                currentAyahIndex = indexInList,
                totalAyahsInSurah = totalCount,
                playbackProgress = 0f,
                currentPositionMs = startPositionMs,
                durationMs = 0,
                errorMessage = null
            )

            val result = QuranAudioService.getAyahAudioFile(
                context = context,
                surah = surah,
                ayah = resolvedAyah,
                reciter = _playbackState.value.selectedReciter
            )

            when (result) {
                is QuranAudioResult.Success -> {
                    initAndPlayMedia(result.audioFile, surah, resolvedAyah, startPositionMs)
                }
                is QuranAudioResult.Error -> {
                    _playbackState.value = _playbackState.value.copy(
                        isLoading = false,
                        isPlaying = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /**
     * Resumes Online Quran Audio from the exact saved Juz/Surah, Ayah, audio position (ms), and Reciter.
     */
    fun resumeSavedProgress(
        surahNumber: Int,
        ayahNumber: Int,
        positionMs: Int,
        reciterId: String
    ) {
        val reciter = ReciterVoicePacks.RECITERS.find { it.id == reciterId } ?: ReciterVoicePacks.DEFAULT_RECITER
        _playbackState.value = _playbackState.value.copy(selectedReciter = reciter)

        val surah = IslamicDataSource.SURAHS.find { it.number == surahNumber } ?: IslamicDataSource.SURAHS[0]
        coroutineScope.launch {
            val surahAyahs = quranRepository.getAyahsForSurah(surah)
            currentAyahsList = surahAyahs
            val targetAyah = surahAyahs.find { it.numberInSurah == ayahNumber } ?: surahAyahs.firstOrNull()
            if (targetAyah != null) {
                playAyah(surah, targetAyah, startPositionMs = positionMs)
            }
        }
    }

    fun playNextAyah() {
        val currentSurah = _playbackState.value.currentSurah ?: return
        val currentAyah = _playbackState.value.currentAyah ?: return
        val currentIndex = _playbackState.value.currentAyahIndex

        if (currentAyahsList.isNotEmpty() && currentIndex + 1 < currentAyahsList.size) {
            val nextAyah = currentAyahsList[currentIndex + 1]
            val nextSurah = if (nextAyah.surahNumber != currentSurah.number) {
                IslamicDataSource.SURAHS.find { it.number == nextAyah.surahNumber } ?: currentSurah
            } else currentSurah
            playAyah(nextSurah, nextAyah, startPositionMs = 0)
        } else if (currentAyah.numberInSurah < currentSurah.numberOfAyahs) {
            val nextAyah = Ayah(
                numberInSurah = currentAyah.numberInSurah + 1,
                overallNumber = currentAyah.overallNumber + 1,
                surahNumber = currentSurah.number,
                arabicText = "",
                englishTranslation = "",
                juz = currentAyah.juz,
                page = currentAyah.page
            )
            playAyah(currentSurah, nextAyah, startPositionMs = 0)
        } else {
            val nextSurahNumber = currentSurah.number + 1
            val nextSurah = IslamicDataSource.SURAHS.find { it.number == nextSurahNumber }
            if (nextSurah != null) {
                coroutineScope.launch {
                    currentAyahsList = quranRepository.getAyahsForSurah(nextSurah)
                    val firstAyah = currentAyahsList.firstOrNull()
                    if (firstAyah != null) {
                        playAyah(nextSurah, firstAyah, startPositionMs = 0)
                    } else {
                        stopAndSave()
                    }
                }
            } else {
                stopAndSave()
            }
        }
    }

    fun nextAyah() = playNextAyah()

    fun playPreviousAyah() {
        val currentSurah = _playbackState.value.currentSurah ?: return
        val currentAyah = _playbackState.value.currentAyah ?: return
        val currentIndex = _playbackState.value.currentAyahIndex

        if (currentAyahsList.isNotEmpty() && currentIndex - 1 >= 0) {
            val prevAyah = currentAyahsList[currentIndex - 1]
            val prevSurah = if (prevAyah.surahNumber != currentSurah.number) {
                IslamicDataSource.SURAHS.find { it.number == prevAyah.surahNumber } ?: currentSurah
            } else currentSurah
            playAyah(prevSurah, prevAyah, startPositionMs = 0)
        } else if (currentAyah.numberInSurah > 1) {
            val prevAyah = Ayah(
                numberInSurah = currentAyah.numberInSurah - 1,
                overallNumber = (currentAyah.overallNumber - 1).coerceAtLeast(1),
                surahNumber = currentSurah.number,
                arabicText = "",
                englishTranslation = "",
                juz = currentAyah.juz,
                page = currentAyah.page
            )
            playAyah(currentSurah, prevAyah, startPositionMs = 0)
        }
    }

    fun previousAyah() = playPreviousAyah()

    fun seekTo(progress: Float) = seekToFraction(progress)

    fun release() {
        stopAndSave()
    }

    fun togglePlayPause() {
        val player = mediaPlayer
        if (player == null) {
            val surah = _playbackState.value.currentSurah ?: IslamicDataSource.SURAHS[0]
            val ayah = _playbackState.value.currentAyah
            if (ayah != null) {
                playAyah(surah, ayah, startPositionMs = _playbackState.value.currentPositionMs)
            } else {
                coroutineScope.launch {
                    val ayahs = quranRepository.getAyahsForSurah(surah)
                    currentAyahsList = ayahs
                    ayahs.firstOrNull()?.let { playAyah(surah, it, 0) }
                }
            }
            return
        }

        if (player.isPlaying) {
            val pos = try { player.currentPosition } catch (_: Exception) { _playbackState.value.currentPositionMs }
            player.pause()
            stopProgressTracker()
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isPaused = true,
                currentPositionMs = pos
            )
            persistCurrentPosition(pos)
            updateForegroundMediaNotification()
        } else {
            player.start()
            startProgressTracker()
            _playbackState.value = _playbackState.value.copy(
                isPlaying = true,
                isPaused = false
            )
            updateForegroundMediaNotification()
        }
    }

    fun seekToFraction(fraction: Float) {
        val player = mediaPlayer ?: return
        try {
            val duration = player.duration
            if (duration > 0) {
                val targetMs = (fraction * duration).toInt()
                player.seekTo(targetMs)
                _playbackState.value = _playbackState.value.copy(
                    playbackProgress = fraction.coerceIn(0f, 1f),
                    currentPositionMs = targetMs
                )
                persistCurrentPosition(targetMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking audio", e)
        }
    }

    fun stop() = stopAndSave()

    /**
     * Stops playback while persisting the exact Surah, Juz, Ayah, Position (ms), and Reciter
     * so the user can resume from the exact moment later.
     */
    fun stopAndSave() {
        val currentPos = try {
            mediaPlayer?.currentPosition ?: _playbackState.value.currentPositionMs
        } catch (_: Exception) {
            _playbackState.value.currentPositionMs
        }
        persistCurrentPosition(currentPos)

        loadJob?.cancel()
        prefetchJob?.cancel()
        stopProgressTracker()
        releasePlayer()
        QuranPlaybackService.stopService(context)

        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isPaused = false,
            isLoading = false,
            currentPositionMs = currentPos
        )
    }

    private fun persistCurrentPosition(positionMs: Int) {
        val state = _playbackState.value
        val surah = state.currentSurah ?: return
        val ayah = state.currentAyah ?: return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                preferencesRepository.saveOnlineAudioProgress(
                    surahNumber = surah.number,
                    juzNumber = ayah.juz,
                    ayahNumber = ayah.numberInSurah,
                    positionMs = positionMs,
                    reciterId = state.selectedReciter.id
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed persisting online audio progress", e)
            }
        }
    }

    private fun updateForegroundMediaNotification() {
        val state = _playbackState.value
        val surah = state.currentSurah ?: return
        val ayah = state.currentAyah ?: return
        QuranPlaybackService.startOrUpdate(
            context = context,
            title = "Surah ${surah.nameEnglish} • Ayah ${ayah.numberInSurah} (Juz ${ayah.juz})",
            subtitle = state.selectedReciter.name,
            isPlaying = state.isPlaying,
            positionMs = state.currentPositionMs,
            durationMs = state.durationMs
        )
    }

    private fun initAndPlayMedia(audioFile: File, surah: Surah, ayah: Ayah, startPositionMs: Int) {
        try {
            releasePlayer()

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                prepare()
            }

            val totalDuration = player.duration.coerceAtLeast(1)
            val safeSeekMs = if (startPositionMs in 1 until (totalDuration - 400)) startPositionMs else 0
            if (safeSeekMs > 0) {
                player.seekTo(safeSeekMs)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    player.playbackParams = player.playbackParams.setSpeed(_playbackState.value.playbackSpeed)
                } catch (_: Exception) {
                }
            }

            player.setOnCompletionListener {
                stopProgressTracker()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    playbackProgress = 1f
                )
                playNextAyah()
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                stopProgressTracker()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    isLoading = false,
                    errorMessage = "Playback error occurred. Please check internet connection and retry."
                )
                true
            }

            player.start()
            mediaPlayer = player

            _playbackState.value = _playbackState.value.copy(
                isPlaying = true,
                isPaused = false,
                isLoading = false,
                currentSurah = surah,
                currentAyah = ayah,
                currentPositionMs = safeSeekMs,
                durationMs = totalDuration,
                playbackProgress = (safeSeekMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f),
                errorMessage = null
            )

            persistCurrentPosition(safeSeekMs)
            updateForegroundMediaNotification()
            startProgressTracker()
            prefetchUpcomingAyahs(surah, ayah)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaPlayer for file ${audioFile.path}", e)
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isPaused = false,
                isLoading = false,
                errorMessage = "Unable to play audio: ${e.message}"
            )
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = coroutineScope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    try {
                        val current = player.currentPosition
                        val duration = player.duration
                        val progress = if (duration > 0) (current.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                        _playbackState.value = _playbackState.value.copy(
                            playbackProgress = progress,
                            currentPositionMs = current,
                            durationMs = duration
                        )
                        val now = System.currentTimeMillis()
                        if (now - lastSavedTimestampMs >= 2000L) {
                            lastSavedTimestampMs = now
                            persistCurrentPosition(current)
                        }
                    } catch (_: Exception) {
                    }
                }
                delay(150L)
            }
        }
    }

    private fun prefetchUpcomingAyahs(currentSurah: Surah, currentAyah: Ayah) {
        prefetchJob?.cancel()
        val reciter = _playbackState.value.selectedReciter
        val currentIdx = _playbackState.value.currentAyahIndex
        val listSnapshot = currentAyahsList
        prefetchJob = coroutineScope.launch(Dispatchers.IO) {
            for (offset in 1..2) {
                if (!isActive) break
                val nextIdx = currentIdx + offset
                if (listSnapshot.isNotEmpty() && nextIdx < listSnapshot.size) {
                    val upcoming = listSnapshot[nextIdx]
                    QuranAudioService.prefetchAyahAudio(
                        context = context,
                        surahNumber = upcoming.surahNumber,
                        ayahNumberInSurah = upcoming.numberInSurah,
                        reciter = reciter
                    )
                } else {
                    val nextNum = currentAyah.numberInSurah + offset
                    if (nextNum <= currentSurah.numberOfAyahs) {
                        QuranAudioService.prefetchAyahAudio(
                            context = context,
                            surahNumber = currentSurah.number,
                            ayahNumberInSurah = nextNum,
                            reciter = reciter
                        )
                    }
                }
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releasePlayer() {
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
