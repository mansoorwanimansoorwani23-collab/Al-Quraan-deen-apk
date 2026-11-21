package com.example.ui.screens.quran

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.IslamicDataSource
import com.example.data.model.Ayah
import com.example.domain.recitation.QuranRecitationAnalyzer
import com.example.domain.recitation.RecitationAnalysisResult
import com.example.domain.recitation.WordEvaluationStatus
import com.example.ui.MainViewModel
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LiquidGlassIconButton
import com.example.ui.components.LiquidGlassTopBarSurface
import com.example.ui.theme.*

/**
 * Separate Quran Recitation Practice Screen.
 * Listens through the device microphone (Arabic SpeechRecognizer), compares recitation
 * against the selected Ayah, highlights mistaken/skipped words, explains pronunciation
 * errors (Makharij), and lets the user repeat and practice.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuranRecitationPracticeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.userSettings.collectAsState()

    var selectedSurah by remember { mutableStateOf(IslamicDataSource.SURAHS[0]) } // Al-Fatihah default
    var surahAyahs by remember { mutableStateOf<List<Ayah>>(emptyList()) }
    var selectedAyahIndex by remember { mutableStateOf(0) }
    var showSurahPicker by remember { mutableStateOf(false) }

    var isListening by remember { mutableStateOf(false) }
    var livePartialText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Tap the microphone and recite the Ayah clearly in Arabic.") }
    var analysisResult by remember { mutableStateOf<RecitationAnalysisResult?>(null) }

    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    LaunchedEffect(selectedSurah.number) {
        val loaded = viewModel.quranRepository.getAyahsForSurah(selectedSurah)
        surahAyahs = loaded
        selectedAyahIndex = 0
        analysisResult = null
        livePartialText = ""
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizer = null
        }
    }

    val currentAyah = surahAyahs.getOrNull(selectedAyahIndex)

    fun startArabicRecitationListening() {
        val targetAyah = currentAyah ?: return
        if (!speechAvailable) {
            statusMessage = "On-device Arabic speech recognition service is unavailable on this device. Install or enable Google Speech Services to use live microphone evaluation."
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    livePartialText = ""
                    analysisResult = null
                    statusMessage = "Listening... Recite Ayah ${targetAyah.numberInSurah} aloud."
                }

                override fun onBeginningOfSpeech() {
                    statusMessage = "Hearing your recitation..."
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    statusMessage = "Analyzing your recitation..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    statusMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Could not recognize clear Arabic words. Try reciting slightly slower and closer to the microphone."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the microphone and recite right away."
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone recording error. Check that no other app is using the mic."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs network or an offline Arabic voice pack installed in Android settings."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for recitation practice."
                        else -> "Unable to capture recitation (code $error). Please tap the microphone to try again."
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    // Pick the candidate transcript that scores closest to the expected Ayah (improves Arabic accuracy)
                    val bestResult = matches
                        ?.map { transcript -> QuranRecitationAnalyzer.analyze(targetAyah.arabicText, transcript) }
                        ?.maxByOrNull { it.accuracyPercent }

                    if (bestResult != null) {
                        analysisResult = bestResult
                        livePartialText = bestResult.spokenTranscript
                        statusMessage = bestResult.overallSummary
                    } else {
                        statusMessage = "No recitation transcript captured. Tap the microphone to practice again."
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        livePartialText = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            }

            recognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            statusMessage = "Could not start microphone listener: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    fun stopListeningNow() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        isListening = false
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startArabicRecitationListening()
        } else {
            statusMessage = "Microphone permission denied. Please grant Record Audio permission to practice recitation."
            Toast.makeText(context, "Microphone permission is needed to listen to your recitation", Toast.LENGTH_SHORT).show()
        }
    }

    LiquidGlassBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LiquidGlassTopBarSurface {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Quran Recitation Practice",
                                    fontFamily = BrandDisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Recite Aloud • Word Check • Pronunciation Guidance",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            Box(modifier = Modifier.padding(start = 10.dp, end = 6.dp)) {
                                LiquidGlassIconButton(
                                    onClick = onBack,
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    size = 40.dp,
                                    modifier = Modifier.testTag("recitation_practice_back_button")
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Surah & Ayah Selector Card
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("practice_selector_card"),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Surah ${selectedSurah.number}. ${selectedSurah.nameEnglish}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${selectedSurah.nameArabic} • Ayah ${selectedAyahIndex + 1} of ${surahAyahs.size.coerceAtLeast(1)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { showSurahPicker = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("practice_change_surah_button")
                            ) {
                                Text("Change Surah", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (selectedAyahIndex > 0) {
                                        selectedAyahIndex--
                                        analysisResult = null
                                        livePartialText = ""
                                    }
                                },
                                enabled = selectedAyahIndex > 0,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Previous", fontSize = 12.sp)
                            }

                            if (currentAyah != null) {
                                TextButton(
                                    onClick = { viewModel.playQuranAyah(selectedSurah, currentAyah, 0) }
                                ) {
                                    Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Listen Reference", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (selectedAyahIndex + 1 < surahAyahs.size) {
                                        selectedAyahIndex++
                                        analysisResult = null
                                        livePartialText = ""
                                    }
                                },
                                enabled = selectedAyahIndex + 1 < surahAyahs.size,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Next", fontSize = 12.sp)
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Target Ayah Display Card
            if (currentAyah != null) {
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth().testTag("practice_target_ayah_card"),
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Recite This Ayah:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = currentAyah.arabicText,
                                fontFamily = resolveQuranFontFamily(settings.quranFontFamily),
                                fontSize = settings.arabicFontSize.coerceIn(22f, 32f).sp,
                                lineHeight = (settings.arabicFontSize.coerceIn(22f, 32f) * 1.8f).sp,
                                textAlign = TextAlign.Right,
                                softWrap = true,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (currentAyah.transliteration.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = currentAyah.transliteration,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.primary,
                                    softWrap = true
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentAyah.englishTranslation,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = true
                            )
                        }
                    }
                }
            }

            // Microphone Capture Control Card
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("practice_mic_card"),
                    borderColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    contentPadding = PaddingValues(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (isListening) {
                                        stopListeningNow()
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            startArabicRecitationListening()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                                .testTag("practice_mic_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isListening) "Stop Listening" else "Start Recitation Practice",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isListening) "Listening... Tap to Finish" else "Tap Microphone to Recite",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusMessage,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true
                        )

                        if (livePartialText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Heard Recitation:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = livePartialText,
                                        fontFamily = AmiriArabicFontFamily,
                                        fontSize = 19.sp,
                                        lineHeight = 32.sp,
                                        textAlign = TextAlign.Right,
                                        softWrap = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Detailed Word-by-Word Feedback & Mistake Guidance
            analysisResult?.let { result ->
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth().testTag("practice_feedback_card"),
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Recitation Check",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (result.accuracyPercent >= 80) Color(0xFF1B5E20).copy(alpha = 0.16f)
                                    else Color(0xFFB71C1C).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "${result.accuracyPercent}% (${result.matchedWordsCount}/${result.totalExpectedWords} words)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (result.accuracyPercent >= 80) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Word-by-word color-coded pill breakdown
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                result.wordEvaluations.forEach { eval ->
                                    val (bg, fg) = when (eval.status) {
                                        WordEvaluationStatus.CORRECT -> Color(0xFF2E7D32).copy(alpha = 0.16f) to Color(0xFF1B5E20)
                                        WordEvaluationStatus.MISTAKEN -> Color(0xFFC62828).copy(alpha = 0.16f) to Color(0xFFB71C1C)
                                        WordEvaluationStatus.MISSED -> Color(0xFFEF6C00).copy(alpha = 0.16f) to Color(0xFFE65100)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = bg,
                                        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = eval.expectedWord,
                                            fontFamily = resolveQuranFontFamily(settings.quranFontFamily),
                                            fontSize = 18.sp,
                                            color = fg,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            if (result.mistakes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Mistakes & Pronunciation Guidance:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                result.mistakes.forEach { mistake ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (mistake.status == WordEvaluationStatus.MISSED) "Missed Word" else "Incorrect Pronunciation",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = mistake.expectedWord,
                                                    fontFamily = resolveQuranFontFamily(settings.quranFontFamily),
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            mistake.explanation?.let {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = it,
                                                    fontSize = 12.sp,
                                                    lineHeight = 18.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    softWrap = true
                                                )
                                            }
                                            mistake.guidance?.let {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Guidance: $it",
                                                    fontSize = 12.sp,
                                                    lineHeight = 18.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    softWrap = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    analysisResult = null
                                    livePartialText = ""
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) startArabicRecitationListening()
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Repeat & Practice Again", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showSurahPicker) {
        LiquidGlassDialog(
            onDismissRequest = { showSurahPicker = false },
            title = { Text("Select Surah to Practice", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(IslamicDataSource.SURAHS, key = { it.number }) { surah ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSurah = surah
                                    showSurahPicker = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${surah.number}. ${surah.nameEnglish}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${surah.numberOfAyahs} Ayahs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = surah.nameArabic,
                                fontFamily = AmiriArabicFontFamily,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSurahPicker = false }) { Text("Close") }
            }
        )
    }
}
