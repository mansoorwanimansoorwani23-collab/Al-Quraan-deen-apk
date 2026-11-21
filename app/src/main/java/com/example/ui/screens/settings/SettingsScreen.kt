package com.example.ui.screens.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai.AiProvider
import com.example.ai.GeminiAiService
import com.example.ai.GeminiModelCategory
import com.example.audio.AdhanAudioPlayer
import com.example.data.local.IslamicDataSource
import com.example.domain.calculator.CalculationMethod
import com.example.domain.calculator.Madhhab
import com.example.ui.MainViewModel
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LocalBottomDockPadding
import com.example.ui.components.LiquidGlassTextField
import com.example.ui.components.SectionHeader
import com.example.ui.screens.ai.MultiAiProviderDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val settings by viewModel.userSettings.collectAsState()
    val isPlayingAdhan by AdhanAudioPlayer.isPlayingFlow.collectAsState()
    val playingAdhanName by AdhanAudioPlayer.currentAdhanNameFlow.collectAsState()
    val availableGeminiModels by viewModel.availableGeminiModels.collectAsState()
    val isFetchingModels by viewModel.isFetchingGeminiModels.collectAsState()
    val modelFetchError by viewModel.geminiModelFetchError.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var geminiKeyInput by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var showGeminiKey by remember { mutableStateOf(false) }

    val textGenerationModels = remember(availableGeminiModels) {
        availableGeminiModels.filter { it.category == GeminiModelCategory.TEXT_GENERATION }
    }
    val audioReadModels = remember(availableGeminiModels) {
        availableGeminiModels.filter { it.category == GeminiModelCategory.AUDIO_READ }
    }

    var showMethodDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showSourcesDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showMultiAiDialog by remember { mutableStateOf(false) }
    var isDetectingGps by remember { mutableStateOf(false) }

    // Profile form state
    var editName by remember(settings.profileName) { mutableStateOf(settings.profileName) }
    var editBio by remember(settings.profileBio) { mutableStateOf(settings.profileBio) }
    var editAvatar by remember(settings.profileAvatar) { mutableStateOf(settings.profileAvatar.ifEmpty { "👤" }) }

    val triggerGpsDetection: () -> Unit = {
        isDetectingGps = true
        viewModel.detectCurrentGpsLocation(context) { _, message ->
            isDetectingGps = false
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            triggerGpsDetection()
        } else {
            Toast.makeText(
                context,
                "Location permission denied. You can pick your city manually.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val requestGpsOrDetect: () -> Unit = {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            triggerGpsDetection()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val bottomDockPadding = LocalBottomDockPadding.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .imePadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = bottomDockPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. App Appearance: Liquid Glass & Islamic Themes
        item {
            SectionHeader(
                title = "App Theme (Liquid Glass & Islamic)",
                subtitle = "Switch between Liquid Glass and Royal Islamic aesthetics"
            )
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_theme_card"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        val isSelected = LocalAppThemeMode.current == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.updateAppTheme(mode.id) }
                                .testTag("theme_option_${mode.id.lowercase()}"),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                            border = BorderStroke(
                                width = if (isSelected) 1.3.dp else 0.8.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.16f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(mode.swatchPrimary, mode.swatchSecondary)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (mode.isIslamicStyle) Icons.Filled.Mosque else Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            softWrap = true
                                        )
                                        Text(
                                            text = mode.subtitle,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            softWrap = true
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Active theme",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Hidayah AI Multi-Provider & 3-Key Fallback Manager
        item {
            SectionHeader(
                title = "Hidayah AI — Multi-Provider & 3-Key Fallback",
                subtitle = "Add up to 3 API keys each for Gemini, Grok, OpenAI & Anthropic with automatic quota failover"
            )
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_multi_ai_card"),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        title = "Manage AI Providers & 3-Key Slots",
                        subtitle = "Active: ${AiProvider.fromId(settings.activeAiProvider).displayName} • ${settings.totalConfiguredAiKeys()}/12 keys saved",
                        icon = Icons.Filled.AutoAwesome,
                        onClick = { showMultiAiDialog = true },
                        testTag = "settings_open_multi_ai_button"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingToggleRow(
                        title = "Auto-Speak Hidayah AI Replies",
                        subtitle = "Automatically read AI answers aloud (voice messages always reply with voice)",
                        icon = Icons.Filled.RecordVoiceOver,
                        isChecked = settings.autoPlayAiVoiceReply,
                        onCheckedChange = { enabled ->
                            viewModel.toggleAutoPlayAiVoiceReply(enabled)
                        }
                    )
                }
            }
        }

        // 2b. Gemini AI API Key & Auto-Discovered Model Selection (Text Generation & Audio Read)
        item {
            SectionHeader(
                title = "Gemini AI & Model Selection",
                subtitle = "Primary Gemini key (Slot 1) & auto-discovered Text / Audio models"
            )
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_gemini_ai_card"),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gemini API Key (Required for AI)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        if (settings.geminiApiKey.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (availableGeminiModels.isNotEmpty()) "Connected" else "Saved",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LiquidGlassTextField(
                        value = geminiKeyInput,
                        onValueChange = { geminiKeyInput = it },
                        label = "Paste Gemini API Key (AIza...)",
                        placeholder = "Enter your Google AI Studio Gemini API key",
                        singleLine = true,
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    imageVector = if (showGeminiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showGeminiKey) "Hide key" else "Show key"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_gemini_api_key_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveGeminiApiKeyAndFetchModels(geminiKeyInput)
                                Toast.makeText(context, "Saving API key & loading Gemini models...", Toast.LENGTH_SHORT).show()
                            },
                            enabled = geminiKeyInput.isNotBlank() && !isFetchingModels,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_save_gemini_key_button")
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading Models...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save & Load Models", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (settings.geminiApiKey.isNotBlank()) {
                            FilledTonalIconButton(
                                onClick = { viewModel.refreshGeminiModels() },
                                enabled = !isFetchingModels
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh Gemini models")
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    geminiKeyInput = ""
                                    viewModel.clearGeminiConfig()
                                    Toast.makeText(context, "Gemini API key cleared", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Clear Gemini API key",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Dedicated "Get Your Gemini API Key Here" Direct Redirect Section
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://aistudio.google.com/app/apikey")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Gemini API Key URL", "https://aistudio.google.com/app/apikey")
                                    )
                                    Toast.makeText(context, "Copied link: aistudio.google.com/app/apikey", Toast.LENGTH_LONG).show()
                                }
                            }
                            .testTag("get_gemini_api_key_section"),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VpnKey,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Get Your Gemini API Key Here",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Opens Google AI Studio API Keys page directly to create & copy your key.",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("get_gemini_api_key_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open Google AI Studio API Key page",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!modelFetchError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = modelFetchError!!,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp),
                                softWrap = true
                            )
                        }
                    }

                    // Auto-Discovered Separate Model Sections
                    if (availableGeminiModels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // SECTION A: TEXT GENERATION MODELS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Text Generation Models (${textGenerationModels.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Best for Text: 3.1 Flash (टेक्स्ट के लिए सबसे बेस्ट मॉडल 3.1 Flash है)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (textGenerationModels.isEmpty()) {
                            Text(
                                text = "No text generation models returned for this key.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_text_models_section"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                textGenerationModels.forEach { model ->
                                    val isSelected = settings.selectedGeminiTextModel == model.id
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewModel.selectGeminiTextModel(model.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = model.displayName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    softWrap = true
                                                )
                                                Text(
                                                    text = "${model.id} • ${model.capabilityTag}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = "Selected text model",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // SECTION B: AUDIO READ & VOICE MODELS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Audio Read & Voice Models (${audioReadModels.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Best for Voice: 3.8 Flash (वॉइस के लिए 3.8 Flash अच्छा है)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (audioReadModels.isEmpty()) {
                            Text(
                                text = "No dedicated audio models returned for this key; device voice fallback will be used.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = true
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_audio_models_section"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                audioReadModels.forEach { model ->
                                    val isSelected = settings.selectedGeminiAudioModel == model.id
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewModel.selectGeminiAudioModel(model.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = model.displayName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    softWrap = true
                                                )
                                                Text(
                                                    text = "${model.id} • ${model.capabilityTag}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = "Selected audio model",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Preferred Audio Voice (Male / Female):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GeminiAiService.AVAILABLE_VOICES) { (voice, desc) ->
                                FilterChip(
                                    selected = settings.selectedGeminiVoice == voice,
                                    onClick = { viewModel.selectGeminiVoice(voice) },
                                    label = { Text("$voice ($desc)", fontSize = 11.sp) }
                                )
                            }
                        }
                    } else if (settings.geminiApiKey.isBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Once you enter your Gemini API key above and tap Save & Load Models, all available models from your account will appear automatically in separate Text Generation and Audio Read sections.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true
                        )
                    }
                }
            }
        }
        // Optional Profile Section
        item {
            SectionHeader(title = "User Profile (Optional)")
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth().testTag("settings_profile_card"),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (settings.hasCustomProfile && settings.profileAvatar.isNotBlank()) settings.profileAvatar else "👤",
                                fontSize = 24.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (settings.hasCustomProfile && settings.profileName.isNotBlank()) settings.profileName else "Guest User (No Profile)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = true
                            )
                            Text(
                                text = if (settings.hasCustomProfile && settings.profileBio.isNotBlank()) settings.profileBio else "Optional — all Hidayah features work offline",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = true
                            )
                        }
                    }

                    Button(
                        onClick = { showProfileDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("manage_profile_button")
                    ) {
                        Text(
                            text = if (settings.hasCustomProfile) "Edit" else "Create",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Location & Calculation Group
        item {
            SectionHeader(title = "Prayer Times & Local Calculations")
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth().testTag("settings_calculation_card"),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        title = "Location / City",
                        subtitle = String.format(
                            Locale.US,
                            "%s, %s (%.4f°, %.4f° • %s)",
                            settings.cityName,
                            settings.countryName,
                            settings.latitude,
                            settings.longitude,
                            settings.timeZoneId
                        ),
                        icon = Icons.Filled.LocationOn,
                        onClick = { showCityDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isDetectingGps) { requestGpsOrDetect() }
                            .padding(vertical = 4.dp)
                            .testTag("settings_detect_gps_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = "Detect GPS",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Detect Current GPS Location",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    softWrap = true
                                )
                                Text(
                                    text = if (isDetectingGps) "Fetching GPS coordinates & timezone..." else "Use your device's exact latitude, longitude & local timezone",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = true
                                )
                            }
                        }
                        if (isDetectingGps) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    SettingRow(
                        title = "Calculation Method",
                        subtitle = settings.calculationMethod.displayName,
                        icon = Icons.Filled.Calculate,
                        onClick = { showMethodDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    SettingToggleRow(
                        title = "Hanafi Asr Time",
                        subtitle = if (settings.madhhab == Madhhab.HANAFI) "Hanafi (Shadow length 2x)" else "Standard Shafi'i/Maliki/Hanbali (Shadow length 1x)",
                        icon = Icons.Filled.AccessTime,
                        isChecked = settings.madhhab == Madhhab.HANAFI,
                        onCheckedChange = { isHanafi ->
                            viewModel.updateMadhhab(if (isHanafi) Madhhab.HANAFI else Madhhab.STANDARD)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    // 12-Hour vs 24-Hour Time Format Setting
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("settings_time_format_section")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Time Format",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (settings.is24HourFormat) "24-Hour Clock (e.g. 17:45)" else "12-Hour Clock (e.g. 5:45 PM)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = !settings.is24HourFormat,
                                onClick = { viewModel.update24HourFormat(false) },
                                label = {
                                    Text(
                                        "12-Hour (1:30 PM)",
                                        fontSize = 12.sp,
                                        fontWeight = if (!settings.is24HourFormat) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (!settings.is24HourFormat) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f).testTag("time_format_12h_chip")
                            )

                            FilterChip(
                                selected = settings.is24HourFormat,
                                onClick = { viewModel.update24HourFormat(true) },
                                label = {
                                    Text(
                                        "24-Hour (13:30)",
                                        fontSize = 12.sp,
                                        fontWeight = if (settings.is24HourFormat) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (settings.is24HourFormat) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f).testTag("time_format_24h_chip")
                            )
                        }
                    }
                }
            }
        }

        // Notification & Real Adhan Preferences
        item {
            SectionHeader(title = "Real Adhan & Prayer Alerts (All 5 Prayers)")
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth().testTag("settings_adhan_card"),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingToggleRow(
                        title = "Adhan Notifications Master",
                        subtitle = "Play authentic Adhan audio with lock-screen notification",
                        icon = Icons.Filled.NotificationsActive,
                        isChecked = settings.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updatePrayerNotification("all", enabled)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    val prayerList = listOf(
                        "Fajr" to settings.fajrNotification,
                        "Dhuhr" to settings.dhuhrNotification,
                        "Asr" to settings.asrNotification,
                        "Maghrib" to settings.maghribNotification,
                        "Isha" to settings.ishaNotification
                    )

                    Text("Individual Prayer Adhan Alerts:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    prayerList.forEach { (prayerName, isEnabled) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(prayerName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { check ->
                                    viewModel.updatePrayerNotification(prayerName.lowercase(), check)
                                },
                                modifier = Modifier.testTag("settings_adhan_switch_${prayerName.lowercase()}")
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    Text("Select Real Adhan Voice:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    AdhanAudioPlayer.ADHAN_VOICES.forEach { voice ->
                        val isSelected = settings.adhanSoundName.equals(voice.name, ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.updateAdhanSound(voice.name) }
                                .testTag("settings_adhan_voice_${voice.name.lowercase().replace(" ", "_")}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = voice.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        softWrap = true
                                    )
                                    Text(
                                        text = voice.subtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Selected Adhan voice",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Real Adhan Audio Preview Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text("Test Real Adhan Audio", fontWeight = FontWeight.Bold, fontSize = 14.sp, softWrap = true)
                            Text(
                                text = if (isPlayingAdhan) "Playing: ${playingAdhanName ?: settings.adhanSoundName}" else "Active voice: ${settings.adhanSoundName}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = true
                            )
                        }

                        Button(
                            onClick = {
                                if (isPlayingAdhan) {
                                    AdhanAudioPlayer.stopAdhan()
                                } else {
                                    AdhanAudioPlayer.playAdhanSound(context, settings.adhanSoundName)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingAdhan) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("settings_test_adhan_button")
                        ) {
                            Icon(
                                imageVector = if (isPlayingAdhan) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPlayingAdhan) "Stop" else "Play Adhan", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Quran Script & Display Settings
        item {
            SectionHeader(title = "Quran Arabic Script & Display")
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth().testTag("settings_quran_card"),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Arabic Quran Script Calligraphy:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    QuranFontOption.entries.forEach { fontOption ->
                        val isSelected = settings.quranFontFamily.equals(fontOption.id, ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        viewModel.preferencesRepository.updateQuranFontFamily(fontOption.id)
                                    }
                                }
                                .testTag("settings_quran_font_${fontOption.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = fontOption.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        softWrap = true
                                    )
                                    Text(
                                        text = fontOption.styleDescription,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                                Text(
                                    text = "بِسْمِ اللَّهِ",
                                    fontFamily = fontOption.fontFamily,
                                    fontSize = 18.sp,
                                    lineHeight = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Arabic Font Size: ${settings.arabicFontSize.toInt()}sp", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Slider(
                        value = settings.arabicFontSize,
                        onValueChange = { coroutineScope.launch { viewModel.preferencesRepository.updateArabicFontSize(it) } },
                        valueRange = 18f..40f,
                        steps = 10
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    SettingToggleRow(
                        title = "Show English Translation",
                        subtitle = "Display Sahih International translation",
                        icon = Icons.Filled.Translate,
                        isChecked = settings.showTranslation,
                        onCheckedChange = { coroutineScope.launch { viewModel.preferencesRepository.updateShowTranslation(it) } }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    SettingToggleRow(
                        title = "Show Transliteration",
                        subtitle = "Helpful pronunciation guide",
                        icon = Icons.Filled.Spellcheck,
                        isChecked = settings.showTransliteration,
                        onCheckedChange = { coroutineScope.launch { viewModel.preferencesRepository.updateShowTransliteration(it) } }
                    )
                }
            }
        }

        // About & Authentic Sources Reassurance
        item {
            SectionHeader(title = "Authenticity & About Hidayah")
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth().testTag("settings_about_card"),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        title = "Islamic Content Sources & References",
                        subtitle = "Verified Uthmani Quran (6,236 Ayahs offline), Sahih Bukhari, Sahih Muslim, Hisn al-Muslim",
                        icon = Icons.Filled.Verified,
                        onClick = { showSourcesDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    SettingRow(
                        title = "Offline-First & Private",
                        subtitle = "Complete Offline Quran, prayer calculations, and tracker data stay locally on your device",
                        icon = Icons.Filled.Security,
                        onClick = {}
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    SettingRow(
                        title = "About Hidayah",
                        subtitle = "Hidayah • Developed by Rauf • Version 2.0",
                        icon = Icons.Filled.Info,
                        onClick = { showAboutDialog = true },
                        testTag = "settings_about_row"
                    )
                }
            }
        }
    }

    // Optional Profile Dialog (Liquid Glass)
    if (showProfileDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (settings.hasCustomProfile) "Edit Profile" else "Create Optional Profile", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Creating a profile is completely optional. You can use Hidayah without creating any profile.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LiquidGlassTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = "Display Name",
                        placeholder = "e.g. Abdullah, Fatima",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LiquidGlassTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = "Spiritual Goal / Bio",
                        placeholder = "e.g. Memorizing Surah Al-Kahf",
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Select Avatar:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    val avatars = listOf("👤", "🌙", "🕌", "⭐", "📖", "🤲", "🌿", "🕋")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        avatars.forEach { avatarEmoji ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (editAvatar == avatarEmoji) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { editAvatar = avatarEmoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(avatarEmoji, fontSize = 18.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.preferencesRepository.saveUserProfile(
                                name = editName.trim().ifEmpty { "Believer" },
                                bio = editBio.trim(),
                                avatar = editAvatar
                            )
                            showProfileDialog = false
                        }
                    }
                ) {
                    Text("Save Profile")
                }
            },
            dismissButton = {
                if (settings.hasCustomProfile) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.preferencesRepository.deleteUserProfile()
                                editName = ""
                                editBio = ""
                                editAvatar = "👤"
                                showProfileDialog = false
                            }
                        }
                    ) {
                        Text("Remove Profile", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Settings -> About Dialog (ONLY place where community dedication appears)
    if (showAboutDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Mosque, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About Hidayah", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Hidayah — Version 2.0",
                        fontFamily = BrandDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Developed by Rauf",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dedicated Community Callout (ONLY in Settings -> About)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().testTag("about_pushpora_dedication")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Especially for Pushpora Masjid and Madrasa Community",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                softWrap = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "• Complete Offline Quran: All 114 Surahs, 30 Juz, and 6,236 authentic Uthmani Ayahs stored locally on your device.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        softWrap = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Separate Online Quran Audio, Recitation Practice with pronunciation feedback, Learn Salah beginner guide, authentic real Adhan voices, and local latitude/longitude/timezone prayer calculations.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        softWrap = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "May Allah accept this humble effort and make Hidayah beneficial for the Ummah.",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                        softWrap = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Sources Dialog
    if (showSourcesDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showSourcesDialog = false },
            title = { Text("Authentic Islamic Sources", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("1. Holy Quran: Verified Uthmani Text (6,236 Ayahs across 114 Surahs & 30 Juz) & Sahih International English translation bundled locally.", fontSize = 13.sp, softWrap = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("2. Hadith: Sahih al-Bukhari & Sahih Muslim collections.", fontSize = 13.sp, softWrap = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("3. Duas: Hisn al-Muslim (Fortress of the Muslim) by Sa'id bin Wahf Al-Qahtani.", fontSize = 13.sp, softWrap = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("4. Prayer Calculations: Muslim World League, ISNA, Umm al-Qura, Egyptian General Authority of Survey, University of Islamic Sciences Karachi, and exact local solar astronomy.", fontSize = 13.sp, softWrap = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // City Selection Dialog
    if (showCityDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showCityDialog = false },
            title = { Text("Select Your Location", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    item {
                        FilledTonalButton(
                            onClick = {
                                showCityDialog = false
                                requestGpsOrDetect()
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Detect Current GPS Location")
                        }
                        HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))
                    }
                    items(IslamicDataSource.POPULAR_CITIES) { city ->
                        val isSelected = city.name == settings.cityName
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.selectCity(city)
                                    showCityDialog = false
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = city.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        softWrap = true
                                    )
                                    Text(
                                        text = "${city.country} • ${city.timeZoneId}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCityDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Calculation Method Dialog
    if (showMethodDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showMethodDialog = false },
            title = { Text("Calculation Method", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(CalculationMethod.entries) { method ->
                        val isSelected = method == settings.calculationMethod
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.updateCalculationMethod(method)
                                    showMethodDialog = false
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = method.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    softWrap = true,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMethodDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showMultiAiDialog) {
        MultiAiProviderDialog(
            settings = settings,
            geminiTextModels = textGenerationModels,
            isFetchingGeminiModels = isFetchingModels,
            onDismiss = { showMultiAiDialog = false },
            onSaveProvider = { provider, key1, key2, key3, model ->
                viewModel.saveProviderKeysAndModel(
                    provider = provider,
                    key1 = key1,
                    key2 = key2,
                    key3 = key3,
                    selectedModel = model
                )
            },
            onOpenGeminiVoiceWizard = { showMultiAiDialog = false }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(vertical = 4.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, softWrap = true)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, softWrap = true)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)
            }
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}
