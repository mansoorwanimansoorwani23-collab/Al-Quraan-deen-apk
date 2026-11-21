package com.example.ai

import com.example.data.preferences.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class AiProvider(
    val id: String,
    val shortName: String,
    val displayName: String,
    val consoleUrl: String,
    val keyHint: String,
    val defaultModels: List<String>
) {
    GEMINI(
        id = "GEMINI",
        shortName = "Gemini",
        displayName = "Google Gemini",
        consoleUrl = "https://aistudio.google.com/app/apikey",
        keyHint = "AIzaSy...",
        defaultModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash-lite"
        )
    ),
    GROK(
        id = "GROK",
        shortName = "Grok",
        displayName = "xAI Grok (Grok 3 / Grok 2)",
        consoleUrl = "https://console.x.ai/",
        keyHint = "xai-...",
        defaultModels = listOf(
            "grok-3-mini",
            "grok-3",
            "grok-2-latest",
            "grok-2-1212"
        )
    ),
    OPENAI(
        id = "OPENAI",
        shortName = "OpenAI",
        displayName = "OpenAI GPT",
        consoleUrl = "https://platform.openai.com/api-keys",
        keyHint = "sk-...",
        defaultModels = listOf(
            "gpt-4o-mini",
            "gpt-4.1-mini",
            "gpt-4o",
            "gpt-4.1"
        )
    ),
    ANTHROPIC(
        id = "ANTHROPIC",
        shortName = "Anthropic",
        displayName = "Anthropic Claude",
        consoleUrl = "https://console.anthropic.com/settings/keys",
        keyHint = "sk-ant-...",
        defaultModels = listOf(
            "claude-3-5-haiku-latest",
            "claude-3-7-sonnet-latest",
            "claude-3-5-sonnet-latest"
        )
    );

    companion object {
        fun fromId(id: String?): AiProvider {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GEMINI
        }
    }
}

sealed class MultiAiTextResult {
    data class Success(
        val text: String,
        val provider: AiProvider,
        val modelId: String,
        val keySlot: Int,
        val usedFallback: Boolean
    ) : MultiAiTextResult()

    data class Error(val message: String) : MultiAiTextResult()
}

data class DetectedClipboardKey(
    val provider: AiProvider,
    val apiKey: String
)

/**
 * Multi-AI & Multi-API-Key Orchestrator with 3-Key Automatic Fallback per Provider
 * + Cross-Provider Quota Failover + Real-Time SSE Streaming across:
 * - Google Gemini (3 API keys)
 * - xAI Grok / Grok 2 / Grok 3 (3 API keys)
 * - OpenAI GPT (3 API keys)
 * - Anthropic Claude (3 API keys)
 */
object MultiAiProviderService {

    private const val SYSTEM_PROMPT =
        "You are Hidayah AI, a respectful, knowledgeable, and concise Islamic companion assistant. " +
            "Answer clearly in the same language the user uses (English, Hindi, Hinglish, Urdu, or Arabic). " +
            "Cite authentic Quran Surahs/Ayahs or Sahih Hadith when relevant. Keep answers clear, warm, and well-structured."

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private const val COOLDOWN_MS = 5 * 60 * 1000L // 5-minute cooldown for rate-limited / exhausted keys

    // Key fingerprint -> epochMillis until cooldown expires
    private val keyCooldownUntil = ConcurrentHashMap<String, Long>()

    @Volatile
    private var activeThirdPartyCall: Call? = null

    private val httpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 24
            maxRequestsPerHost = 10
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Auto-detects an API key from clipboard text for 1-tap easy setup.
     */
    fun detectClipboardApiKey(rawClipboardText: String?): DetectedClipboardKey? {
        val cleaned = rawClipboardText?.trim().orEmpty()
        if (cleaned.length < 24 || cleaned.length > 260) return null
        if (cleaned.any { it.isWhitespace() }) return null

        return when {
            cleaned.startsWith("AIzaSy") && cleaned.length >= 30 ->
                DetectedClipboardKey(AiProvider.GEMINI, cleaned)
            cleaned.startsWith("xai-") && cleaned.length >= 24 ->
                DetectedClipboardKey(AiProvider.GROK, cleaned)
            cleaned.startsWith("sk-ant-") && cleaned.length >= 30 ->
                DetectedClipboardKey(AiProvider.ANTHROPIC, cleaned)
            cleaned.startsWith("sk-") && cleaned.length >= 24 ->
                DetectedClipboardKey(AiProvider.OPENAI, cleaned)
            else -> null
        }
    }

    fun cancelActiveGeneration() {
        GeminiAiService.cancelActiveTextGeneration()
        val call = activeThirdPartyCall
        activeThirdPartyCall = null
        runCatching { call?.cancel() }
    }

    fun markKeyExhausted(apiKey: String) {
        if (apiKey.isBlank()) return
        keyCooldownUntil[keyFingerprint(apiKey)] = System.currentTimeMillis() + COOLDOWN_MS
    }

    fun clearKeyCooldown(apiKey: String) {
        if (apiKey.isBlank()) return
        keyCooldownUntil.remove(keyFingerprint(apiKey))
    }

    fun isKeyCoolingDown(apiKey: String): Boolean {
        if (apiKey.isBlank()) return false
        val until = keyCooldownUntil[keyFingerprint(apiKey)] ?: return false
        if (System.currentTimeMillis() >= until) {
            keyCooldownUntil.remove(keyFingerprint(apiKey))
            return false
        }
        return true
    }

    private fun keyFingerprint(apiKey: String): String {
        val trimmed = apiKey.trim()
        return "${trimmed.take(8)}_${trimmed.takeLast(6)}_${trimmed.length}"
    }

    /**
     * Picks the best non-cooled Gemini API key across all 3 Gemini slots for Gemini Voice TTS.
     */
    fun resolveBestGeminiKey(settings: UserSettings): String {
        val slots = settings.getConfiguredKeysForProvider(AiProvider.GEMINI)
        if (slots.isEmpty()) return ""
        return slots.firstOrNull { !isKeyCoolingDown(it.second) }?.second
            ?: slots.first().second
    }

    /**
     * Pre-warms network sockets for whichever providers the user has configured.
     */
    suspend fun warmUpConfiguredProviders(settings: UserSettings) = withContext(Dispatchers.IO) {
        if (settings.getConfiguredKeysForProvider(AiProvider.GEMINI).isNotEmpty()) {
            GeminiAiService.warmUpConnection()
        }
        val activeProvider = AiProvider.fromId(settings.activeAiProvider)
        val warmUrl = when (activeProvider) {
            AiProvider.GROK -> "https://api.x.ai/"
            AiProvider.OPENAI -> "https://api.openai.com/"
            AiProvider.ANTHROPIC -> "https://api.anthropic.com/"
            AiProvider.GEMINI -> null
        }
        if (warmUrl != null) {
            runCatching {
                val req = Request.Builder().url(warmUrl).head().build()
                httpClient.newCall(req).execute().use { }
            }
        }
    }

    private data class CandidateAttempt(
        val provider: AiProvider,
        val modelId: String,
        val keySlot: Int,
        val apiKey: String,
        val isPrimaryCandidate: Boolean
    )

    /**
     * Streams an AI response with automatic 3-key fallback within the active provider,
     * followed by automatic cross-provider failover if all 3 keys hit quota or fail.
     */
    suspend fun generateTextStreamingWithFallback(
        settings: UserSettings,
        geminiFallbackModelId: String,
        userPrompt: String,
        recentHistory: List<AiChatMessage>,
        onActiveAttemptChanged: ((provider: AiProvider, keySlot: Int, modelId: String) -> Unit)? = null,
        onPartialText: (String) -> Unit
    ): MultiAiTextResult = withContext(Dispatchers.IO) {
        val activeProvider = AiProvider.fromId(settings.activeAiProvider)

        // Build ordered provider list: active provider first, then remaining providers as seamless backup
        val providerOrder = listOf(activeProvider) + AiProvider.entries.filter { it != activeProvider }

        val candidates = mutableListOf<CandidateAttempt>()
        providerOrder.forEachIndexed { providerIdx, provider ->
            val configuredSlots = settings.getConfiguredKeysForProvider(provider)
            if (configuredSlots.isEmpty()) return@forEachIndexed

            val modelId = when (provider) {
                AiProvider.GEMINI -> settings.selectedGeminiTextModel.ifBlank {
                    geminiFallbackModelId.ifBlank { provider.defaultModels.first() }
                }
                else -> settings.getSelectedModelForProvider(provider)
            }

            // Try fresh (non-rate-limited) keys first, then cooled-down keys as last resort
            val freshSlots = configuredSlots.filter { !isKeyCoolingDown(it.second) }
            val cooledSlots = configuredSlots.filter { isKeyCoolingDown(it.second) }
            val orderedSlots = freshSlots + cooledSlots

            orderedSlots.forEachIndexed { slotIdx, (slotNumber, apiKey) ->
                candidates.add(
                    CandidateAttempt(
                        provider = provider,
                        modelId = modelId,
                        keySlot = slotNumber,
                        apiKey = apiKey,
                        isPrimaryCandidate = (providerIdx == 0 && slotIdx == 0 && slotNumber == 1)
                    )
                )
            }
        }

        if (candidates.isEmpty()) {
            return@withContext MultiAiTextResult.Error(
                "No AI API key configured yet. Tap 'AI Keys' to add your Gemini, Grok, OpenAI, or Anthropic key."
            )
        }

        val errors = mutableListOf<String>()

        for ((index, candidate) in candidates.withIndex()) {
            if (!isActive) throw CancellationException("Cancelled")

            onActiveAttemptChanged?.invoke(candidate.provider, candidate.keySlot, candidate.modelId)

            val result = when (candidate.provider) {
                AiProvider.GEMINI -> {
                    when (
                        val gRes = GeminiAiService.generateTextStreaming(
                            apiKey = candidate.apiKey,
                            modelId = candidate.modelId,
                            userPrompt = userPrompt,
                            recentHistory = recentHistory,
                            onPartialText = onPartialText
                        )
                    ) {
                        is GeminiTextResult.Success -> {
                            clearKeyCooldown(candidate.apiKey)
                            MultiAiTextResult.Success(
                                text = gRes.text,
                                provider = AiProvider.GEMINI,
                                modelId = gRes.modelId,
                                keySlot = candidate.keySlot,
                                usedFallback = index > 0 || !candidate.isPrimaryCandidate
                            )
                        }
                        is GeminiTextResult.Error -> {
                            if (isQuotaOrAuthFailure(gRes.message)) {
                                markKeyExhausted(candidate.apiKey)
                            }
                            MultiAiTextResult.Error(gRes.message)
                        }
                    }
                }

                AiProvider.GROK -> streamOpenAiCompatible(
                    endpointUrl = "https://api.x.ai/v1/chat/completions",
                    provider = AiProvider.GROK,
                    apiKey = candidate.apiKey,
                    modelId = candidate.modelId,
                    keySlot = candidate.keySlot,
                    usedFallback = index > 0 || !candidate.isPrimaryCandidate,
                    userPrompt = userPrompt,
                    recentHistory = recentHistory,
                    onPartialText = onPartialText
                )

                AiProvider.OPENAI -> streamOpenAiCompatible(
                    endpointUrl = "https://api.openai.com/v1/chat/completions",
                    provider = AiProvider.OPENAI,
                    apiKey = candidate.apiKey,
                    modelId = candidate.modelId,
                    keySlot = candidate.keySlot,
                    usedFallback = index > 0 || !candidate.isPrimaryCandidate,
                    userPrompt = userPrompt,
                    recentHistory = recentHistory,
                    onPartialText = onPartialText
                )

                AiProvider.ANTHROPIC -> streamAnthropic(
                    apiKey = candidate.apiKey,
                    modelId = candidate.modelId,
                    keySlot = candidate.keySlot,
                    usedFallback = index > 0 || !candidate.isPrimaryCandidate,
                    userPrompt = userPrompt,
                    recentHistory = recentHistory,
                    onPartialText = onPartialText
                )
            }

            when (result) {
                is MultiAiTextResult.Success -> return@withContext result
                is MultiAiTextResult.Error -> {
                    errors.add("${candidate.provider.shortName} Key #${candidate.keySlot}: ${result.message}")
                }
            }
        }

        val summary = if (errors.size == 1) {
            errors.first()
        } else {
            "All configured API keys hit limits or failed (${errors.size} tried). Latest: ${errors.lastOrNull().orEmpty()}"
        }
        MultiAiTextResult.Error(summary)
    }

    private fun isQuotaOrAuthFailure(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("429") ||
            m.contains("quota") ||
            m.contains("rate limit") ||
            m.contains("resource_exhausted") ||
            m.contains("insufficient") ||
            m.contains("credit") ||
            m.contains("401") ||
            m.contains("403") ||
            m.contains("invalid") ||
            m.contains("limit")
    }

    /**
     * Streams responses from OpenAI-compatible Chat Completions endpoints (OpenAI & xAI Grok).
     */
    private suspend fun streamOpenAiCompatible(
        endpointUrl: String,
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        keySlot: Int,
        usedFallback: Boolean,
        userPrompt: String,
        recentHistory: List<AiChatMessage>,
        onPartialText: (String) -> Unit
    ): MultiAiTextResult = withContext(Dispatchers.IO) {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            recentHistory.takeLast(8).forEach { msg ->
                if (msg.text.isNotBlank() && !msg.isStreaming) {
                    put(
                        JSONObject()
                            .put("role", if (msg.isUser) "user" else "assistant")
                            .put("content", msg.text)
                    )
                }
            }
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val bodyJson = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", 0.65)
            .toString()

        val request = Request.Builder()
            .url(endpointUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        val call = httpClient.newCall(request)
        activeThirdPartyCall = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    val parsedMsg = extractApiError(errBody, response.code, provider.shortName)
                    if (response.code == 429 || response.code == 401 || response.code == 403 || isQuotaOrAuthFailure(parsedMsg)) {
                        markKeyExhausted(apiKey)
                    }
                    return@withContext MultiAiTextResult.Error(parsedMsg)
                }

                val source = response.body?.source()
                    ?: return@withContext MultiAiTextResult.Error("Empty stream from ${provider.shortName}")

                val fullBuilder = StringBuilder()
                var lastEmitMs = 0L

                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue

                    val deltaText = runCatching {
                        val json = JSONObject(payload)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")
                            delta?.optString("content", "")
                                ?: choice.optJSONObject("message")?.optString("content", "")
                                ?: ""
                        } else ""
                    }.getOrDefault("")

                    if (deltaText.isNotEmpty()) {
                        fullBuilder.append(deltaText)
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 35L) {
                            lastEmitMs = now
                            onPartialText(fullBuilder.toString())
                        }
                    }
                }

                val finalText = fullBuilder.toString().trim()
                if (finalText.isBlank()) {
                    return@withContext MultiAiTextResult.Error("${provider.shortName} returned an empty response.")
                }

                clearKeyCooldown(apiKey)
                onPartialText(finalText)
                MultiAiTextResult.Success(
                    text = finalText,
                    provider = provider,
                    modelId = modelId,
                    keySlot = keySlot,
                    usedFallback = usedFallback
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ioe: IOException) {
            if (call.isCanceled()) throw CancellationException("Cancelled")
            MultiAiTextResult.Error("Network error (${provider.shortName}): ${ioe.localizedMessage ?: "Check connection"}")
        } catch (e: Exception) {
            MultiAiTextResult.Error("${provider.shortName} error: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            if (activeThirdPartyCall === call) {
                activeThirdPartyCall = null
            }
        }
    }

    /**
     * Streams responses from Anthropic Messages API (Claude 3.5 / 3.7).
     */
    private suspend fun streamAnthropic(
        apiKey: String,
        modelId: String,
        keySlot: Int,
        usedFallback: Boolean,
        userPrompt: String,
        recentHistory: List<AiChatMessage>,
        onPartialText: (String) -> Unit
    ): MultiAiTextResult = withContext(Dispatchers.IO) {
        val messages = JSONArray().apply {
            recentHistory.takeLast(8).forEach { msg ->
                if (msg.text.isNotBlank() && !msg.isStreaming) {
                    put(
                        JSONObject()
                            .put("role", if (msg.isUser) "user" else "assistant")
                            .put("content", msg.text)
                    )
                }
            }
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val bodyJson = JSONObject()
            .put("model", modelId)
            .put("max_tokens", 1024)
            .put("system", SYSTEM_PROMPT)
            .put("messages", messages)
            .put("stream", true)
            .toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        val call = httpClient.newCall(request)
        activeThirdPartyCall = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    val parsedMsg = extractApiError(errBody, response.code, "Anthropic")
                    if (response.code == 429 || response.code == 401 || response.code == 403 || isQuotaOrAuthFailure(parsedMsg)) {
                        markKeyExhausted(apiKey)
                    }
                    return@withContext MultiAiTextResult.Error(parsedMsg)
                }

                val source = response.body?.source()
                    ?: return@withContext MultiAiTextResult.Error("Empty stream from Anthropic")

                val fullBuilder = StringBuilder()
                var lastEmitMs = 0L

                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue

                    val deltaText = runCatching {
                        val json = JSONObject(payload)
                        if (json.optString("type") == "content_block_delta") {
                            json.optJSONObject("delta")?.optString("text", "") ?: ""
                        } else ""
                    }.getOrDefault("")

                    if (deltaText.isNotEmpty()) {
                        fullBuilder.append(deltaText)
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 35L) {
                            lastEmitMs = now
                            onPartialText(fullBuilder.toString())
                        }
                    }
                }

                val finalText = fullBuilder.toString().trim()
                if (finalText.isBlank()) {
                    return@withContext MultiAiTextResult.Error("Anthropic returned an empty response.")
                }

                clearKeyCooldown(apiKey)
                onPartialText(finalText)
                MultiAiTextResult.Success(
                    text = finalText,
                    provider = AiProvider.ANTHROPIC,
                    modelId = modelId,
                    keySlot = keySlot,
                    usedFallback = usedFallback
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ioe: IOException) {
            if (call.isCanceled()) throw CancellationException("Cancelled")
            MultiAiTextResult.Error("Network error (Anthropic): ${ioe.localizedMessage ?: "Check connection"}")
        } catch (e: Exception) {
            MultiAiTextResult.Error("Anthropic error: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            if (activeThirdPartyCall === call) {
                activeThirdPartyCall = null
            }
        }
    }

    private fun extractApiError(body: String, httpCode: Int, providerName: String): String {
        val parsed = runCatching {
            val root = JSONObject(body)
            val errObj = root.optJSONObject("error")
            errObj?.optString("message")?.takeIf { it.isNotBlank() }
                ?: root.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return when {
            httpCode == 429 -> "$providerName quota/rate limit reached (429)"
            httpCode == 401 || httpCode == 403 -> "$providerName API key invalid or unauthorized ($httpCode)"
            !parsed.isNullOrBlank() -> "$providerName ($httpCode): $parsed"
            else -> "$providerName request failed (HTTP $httpCode)"
        }
    }
}
