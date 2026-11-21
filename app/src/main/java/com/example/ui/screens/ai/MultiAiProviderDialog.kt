package com.example.ui.screens.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiProvider
import com.example.ai.DetectedClipboardKey
import com.example.ai.GeminiModelInfo
import com.example.ai.MultiAiProviderService
import com.example.data.preferences.UserSettings
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LiquidGlassTextField

/**
 * Multi-AI & 3-Key Automatic Fallback Manager Dialog.
 * Supports Gemini, Grok (xAI / Grok 2 / Grok 3), OpenAI, and Anthropic (Claude).
 * Every provider has 3 API key slots (Primary + Fallback 1 + Fallback 2) + 1-tap Clipboard Auto-Detect.
 */
@Composable
fun MultiAiProviderDialog(
    settings: UserSettings,
    geminiTextModels: List<GeminiModelInfo>,
    isFetchingGeminiModels: Boolean,
    initialProvider: AiProvider = AiProvider.fromId(settings.activeAiProvider),
    onDismiss: () -> Unit,
    onSaveProvider: (provider: AiProvider, k1: String, k2: String, k3: String, model: String) -> Unit,
    onOpenGeminiVoiceWizard: () -> Unit
) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf(initialProvider) }

    val initialKeys = remember(selectedProvider, settings) {
        settings.getKeysForProvider(selectedProvider)
    }
    var key1 by remember(selectedProvider, settings) { mutableStateOf(initialKeys[0]) }
    var key2 by remember(selectedProvider, settings) { mutableStateOf(initialKeys[1]) }
    var key3 by remember(selectedProvider, settings) { mutableStateOf(initialKeys[2]) }
    var selectedModel by remember(selectedProvider, settings) {
        mutableStateOf(settings.getSelectedModelForProvider(selectedProvider))
    }
    var showKeysPlain by remember { mutableStateOf(false) }

    // Check clipboard for 1-tap paste into the next empty slot
    var clipboardSuggestion by remember { mutableStateOf<DetectedClipboardKey?>(null) }

    fun scanClipboardNow() {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            val detected = MultiAiProviderService.detectClipboardApiKey(clipText)
            clipboardSuggestion = if (detected != null && !settings.containsApiKey(detected.apiKey)) {
                detected
            } else null
        }
    }

    LaunchedEffect(Unit) {
        scanClipboardNow()
    }

    fun openConsoleUrl(provider: AiProvider) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.consoleUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("${provider.shortName} API Console", provider.consoleUrl))
            Toast.makeText(context, "Copied link: ${provider.consoleUrl}", Toast.LENGTH_LONG).show()
        }
    }

    val modelChoices = remember(selectedProvider, geminiTextModels) {
        if (selectedProvider == AiProvider.GEMINI && geminiTextModels.isNotEmpty()) {
            geminiTextModels.map { it.id }.distinct()
        } else {
            selectedProvider.defaultModels
        }
    }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Multi-AI & 3-Key Fallback", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Auto-switches Key 1 → Key 2 → Key 3 on quota limit",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Provider Selector Pills: Gemini | Grok | OpenAI | Anthropic
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(AiProvider.entries) { provider ->
                        val isSelected = selectedProvider == provider
                        val keyCount = settings.getConfiguredKeysForProvider(provider).size
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedProvider = provider }
                                .testTag("multi_ai_tab_${provider.id.lowercase()}"),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = provider.shortName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                    } else if (keyCount > 0) {
                                        Color(0xFF16A34A).copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                                    }
                                ) {
                                    Text(
                                        text = "$keyCount/3",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else if (keyCount > 0) {
                                            Color(0xFF16A34A)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Clipboard 1-Tap Auto-Detect Banner
                clipboardSuggestion?.let { detected ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF16A34A).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.40f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPasteGo,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${detected.provider.shortName} Key Detected!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                                Text(
                                    text = "Tap Fill to place into next empty slot",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    selectedProvider = detected.provider
                                    when {
                                        key1.isBlank() -> key1 = detected.apiKey
                                        key2.isBlank() -> key2 = detected.apiKey
                                        else -> key3 = detected.apiKey
                                    }
                                    clipboardSuggestion = null
                                }
                            ) {
                                Text("Fill", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Active Provider Banner + Get Key Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedProvider.displayName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { openConsoleUrl(selectedProvider) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Model Selector Chips
                Text(
                    text = "Model",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(modelChoices) { modelId ->
                        val active = selectedModel == modelId
                        FilterChip(
                            selected = active,
                            onClick = { selectedModel = modelId },
                            label = {
                                Text(
                                    text = modelId,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3 API Key Slots (Primary + Fallback 1 + Fallback 2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3 API Key Slots (Quota Fallback)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = { showKeysPlain = !showKeysPlain },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = if (showKeysPlain) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showKeysPlain) "Hide" else "Show", fontSize = 11.sp)
                    }
                }

                val visTransform = if (showKeysPlain) VisualTransformation.None else PasswordVisualTransformation()

                LiquidGlassTextField(
                    value = key1,
                    onValueChange = { key1 = it },
                    label = "API Key 1 (Primary)",
                    placeholder = selectedProvider.keyHint,
                    singleLine = true,
                    visualTransformation = visTransform,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("multi_ai_key_slot_1")
                )
                Spacer(modifier = Modifier.height(8.dp))
                LiquidGlassTextField(
                    value = key2,
                    onValueChange = { key2 = it },
                    label = "API Key 2 (Fallback 1)",
                    placeholder = "Auto-used if Key 1 reaches quota",
                    singleLine = true,
                    visualTransformation = visTransform,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("multi_ai_key_slot_2")
                )
                Spacer(modifier = Modifier.height(8.dp))
                LiquidGlassTextField(
                    value = key3,
                    onValueChange = { key3 = it },
                    label = "API Key 3 (Fallback 2)",
                    placeholder = "Auto-used if Key 2 reaches quota",
                    singleLine = true,
                    visualTransformation = visTransform,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("multi_ai_key_slot_3")
                )

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "Smart Fallback: If Key 1 hits quota, Hidayah automatically switches to Key 2 → Key 3 → then any other configured AI provider (Grok / OpenAI / Anthropic / Gemini) without stopping your chat.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (selectedProvider == AiProvider.GEMINI && settings.getConfiguredKeysForProvider(AiProvider.GEMINI).isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenGeminiVoiceWizard()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Gemini Voice & Model Wizard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveProvider(selectedProvider, key1.trim(), key2.trim(), key3.trim(), selectedModel)
                },
                enabled = !isFetchingGeminiModels,
                modifier = Modifier.testTag("multi_ai_save_button")
            ) {
                if (isFetchingGeminiModels) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Save & Use ${selectedProvider.shortName}", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
