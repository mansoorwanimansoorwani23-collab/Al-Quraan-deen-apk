package com.example.ui.screens.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiChatMessage
import com.example.ai.GeminiAiService
import com.example.ai.GeminiModelCategory
import com.example.ai.GeminiModelInfo
import com.example.ui.MainViewModel
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LiquidGlassIconButton
import com.example.ui.components.LiquidGlassSegmentedTabs
import com.example.ui.components.LiquidGlassTextField
import com.example.ui.theme.BrandDisplayFontFamily
import com.example.ui.theme.GoldAccentDark

private const val GEMINI_API_KEY_URL = "https://aistudio.google.com/app/apikey"

@Composable
fun HidayahAiScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.userSettings.collectAsState()
    val availableModels by viewModel.availableGeminiModels.collectAsState()
    val isFetchingModels by viewModel.isFetchingGeminiModels.collectAsState()
    val modelFetchError by viewModel.geminiModelFetchError.collectAsState()
    val chatMessages by viewModel.aiChatMessages.collectAsState()
    val isGeneratingText by viewModel.isAiGeneratingText.collectAsState()
    val isGeneratingAudio by viewModel.isAiGeneratingAudio.collectAsState()
    val isAudioPlaying by viewModel.isAiAudioPlaying.collectAsState()
    val audioStatus by viewModel.aiAudioStatusMessage.collectAsState()
    val activePlayingMessageId by viewModel.activePlayingMessageId.collectAsState()

    var selectedModeTab by remember { mutableStateOf(0) } // 0 = Text Generation, 1 = Audio Read
    var promptInput by remember { mutableStateOf("") }
    var audioTextInput by remember { mutableStateOf("") }
    var apiKeyDraft by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var showApiKeyPlain by remember { mutableStateOf(false) }

    // Guided 3-step setup pop-up state: Step 0 = Text Model, Step 1 = Voice Model, Step 2 = Voice (Male/Female)
    var showModelSetupPopup by remember { mutableStateOf(false) }
    var setupWizardInitialStep by remember { mutableStateOf(0) }
    var showApiKeyChangeDialog by remember { mutableStateOf(false) }

    val chatListState = rememberLazyListState()

    val hasApiKey = settings.geminiApiKey.isNotBlank()
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

    // Pre-warm HTTP/2 TLS socket + fallback TTS engine as soon as user opens AI screen
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

    fun openGetGeminiKeyPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_API_KEY_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Gemini API Key URL", GEMINI_API_KEY_URL))
            Toast.makeText(context, "Copied API Key link: aistudio.google.com/app/apikey", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header + Active Models Card
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hidayah_ai_header_card"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Hidayah AI",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hidayah AI",
                                fontFamily = BrandDisplayFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (hasApiKey) {
                                    "Voice: ${activeVoiceOption.name} (${activeVoiceOption.gender})"
                                } else {
                                    "Enter Gemini API key below to start"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasApiKey) {
                            LiquidGlassIconButton(
                                onClick = {
                                    setupWizardInitialStep = 0
                                    if (availableModels.isEmpty()) {
                                        viewModel.refreshGeminiModels { ok ->
                                            if (ok) showModelSetupPopup = true
                                        }
                                    } else {
                                        showModelSetupPopup = true
                                    }
                                },
                                icon = Icons.Filled.Tune,
                                contentDescription = "Select AI Models & Voice",
                                size = 40.dp,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("ai_open_model_wizard_button")
                            )
                        }
                        LiquidGlassIconButton(
                            onClick = {
                                if (hasApiKey) showApiKeyChangeDialog = true else onOpenSettings()
                            },
                            icon = if (hasApiKey) Icons.Filled.Key else Icons.Filled.Settings,
                            contentDescription = "Manage Gemini API Key",
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("ai_open_settings_button")
                        )
                    }
                }

                if (hasApiKey) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    setupWizardInitialStep = 0
                                    showModelSetupPopup = true
                                }
                                .testTag("ai_chip_text_model"),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "TEXT MODEL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = settings.selectedGeminiTextModel.ifBlank { "Tap to choose" },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    setupWizardInitialStep = 1
                                    showModelSetupPopup = true
                                }
                                .testTag("ai_chip_voice_model"),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "VOICE MODEL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = settings.selectedGeminiAudioModel.ifBlank { "Tap to choose" },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!hasApiKey) {
            // Direct In-Screen Gemini API Key Setup Card (Triggers 3-Step Model & Voice Setup Pop-up)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 114.dp),
                verticalArrangement = Arrangement.Center
            ) {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_api_key_required_card"),
                    contentPadding = PaddingValues(22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Connect Gemini AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter your Gemini API key below. Right after saving, a quick 3-step pop-up will let you choose your Text Model, Voice Model, and Male/Female Voice.",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            softWrap = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        LiquidGlassTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            label = "Gemini API Key",
                            placeholder = "AIzaSy...",
                            singleLine = true,
                            visualTransformation = if (showApiKeyPlain) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
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

                        if (!modelFetchError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = modelFetchError.orEmpty(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                softWrap = true
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val trimmed = apiKeyDraft.trim()
                                if (trimmed.isBlank()) {
                                    Toast.makeText(context, "Please paste your Gemini API key first", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.saveGeminiApiKeyAndFetchModels(trimmed) { success ->
                                        if (success) {
                                            setupWizardInitialStep = 0
                                            showModelSetupPopup = true
                                        }
                                    }
                                }
                            },
                            enabled = !isFetchingModels && apiKeyDraft.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("ai_save_key_open_wizard_button")
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Connecting & Loading Models...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Key & Choose Models", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { openGetGeminiKeyPage() },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ai_get_gemini_key_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Get Your Gemini API Key Here", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        } else {
            // Mode Switcher: Liquid Glass Segmented Control
            LiquidGlassSegmentedTabs(
                tabs = listOf("Text Generation", "Audio Read"),
                selectedIndex = selectedModeTab,
                onSelect = { selectedModeTab = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Live Audio Status Banner (if playing/generating)
            AnimatedVisibility(visible = isGeneratingAudio || isAudioPlaying || !audioStatus.isNullOrBlank()) {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (isGeneratingAudio) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = audioStatus ?: if (isGeneratingAudio) "Generating AI audio..." else "Playing AI Audio Read",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = true
                            )
                        }
                        if (isAudioPlaying || isGeneratingAudio) {
                            TextButton(onClick = { viewModel.stopAiAudioPlayback() }) {
                                Text("Stop", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (selectedModeTab == 0) {
                // ================= TEXT GENERATION TAB =================
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
                                        text = "Assalamu Alaikum — Ask Hidayah AI",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Ask questions in English, Hindi, Urdu, or Arabic about Tafsir, Hadith, Duas, Salah, or daily guidance. Tap any reply's speaker icon to hear it in ${activeVoiceOption.name} (${activeVoiceOption.gender}) voice.",
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val starters = listOf(
                                        "Explain Ayat al-Kursi simply",
                                        "Morning Dua with meaning",
                                        "How to improve Khushu in Salah?",
                                        "नमाज़ में ध्यान लगाने के तरीके बताएं"
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(starters) { starter ->
                                            AssistChip(
                                                onClick = { viewModel.sendAiChatPrompt(starter) },
                                                label = { Text(starter, fontSize = 12.sp) }
                                            )
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
                                    Text("Clear Chat", fontSize = 12.sp)
                                }
                            }
                        }

                        items(chatMessages, key = { it.id }) { msg ->
                            val isThisMessageAudioActive = activePlayingMessageId == msg.id
                            AiChatBubble(
                                message = msg,
                                isAudioLoading = isThisMessageAudioActive && isGeneratingAudio,
                                isAudioPlaying = isThisMessageAudioActive && isAudioPlaying,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Hidayah AI", msg.text))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                onReadAloud = {
                                    viewModel.readTextWithGeminiAudio(msg.text, messageId = msg.id)
                                }
                            )
                        }
                    }
                }

                // Bottom Composer (spaced cleanly above floating Liquid Glass dock)
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 98.dp),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            placeholder = { Text("Ask anything...", fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_prompt_input"),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
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
                                        viewModel.sendAiChatPrompt(trimmed)
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
                // ================= AUDIO READ TAB =================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(18.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "AI Audio Read Studio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Voice Model: ${settings.selectedGeminiAudioModel.ifBlank { "Tap Tune icon to select" }}",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Select Voice (Male / Female):",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))

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
                                    label = "Text for AI to Read Aloud",
                                    placeholder = "Type or paste a Dua meaning, Hadith, reflection, or lesson...",
                                    singleLine = false,
                                    minLines = 4,
                                    maxLines = 7,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_audio_read_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        if (isAudioPlaying) {
                                            viewModel.stopAiAudioPlayback()
                                        } else if (audioTextInput.isNotBlank()) {
                                            viewModel.readTextWithGeminiAudio(audioTextInput)
                                        }
                                    },
                                    enabled = (audioTextInput.isNotBlank() && !isGeneratingAudio) || isAudioPlaying,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAudioPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("ai_audio_read_play_button")
                                ) {
                                    Icon(
                                        imageVector = if (isAudioPlaying) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            isGeneratingAudio -> "Generating Audio..."
                                            isAudioPlaying -> "Stop Audio"
                                            else -> "Read Aloud with AI"
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
                                    text = "Quick Passages to Listen",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val samples = listOf(
                                    "Hadith on Intentions" to "The Prophet Muhammad (peace be upon him) said: Actions are judged by intentions, and every person will have what they intended.",
                                    "Morning Gratitude Reflection" to "O Allah, whatever blessing has been received by me or by any of Your creation is from You alone, without partner; so for You is all praise and thanks.",
                                    "Peace & Remembrance (Hindi)" to "बेशक अल्लाह के ज़िक्र से ही दिलों को सच्चा सुकून मिलता है। हर सुबह और शाम अल्लाह का शुक्र अदा करें।"
                                )

                                samples.forEach { (title, text) ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { audioTextInput = text },
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
                                                contentDescription = "Load sample",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
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
                    "AI configured: $chosenText • $chosenVoice",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // ================= UPDATE API KEY POP-UP (From AI Header Key Icon) =================
    if (showApiKeyChangeDialog) {
        var modalKey by remember { mutableStateOf(settings.geminiApiKey) }
        var showPlain by remember { mutableStateOf(false) }

        LiquidGlassDialog(
            onDismissRequest = { showApiKeyChangeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gemini API Key")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Update your Gemini API key below. After saving, the 3-step Model & Voice selector will open automatically.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidGlassTextField(
                        value = modalKey,
                        onValueChange = { modalKey = it },
                        label = "Gemini API Key",
                        placeholder = "AIzaSy...",
                        singleLine = true,
                        visualTransformation = if (showPlain) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPlain = !showPlain }) {
                                Icon(
                                    imageVector = if (showPlain) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { openGetGeminiKeyPage() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Get Your Gemini API Key Here", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyChangeDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = modalKey.trim()
                        if (trimmed.isNotBlank()) {
                            showApiKeyChangeDialog = false
                            viewModel.saveGeminiApiKeyAndFetchModels(trimmed) { success ->
                                if (success) {
                                    setupWizardInitialStep = 0
                                    showModelSetupPopup = true
                                }
                            }
                        }
                    },
                    enabled = modalKey.isNotBlank() && !isFetchingModels,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save & Select Models", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Clean, user-friendly 3-Step Guided AI Setup Pop-up:
 * Step 1: Select Text Model (recommends 3.1 Flash at top, user can pick any model)
 * Step 2: Select Voice/Audio Model (recommends 3.8 Flash at top, user can pick any model)
 * Step 3: Select Voice with clear Male / Female labels next to each voice -> Submit & Save all 3
 */
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
                // 3-step progress indicator bar
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
                transitionSpec = {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                },
                label = "ai_setup_wizard_step"
            ) { step ->
                when (step) {
                    0 -> {
                        // STEP 1: TEXT MODEL
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
                        // STEP 2: VOICE / AUDIO MODEL
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
                        // STEP 3: VOICE SELECTION (WITH MALE / FEMALE LABELS)
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
                    onClick = {
                        onSubmit(chosenTextModel, chosenAudioModel, chosenVoice)
                    },
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

@Composable
private fun AiChatBubble(
    message: AiChatMessage,
    isAudioLoading: Boolean = false,
    isAudioPlaying: Boolean = false,
    onCopy: () -> Unit,
    onReadAloud: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(if (message.isUser) 0.86f else 0.96f),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isUser) 20.dp else 6.dp,
                bottomEnd = if (message.isUser) 6.dp else 20.dp
            ),
            borderColor = if (message.isUser) {
                GoldAccentDark.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!message.isUser && message.modelUsed.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "Hidayah AI • ${message.modelUsed}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (message.isStreaming) {
                            Spacer(modifier = Modifier.width(6.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.6.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (!message.isUser && message.isStreaming && message.text.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Responding...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true
                    )
                }
                if (!message.isUser && message.text.isNotBlank() && !message.isStreaming) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy AI reply",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onReadAloud, modifier = Modifier.size(40.dp)) {
                            when {
                                isAudioLoading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                isAudioPlaying -> {
                                    Icon(
                                        imageVector = Icons.Filled.StopCircle,
                                        contentDescription = "Stop reading AI reply",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Read AI reply aloud",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
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
