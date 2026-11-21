package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class AiVoicePlaybackState(
    val activeMessageId: String? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isUsingDeviceTts: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val activeVoiceName: String = "Kore",
    val activeModelName: String = "",
    val statusMessage: String? = null,
    val preloadedMessageIds: Set<String> = emptySet(),
    val preloadingMessageIds: Set<String> = emptySet()
) {
    val isActive: Boolean get() = isLoading || isPlaying || isPaused
    val progressFraction: Float
        get() = if (totalDurationMs > 0L) {
            (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

private data class PreloadedMessageBuffer(
    val cacheSignature: String,
    val sampleRateHz: Int,
    val modelId: String,
    val pcmBytes: ByteArray,
    val isComplete: Boolean
)

/**
 * Seekable, Pausable, Resumable AI Voice Player with Automatic Background Preloading.
 *
 * Key capabilities:
 * - Preloads voice PCM audio automatically in the background while an AI answer streams in,
 *   so pressing Listen starts instantaneously (0ms wait!).
 * - Supports Pause & Resume from the EXACT same millisecond/syllable.
 * - Supports Rewind (-5s), Forward (+5s), interactive Seekbar scrubbing, and Speed (0.75x–1.5x).
 * - Supports the same Pause / Resume / Back / Forward / Speed controls when using Device TTS fallback,
 *   with automatic Hindi/Arabic/English locale detection.
 */
class AiVoicePlayer(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AiVoicePlaybackState())
    val state: StateFlow<AiVoicePlaybackState> = _state.asStateFlow()

    // Background preload jobs & ready PCM buffers per messageId (capped to last 8 messages)
    private val preloadJobs = ConcurrentHashMap<String, Job>()
    private val preloadedBuffers = ConcurrentHashMap<String, PreloadedMessageBuffer>()
    private val preloadedOrder = ArrayDeque<String>()

    // Active playback jobs & state
    private var playbackJob: Job? = null
    private var fetchJob: Job? = null
    private val sessionCounter = AtomicLong(0L)

    // PCM buffer being played (grows live while chunks stream in, allowing instant seeking)
    private val pcmLock = Any()
    private var pcmBuffer = ByteArray(0)
    @Volatile
    private var pcmFullyLoaded = false
    @Volatile
    private var currentSampleRateHz = 24000
    private val playByteOffset = AtomicInteger(0)
    private val seekRequested = AtomicBoolean(false)
    private val pausedFlag = AtomicBoolean(false)

    @Volatile
    private var audioTrack: AudioTrack? = null

    // Device TTS fallback state (sentence-indexed for Pause / Resume / Back / Forward)
    private var deviceTts: TextToSpeech? = null
    @Volatile
    private var isDeviceTtsReady = false
    private var ttsSentences: List<String> = emptyList()
    private val ttsSentenceIndex = AtomicInteger(0)
    @Volatile
    private var ttsSessionId = 0L
    @Volatile
    private var pendingTtsStartSessionId = -1L

    fun prewarmEngine() {
        ensureDeviceTtsInitialized()
    }

    private fun ensureDeviceTtsInitialized() {
        if (deviceTts != null) return
        runCatching {
            deviceTts = TextToSpeech(appContext) { status ->
                isDeviceTtsReady = status == TextToSpeech.SUCCESS
                if (isDeviceTtsReady) {
                    deviceTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId?.startsWith("ai_${ttsSessionId}_") == true) {
                                updateTtsProgressUi(isPlaying = true, isPaused = false)
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            val expectedPrefix = "ai_${ttsSessionId}_"
                            if (utteranceId == null || !utteranceId.startsWith(expectedPrefix)) return
                            if (pausedFlag.get() || sessionCounter.get() != ttsSessionId) return
                            val nextIdx = ttsSentenceIndex.incrementAndGet()
                            if (nextIdx < ttsSentences.size) {
                                speakCurrentTtsSentence(ttsSessionId)
                            } else {
                                scope.launch {
                                    if (sessionCounter.get() == ttsSessionId) {
                                        _state.update {
                                            it.copy(
                                                isPlaying = false,
                                                isPaused = false,
                                                isLoading = false,
                                                currentPositionMs = it.totalDurationMs,
                                                activeMessageId = null,
                                                statusMessage = null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            scope.launch {
                                if (sessionCounter.get() == ttsSessionId) {
                                    _state.update {
                                        it.copy(isPlaying = false, isPaused = false, isLoading = false)
                                    }
                                }
                            }
                        }
                    })

                    if (pendingTtsStartSessionId == sessionCounter.get() && !pausedFlag.get()) {
                        pendingTtsStartSessionId = -1L
                        speakCurrentTtsSentence(ttsSessionId)
                    }
                }
            }
        }
    }

    /**
     * Automatically preloads voice audio in the background while an AI answer streams/finishes,
     * so tapping Listen plays instantaneously with zero wait.
     */
    fun preloadMessageVoice(
        messageId: String,
        rawText: String,
        apiKey: String,
        audioModelId: String,
        fallbackTtsModelId: String?,
        voiceName: String,
        isFinalText: Boolean
    ) {
        val cleanText = GeminiAiService.cleanTextForSpeech(rawText)
        if (cleanText.length < 20 || apiKey.isBlank()) return

        val effectiveModel = resolveEffectiveAudioModel(audioModelId, fallbackTtsModelId) ?: return
        val safeVoice = voiceName.ifBlank { "Kore" }
        val signature = "$effectiveModel|$safeVoice|${cleanText.hashCode()}|${cleanText.length}"

        val existing = preloadedBuffers[messageId]
        if (existing != null && existing.cacheSignature == signature && existing.isComplete) {
            return
        }
        // If we already have an active preload for the exact same final text, keep it running
        if (!isFinalText && preloadJobs[messageId]?.isActive == true) {
            return
        }

        preloadJobs.remove(messageId)?.cancel()

        _state.update {
            it.copy(preloadingMessageIds = it.preloadingMessageIds + messageId)
        }

        val job = scope.launch(Dispatchers.IO) {
            try {
                val chunks = GeminiAiService.splitIntoSpeechChunks(
                    rawText = cleanText,
                    firstChunkMaxChars = 150,
                    nextChunkMaxChars = 320
                )
                if (chunks.isEmpty()) return@launch

                // During live streaming, preload only Chunk 0 immediately for instant start!
                // Once final text arrives, preload all chunks sequentially/concurrently.
                val targetChunks = if (isFinalText) chunks else listOf(chunks.first())
                val combined = ByteArrayOutputStream()
                var sampleRate = 24000
                var activeModel = effectiveModel

                // Fetch Chunk 0 first to validate native audio support
                var res0 = GeminiAiService.fetchAudioChunkWithCache(apiKey.trim(), activeModel, targetChunks[0], safeVoice)
                if (res0 is GeminiAudioResult.FallbackToDeviceTts) {
                    GeminiAiService.markModelWithoutNativeAudio(activeModel)
                    val fb = fallbackTtsModelId?.removePrefix("models/")?.trim().orEmpty()
                    if (fb.isNotBlank() && fb != activeModel && !GeminiAiService.isModelKnownWithoutNativeAudio(fb)) {
                        activeModel = fb
                        res0 = GeminiAiService.fetchAudioChunkWithCache(apiKey.trim(), activeModel, targetChunks[0], safeVoice)
                        if (res0 is GeminiAudioResult.FallbackToDeviceTts) {
                            GeminiAiService.markModelWithoutNativeAudio(activeModel)
                        }
                    }
                }

                if (res0 !is GeminiAudioResult.NativePcmAudio) {
                    return@launch
                }

                sampleRate = res0.sampleRateHz
                combined.write(res0.pcmBytes)

                // Immediately mark ready as soon as Chunk 0 is cached!
                storePreloadedBuffer(
                    messageId = messageId,
                    buffer = PreloadedMessageBuffer(
                        cacheSignature = signature,
                        sampleRateHz = sampleRate,
                        modelId = activeModel,
                        pcmBytes = combined.toByteArray(),
                        isComplete = isFinalText && targetChunks.size == 1
                    )
                )

                // Preload remaining chunks in parallel batches of 2 so full answer is seekable instantly
                if (isFinalText && targetChunks.size > 1) {
                    var idx = 1
                    while (idx < targetChunks.size && isActive) {
                        val batchEnd = minOf(targetChunks.size, idx + 2)
                        val deferreds = (idx until batchEnd).map { chunkIdx ->
                            async(Dispatchers.IO) {
                                GeminiAiService.fetchAudioChunkWithCache(
                                    apiKey.trim(),
                                    activeModel,
                                    targetChunks[chunkIdx],
                                    safeVoice
                                )
                            }
                        }
                        for (d in deferreds) {
                            val r = d.await()
                            if (r is GeminiAudioResult.NativePcmAudio) {
                                combined.write(r.pcmBytes)
                            }
                        }
                        idx = batchEnd
                    }

                    if (isActive) {
                        storePreloadedBuffer(
                            messageId = messageId,
                            buffer = PreloadedMessageBuffer(
                                cacheSignature = signature,
                                sampleRateHz = sampleRate,
                                modelId = activeModel,
                                pcmBytes = combined.toByteArray(),
                                isComplete = true
                            )
                        )
                    }
                }
            } catch (_: CancellationException) {
                // Canceled cleanly
            } catch (_: Exception) {
                // Best-effort preload; user can still play normally on tap
            } finally {
                _state.update {
                    it.copy(preloadingMessageIds = it.preloadingMessageIds - messageId)
                }
            }
        }

        preloadJobs[messageId] = job
    }

    private fun storePreloadedBuffer(messageId: String, buffer: PreloadedMessageBuffer) {
        synchronized(preloadedOrder) {
            preloadedBuffers[messageId] = buffer
            preloadedOrder.remove(messageId)
            preloadedOrder.addLast(messageId)
            while (preloadedOrder.size > 8) {
                val evicted = preloadedOrder.removeFirst()
                preloadedBuffers.remove(evicted)
            }
        }
        _state.update {
            it.copy(preloadedMessageIds = it.preloadedMessageIds + messageId)
        }
    }

    private fun resolveEffectiveAudioModel(audioModelId: String, fallbackTtsModelId: String?): String? {
        val primary = audioModelId.removePrefix("models/").trim()
        val fallback = fallbackTtsModelId?.removePrefix("models/")?.trim().orEmpty()
        return when {
            primary.isNotBlank() && !GeminiAiService.isModelKnownWithoutNativeAudio(primary) -> primary
            fallback.isNotBlank() && !GeminiAiService.isModelKnownWithoutNativeAudio(fallback) -> fallback
            else -> null
        }
    }

    /**
     * Plays a message (or resumes/pauses if it's already the active message!).
     */
    fun playOrToggleMessage(
        textToRead: String,
        messageId: String?,
        apiKey: String,
        audioModelId: String,
        fallbackTtsModelId: String?,
        voiceName: String
    ) {
        val current = _state.value
        if (messageId != null && current.activeMessageId == messageId && (current.isPlaying || current.isPaused)) {
            togglePauseResume()
            return
        }

        val cleanText = GeminiAiService.cleanTextForSpeech(textToRead)
        if (cleanText.isBlank()) return

        stop()
        ensureDeviceTtsInitialized()

        val sessionId = sessionCounter.incrementAndGet()
        val safeVoice = voiceName.ifBlank { "Kore" }
        val effectiveModel = resolveEffectiveAudioModel(audioModelId, fallbackTtsModelId)

        if (apiKey.isBlank() || effectiveModel == null) {
            startDeviceTtsPlayback(
                sessionId = sessionId,
                messageId = messageId,
                cleanText = cleanText,
                voiceName = safeVoice
            )
            return
        }

        // Check if we ALREADY preloaded the complete PCM audio for this message! (0ms instant start!)
        val signature = "$effectiveModel|$safeVoice|${cleanText.hashCode()}|${cleanText.length}"
        val preloaded = if (messageId != null) preloadedBuffers[messageId] else null

        if (preloaded != null && preloaded.cacheSignature == signature && preloaded.isComplete && preloaded.pcmBytes.isNotEmpty()) {
            synchronized(pcmLock) {
                pcmBuffer = preloaded.pcmBytes
                currentSampleRateHz = preloaded.sampleRateHz
                pcmFullyLoaded = true
            }
            playByteOffset.set(0)
            pausedFlag.set(false)
            seekRequested.set(false)

            val totalMs = bytesToMs(preloaded.pcmBytes.size, preloaded.sampleRateHz)
            _state.update {
                it.copy(
                    activeMessageId = messageId,
                    isLoading = false,
                    isPlaying = true,
                    isPaused = false,
                    isUsingDeviceTts = false,
                    currentPositionMs = 0L,
                    totalDurationMs = totalMs,
                    activeVoiceName = safeVoice,
                    activeModelName = preloaded.modelId,
                    statusMessage = null
                )
            }
            startPcmPlaybackLoop(sessionId)
            return
        }

        // Otherwise, stream chunks progressively into our seekable PCM buffer & start playing immediately!
        synchronized(pcmLock) {
            pcmBuffer = ByteArray(0)
            currentSampleRateHz = 24000
            pcmFullyLoaded = false
        }
        playByteOffset.set(0)
        pausedFlag.set(false)
        seekRequested.set(false)

        _state.update {
            it.copy(
                activeMessageId = messageId,
                isLoading = true,
                isPlaying = false,
                isPaused = false,
                isUsingDeviceTts = false,
                currentPositionMs = 0L,
                totalDurationMs = 0L,
                activeVoiceName = safeVoice,
                activeModelName = effectiveModel,
                statusMessage = "Loading voice..."
            )
        }

        fetchJob = scope.launch(Dispatchers.IO) {
            try {
                val chunks = GeminiAiService.splitIntoSpeechChunks(
                    rawText = cleanText,
                    firstChunkMaxChars = 150,
                    nextChunkMaxChars = 320
                )
                if (chunks.isEmpty()) {
                    stop()
                    return@launch
                }

                var activeModel = effectiveModel
                var res0 = GeminiAiService.fetchAudioChunkWithCache(apiKey.trim(), activeModel, chunks[0], safeVoice)

                if (res0 is GeminiAudioResult.FallbackToDeviceTts) {
                    GeminiAiService.markModelWithoutNativeAudio(activeModel)
                    val fb = fallbackTtsModelId?.removePrefix("models/")?.trim().orEmpty()
                    if (fb.isNotBlank() && fb != activeModel && !GeminiAiService.isModelKnownWithoutNativeAudio(fb)) {
                        activeModel = fb
                        res0 = GeminiAiService.fetchAudioChunkWithCache(apiKey.trim(), activeModel, chunks[0], safeVoice)
                        if (res0 is GeminiAudioResult.FallbackToDeviceTts) {
                            GeminiAiService.markModelWithoutNativeAudio(activeModel)
                        }
                    }
                }

                if (sessionCounter.get() != sessionId || !isActive) return@launch

                when (val first = res0) {
                    is GeminiAudioResult.FallbackToDeviceTts -> {
                        withContext(Dispatchers.Main.immediate) {
                            startDeviceTtsPlayback(sessionId, messageId, cleanText, safeVoice)
                        }
                        return@launch
                    }
                    is GeminiAudioResult.Error -> {
                        withContext(Dispatchers.Main.immediate) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    isPlaying = false,
                                    isPaused = false,
                                    statusMessage = first.message
                                )
                            }
                        }
                        return@launch
                    }
                    is GeminiAudioResult.NativePcmAudio -> {
                        appendPcmBytes(first.pcmBytes, first.sampleRateHz, isLast = chunks.size == 1)

                        withContext(Dispatchers.Main.immediate) {
                            val totalMs = bytesToMs(getPcmSize(), first.sampleRateHz)
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    isPlaying = true,
                                    isPaused = false,
                                    activeModelName = activeModel,
                                    totalDurationMs = totalMs,
                                    statusMessage = null
                                )
                            }
                            startPcmPlaybackLoop(sessionId)
                        }

                        // Fetch remaining chunks concurrently in batches of 2 while Chunk 0 plays!
                        var idx = 1
                        while (idx < chunks.size && sessionCounter.get() == sessionId && isActive) {
                            val batchEnd = minOf(chunks.size, idx + 2)
                            val deferreds = (idx until batchEnd).map { cIdx ->
                                async(Dispatchers.IO) {
                                    GeminiAiService.fetchAudioChunkWithCache(
                                        apiKey.trim(),
                                        activeModel,
                                        chunks[cIdx],
                                        safeVoice
                                    )
                                }
                            }
                            for ((offsetIdx, d) in deferreds.withIndex()) {
                                val r = d.await()
                                if (sessionCounter.get() != sessionId || !isActive) return@launch
                                val isLast = (idx + offsetIdx) == chunks.lastIndex
                                if (r is GeminiAudioResult.NativePcmAudio) {
                                    appendPcmBytes(r.pcmBytes, r.sampleRateHz, isLast = isLast)
                                } else if (isLast) {
                                    markPcmFullyLoaded()
                                }
                            }
                            idx = batchEnd
                        }

                        markPcmFullyLoaded()
                        // Save completed buffer into preloaded cache for instant future replay!
                        if (messageId != null && sessionCounter.get() == sessionId) {
                            val finalBytes = snapshotPcmBytes()
                            storePreloadedBuffer(
                                messageId = messageId,
                                buffer = PreloadedMessageBuffer(
                                    cacheSignature = "$activeModel|$safeVoice|${cleanText.hashCode()}|${cleanText.length}",
                                    sampleRateHz = currentSampleRateHz,
                                    modelId = activeModel,
                                    pcmBytes = finalBytes,
                                    isComplete = true
                                )
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Canceled cleanly
            } catch (e: Exception) {
                if (sessionCounter.get() == sessionId) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isPlaying = false,
                            isPaused = false,
                            statusMessage = "Voice error: ${e.localizedMessage ?: "Check connection"}"
                        )
                    }
                }
            }
        }
    }

    private fun startPcmPlaybackLoop(sessionId: Long) {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                val sr = currentSampleRateHz
                track = createAudioTrack(sr)
                audioTrack = track
                applySpeedToTrack(track, _state.value.playbackSpeed)
                track.play()

                val writeFrameSize = 3840 // ~40ms of 24kHz 16-bit mono audio for super responsive Pause/Seek!
                var lastUiUpdateMs = 0L

                while (sessionCounter.get() == sessionId && isActive) {
                    if (pausedFlag.get()) {
                        delay(45L)
                        continue
                    }

                    if (seekRequested.compareAndSet(true, false)) {
                        runCatching {
                            track.pause()
                            track.flush()
                            track.play()
                        }
                    }

                    val offset = playByteOffset.get() and -2 // Keep 16-bit PCM sample alignment
                    val chunkToPlay: ByteArray?
                    val totalSize: Int
                    val doneLoading: Boolean

                    synchronized(pcmLock) {
                        totalSize = pcmBuffer.size
                        doneLoading = pcmFullyLoaded
                        if (offset < totalSize) {
                            val count = minOf(writeFrameSize, totalSize - offset) and -2
                            chunkToPlay = if (count > 0) pcmBuffer.copyOfRange(offset, offset + count) else null
                        } else {
                            chunkToPlay = null
                        }
                    }

                    if (chunkToPlay != null) {
                        val written = track.write(chunkToPlay, 0, chunkToPlay.size)
                        if (written <= 0) break

                        // Only advance if user didn't seek mid-write
                        if (!seekRequested.get()) {
                            playByteOffset.compareAndSet(offset, offset + written)
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdateMs >= 80L) {
                            lastUiUpdateMs = now
                            val posMs = bytesToMs(playByteOffset.get(), sr)
                            val durMs = bytesToMs(totalSize, sr)
                            _state.update {
                                it.copy(
                                    currentPositionMs = posMs.coerceAtMost(durMs),
                                    totalDurationMs = durMs
                                )
                            }
                        }
                    } else if (doneLoading) {
                        // Finished playing all PCM audio!
                        val durMs = bytesToMs(totalSize, sr)
                        _state.update {
                            it.copy(
                                isPlaying = false,
                                isPaused = false,
                                isLoading = false,
                                currentPositionMs = durMs,
                                totalDurationMs = durMs,
                                statusMessage = null
                            )
                        }
                        break
                    } else {
                        // Waiting briefly for next chunk to arrive from background fetch
                        delay(40L)
                    }
                }
            } catch (_: CancellationException) {
                // Stopped cleanly
            } catch (_: Exception) {
                // Handled in finally
            } finally {
                if (audioTrack === track) {
                    audioTrack = null
                }
                releaseTrackQuietly(track)
            }
        }
    }

    /**
     * Pauses playback right where it is, or resumes from the EXACT paused position!
     */
    fun togglePauseResume() {
        val current = _state.value
        if (!current.isPlaying && !current.isPaused) return

        if (current.isUsingDeviceTts) {
            if (current.isPlaying) {
                pausedFlag.set(true)
                runCatching { deviceTts?.stop() }
                updateTtsProgressUi(isPlaying = false, isPaused = true)
            } else {
                pausedFlag.set(false)
                updateTtsProgressUi(isPlaying = true, isPaused = false)
                speakCurrentTtsSentence(ttsSessionId)
            }
            return
        }

        if (current.isPlaying) {
            pausedFlag.set(true)
            runCatching { audioTrack?.pause() }
            _state.update { it.copy(isPlaying = false, isPaused = true) }
        } else {
            pausedFlag.set(false)
            val totalBytes = getPcmSize()
            if (playByteOffset.get() >= totalBytes && totalBytes > 0) {
                playByteOffset.set(0)
            }
            runCatching { audioTrack?.play() }
            _state.update { it.copy(isPlaying = true, isPaused = false) }
            if (playbackJob?.isActive != true && totalBytes > 0) {
                startPcmPlaybackLoop(sessionCounter.get())
            }
        }
    }

    /**
     * Rewinds or fast-forwards by [deltaMs] (e.g., -5000L for -5s, +5000L for +5s).
     */
    fun seekByMs(deltaMs: Long) {
        val current = _state.value
        if (!current.isActive) return

        if (current.isUsingDeviceTts) {
            if (ttsSentences.isEmpty()) return
            val step = if (deltaMs < 0) -1 else 1
            val nextIdx = (ttsSentenceIndex.get() + step).coerceIn(0, ttsSentences.lastIndex)
            ttsSentenceIndex.set(nextIdx)
            updateTtsProgressUi(isPlaying = !pausedFlag.get(), isPaused = pausedFlag.get())
            if (!pausedFlag.get()) {
                speakCurrentTtsSentence(ttsSessionId)
            }
            return
        }

        val sr = currentSampleRateHz
        val totalBytes = getPcmSize()
        if (totalBytes <= 0) return

        val deltaBytes = msToBytes(deltaMs, sr)
        val targetBytes = ((playByteOffset.get() + deltaBytes).coerceIn(0, totalBytes)) and -2
        playByteOffset.set(targetBytes)
        seekRequested.set(true)

        val posMs = bytesToMs(targetBytes, sr)
        val durMs = bytesToMs(totalBytes, sr)
        _state.update {
            it.copy(
                currentPositionMs = posMs,
                totalDurationMs = durMs
            )
        }

        // If playback had already reached the end and user rewinds, restart playback loop!
        if (!pausedFlag.get() && playbackJob?.isActive != true && targetBytes < totalBytes) {
            _state.update { it.copy(isPlaying = true, isPaused = false) }
            startPcmPlaybackLoop(sessionCounter.get())
        }
    }

    /**
     * Scrubs to an exact timeline fraction (0.0f .. 1.0f).
     */
    fun seekToFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        val current = _state.value
        if (!current.isActive) return

        if (current.isUsingDeviceTts) {
            if (ttsSentences.isEmpty()) return
            val targetIdx = (clamped * ttsSentences.lastIndex).toInt().coerceIn(0, ttsSentences.lastIndex)
            ttsSentenceIndex.set(targetIdx)
            updateTtsProgressUi(isPlaying = !pausedFlag.get(), isPaused = pausedFlag.get())
            if (!pausedFlag.get()) {
                speakCurrentTtsSentence(ttsSessionId)
            }
            return
        }

        val sr = currentSampleRateHz
        val totalBytes = getPcmSize()
        if (totalBytes <= 0) return

        val targetBytes = ((clamped * totalBytes).toInt().coerceIn(0, totalBytes)) and -2
        playByteOffset.set(targetBytes)
        seekRequested.set(true)

        val posMs = bytesToMs(targetBytes, sr)
        val durMs = bytesToMs(totalBytes, sr)
        _state.update {
            it.copy(
                currentPositionMs = posMs,
                totalDurationMs = durMs
            )
        }
    }

    /**
     * Cycles voice playback speed: 1.0x -> 1.25x -> 1.5x -> 0.75x -> 1.0x.
     */
    fun cyclePlaybackSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 0.75f)
        val current = _state.value.playbackSpeed
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val nextSpeed = speeds[(idx + 1) % speeds.size]
        _state.update { it.copy(playbackSpeed = nextSpeed) }

        if (_state.value.isUsingDeviceTts) {
            runCatching { deviceTts?.setSpeechRate(nextSpeed) }
            if (_state.value.isPlaying && !pausedFlag.get()) {
                speakCurrentTtsSentence(ttsSessionId)
            }
        } else {
            audioTrack?.let { applySpeedToTrack(it, nextSpeed) }
        }
    }

    fun stop() {
        sessionCounter.incrementAndGet()
        pausedFlag.set(false)
        seekRequested.set(false)
        pendingTtsStartSessionId = -1L

        fetchJob?.cancel()
        fetchJob = null
        playbackJob?.cancel()
        playbackJob = null

        GeminiAiService.stopPcmAudio()
        val track = audioTrack
        audioTrack = null
        releaseTrackQuietly(track)

        runCatching { deviceTts?.stop() }

        _state.update {
            it.copy(
                activeMessageId = null,
                isLoading = false,
                isPlaying = false,
                isPaused = false,
                isUsingDeviceTts = false,
                currentPositionMs = 0L,
                totalDurationMs = 0L,
                statusMessage = null
            )
        }
    }

    fun clearPreloadedAudio() {
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
        synchronized(preloadedOrder) {
            preloadedBuffers.clear()
            preloadedOrder.clear()
        }
        _state.update {
            it.copy(preloadedMessageIds = emptySet(), preloadingMessageIds = emptySet())
        }
    }

    fun release() {
        stop()
        clearPreloadedAudio()
        runCatching { deviceTts?.shutdown() }
        deviceTts = null
    }

    // ================= DEVICE TTS FALLBACK WITH PAUSE / RESUME / SEEK =================

    private fun startDeviceTtsPlayback(
        sessionId: Long,
        messageId: String?,
        cleanText: String,
        voiceName: String
    ) {
        ttsSessionId = sessionId
        ttsSentences = GeminiAiService.splitIntoSpeechChunks(
            rawText = cleanText,
            firstChunkMaxChars = 120,
            nextChunkMaxChars = 180
        ).ifEmpty { listOf(cleanText) }
        ttsSentenceIndex.set(0)
        pausedFlag.set(false)

        val estTotalMs = ttsSentences.size * 4500L
        _state.update {
            it.copy(
                activeMessageId = messageId,
                isLoading = !isDeviceTtsReady,
                isPlaying = isDeviceTtsReady,
                isPaused = false,
                isUsingDeviceTts = true,
                currentPositionMs = 0L,
                totalDurationMs = estTotalMs,
                activeVoiceName = "$voiceName (Device)",
                activeModelName = "Device Voice",
                statusMessage = null
            )
        }

        if (isDeviceTtsReady) {
            speakCurrentTtsSentence(sessionId)
        } else {
            pendingTtsStartSessionId = sessionId
            ensureDeviceTtsInitialized()
        }
    }

    private fun speakCurrentTtsSentence(sessionId: Long) {
        val tts = deviceTts ?: return
        if (sessionCounter.get() != sessionId || pausedFlag.get()) return
        val idx = ttsSentenceIndex.get().coerceIn(0, (ttsSentences.size - 1).coerceAtLeast(0))
        val sentence = ttsSentences.getOrNull(idx) ?: return

        // Auto-detect Hindi / Arabic / English locale for natural pronunciation
        val locale = detectSpeechLocale(sentence)
        runCatching {
            val avail = tts.isLanguageAvailable(locale)
            if (avail >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = locale
            }
            tts.setSpeechRate(_state.value.playbackSpeed)
        }
        tts.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "ai_${sessionId}_$idx")
    }

    private fun updateTtsProgressUi(isPlaying: Boolean, isPaused: Boolean) {
        val count = ttsSentences.size.coerceAtLeast(1)
        val idx = ttsSentenceIndex.get().coerceIn(0, count)
        val estTotalMs = count * 4500L
        val estPosMs = (idx * 4500L).coerceAtMost(estTotalMs)
        _state.update {
            it.copy(
                isLoading = false,
                isPlaying = isPlaying,
                isPaused = isPaused,
                currentPositionMs = estPosMs,
                totalDurationMs = estTotalMs
            )
        }
    }

    private fun detectSpeechLocale(text: String): Locale {
        var devanagariCount = 0
        var arabicCount = 0
        for (ch in text) {
            when (ch.code) {
                in 0x0900..0x097F -> devanagariCount++
                in 0x0600..0x06FF -> arabicCount++
            }
        }
        return when {
            devanagariCount >= 3 -> Locale("hi", "IN")
            arabicCount >= 4 -> Locale("ar")
            else -> Locale.getDefault()
        }
    }

    // ================= PCM BUFFER & AUDIOTRACK HELPERS =================

    private fun appendPcmBytes(newBytes: ByteArray, sampleRateHz: Int, isLast: Boolean) {
        if (newBytes.isEmpty()) {
            if (isLast) markPcmFullyLoaded()
            return
        }
        val totalSize: Int
        synchronized(pcmLock) {
            currentSampleRateHz = sampleRateHz
            val combined = ByteArray(pcmBuffer.size + newBytes.size)
            System.arraycopy(pcmBuffer, 0, combined, 0, pcmBuffer.size)
            System.arraycopy(newBytes, 0, combined, pcmBuffer.size, newBytes.size)
            pcmBuffer = combined
            totalSize = combined.size
            if (isLast) pcmFullyLoaded = true
        }
        val durMs = bytesToMs(totalSize, sampleRateHz)
        _state.update { it.copy(totalDurationMs = durMs) }
    }

    private fun markPcmFullyLoaded() {
        synchronized(pcmLock) { pcmFullyLoaded = true }
    }

    private fun getPcmSize(): Int = synchronized(pcmLock) { pcmBuffer.size }

    private fun snapshotPcmBytes(): ByteArray = synchronized(pcmLock) { pcmBuffer.copyOf() }

    private fun bytesToMs(bytes: Int, sampleRateHz: Int): Long {
        val bytesPerSec = sampleRateHz.coerceAtLeast(8000) * 2L
        return (bytes.toLong() * 1000L) / bytesPerSec
    }

    private fun msToBytes(ms: Long, sampleRateHz: Int): Int {
        val bytesPerSec = sampleRateHz.coerceAtLeast(8000) * 2L
        return ((ms * bytesPerSec) / 1000L).toInt()
    }

    private fun createAudioTrack(sampleRateHz: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        return builder.build()
    }

    private fun applySpeedToTrack(track: AudioTrack, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val params = track.playbackParams
                params.speed = speed.coerceIn(0.5f, 2.0f)
                track.playbackParams = params
            }
        }
    }

    private fun releaseTrackQuietly(track: AudioTrack?) {
        if (track == null) return
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }
}
