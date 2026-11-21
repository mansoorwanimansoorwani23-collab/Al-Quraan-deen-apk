package com.example.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class GeminiModelCategory(val displayTitle: String) {
    TEXT_GENERATION("Text Generation"),
    AUDIO_READ("Audio Read & Voice")
}

data class GeminiModelInfo(
    val id: String,
    val fullName: String,
    val displayName: String,
    val description: String,
    val category: GeminiModelCategory,
    val capabilityTag: String,
    val inputTokenLimit: Int,
    val outputTokenLimit: Int,
    val supportsNativeAudioOutput: Boolean
)

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val modelUsed: String = "",
    val isStreaming: Boolean = false,
    val isVoiceMessage: Boolean = false,
    val providerLabel: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

sealed class GeminiModelsFetchResult {
    data class Success(val models: List<GeminiModelInfo>, val rawCacheJson: String) : GeminiModelsFetchResult()
    data class Error(val message: String) : GeminiModelsFetchResult()
}

sealed class GeminiTextResult {
    data class Success(val text: String, val modelId: String) : GeminiTextResult()
    data class Error(val message: String) : GeminiTextResult()
}

sealed class GeminiAudioResult {
    data class NativePcmAudio(val pcmBytes: ByteArray, val sampleRateHz: Int, val modelId: String) : GeminiAudioResult()
    data class FallbackToDeviceTts(val textToSpeak: String, val note: String) : GeminiAudioResult()
    data class Error(val message: String) : GeminiAudioResult()
}

private data class CachedPcmClip(
    val pcmBytes: ByteArray,
    val sampleRateHz: Int,
    val modelId: String
)

/**
 * High-speed Direct Gemini REST API Client using the user's own Gemini API key.
 * Optimized for low latency and high stability on mobile networks & low-end devices:
 * - Warm HTTP/2 ConnectionPool + tuned Dispatcher + automatic transient retries
 * - Real-time SSE token streaming (:streamGenerateContent?alt=sse) for instant text responses
 * - Progressive sentence-chunked pipelined voice synthesis with background prefetch & LRU PCM cache
 * - Single continuous low-latency AudioTrack for gapless speech playback
 */
object GeminiAiService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    data class GeminiVoiceOption(
        val name: String,
        val gender: String,
        val style: String
    ) {
        val displayLabel: String get() = "$name ($gender) • $style"
    }

    val VOICE_OPTIONS = listOf(
        GeminiVoiceOption("Kore", "Female", "Calm & Balanced"),
        GeminiVoiceOption("Puck", "Male", "Warm & Clear"),
        GeminiVoiceOption("Aoede", "Female", "Gentle & Melodic"),
        GeminiVoiceOption("Charon", "Male", "Deep & Steady"),
        GeminiVoiceOption("Leda", "Female", "Soft & Clear"),
        GeminiVoiceOption("Fenrir", "Male", "Resonant & Strong"),
        GeminiVoiceOption("Zephyr", "Female", "Bright & Friendly"),
        GeminiVoiceOption("Orus", "Male", "Firm & Composed")
    )

    val AVAILABLE_VOICES = VOICE_OPTIONS.map { it.name to "${it.gender} • ${it.style}" }

    // Tuned HTTP/2 client with warm keep-alive pool and higher concurrency per host
    private val client: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 12
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    // Dedicated audio chunk client with tighter read timeout so stalled chunks recover fast
    private val audioClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(22, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    // Track model quirks during session to eliminate wasted failed round-trips
    private val modelsWithoutThinkingConfig = ConcurrentHashMap.newKeySet<String>()
    private val modelsWithoutNativeAudio = ConcurrentHashMap.newKeySet<String>()

    // Active network calls for instant cancellation on Stop / new request
    @Volatile
    private var activeTextCall: Call? = null
    private val activeAudioCalls = Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())

    // Active AudioTrack & playback session counter (prevents stale playback races)
    @Volatile
    private var activeAudioTrack: AudioTrack? = null
    private val audioSessionCounter = AtomicLong(0L)

    // Bounded in-memory LRU cache (~6 MB max) for instant 0ms voice replay
    private const val MAX_PCM_CACHE_BYTES = 6 * 1024 * 1024
    private val pcmCacheLock = Any()
    private var pcmCacheCurrentBytes = 0
    private val pcmCache = object : LinkedHashMap<String, CachedPcmClip>(16, 0.75f, true) {}

    @Volatile
    private var lastWarmUpMs = 0L

    /**
     * Pre-warms DNS + TCP + TLS + HTTP/2 connection to Google's Gemini endpoint so
     * the very first prompt or voice request starts ~400-800ms faster.
     */
    suspend fun warmUpConnection() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastWarmUpMs < 45_000L) return@withContext
        lastWarmUpMs = now
        try {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/")
                .head()
                .build()
            client.newCall(req).execute().use { /* Connection pooled & warm */ }
        } catch (_: Exception) {
            // Best-effort warm-up only
        }
    }

    fun cancelActiveTextGeneration() {
        val call = activeTextCall
        activeTextCall = null
        runCatching { call?.cancel() }
    }

    private fun cancelActiveAudioCalls() {
        val snapshot = activeAudioCalls.toList()
        activeAudioCalls.clear()
        snapshot.forEach { call -> runCatching { call.cancel() } }
    }

    private fun getCachedPcm(key: String): CachedPcmClip? = synchronized(pcmCacheLock) {
        pcmCache[key]
    }

    private fun putCachedPcm(key: String, clip: CachedPcmClip) {
        if (clip.pcmBytes.isEmpty() || clip.pcmBytes.size > MAX_PCM_CACHE_BYTES / 2) return
        synchronized(pcmCacheLock) {
            pcmCache.remove(key)?.let { old -> pcmCacheCurrentBytes -= old.pcmBytes.size }
            pcmCache[key] = clip
            pcmCacheCurrentBytes += clip.pcmBytes.size
            val iter = pcmCache.entries.iterator()
            while (pcmCacheCurrentBytes > MAX_PCM_CACHE_BYTES && iter.hasNext()) {
                val eldest = iter.next()
                pcmCacheCurrentBytes -= eldest.value.pcmBytes.size
                iter.remove()
            }
        }
    }

    /**
     * Executes an OkHttp request with automatic fast retry on transient network errors
     * or 429/5xx server hiccups for rock-solid stability.
     */
    private suspend fun executeWithRetry(
        httpClient: OkHttpClient,
        request: Request,
        maxAttempts: Int = 2,
        trackAsTextCall: Boolean = false,
        trackAsAudioCall: Boolean = false
    ): Response {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            val call = httpClient.newCall(request)
            if (trackAsTextCall) activeTextCall = call
            if (trackAsAudioCall) activeAudioCalls.add(call)
            try {
                val response = call.execute()
                val retryableCode = response.code == 429 || response.code in 500..504
                if (retryableCode && attempt < maxAttempts) {
                    response.close()
                    delay(320L * attempt)
                    continue
                }
                return response
            } catch (e: CancellationException) {
                runCatching { call.cancel() }
                throw e
            } catch (e: IOException) {
                lastException = e
                if (call.isCanceled() || attempt >= maxAttempts) {
                    throw e
                }
                delay(320L * attempt)
            } finally {
                if (trackAsAudioCall) activeAudioCalls.remove(call)
                if (trackAsTextCall && activeTextCall === call) activeTextCall = null
            }
        }
        throw lastException ?: IOException("Network request failed")
    }

    /**
     * Fetches all available models for the provided Gemini API key and categorizes them
     * into Text Generation and Audio Read sections.
     */
    suspend fun fetchAvailableModels(apiKey: String): GeminiModelsFetchResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiModelsFetchResult.Error("Please enter your Gemini API key first.")
        }

        try {
            val allRawModels = mutableListOf<JSONObject>()
            var nextPageToken: String? = null
            var pagesFetched = 0

            do {
                val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")
                val urlBuilder = StringBuilder("$BASE_URL/models?pageSize=100&key=$encodedKey")
                if (!nextPageToken.isNullOrBlank()) {
                    urlBuilder.append("&pageToken=").append(URLEncoder.encode(nextPageToken, "UTF-8"))
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .get()
                    .build()

                executeWithRetry(client, request, maxAttempts = 2).use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val apiErr = parseApiError(bodyStr, "HTTP ${response.code}: Unable to verify Gemini API key.")
                        return@withContext GeminiModelsFetchResult.Error(apiErr)
                    }

                    val root = JSONObject(bodyStr)
                    val arr = root.optJSONArray("models") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { allRawModels.add(it) }
                    }
                    nextPageToken = root.optString("nextPageToken", "").ifBlank { null }
                    pagesFetched++
                }
            } while (nextPageToken != null && pagesFetched < 3)

            val categorized = parseAndCategorizeModels(allRawModels)
            if (categorized.isEmpty()) {
                return@withContext GeminiModelsFetchResult.Error("API key connected, but no compatible Gemini models were returned.")
            }

            val cacheJson = serializeModelsToJson(categorized)
            GeminiModelsFetchResult.Success(categorized, cacheJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeminiModelsFetchResult.Error("Network error while fetching Gemini models: ${e.localizedMessage ?: "Check internet connection"}")
        }
    }

    internal fun parseAndCategorizeModels(rawModels: List<JSONObject>): List<GeminiModelInfo> {
        val textModels = mutableListOf<GeminiModelInfo>()
        val audioModels = mutableListOf<GeminiModelInfo>()

        for (obj in rawModels) {
            val fullName = obj.optString("name", "")
            val id = fullName.removePrefix("models/").trim()
            if (id.isBlank()) continue

            val idLower = id.lowercase()
            val displayName = obj.optString("displayName", id).ifBlank { id }
            val description = obj.optString("description", "").trim()
            val inputLimit = obj.optInt("inputTokenLimit", 0)
            val outputLimit = obj.optInt("outputTokenLimit", 0)

            val methodsArr = obj.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            val methods = mutableListOf<String>()
            for (i in 0 until methodsArr.length()) {
                methods.add(methodsArr.optString(i, ""))
            }

            val supportsGenerate = methods.any { it.equals("generateContent", ignoreCase = true) }
            val supportsBidi = methods.any { it.equals("bidiGenerateContent", ignoreCase = true) }
            val isExcluded = idLower.contains("embedding") ||
                idLower.contains("imagen") ||
                idLower.contains("veo") ||
                idLower.contains("aqa") ||
                idLower.contains("bisociative") ||
                (idLower.contains("learnlm") && !supportsGenerate)

            if (isExcluded || (!supportsGenerate && !supportsBidi)) continue

            val isDedicatedTts = idLower.contains("tts") || idLower.contains("speech")
            val isNativeAudio = idLower.contains("native-audio") || idLower.contains("audio")
            val isMultimodalAudioCapable = isDedicatedTts || isNativeAudio ||
                (supportsBidi && idLower.contains("flash")) ||
                (idLower.contains("3.8") && idLower.contains("flash")) ||
                (idLower.contains("gemini-2.5-flash") && !idLower.contains("image")) ||
                (idLower.contains("gemini-2.0-flash") && !idLower.contains("image") && !idLower.contains("lite"))

            if (isDedicatedTts || isNativeAudio || isMultimodalAudioCapable) {
                val tag = when {
                    isDedicatedTts -> "Native TTS Audio"
                    isNativeAudio -> "Native Voice Audio"
                    supportsBidi -> "Live Audio & Voice"
                    else -> "Audio Read Ready"
                }
                audioModels.add(
                    GeminiModelInfo(
                        id = id,
                        fullName = fullName,
                        displayName = displayName,
                        description = description.ifBlank { "Supports AI voice & audio reading output" },
                        category = GeminiModelCategory.AUDIO_READ,
                        capabilityTag = tag,
                        inputTokenLimit = inputLimit,
                        outputTokenLimit = outputLimit,
                        supportsNativeAudioOutput = isDedicatedTts || isNativeAudio || idLower.contains("2.5") || idLower.contains("2.0")
                    )
                )
            }

            val isImageOnly = idLower.contains("-image") || idLower.contains("image-generation")
            if (supportsGenerate && !isDedicatedTts && !isNativeAudio && !isImageOnly) {
                val tag = when {
                    idLower.contains("pro") -> "Reasoning & Pro Text"
                    idLower.contains("flash-lite") -> "Ultra Fast Text"
                    idLower.contains("flash") -> "Fast Text Generation"
                    idLower.contains("gemma") -> "Open Text Model"
                    else -> "Text Generation"
                }
                textModels.add(
                    GeminiModelInfo(
                        id = id,
                        fullName = fullName,
                        displayName = displayName,
                        description = description.ifBlank { "Supports conversational text generation & Islamic Q&A" },
                        category = GeminiModelCategory.TEXT_GENERATION,
                        capabilityTag = tag,
                        inputTokenLimit = inputLimit,
                        outputTokenLimit = outputLimit,
                        supportsNativeAudioOutput = false
                    )
                )
            }
        }

        val sortedText = textModels.sortedWith(
            compareByDescending<GeminiModelInfo> { scoreTextModelPriority(it.id) }
                .thenBy { it.displayName }
        )
        val sortedAudio = audioModels.sortedWith(
            compareByDescending<GeminiModelInfo> { scoreAudioModelPriority(it.id) }
                .thenBy { it.displayName }
        )

        return sortedText + sortedAudio
    }

    private fun scoreTextModelPriority(id: String): Int {
        val l = id.lowercase()
        var score = 0
        if (l.contains("3.1") && l.contains("flash")) score += 140
        else if (l.contains("gemini-3") && l.contains("flash")) score += 120
        else if (l.contains("gemini-2.5-flash") && !l.contains("lite") && !l.contains("preview")) score += 100
        else if (l.contains("gemini-2.5-flash")) score += 92
        if (l.contains("gemini-2.5-pro")) score += 90
        if (l.contains("gemini-2.0-flash") && !l.contains("lite")) score += 80
        if (l.contains("gemini-3")) score += 30
        if (l.contains("flash")) score += 20
        if (l.contains("pro")) score += 15
        if (l.contains("latest")) score += 10
        if (l.contains("exp")) score -= 5
        return score
    }

    private fun scoreAudioModelPriority(id: String): Int {
        val l = id.lowercase()
        var score = 0
        if (l.contains("3.8") && l.contains("flash")) score += 160
        if (l.contains("tts")) score += 120
        if (l.contains("native-audio")) score += 100
        if (l.contains("2.5-flash")) score += 40
        if (l.contains("2.5-pro")) score += 35
        if (l.contains("2.0-flash")) score += 25
        return score
    }

    fun serializeModelsToJson(models: List<GeminiModelInfo>): String {
        val arr = JSONArray()
        for (m in models) {
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("fullName", m.fullName)
                    put("displayName", m.displayName)
                    put("description", m.description)
                    put("category", m.category.name)
                    put("capabilityTag", m.capabilityTag)
                    put("inputTokenLimit", m.inputTokenLimit)
                    put("outputTokenLimit", m.outputTokenLimit)
                    put("supportsNativeAudioOutput", m.supportsNativeAudioOutput)
                }
            )
        }
        return arr.toString()
    }

    fun deserializeModelsFromJson(json: String): List<GeminiModelInfo> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<GeminiModelInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val catName = obj.optString("category", GeminiModelCategory.TEXT_GENERATION.name)
                val cat = runCatching { GeminiModelCategory.valueOf(catName) }
                    .getOrDefault(GeminiModelCategory.TEXT_GENERATION)
                list.add(
                    GeminiModelInfo(
                        id = obj.optString("id"),
                        fullName = obj.optString("fullName"),
                        displayName = obj.optString("displayName"),
                        description = obj.optString("description"),
                        category = cat,
                        capabilityTag = obj.optString("capabilityTag"),
                        inputTokenLimit = obj.optInt("inputTokenLimit", 0),
                        outputTokenLimit = obj.optInt("outputTokenLimit", 0),
                        supportsNativeAudioOutput = obj.optBoolean("supportsNativeAudioOutput", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildTextPayload(
        cleanModel: String,
        userPrompt: String,
        recentHistory: List<AiChatMessage>,
        includeLowLatencyThinking: Boolean
    ): JSONObject {
        val contents = JSONArray()
        recentHistory.takeLast(6).forEach { msg ->
            val trimmedMsg = if (msg.text.length > 750) msg.text.take(750) + "…" else msg.text
            if (trimmedMsg.isNotBlank()) {
                contents.put(
                    JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", trimmedMsg)))
                    }
                )
            }
        }
        contents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
            }
        )

        val genConfig = JSONObject().apply {
            put("maxOutputTokens", 1024)
            put("temperature", 0.7)
            if (includeLowLatencyThinking) {
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            }
        }

        return JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            "You are Hidayah AI, a warm, respectful assistant in the Hidayah Islamic app. " +
                                "Reply concisely, clearly, and directly in the user's language (Hindi, English, Urdu, Arabic, etc.). " +
                                "Cite authentic Surah/Ayah or Hadith references when relevant."
                        )
                    )
                )
            )
            put("contents", contents)
            put("generationConfig", genConfig)
        }
    }

    internal fun extractTextDeltaFromJson(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates") ?: return ""
            val parts = candidates.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) continue
                val txt = part.optString("text", "")
                if (txt.isNotEmpty()) {
                    sb.append(txt)
                }
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Real-time Server-Sent Events (SSE) text streaming so the user sees the first words
     * in ~0.5–1 second instead of waiting for the entire answer to finish.
     */
    suspend fun generateTextStreaming(
        apiKey: String,
        modelId: String,
        userPrompt: String,
        recentHistory: List<AiChatMessage> = emptyList(),
        onPartialText: (String) -> Unit
    ): GeminiTextResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanModel = modelId.removePrefix("models/").trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiTextResult.Error("Gemini API key is not configured. Add your key above.")
        }
        if (cleanModel.isBlank()) {
            return@withContext GeminiTextResult.Error("No Text model selected. Tap the Tune icon to choose one.")
        }

        val lowerModel = cleanModel.lowercase()
        val tryZeroThinking = (lowerModel.contains("2.5-flash") || (lowerModel.contains("gemini-3") && lowerModel.contains("flash"))) &&
            !modelsWithoutThinkingConfig.contains(cleanModel)

        val firstAttempt = executeStreamingTextRequest(
            cleanKey = cleanKey,
            cleanModel = cleanModel,
            userPrompt = userPrompt,
            recentHistory = recentHistory,
            includeLowLatencyThinking = tryZeroThinking,
            onPartialText = onPartialText
        )

        // If the model rejects thinkingConfig with HTTP 400, remember and retry immediately without it
        if (firstAttempt is StreamAttempt.RetryWithoutThinking) {
            modelsWithoutThinkingConfig.add(cleanModel)
            return@withContext when (
                val second = executeStreamingTextRequest(
                    cleanKey = cleanKey,
                    cleanModel = cleanModel,
                    userPrompt = userPrompt,
                    recentHistory = recentHistory,
                    includeLowLatencyThinking = false,
                    onPartialText = onPartialText
                )
            ) {
                is StreamAttempt.Completed -> second.result
                is StreamAttempt.RetryWithoutThinking -> GeminiTextResult.Error("Model rejected configuration. Please pick another Text model.")
            }
        }

        (firstAttempt as StreamAttempt.Completed).result
    }

    private sealed class StreamAttempt {
        data class Completed(val result: GeminiTextResult) : StreamAttempt()
        data object RetryWithoutThinking : StreamAttempt()
    }

    private suspend fun executeStreamingTextRequest(
        cleanKey: String,
        cleanModel: String,
        userPrompt: String,
        recentHistory: List<AiChatMessage>,
        includeLowLatencyThinking: Boolean,
        onPartialText: (String) -> Unit
    ): StreamAttempt {
        try {
            val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")
            val url = "$BASE_URL/models/$cleanModel:streamGenerateContent?alt=sse&key=$encodedKey"
            val payload = buildTextPayload(cleanModel, userPrompt, recentHistory, includeLowLatencyThinking)

            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            executeWithRetry(
                httpClient = client,
                request = request,
                maxAttempts = 2,
                trackAsTextCall = true
            ).use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val apiErr = parseApiError(bodyStr, "Request failed (HTTP ${response.code}).")
                    if (includeLowLatencyThinking && response.code == 400 &&
                        (apiErr.contains("thinking", ignoreCase = true) || apiErr.contains("Unknown name", ignoreCase = true))
                    ) {
                        return StreamAttempt.RetryWithoutThinking
                    }
                    return StreamAttempt.Completed(GeminiTextResult.Error(apiErr))
                }

                val body = response.body ?: return StreamAttempt.Completed(
                    GeminiTextResult.Error("Empty stream received. Please retry.")
                )

                val accumulated = StringBuilder()
                val reader = body.charStream().buffered()
                var lastEmitTimeMs = 0L

                while (true) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data:")) continue
                    val dataJson = trimmed.removePrefix("data:").trim()
                    if (dataJson.isEmpty() || dataJson == "[DONE]") continue

                    val delta = extractTextDeltaFromJson(dataJson)
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        val now = System.currentTimeMillis()
                        // Throttle UI callbacks slightly (~40ms) for silky smoothness on low-end devices
                        if (now - lastEmitTimeMs >= 40L || accumulated.length <= 24) {
                            lastEmitTimeMs = now
                            onPartialText(accumulated.toString())
                        }
                    }
                }

                val finalAnswer = accumulated.toString().trim()
                return if (finalAnswer.isBlank()) {
                    // Fallback once to standard generateContent in case model returned non-SSE payload
                    StreamAttempt.Completed(generateText(cleanKey, cleanModel, userPrompt, recentHistory))
                } else {
                    onPartialText(finalAnswer)
                    StreamAttempt.Completed(GeminiTextResult.Success(finalAnswer, cleanModel))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is IOException && e.message?.contains("Canceled", ignoreCase = true) == true) {
                throw CancellationException("Text generation canceled")
            }
            return StreamAttempt.Completed(
                GeminiTextResult.Error("Connection error: ${e.localizedMessage ?: "Please check your internet connection."}")
            )
        }
    }

    /**
     * Standard non-streaming text generation (kept as fallback & helper).
     */
    suspend fun generateText(
        apiKey: String,
        modelId: String,
        userPrompt: String,
        recentHistory: List<AiChatMessage> = emptyList()
    ): GeminiTextResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanModel = modelId.removePrefix("models/").trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiTextResult.Error("Gemini API key is not configured.")
        }
        if (cleanModel.isBlank()) {
            return@withContext GeminiTextResult.Error("No Text Generation model selected.")
        }

        try {
            val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")
            val url = "$BASE_URL/models/$cleanModel:generateContent?key=$encodedKey"
            val payload = buildTextPayload(cleanModel, userPrompt, recentHistory, includeLowLatencyThinking = false)

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            executeWithRetry(client, request, maxAttempts = 2, trackAsTextCall = true).use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext GeminiTextResult.Error(
                        parseApiError(bodyStr, "Request failed (HTTP ${response.code}). Try another Text model.")
                    )
                }

                val answer = extractTextDeltaFromJson(bodyStr).trim()
                if (answer.isBlank()) {
                    GeminiTextResult.Error("The selected model returned an empty response. Try rephrasing or selecting another model.")
                } else {
                    GeminiTextResult.Success(answer, cleanModel)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeminiTextResult.Error("Connection error: ${e.localizedMessage ?: "Please check your internet connection."}")
        }
    }

    /**
     * Deep speech sanitizer: strips markdown, URLs, slashes (/ and \), pipes, brackets,
     * emojis, and non-spoken technical symbols so TTS NEVER reads "slash", "shift",
     * "asterisk", or formatting noise aloud. Preserves natural English, Hindi, Urdu & Arabic text.
     */
    internal fun cleanTextForSpeech(rawText: String): String {
        return rawText
            // Remove fenced code blocks and URLs
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("https?://\\S+"), " ")
            // Convert markdown links [label](url) -> label
            .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            // Strip markdown formatting characters & technical symbols
            .replace(Regex("[*_~`#>|<>{}\\[\\]^\\\\]+"), " ")
            // Replace forward slashes with natural space so TTS never says "slash"
            .replace("/", " ")
            // Replace dashes / em-dashes / bullets with clean pauses
            .replace(Regex("[•●▪▫◆◇★☆→←↑↓⇒⇐]+"), ", ")
            .replace(Regex("\\s*[-–—]{2,}\\s*"), ". ")
            .replace(Regex("^\\s*[-–—+]\\s+", RegexOption.MULTILINE), "")
            // Remove standalone colons at end of headings/labels
            .replace(Regex(":\\s*\\n"), ". ")
            .replace(":", ", ")
            // Strip emojis & pictographic symbols that TTS pronounces awkwardly
            .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), " ")
            .replace(Regex("[\\u2600-\\u27BF]+"), " ")
            // Clean parentheses without removing inner words
            .replace("(", ", ")
            .replace(")", ", ")
            // Collapse repeated punctuation and extra whitespace
            .replace(Regex("\\.{2,}"), ".")
            .replace(Regex(",\\s*,+"), ",")
            .replace(Regex("\\.\\s*,+"), ".")
            .replace(Regex("\\n{2,}"), ". ")
            .replace("\n", " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trim(',', ' ')
    }

    /**
     * Splits text into natural progressive speech chunks:
     * - Chunk 0 is kept short (~150 chars) so the FIRST audio starts speaking in ~1–2 seconds!
     * - Subsequent chunks (~300 chars) download in the background while earlier chunks play.
     */
    internal fun splitIntoSpeechChunks(
        rawText: String,
        firstChunkMaxChars: Int = 150,
        nextChunkMaxChars: Int = 300
    ): List<String> {
        val cleaned = cleanTextForSpeech(rawText)
        if (cleaned.isBlank()) return emptyList()
        if (cleaned.length <= firstChunkMaxChars) return listOf(cleaned)

        // Split on English, Hindi (।), Urdu/Arabic (؟ ۔), or newline sentence boundaries
        val sentenceRegex = Regex("(?<=[.!?।؟۔])\\s+")
        val sentences = cleaned.split(sentenceRegex).map { it.trim() }.filter { it.isNotEmpty() }

        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        fun limitForCurrentChunk(): Int = if (chunks.isEmpty()) firstChunkMaxChars else nextChunkMaxChars

        for (sentence in sentences) {
            val limit = limitForCurrentChunk()
            if (sentence.length > limit) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.setLength(0)
                }
                // Break long sentence cleanly on comma or word boundaries
                var remaining = sentence
                while (remaining.isNotEmpty()) {
                    val subLimit = limitForCurrentChunk()
                    if (remaining.length <= subLimit) {
                        current.append(remaining)
                        break
                    }
                    val commaIdx = remaining.lastIndexOf(',', subLimit)
                    val spaceIdx = remaining.lastIndexOf(' ', subLimit)
                    val splitAt = when {
                        commaIdx >= subLimit / 2 -> commaIdx + 1
                        spaceIdx > 0 -> spaceIdx
                        else -> subLimit
                    }
                    chunks.add(remaining.substring(0, splitAt).trim())
                    remaining = remaining.substring(splitAt).trim()
                }
            } else if (current.isEmpty()) {
                current.append(sentence)
            } else if (current.length + 1 + sentence.length <= limit) {
                current.append(' ').append(sentence)
            } else {
                chunks.add(current.toString().trim())
                current.setLength(0)
                current.append(sentence)
            }
        }

        if (current.isNotEmpty()) {
            chunks.add(current.toString().trim())
        }
        return chunks.filter { it.isNotBlank() }
    }

    /**
     * Ultra-fast Pipelined Voice Playback:
     * 1. Splits text into progressive chunks (short Chunk 0 for rapid first sound!).
     * 2. Prefetches Chunk 1 & Chunk 2 concurrently while Chunk 0 is already playing.
     * 3. Uses bounded LRU PCM cache for instant 0ms replay.
     * 4. Automatically switches to a working TTS model or pre-warmed Device TTS if selected model rejects AUDIO.
     */
    suspend fun streamAndPlayAudioRead(
        apiKey: String,
        modelId: String,
        fallbackTtsModelId: String?,
        textToRead: String,
        voiceName: String = "Kore",
        onStatusUpdate: (String) -> Unit,
        onPlaybackStarted: (activeModelId: String) -> Unit,
        onFallbackToDeviceTts: (cleanText: String, note: String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) = withContext(Dispatchers.IO) {
        stopPcmAudio()
        val sessionId = audioSessionCounter.incrementAndGet()

        val cleanKey = apiKey.trim()
        val cleanText = cleanTextForSpeech(textToRead)
        val safeVoice = voiceName.ifBlank { "Kore" }

        if (cleanText.isBlank()) {
            onError("Enter some text to read aloud.")
            onComplete()
            return@withContext
        }
        if (cleanKey.isBlank()) {
            onError("Gemini API key is not configured.")
            onComplete()
            return@withContext
        }

        // Resolve best working audio model (skip models already known to reject AUDIO modality)
        var effectiveModel = modelId.removePrefix("models/").trim()
        val cleanFallbackTts = fallbackTtsModelId?.removePrefix("models/")?.trim().orEmpty()

        if (effectiveModel.isBlank() || modelsWithoutNativeAudio.contains(effectiveModel)) {
            if (cleanFallbackTts.isNotBlank() && !modelsWithoutNativeAudio.contains(cleanFallbackTts)) {
                effectiveModel = cleanFallbackTts
            } else {
                onFallbackToDeviceTts(
                    cleanText,
                    "Instant device voice active (select a '-tts' model for Gemini voice)"
                )
                return@withContext
            }
        }

        val chunks = splitIntoSpeechChunks(cleanText)
        if (chunks.isEmpty()) {
            onComplete()
            return@withContext
        }

        var track: AudioTrack? = null
        var currentSampleRate = -1
        var playbackStartedNotified = false

        try {
            coroutineScope {
                onStatusUpdate("Loading voice ($safeVoice)...")

                // Fetch Chunk 0 first so we can verify model AUDIO capability immediately
                var firstChunkRes = fetchAudioChunkWithCache(cleanKey, effectiveModel, chunks[0], safeVoice)

                // If chosen model doesn't support AUDIO, automatically retry with dedicated TTS model if available
                if (firstChunkRes is GeminiAudioResult.FallbackToDeviceTts) {
                    modelsWithoutNativeAudio.add(effectiveModel)
                    if (cleanFallbackTts.isNotBlank() &&
                        cleanFallbackTts != effectiveModel &&
                        !modelsWithoutNativeAudio.contains(cleanFallbackTts)
                    ) {
                        effectiveModel = cleanFallbackTts
                        onStatusUpdate("Switching to fast voice model ($effectiveModel)...")
                        firstChunkRes = fetchAudioChunkWithCache(cleanKey, effectiveModel, chunks[0], safeVoice)
                        if (firstChunkRes is GeminiAudioResult.FallbackToDeviceTts) {
                            modelsWithoutNativeAudio.add(effectiveModel)
                        }
                    }
                }

                if (audioSessionCounter.get() != sessionId || !isActive) return@coroutineScope

                when (val res0 = firstChunkRes) {
                    is GeminiAudioResult.FallbackToDeviceTts -> {
                        onFallbackToDeviceTts(cleanText, res0.note)
                        return@coroutineScope
                    }
                    is GeminiAudioResult.Error -> {
                        onError(res0.message)
                        return@coroutineScope
                    }
                    is GeminiAudioResult.NativePcmAudio -> {
                        // Start prefetching upcoming chunks (lookahead window of 2) IN PARALLEL right now!
                        val activeModel = effectiveModel
                        val prefetched = HashMap<Int, kotlinx.coroutines.Deferred<GeminiAudioResult>>()

                        fun schedulePrefetchUpTo(fromIndex: Int) {
                            val end = minOf(chunks.lastIndex, fromIndex + 1)
                            for (idx in fromIndex..end) {
                                if (!prefetched.containsKey(idx)) {
                                    prefetched[idx] = async(Dispatchers.IO) {
                                        fetchAudioChunkWithCache(cleanKey, activeModel, chunks[idx], safeVoice)
                                    }
                                }
                            }
                        }

                        if (chunks.size > 1) {
                            schedulePrefetchUpTo(1)
                        }

                        // Open a single continuous low-latency AudioTrack and play Chunk 0 immediately
                        track = createLowLatencyAudioTrack(res0.sampleRateHz)
                        currentSampleRate = res0.sampleRateHz
                        activeAudioTrack = track
                        track?.play()

                        playbackStartedNotified = true
                        onPlaybackStarted(activeModel)
                        writePcmToTrack(track!!, res0.pcmBytes, sessionId)

                        // Seamlessly play remaining prefetched chunks with zero pause between sentences
                        for (i in 1 until chunks.size) {
                            if (audioSessionCounter.get() != sessionId || !isActive) break
                            if (i + 1 <= chunks.lastIndex) {
                                schedulePrefetchUpTo(i + 1)
                            }
                            val chunkRes = prefetched.remove(i)?.await()
                                ?: fetchAudioChunkWithCache(cleanKey, activeModel, chunks[i], safeVoice)

                            if (audioSessionCounter.get() != sessionId || !isActive) break

                            when (chunkRes) {
                                is GeminiAudioResult.NativePcmAudio -> {
                                    if (chunkRes.sampleRateHz != currentSampleRate || track == null) {
                                        releaseTrackQuietly(track)
                                        track = createLowLatencyAudioTrack(chunkRes.sampleRateHz)
                                        currentSampleRate = chunkRes.sampleRateHz
                                        activeAudioTrack = track
                                        track?.play()
                                    }
                                    writePcmToTrack(track!!, chunkRes.pcmBytes, sessionId)
                                }
                                is GeminiAudioResult.FallbackToDeviceTts,
                                is GeminiAudioResult.Error -> {
                                    // If a later chunk transiently fails, continue cleanly without crashing
                                    continue
                                }
                            }
                        }

                        // Cancel any leftover prefetches if user stopped early
                        prefetched.values.forEach { it.cancel() }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (audioSessionCounter.get() == sessionId) {
                onError("Audio error: ${e.localizedMessage ?: "Check connection"}")
            }
        } finally {
            if (activeAudioTrack === track) {
                releaseTrackQuietly(track)
                activeAudioTrack = null
            } else {
                releaseTrackQuietly(track)
            }
            if (playbackStartedNotified || audioSessionCounter.get() == sessionId) {
                onComplete()
            }
        }
    }

    internal fun isModelKnownWithoutNativeAudio(modelId: String): Boolean {
        val clean = modelId.removePrefix("models/").trim()
        return clean.isBlank() || modelsWithoutNativeAudio.contains(clean)
    }

    internal fun markModelWithoutNativeAudio(modelId: String) {
        val clean = modelId.removePrefix("models/").trim()
        if (clean.isNotBlank()) modelsWithoutNativeAudio.add(clean)
    }

    internal suspend fun fetchAudioChunkWithCache(
        cleanKey: String,
        cleanModel: String,
        chunkText: String,
        voiceName: String
    ): GeminiAudioResult {
        val cleanChunk = cleanTextForSpeech(chunkText)
        if (cleanChunk.isBlank()) {
            return GeminiAudioResult.Error("No speakable text.")
        }
        val cacheKey = "$cleanModel|$voiceName|${cleanChunk.hashCode()}|${cleanChunk.length}"
        getCachedPcm(cacheKey)?.let { cached ->
            return GeminiAudioResult.NativePcmAudio(cached.pcmBytes, cached.sampleRateHz, cached.modelId)
        }

        val result = generateAudioRead(cleanKey, cleanModel, cleanChunk, voiceName)
        if (result is GeminiAudioResult.NativePcmAudio) {
            putCachedPcm(cacheKey, CachedPcmClip(result.pcmBytes, result.sampleRateHz, result.modelId))
        }
        return result
    }

    /**
     * Generates spoken audio using the user's selected Gemini Audio Read model.
     * IMPORTANT: Sends ONLY the clean spoken text (NO 'Read aloud' instruction prefix)
     * so Gemini TTS never speaks instruction words aloud!
     */
    suspend fun generateAudioRead(
        apiKey: String,
        modelId: String,
        textToRead: String,
        voiceName: String = "Kore"
    ): GeminiAudioResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanModel = modelId.removePrefix("models/").trim()
        val cleanText = cleanTextForSpeech(textToRead)

        if (cleanText.isBlank()) {
            return@withContext GeminiAudioResult.Error("Enter some text to read aloud.")
        }
        if (cleanKey.isBlank()) {
            return@withContext GeminiAudioResult.Error("Gemini API key is not configured.")
        }
        if (cleanModel.isBlank()) {
            return@withContext GeminiAudioResult.FallbackToDeviceTts(
                textToSpeak = cleanText,
                note = "Playing with device voice"
            )
        }

        try {
            val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")
            val url = "$BASE_URL/models/$cleanModel:generateContent?key=$encodedKey"

            // Pass ONLY the pure sanitized text — zero "Read aloud" prefix!
            val payload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", cleanText)
                            )
                        )
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        put(
                            "speechConfig",
                            JSONObject().put(
                                "voiceConfig",
                                JSONObject().put(
                                    "prebuiltVoiceConfig",
                                    JSONObject().put("voiceName", voiceName.ifBlank { "Kore" })
                                )
                            )
                        )
                    }
                )
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            executeWithRetry(
                httpClient = audioClient,
                request = request,
                maxAttempts = 2,
                trackAsAudioCall = true
            ).use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = parseApiError(bodyStr, "HTTP ${response.code}")
                    if (response.code == 400 || response.code == 404 ||
                        errMsg.contains("modality", ignoreCase = true) ||
                        errMsg.contains("AUDIO", ignoreCase = true) ||
                        errMsg.contains("speechConfig", ignoreCase = true)
                    ) {
                        return@withContext GeminiAudioResult.FallbackToDeviceTts(
                            textToSpeak = cleanText,
                            note = "Playing via instant device voice (choose a '-tts' model for Gemini neural audio)."
                        )
                    }
                    return@withContext GeminiAudioResult.Error(errMsg)
                }

                val root = JSONObject(bodyStr)
                val parts = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")

                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val b64 = inlineData.optString("data", "")
                            val mime = inlineData.optString("mimeType", "audio/L16;rate=24000")
                            if (b64.isNotBlank()) {
                                val rawBytes = Base64.decode(b64, Base64.DEFAULT)
                                val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24000
                                return@withContext GeminiAudioResult.NativePcmAudio(rawBytes, rate, cleanModel)
                            }
                        }
                    }
                }

                GeminiAudioResult.FallbackToDeviceTts(
                    textToSpeak = cleanText,
                    note = "Speaking via instant device voice."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is IOException && e.message?.contains("Canceled", ignoreCase = true) == true) {
                throw CancellationException("Audio fetch canceled")
            }
            GeminiAudioResult.Error("Audio generation failed: ${e.localizedMessage ?: "Check network"}")
        }
    }

    private fun createLowLatencyAudioTrack(sampleRateHz: Int): AudioTrack {
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

    private fun writePcmToTrack(track: AudioTrack, pcmBytes: ByteArray, sessionId: Long) {
        var offset = 0
        val chunkSize = 4096
        while (offset < pcmBytes.size && audioSessionCounter.get() == sessionId && activeAudioTrack === track) {
            val toWrite = minOf(chunkSize, pcmBytes.size - offset)
            val written = track.write(pcmBytes, offset, toWrite)
            if (written <= 0) break
            offset += written
        }
    }

    private fun releaseTrackQuietly(track: AudioTrack?) {
        if (track == null) return
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    /**
     * Plays 16-bit mono PCM bytes returned by Gemini Native TTS Audio API.
     */
    suspend fun playPcmAudio(
        pcmBytes: ByteArray,
        sampleRateHz: Int = 24000,
        onComplete: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        stopPcmAudio()
        val sessionId = audioSessionCounter.incrementAndGet()
        var track: AudioTrack? = null
        try {
            track = createLowLatencyAudioTrack(sampleRateHz)
            activeAudioTrack = track
            track.play()
            writePcmToTrack(track, pcmBytes, sessionId)
        } catch (_: Exception) {
            stopPcmAudio()
        } finally {
            if (activeAudioTrack === track) {
                releaseTrackQuietly(track)
                activeAudioTrack = null
            } else {
                releaseTrackQuietly(track)
            }
            onComplete()
        }
    }

    fun stopPcmAudio() {
        audioSessionCounter.incrementAndGet()
        cancelActiveAudioCalls()
        val current = activeAudioTrack
        activeAudioTrack = null
        releaseTrackQuietly(current)
    }

    private fun parseApiError(body: String, fallback: String): String {
        return try {
            val obj = JSONObject(body)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
