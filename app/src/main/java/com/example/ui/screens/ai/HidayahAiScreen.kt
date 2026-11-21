package com.example.ui.screens.ai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ai.AiProvider
import com.example.ai.DetectedClipboardKey
import com.example.ai.GeminiAiService
import com.example.ai.GeminiModelCategory
import com.example.ai.GeminiModelInfo
import com.example.ai.MultiAiProviderService
import com.example.ui.MainViewModel
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LiquidGlassIconButton
import com.example.ui.components.LiquidGlassSegmentedTabs
import com.example.ui.components.LiquidGlassTextField
import com.example.ui.components.LocalBottomDockPadding
import com.example.ui.theme.BrandDisplayFontFamily

private val VOICE_INPUT_LANGUAGES = listOf(
    "hi-IN" to "Hindi / Hinglish",
    "en-US" to "English",
    "ur-PK" to "Urdu (اردو)",
    "ar-SA" to "Arabic (عربي)"
)

@Composable
fun HidayahAiScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.userSettings.collectAsState()
    val availableModels by viewModel.availableGeminiModels.collectAsState()
    val isFetchingModels by viewModel.isFetchingGeminiModels.collectAsState()
    val modelFetchError by viewModel.geminiModelFetchError.collectAsState()
    val chatMessages by viewModel.aiChatMessages.collectAsState()
    val isGeneratingText by viewModel.isAiGeneratingText.collectAsState()
    val voiceState by viewModel.aiVoiceState.collectAsState()

    var selectedModeTab by remember { mutableStateOf(0) } // 0 = Chat, 1 = Voice Reader
    var promptInput by remember { mutableStateOf("") }
    var audioTextInput by remember { mutableStateOf("") }

    // Onboarding Multi-AI setup drafts (when no key configured yet)
    var setupProvider by remember { mutableStateOf(AiProvider.fromId(settings.activeAiProvider)) }
    var setupKey1 by remember(setupProvider) { mutableStateOf(settings.getKeysForProvider(setupProvider)[0]) }
    var setupKey2 by remember(setupProvider) { mutableStateOf(settings.getKeysForProvider(setupProvider)[1]) }
    var setupKey3 by remember(setupProvider) { mutableStateOf(settings.getKeysForProvider(setupProvider)[2]) }
    var showExtraFallbackInputs by remember { mutableStateOf(false) }
    var showApiKeyPlain by remember { mutableStateOf(false) }

    // Option 3: Clipboard Auto-Detect API Key state
    var detectedClipboardKey by remember { mutableStateOf<DetectedClipboardKey?>(null) }
    var dismissedClipboardKeyValue by remember { mutableStateOf<String?>(null) }

    // Voice-to-AI Messaging state (SpeechRecognizer)
    var isVoiceListening by remember { mutableStateOf(false) }
    var liveVoiceTranscript by remember { mutableStateOf("") }
    var voiceInputLangTag by remember { mutableStateOf("hi-IN") }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Dialog states
    var showModelSetupPopup by remember { mutableStateOf(false) }
    var setupWizardInitialStep by remember { mutableStateOf(0) }
    var showMultiAiDialog by remember { mutableStateOf(false) }

    val chatListState = rememberLazyListState()

    val hasAnyAiKey = settings.hasAnyAiApiKey()
    val activeProvider = AiProvider.fromId(settings.activeAiProvider)
    val activeProviderKeyCount = settings.getConfiguredKeysForProvider(activeProvider).size
    val totalKeysCount = settings.totalConfiguredAiKeys()
    val hasGeminiKey = settings.getConfiguredKeysForProvider(AiProvider.GEMINI).isNotEmpty()

    val textModels = remember(availableModels) {
        availableModels.filter { it.category == GeminiModelCategory.TEXT_GENERATION }
    }
    val audioModels = remember(availableModels) {
        availableModels.filter { it.category == GeminiModelCategory.AUDIO_READ }
    }

    val activeVoiceOption = remember(settings.selectedGeminiVoice) {
        GeminiAiService.VOICE_OPTIONS.find { it.name.equals(settings.selectedGeminiVoice, ignoreCase = true) }
            ?: GeminiAiService.VOICE_OPTIONS.first()
    }

    fun checkClipboardForApiKey() {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            val detected = MultiAiProviderService.detectClipboardApiKey(clipText)
            if (detected != null &&
                detected.apiKey != dismissedClipboardKeyValue &&
                !settings.containsApiKey(detected.apiKey)
            ) {
                detectedClipboardKey = detected
            } else {
                detectedClipboardKey = null
            }
        }
    }

    // Automatically scan clipboard on screen launch & whenever user returns from browser/AI Studio
    LaunchedEffect(settings) {
        checkClipboardForApiKey()
    }

    DisposableEffect(lifecycleOwner, settings) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkClipboardForApiKey()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    fun stopVoiceRecording(cancelOnly: Boolean = false) {
        runCatching {
            if (cancelOnly) speechRecognizer?.cancel() else speechRecognizer?.stopListening()
        }
        isVoiceListening = false
        if (cancelOnly) {
            liveVoiceTranscript = ""
        }
    }

    fun startVoiceMessageRecording() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Voice input is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.stopAiAudioPlayback()
        liveVoiceTranscript = ""
        isVoiceListening = true

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isVoiceListening = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isVoiceListening = false
                }

                override fun onError(error: Int) {
                    isVoiceListening = false
                    val pending = liveVoiceTranscript.trim()
                    if (pending.isNotBlank()) {
                        liveVoiceTranscript = ""
                        viewModel.sendAiChatPrompt(pending, isVoiceMessage = true)
                    } else if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(context, "Could not hear clearly. Tap Mic to try again.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResults(results: Bundle?) {
                    isVoiceListening = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                        .ifBlank { liveVoiceTranscript.trim() }
                    liveVoiceTranscript = ""
                    if (spoken.isNotBlank()) {
                        viewModel.sendAiChatPrompt(spoken, isVoiceMessage = true)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        liveVoiceTranscript = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceInputLangTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, voiceInputLangTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer.startListening(intent)
        } catch (_: Exception) {
            isVoiceListening = false
            Toast.makeText(context, "Unable to start microphone", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceMessageRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice messages", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestOrToggleVoiceRecording() {
        if (isVoiceListening) {
            val captured = liveVoiceTranscript.trim()
            stopVoiceRecording(cancelOnly = false)
            if (captured.isNotBlank()) {
                liveVoiceTranscript = ""
                viewModel.sendAiChatPrompt(captured, isVoiceMessage = true)
            }
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoiceMessageRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Pre-warm HTTP/2 TLS sockets + fallback TTS engine on screen entry
    LaunchedEffect(Unit) {
        viewModel.warmUpAiNetwork()
    }

    val lastMsgLength = chatMessages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(chatMessages.size, lastMsgLength) {
        if (chatMessages.isNotEmpty() && selectedModeTab == 0) {
            if (isGeneratingText) {
                chatListState.scrollToItem(chatMessages.lastIndex)
            } else {
                chatListState.animateScrollToItem(chatMessages.lastIndex)
            }
        }
    }

    fun openProviderKeyPage(provider: AiProvider) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.consoleUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("${provider.shortName} API Key URL", provider.consoleUrl))
            Toast.makeText(context, "Copied link: ${provider.consoleUrl}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareReplyText(text: String) {
        runCatching {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$text\n\n— Shared from Hidayah")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
    }

    val bottomDockPadding = LocalBottomDockPadding.current
    val density = LocalDensity.current
    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val effectiveBottomInset = maxOf(bottomDockPadding, imeBottomDp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(bottom = effectiveBottomInset),
        contentAlignment = Alignment.TopCenter
    ) {
        val horizontalPadding = if (maxWidth < 360.dp) 12.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = horizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Clean "Hidayah AI" Front Header (Multi-AI + 3-Key Fallback powered seamlessly underneath)
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hidayah_ai_header_card"),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Hidayah AI",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hidayah AI",
                                fontFamily = BrandDisplayFontFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = if (hasAnyAiKey) {
                                    "${activeProvider.shortName} ($activeProviderKeyCount/3) • $totalKeysCount Total Keys • ${activeVoiceOption.name}"
                                } else {
                                    "Multi-AI Ready (Gemini, Grok, OpenAI, Claude)"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasAnyAiKey) {
                            // Auto-Voice Reply toggle (Voice messages always auto-speak; toggle enables for text too)
                            LiquidGlassIconButton(
                                onClick = {
                                    val next = !settings.autoPlayAiVoiceReply
                                    viewModel.toggleAutoPlayAiVoiceReply(next)
                                    Toast.makeText(
                                        context,
                                        if (next) "Auto-Voice replies ON" else "Auto-Voice for voice messages only",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                icon = if (settings.autoPlayAiVoiceReply) {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                },
                                contentDescription = "Toggle Auto-Voice Replies",
                                size = 38.dp,
                                tint = if (settings.autoPlayAiVoiceReply) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.testTag("ai_auto_voice_toggle_button")
                            )

                            Surface(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .defaultMinSize(minHeight = 38.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showMultiAiDialog = true }
                                    .testTag("ai_open_model_wizard_button"),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Hub,
                                        contentDescription = "Manage Multi-AI Providers & 3 Keys",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "AI Keys",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        } else {
                            LiquidGlassIconButton(
                                onClick = { showMultiAiDialog = true },
                                icon = Icons.Filled.Hub,
                                contentDescription = "Multi-AI Providers & Keys",
                                size = 40.dp,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("ai_open_settings_button")
                            )
                        }
                    }
                }
            }

            // Option 3: Live 1-Tap Clipboard Auto-Detected API Key Banner
            AnimatedVisibility(visible = detectedClipboardKey != null) {
                detectedClipboardKey?.let { detected ->
                    val nextSlot = settings.getKeysForProvider(detected.provider)
                        .indexOfFirst { it.isBlank() }
                        .let { if (it >= 0) it + 1 else 3 }
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("ai_clipboard_autodetect_banner"),
                        shape = RoundedCornerShape(18.dp),
                        borderColor = Color(0xFF16A34A).copy(alpha = 0.55f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPasteGo,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${detected.provider.shortName} API Key Detected!",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                                Text(
                                    text = "1-Tap Save into Slot #$nextSlot (${if (nextSlot == 1) "Primary" else "Fallback ${nextSlot - 1}"})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.saveDetectedClipboardApiKey(detected.provider, detected.apiKey) { slot, loadedModels ->
                                        detectedClipboardKey = null
                                        Toast.makeText(
                                            context,
                                            "Saved ${detected.provider.shortName} Key #$slot automatically!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        if (detected.provider == AiProvider.GEMINI && loadedModels && slot == 1) {
                                            setupWizardInitialStep = 0
                                            showModelSetupPopup = true
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                            ) {
                                Text("Save", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    dismissedClipboardKeyValue = detected.apiKey
                                    detectedClipboardKey = null
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss detected key",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!hasAnyAiKey) {
                // Direct Multi-AI Setup Card (Gemini, Grok, OpenAI, Anthropic — up to 3 Fallback Keys each + Clipboard Auto-Detect)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_api_key_required_card"),
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Hub,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Connect Hidayah Multi-AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Choose Gemini, Grok (xAI), OpenAI, or Anthropic. Add up to 3 API keys per provider for automatic quota fallback — or just copy a key to auto-detect in 1 tap!",
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                softWrap = true
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Provider Picker Chips
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                            ) {
                                items(AiProvider.entries) { provider ->
                                    val selected = setupProvider == provider
                                    FilterChip(
                                        selected = selected,
                                        onClick = { setupProvider = provider },
                                        label = {
                                            Text(
                                                text = provider.shortName,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val vis = if (showApiKeyPlain) VisualTransformation.None else PasswordVisualTransformation()
                            LiquidGlassTextField(
                                value = setupKey1,
                                onValueChange = { setupKey1 = it },
                                label = "${setupProvider.shortName} API Key 1 (Primary)",
                                placeholder = setupProvider.keyHint,
                                singleLine = true,
                                visualTransformation = vis,
                                trailingIcon = {
                                    IconButton(onClick = { showApiKeyPlain = !showApiKeyPlain }) {
                                        Icon(
                                            imageVector = if (showApiKeyPlain) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle key visibility"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_inline_api_key_input")
                            )

                            TextButton(
                                onClick = { showExtraFallbackInputs = !showExtraFallbackInputs },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    imageVector = if (showExtraFallbackInputs) Icons.Filled.ExpandLess else Icons.Filled.AddCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showExtraFallbackInputs) "Hide Fallback Keys 2 & 3" else "Add Fallback Keys 2 & 3 (Optional)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            AnimatedVisibility(visible = showExtraFallbackInputs) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    LiquidGlassTextField(
                                        value = setupKey2,
                                        onValueChange = { setupKey2 = it },
                                        label = "${setupProvider.shortName} API Key 2 (Fallback 1)",
                                        placeholder = "Auto-used if Key 1 hits quota",
                                        singleLine = true,
                                        visualTransformation = vis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LiquidGlassTextField(
                                        value = setupKey3,
                                        onValueChange = { setupKey3 = it },
                                        label = "${setupProvider.shortName} API Key 3 (Fallback 2)",
                                        placeholder = "Auto-used if Key 2 hits quota",
                                        singleLine = true,
                                        visualTransformation = vis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }

                            if (!modelFetchError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = modelFetchError.orEmpty(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    softWrap = true
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val k1 = setupKey1.trim()
                                    val k2 = setupKey2.trim()
                                    val k3 = setupKey3.trim()
                                    if (k1.isBlank() && k2.isBlank() && k3.isBlank()) {
                                        Toast.makeText(context, "Please paste at least one ${setupProvider.shortName} API key", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.saveProviderKeysAndModel(
                                            provider = setupProvider,
                                            key1 = k1,
                                            key2 = k2,
                                            key3 = k3,
                                            selectedModel = settings.getSelectedModelForProvider(setupProvider),
                                            makeActive = true
                                        ) { ok, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            if (setupProvider == AiProvider.GEMINI && ok) {
                                                setupWizardInitialStep = 0
                                                showModelSetupPopup = true
                                            }
                                        }
                                    }
                                },
                                enabled = !isFetchingModels && (setupKey1.isNotBlank() || setupKey2.isNotBlank() || setupKey3.isNotBlank()),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("ai_save_key_open_wizard_button")
                            ) {
                                if (isFetchingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Connecting...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save & Start Hidayah AI", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { openProviderKeyPage(setupProvider) },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("ai_get_gemini_key_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Get ${setupProvider.shortName} API Key (Auto-Detect Ready)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                // Simple, Friendly Mode Tabs: Ask AI | Voice Reader
                LiquidGlassSegmentedTabs(
                    tabs = listOf("Ask AI", "Voice Reader"),
                    selectedIndex = selectedModeTab,
                    onSelect = { selectedModeTab = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Interactive Seekable Voice Player (Pause / Resume / -5s / +5s / Speed / Seekbar)
                AnimatedVisibility(visible = voiceState.isActive || !voiceState.statusMessage.isNullOrBlank()) {
                    AiVoicePlayerBar(
                        voiceState = voiceState,
                        onTogglePauseResume = { viewModel.togglePauseResumeAiVoice() },
                        onRewind = { viewModel.rewindAiVoice(5_000L) },
                        onForward = { viewModel.forwardAiVoice(5_000L) },
                        onSeekFraction = { viewModel.seekAiVoice(it) },
                        onCycleSpeed = { viewModel.cycleAiVoiceSpeed() },
                        onStop = { viewModel.stopAiAudioPlayback() },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (selectedModeTab == 0) {
                // ================= CHAT TAB =================
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (chatMessages.isEmpty()) {
                        item {
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(18.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Assalamu Alaikum",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Ask any Islamic question in Hindi, English, Urdu, or Arabic. Voice automatically prepares alongside every answer so you can listen instantly, pause, rewind, or skip ahead.",
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Try asking:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val starters = listOf(
                                        "Ayat al-Kursi meaning" to "Explain Ayat al-Kursi in simple words",
                                        "नमाज़ में खुशु" to "नमाज़ में ध्यान और खुशु लगाने के आसान तरीके बताएं",
                                        "Morning Dua" to "Share a short morning Dua with meaning",
                                        "सुकून की दुआ" to "दिल को सुकून मिलने की आसान दुआ और उसका मतलब बताएं"
                                    )

                                    starters.chunked(2).forEach { pair ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            pair.forEach { (title, prompt) ->
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .defaultMinSize(minHeight = 56.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .clickable { viewModel.sendAiChatPrompt(prompt) },
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(11.dp),
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            text = title,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = prompt,
                                                            fontSize = 11.sp,
                                                            lineHeight = 15.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.clearAiChat() }) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Chat", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        items(chatMessages, key = { it.id }) { msg ->
                            val isActive = voiceState.activeMessageId == msg.id
                            AiChatBubble(
                                message = msg,
                                isAudioLoading = isActive && voiceState.isLoading,
                                isAudioPlaying = isActive && voiceState.isPlaying,
                                isAudioPaused = isActive && voiceState.isPaused,
                                isVoicePreloaded = voiceState.preloadedMessageIds.contains(msg.id),
                                isVoicePreloading = voiceState.preloadingMessageIds.contains(msg.id),
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Hidayah AI", msg.text))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                onShare = { shareReplyText(msg.text) },
                                onReadAloud = {
                                    viewModel.readTextWithGeminiAudio(msg.text, messageId = msg.id)
                                }
                            )
                        }
                    }
                }

                // Live Voice Message Recording Panel (Speak -> Auto-Send -> Auto-Spoken AI Reply!)
                AnimatedVisibility(visible = isVoiceListening) {
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("ai_voice_recording_panel"),
                        shape = RoundedCornerShape(20.dp),
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Mic,
                                            contentDescription = "Listening",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Listening... Speak your question",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = liveVoiceTranscript.ifBlank { "AI will answer automatically in Voice + Text" },
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                TextButton(onClick = { stopVoiceRecording(cancelOnly = true) }) {
                                    Text("Cancel", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(VOICE_INPUT_LANGUAGES) { (tag, label) ->
                                    val selected = voiceInputLangTag == tag
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            voiceInputLangTag = tag
                                            stopVoiceRecording(cancelOnly = true)
                                            startVoiceMessageRecording()
                                        },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Clean Bottom Composer with Voice Message Mic + Text Input + Send (sits cleanly above dock or keyboard)
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Voice Message Mic Button (Speak to AI -> AI answers with Text + Automatic Voice!)
                        FilledTonalIconButton(
                            onClick = { requestOrToggleVoiceRecording() },
                            enabled = !isGeneratingText,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isVoiceListening) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                },
                                contentColor = if (isVoiceListening) {
                                    MaterialTheme.colorScheme.onError
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("ai_voice_message_mic_button")
                        ) {
                            Icon(
                                imageVector = if (isVoiceListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isVoiceListening) "Finish voice message" else "Send voice message",
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            placeholder = { Text("Ask or tap mic to speak...", fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_prompt_input"),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (isGeneratingText) {
                            FilledIconButton(
                                onClick = { viewModel.stopAiTextGeneration() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("ai_stop_generation_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop generating",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            FilledIconButton(
                                onClick = {
                                    val trimmed = promptInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        promptInput = ""
                                        viewModel.sendAiChatPrompt(trimmed, isVoiceMessage = false)
                                    }
                                },
                                enabled = promptInput.isNotBlank(),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("ai_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send prompt",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // ================= VOICE READER TAB =================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(18.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Voice Reader",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Paste or type any passage to listen with pause, rewind, and skip controls.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Choose Voice (Male / Female)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(GeminiAiService.VOICE_OPTIONS) { option ->
                                        val isSelected = settings.selectedGeminiVoice.equals(option.name, ignoreCase = true)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.selectGeminiVoice(option.name) },
                                            label = {
                                                Text(
                                                    text = "${option.name} (${option.gender})",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                LiquidGlassTextField(
                                    value = audioTextInput,
                                    onValueChange = { audioTextInput = it },
                                    label = "Text to Listen",
                                    placeholder = "Type or paste a Dua meaning, Hadith, or note...",
                                    singleLine = false,
                                    minLines = 4,
                                    maxLines = 7,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_audio_read_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                val isStudioPlaying = voiceState.activeMessageId == "voice_studio" && voiceState.isPlaying
                                val isStudioPaused = voiceState.activeMessageId == "voice_studio" && voiceState.isPaused
                                Button(
                                    onClick = {
                                        if (isStudioPlaying || isStudioPaused) {
                                            viewModel.togglePauseResumeAiVoice()
                                        } else if (audioTextInput.isNotBlank()) {
                                            viewModel.readTextWithGeminiAudio(audioTextInput, messageId = "voice_studio")
                                        }
                                    },
                                    enabled = (audioTextInput.isNotBlank() && !voiceState.isLoading) || isStudioPlaying || isStudioPaused,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("ai_audio_read_play_button")
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isStudioPlaying -> Icons.Filled.Pause
                                            isStudioPaused -> Icons.Filled.PlayArrow
                                            else -> Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            voiceState.isLoading -> "Loading Voice..."
                                            isStudioPlaying -> "Pause Voice"
                                            isStudioPaused -> "Resume Voice"
                                            else -> "Play Voice"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    item {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Quick Samples",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val samples = listOf(
                                    "Hadith on Intentions" to "The Prophet Muhammad, peace be upon him, said, Actions are judged by intentions, and every person will have what they intended.",
                                    "Morning Gratitude" to "O Allah, whatever blessing has been received by me or by any of Your creation is from You alone, without partner, so for You is all praise and thanks.",
                                    "सुकून और ज़िक्र (Hindi)" to "बेशक अल्लाह के ज़िक्र से ही दिलों को सच्चा सुकून मिलता है। हर सुबह और शाम अल्लाह का शुक्र अदा करें।"
                                )

                                samples.forEach { (title, text) ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                audioTextInput = text
                                                viewModel.readTextWithGeminiAudio(text, messageId = "voice_studio")
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = text,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Filled.PlayCircle,
                                                contentDescription = "Play sample",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // ================= 3-STEP AI MODEL & VOICE SETUP POP-UP =================
    if (showModelSetupPopup) {
        AiModelSetupWizardDialog(
            initialStep = setupWizardInitialStep,
            textModels = textModels.ifEmpty { availableModels },
            audioModels = audioModels.ifEmpty { availableModels },
            initialTextModelId = settings.selectedGeminiTextModel,
            initialAudioModelId = settings.selectedGeminiAudioModel,
            initialVoiceName = settings.selectedGeminiVoice,
            onDismiss = { showModelSetupPopup = false },
            onSubmit = { chosenText, chosenAudio, chosenVoice ->
                viewModel.applyAiSetupSelections(
                    textModelId = chosenText,
                    audioModelId = chosenAudio,
                    voiceName = chosenVoice
                )
                showModelSetupPopup = false
                Toast.makeText(
                    context,
                    "Saved: $chosenText • $chosenVoice",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // ================= MULTI-AI & 3-KEY FALLBACK MANAGER DIALOG =================
    if (showMultiAiDialog) {
        MultiAiProviderDialog(
            settings = settings,
            geminiTextModels = textModels,
            isFetchingGeminiModels = isFetchingModels,
            initialProvider = activeProvider,
            onDismiss = { showMultiAiDialog = false },
            onSaveProvider = { provider, k1, k2, k3, modelId ->
                showMultiAiDialog = false
                viewModel.saveProviderKeysAndModel(
                    provider = provider,
                    key1 = k1,
                    key2 = k2,
                    key3 = k3,
                    selectedModel = modelId,
                    makeActive = true
                ) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onOpenGeminiVoiceWizard = {
                setupWizardInitialStep = 0
                if (availableModels.isEmpty() && hasGeminiKey) {
                    viewModel.refreshGeminiModels { ok ->
                        if (ok) showModelSetupPopup = true
                    }
                } else {
                    showModelSetupPopup = true
                }
            }
        )
    }
}

@Composable
private fun AiModelSetupWizardDialog(
    initialStep: Int,
    textModels: List<GeminiModelInfo>,
    audioModels: List<GeminiModelInfo>,
    initialTextModelId: String,
    initialAudioModelId: String,
    initialVoiceName: String,
    onDismiss: () -> Unit,
    onSubmit: (textModelId: String, audioModelId: String, voiceName: String) -> Unit
) {
    var currentStep by remember { mutableStateOf(initialStep.coerceIn(0, 2)) }

    var chosenTextModel by remember(textModels, initialTextModelId) {
        mutableStateOf(
            initialTextModelId.takeIf { id -> textModels.any { it.id == id } }
                ?: textModels.firstOrNull()?.id.orEmpty()
        )
    }
    var chosenAudioModel by remember(audioModels, initialAudioModelId) {
        mutableStateOf(
            initialAudioModelId.takeIf { id -> audioModels.any { it.id == id } }
                ?: audioModels.firstOrNull()?.id.orEmpty()
        )
    }
    var chosenVoice by remember(initialVoiceName) {
        mutableStateOf(
            GeminiAiService.VOICE_OPTIONS.find { it.name.equals(initialVoiceName, ignoreCase = true) }?.name
                ?: GeminiAiService.VOICE_OPTIONS.first().name
        )
    }

    val stepTitle = when (currentStep) {
        0 -> "Step 1 of 3 • Text Model"
        1 -> "Step 2 of 3 • Voice Model"
        else -> "Step 3 of 3 • Select Voice"
    }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stepTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close setup",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (stepIdx in 0..2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (stepIdx <= currentStep) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                                    }
                                )
                        )
                    }
                }
            }
        },
        text = {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "ai_setup_wizard_step"
            ) { step ->
                when (step) {
                    0 -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            RecommendationBanner(
                                badge = "RECOMMENDED FOR TEXT",
                                title = "Best Model for Text: 3.1 Flash",
                                subtitle = "You can use 3.1 Flash for Text (टेक्स्ट के लिए सबसे बेस्ट मॉडल 3.1 Flash है). Choose any model you prefer below:"
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 270.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(textModels, key = { "txt_${it.id}" }) { model ->
                                    val isSelected = chosenTextModel == model.id
                                    val isRecommended = model.id.contains("3.1", ignoreCase = true) &&
                                        model.id.contains("flash", ignoreCase = true)
                                    SetupSelectionTile(
                                        title = model.displayName,
                                        subtitle = model.id,
                                        badgeText = if (isRecommended) "Best • 3.1 Flash" else model.capabilityTag,
                                        isSelected = isSelected,
                                        onClick = { chosenTextModel = model.id },
                                        testTag = "wizard_text_model_${model.id}"
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            RecommendationBanner(
                                badge = "RECOMMENDED FOR VOICE",
                                title = "Best Model for Voice: 3.8 Flash",
                                subtitle = "3.8 Flash is great for Voice and 3.1 Flash is best for Text (वॉइस के लिए 3.8 Flash अच्छा है). Choose any model you prefer below:"
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 270.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(audioModels, key = { "aud_${it.id}" }) { model ->
                                    val isSelected = chosenAudioModel == model.id
                                    val isRecommended = (model.id.contains("3.8", ignoreCase = true) &&
                                        model.id.contains("flash", ignoreCase = true)) ||
                                        model.id.contains("tts", ignoreCase = true)
                                    SetupSelectionTile(
                                        title = model.displayName,
                                        subtitle = model.id,
                                        badgeText = if (model.id.contains("3.8", ignoreCase = true)) {
                                            "Best • 3.8 Flash"
                                        } else if (isRecommended) {
                                            "Voice Ready"
                                        } else {
                                            model.capabilityTag
                                        },
                                        isSelected = isSelected,
                                        onClick = { chosenAudioModel = model.id },
                                        testTag = "wizard_voice_model_${model.id}"
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            RecommendationBanner(
                                badge = "VOICE GENDER & STYLE",
                                title = "Choose Your Voice (Male / Female)",
                                subtitle = "Select a Male or Female voice below, then tap Submit to save all 3 settings."
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 170.dp, max = 270.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(GeminiAiService.VOICE_OPTIONS, key = { "voice_${it.name}" }) { voice ->
                                    val isSelected = chosenVoice.equals(voice.name, ignoreCase = true)
                                    SetupSelectionTile(
                                        title = "${voice.name} — ${voice.gender}",
                                        subtitle = voice.style,
                                        badgeText = voice.gender,
                                        isSelected = isSelected,
                                        onClick = { chosenVoice = voice.name },
                                        testTag = "wizard_voice_${voice.name.lowercase()}"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = { currentStep-- },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back", fontWeight = FontWeight.SemiBold)
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("Later")
                }
            }
        },
        confirmButton = {
            if (currentStep < 2) {
                Button(
                    onClick = { currentStep++ },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("ai_setup_next_button")
                ) {
                    Text(
                        text = if (currentStep == 0) "Next: Voice Model" else "Next: Voice",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            } else {
                Button(
                    onClick = { onSubmit(chosenTextModel, chosenAudioModel, chosenVoice) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("ai_setup_submit_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Submit & Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun RecommendationBanner(
    badge: String,
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                text = badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true
            )
        }
    }
}

@Composable
private fun SetupSelectionTile(
    title: String,
    subtitle: String,
    badgeText: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                }
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
