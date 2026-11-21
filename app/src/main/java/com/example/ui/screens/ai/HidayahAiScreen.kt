package com.example.ui.screens.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiChatMessage
import com.example.ai.GeminiAiService
import com.example.ai.GeminiModelCategory
import com.example.ui.MainViewModel
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassSegmentedTabs
import com.example.ui.components.LiquidGlassTextField
import com.example.ui.theme.BrandDisplayFontFamily
import com.example.ui.theme.GoldAccentDark

@Composable
fun HidayahAiScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.userSettings.collectAsState()
    val availableModels by viewModel.availableGeminiModels.collectAsState()
    val chatMessages by viewModel.aiChatMessages.collectAsState()
    val isGeneratingText by viewModel.isAiGeneratingText.collectAsState()
    val isGeneratingAudio by viewModel.isAiGeneratingAudio.collectAsState()
    val isAudioPlaying by viewModel.isAiAudioPlaying.collectAsState()
    val audioStatus by viewModel.aiAudioStatusMessage.collectAsState()

    var selectedModeTab by remember { mutableStateOf(0) } // 0 = Text Generation, 1 = Audio Read
    var promptInput by remember { mutableStateOf("") }
    var audioTextInput by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    val hasApiKey = settings.geminiApiKey.isNotBlank()
    val textModels = remember(availableModels) {
        availableModels.filter { it.category == GeminiModelCategory.TEXT_GENERATION }
    }
    val audioModels = remember(availableModels) {
        availableModels.filter { it.category == GeminiModelCategory.AUDIO_READ }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty() && selectedModeTab == 0) {
            chatListState.animateScrollToItem(chatMessages.lastIndex)
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
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (hasApiKey) {
                                    "${textModels.size} Text • ${audioModels.size} Audio models loaded"
                                } else {
                                    "Gemini API key required in Settings"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = true
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("ai_open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Configure Gemini Models in Settings",
                            modifier = Modifier.size(18.dp)
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
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenSettings() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = "TEXT MODEL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = settings.selectedGeminiTextModel.ifBlank { "Tap to choose" },
                                    fontSize = 11.sp,
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
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenSettings() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = "AUDIO READ MODEL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = settings.selectedGeminiAudioModel.ifBlank { "Tap to choose" },
                                    fontSize = 11.sp,
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
            // Required API Key Onboarding Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 96.dp),
                contentAlignment = Alignment.Center
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
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Gemini API Key Required",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Open Settings and enter your Gemini API key. All available Gemini models will load automatically in separate Text Generation and Audio Read sections so you can pick your preferred models.",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            softWrap = true
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ai_go_to_settings_button")
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enter Gemini API Key in Settings", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://aistudio.google.com/app/apikey")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Gemini API Key URL", "https://aistudio.google.com/app/apikey"))
                                    Toast.makeText(context, "Copied API Key link: aistudio.google.com/app/apikey", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_get_gemini_key_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Get Your Gemini API Key Here", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            return
        }

        // Mode Switcher: iOS 26 Liquid Glass Segmented Control
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
                                    text = "Ask questions in English, Hindi, Urdu, or Arabic about Tafsir, Hadith, Duas, Salah, or daily guidance. Tap any reply's speaker icon to hear it via your Audio Read model.",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
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
                        AiChatBubble(
                            message = msg,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Hidayah AI", msg.text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            onReadAloud = {
                                viewModel.readTextWithGeminiAudio(msg.text)
                            }
                        )
                    }
                }

                if (isGeneratingText) {
                    item {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Thinking with ${settings.selectedGeminiTextModel.ifBlank { "Gemini" }}...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Composer (spaced above floating Liquid Glass bottom bar)
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 92.dp),
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
                        placeholder = { Text("Ask anything...", fontSize = 13.sp) },
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
                    FilledIconButton(
                        onClick = {
                            val trimmed = promptInput.trim()
                            if (trimmed.isNotEmpty() && !isGeneratingText) {
                                promptInput = ""
                                viewModel.sendAiChatPrompt(trimmed)
                            }
                        },
                        enabled = promptInput.isNotBlank() && !isGeneratingText,
                        modifier = Modifier
                            .size(44.dp)
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
        } else {
            // ================= AUDIO READ TAB =================
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 104.dp),
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
                                text = "Uses your chosen Audio Read model (${settings.selectedGeminiAudioModel.ifBlank { "select in Settings" }}) to read Duas, Hadith, Islamic lessons, or any custom text aloud.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Voice Style:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(GeminiAiService.AVAILABLE_VOICES) { (voice, desc) ->
                                    val isSelected = settings.selectedGeminiVoice == voice
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.selectGeminiVoice(voice) },
                                        label = { Text("$voice • $desc", fontSize = 11.sp) }
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isAudioPlaying) {
                                            viewModel.stopAiAudioPlayback()
                                        } else if (audioTextInput.isNotBlank()) {
                                            viewModel.readTextWithGeminiAudio(audioTextInput)
                                        }
                                    },
                                    enabled = (audioTextInput.isNotBlank() && !isGeneratingAudio) || isAudioPlaying,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAudioPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
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
                                        fontWeight = FontWeight.Bold
                                    )
                                }
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
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { audioTextInput = text },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = text,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.PlayCircle,
                                            contentDescription = "Load sample",
                                            tint = MaterialTheme.colorScheme.primary
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

@Composable
private fun AiChatBubble(
    message: AiChatMessage,
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
                    Text(
                        text = "Hidayah AI • ${message.modelUsed}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )
                if (!message.isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy AI reply",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onReadAloud, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read AI reply aloud",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
