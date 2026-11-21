package com.example.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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

/**
 * Direct Gemini REST API Client using the user's own Gemini API key entered in Settings.
 * - Auto-discovers all available models for the user's API key via /v1beta/models
 * - Separates models into Text Generation and Audio Read / Voice sections
 * - Executes Text Generation & Gemini Native Audio Read (TTS) with PCM AudioTrack playback
 */
object GeminiAiService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    val AVAILABLE_VOICES = listOf(
        "Kore" to "Calm & Balanced",
        "Puck" to "Warm & Clear",
        "Charon" to "Deep & Steady",
        "Fenrir" to "Resonant & Strong",
        "Aoede" to "Gentle & Melodic"
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

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

                client.newCall(request).execute().use { response ->
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

            // Skip embedding-only, imagen, veo, aqa, robotics/computer-use models
            val supportsGenerate = methods.any { it.equals("generateContent", ignoreCase = true) }
            val supportsBidi = methods.any { it.equals("bidiGenerateContent", ignoreCase = true) }
            val isExcluded = idLower.contains("embedding") ||
                idLower.contains("imagen") ||
                idLower.contains("veo") ||
                idLower.contains("aqa") ||
                idLower.contains("bisociative") ||
                idLower.contains("learnlm") && !supportsGenerate

            if (isExcluded || (!supportsGenerate && !supportsBidi)) continue

            val isDedicatedTts = idLower.contains("tts") || idLower.contains("speech")
            val isNativeAudio = idLower.contains("native-audio") || idLower.contains("audio")
            val isMultimodalAudioCapable = isDedicatedTts || isNativeAudio ||
                (supportsBidi && idLower.contains("flash")) ||
                (idLower.contains("gemini-2.5-flash") && !idLower.contains("image")) ||
                (idLower.contains("gemini-2.0-flash") && !idLower.contains("image") && !idLower.contains("lite"))

            // 1. Add to Audio Read section if dedicated TTS, native audio, or multimodal audio-capable
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

            // 2. Add to Text Generation section if it supports generateContent and is not TTS-only or image-only
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
        if (l.contains("gemini-2.5-flash") && !l.contains("lite") && !l.contains("preview")) score += 100
        else if (l.contains("gemini-2.5-flash")) score += 92
        if (l.contains("gemini-2.5-pro")) score += 90
        if (l.contains("gemini-2.0-flash") && !l.contains("lite")) score += 80
        if (l.contains("gemini-3")) score += 96
        if (l.contains("flash")) score += 20
        if (l.contains("pro")) score += 15
        if (l.contains("latest")) score += 10
        if (l.contains("exp")) score -= 5
        return score
    }

    private fun scoreAudioModelPriority(id: String): Int {
        val l = id.lowercase()
        var score = 0
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

    /**
     * Generates a text response using the user's Gemini API key and chosen Text Generation model.
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
            return@withContext GeminiTextResult.Error("Gemini API key is not configured. Open Settings to add your key.")
        }
        if (cleanModel.isBlank()) {
            return@withContext GeminiTextResult.Error("No Text Generation model selected. Open Settings and select a model.")
        }

        try {
            val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")
            val url = "$BASE_URL/models/$cleanModel:generateContent?key=$encodedKey"

            val contents = JSONArray()
            recentHistory.takeLast(8).forEach { msg ->
                contents.put(
                    JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    }
                )
            }
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                }
            )

            val payload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put(
                                "text",
                                "You are Hidayah AI, a respectful, knowledgeable assistant inside the Hidayah Islamic app. " +
                                    "Answer clearly and warmly in the same language the user writes in (Hindi, English, Urdu, Arabic, etc.). " +
                                    "When discussing Quran verses, Hadith, or Islamic teachings, be accurate and cite Surah/Ayah or authentic references whenever possible."
                            )
                        )
                    )
                )
                put("contents", contents)
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext GeminiTextResult.Error(
                        parseApiError(bodyStr, "Request failed (HTTP ${response.code}). Try another Text model in Settings.")
                    )
                }

                val root = JSONObject(bodyStr)
                val candidates = root.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val txt = parts.optJSONObject(i)?.optString("text", "")
                        if (!txt.isNullOrBlank()) {
                            if (sb.isNotEmpty()) sb.append("\n")
                            sb.append(txt)
                        }
                    }
                }

                val answer = sb.toString().trim()
                if (answer.isBlank()) {
                    GeminiTextResult.Error("The selected model returned an empty response. Try rephrasing or selecting another model.")
                } else {
                    GeminiTextResult.Success(answer, cleanModel)
                }
            }
        } catch (e: Exception) {
            GeminiTextResult.Error("Connection error: ${e.localizedMessage ?: "Please check your internet connection."}")
        }
    }

    /**
     * Generates spoken audio using the user's selected Gemini Audio Read model.
     * Returns raw 16-bit PCM audio when the model supports native AUDIO output (e.g. gemini-2.5-flash-preview-tts),
     * or signals graceful fallback to on-device TextToSpeech if the chosen model is text-only.
     */
    suspend fun generateAudioRead(
        apiKey: String,
        modelId: String,
        textToRead: String,
        voiceName: String = "Kore"
    ): GeminiAudioResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanModel = modelId.removePrefix("models/").trim()
        val cleanText = textToRead.trim()

        if (cleanText.isBlank()) {
            return@withContext GeminiAudioResult.Error("Enter some text to read aloud.")
        }
        if (cleanKey.isBlank()) {
            return@withContext GeminiAudioResult.Error("Gemini API key is not configured. Open Settings to add your key.")
        }
        if (cleanModel.isBlank()) {
            return@withContext GeminiAudioResult.FallbackToDeviceTts(
                textToSpeak = cleanText,
                note = "No Audio Read model selected in Settings. Playing via device voice."
            )
        }

        try {
            val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")
            val url = "$BASE_URL/models/$cleanModel:generateContent?key=$encodedKey"

            val payload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", "Read the following passage aloud clearly, naturally, and respectfully:\n\n$cleanText")
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

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // If the chosen model doesn't support AUDIO modality, fall back gracefully to device TTS
                    val errMsg = parseApiError(bodyStr, "HTTP ${response.code}")
                    if (response.code == 400 || errMsg.contains("modality", ignoreCase = true) || errMsg.contains("AUDIO", ignoreCase = true)) {
                        return@withContext GeminiAudioResult.FallbackToDeviceTts(
                            textToSpeak = cleanText,
                            note = "Model '$cleanModel' does not emit direct audio bytes; reading via device voice. Select a '-tts' model in Settings for Gemini neural audio."
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
                    note = "No raw audio returned by '$cleanModel'; speaking via device voice."
                )
            }
        } catch (e: Exception) {
            GeminiAudioResult.Error("Audio generation failed: ${e.localizedMessage ?: "Check network"}")
        }
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
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRateHz,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
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
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            activeAudioTrack = track
            track.play()

            var offset = 0
            val chunkSize = 4096
            while (offset < pcmBytes.size && activeAudioTrack === track) {
                val toWrite = minOf(chunkSize, pcmBytes.size - offset)
                val written = track.write(pcmBytes, offset, toWrite)
                if (written <= 0) break
                offset += written
            }

            if (activeAudioTrack === track) {
                runCatching { track.stop() }
                runCatching { track.release() }
                activeAudioTrack = null
            }
        } catch (_: Exception) {
            stopPcmAudio()
        } finally {
            onComplete()
        }
    }

    fun stopPcmAudio() {
        val current = activeAudioTrack
        activeAudioTrack = null
        if (current != null) {
            runCatching { current.pause() }
            runCatching { current.flush() }
            runCatching { current.stop() }
            runCatching { current.release() }
        }
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
