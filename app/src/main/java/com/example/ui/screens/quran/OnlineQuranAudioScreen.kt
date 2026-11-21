package com.example.ui.screens.quran

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ReciterVoicePacks
import com.example.data.local.IslamicDataSource
import com.example.data.model.Ayah
import com.example.data.model.Surah
import com.example.ui.MainViewModel
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassIconButton
import com.example.ui.components.LiquidGlassSegmentedTabs
import com.example.ui.components.LiquidGlassTextField
import com.example.ui.components.LiquidGlassTopBarSurface
import com.example.ui.components.rememberLiquidGlassPalette
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Dedicated Online Quran Audio Screen — completely separate from Offline Quran reading.
 * Features:
 * - Exact Resume Banner saving Surah, Juz, Ayah, Audio Position (ms), and Reciter.
 * - Background playback & lock-screen media controls via QuranPlaybackService.
 * - Browse & listen by Surah (114) or Juz (30) with Reciter & Speed selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineQuranAudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.userSettings.collectAsState()
    val playbackState by viewModel.quranPlaybackState.collectAsState()

    var selectedBrowseTab by remember { mutableStateOf(0) } // 0: Surahs, 1: Juz, 2: Active Surah Ayahs
    var selectedSurahForAudio by remember {
        mutableStateOf(
            IslamicDataSource.SURAHS.find { it.number == settings.savedAudioSurahNumber }
                ?: IslamicDataSource.SURAHS[0]
        )
    }
    var activeSurahAyahs by remember { mutableStateOf<List<Ayah>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showReciterSheet by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSurahForAudio.number) {
        val loaded = viewModel.quranRepository.getAyahsForSurah(selectedSurahForAudio)
        activeSurahAyahs = loaded
    }

    val savedSurah = remember(settings.savedAudioSurahNumber) {
        IslamicDataSource.SURAHS.find { it.number == settings.savedAudioSurahNumber }
            ?: IslamicDataSource.SURAHS[0]
    }
    val savedReciter = remember(settings.savedAudioReciterId) {
        ReciterVoicePacks.RECITERS.find { it.id == settings.savedAudioReciterId }
            ?: ReciterVoicePacks.DEFAULT_RECITER
    }

    val filteredSurahs = remember(searchQuery) {
        if (searchQuery.isBlank()) IslamicDataSource.SURAHS
        else IslamicDataSource.SURAHS.filter {
            it.nameEnglish.contains(searchQuery, ignoreCase = true) ||
                it.englishTranslation.contains(searchQuery, ignoreCase = true) ||
                it.nameArabic.contains(searchQuery) ||
                it.number.toString() == searchQuery.trim()
        }
    }

    LiquidGlassBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                LiquidGlassTopBarSurface {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Online Quran Audio",
                                    fontFamily = BrandDisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Streaming Recitation • Background & Lock-Screen Controls",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                    modifier = Modifier.testTag("online_audio_back_button")
                                )
                            }
                        },
                        actions = {
                            Box(modifier = Modifier.padding(end = 10.dp)) {
                                LiquidGlassIconButton(
                                    onClick = { showReciterSheet = true },
                                    icon = Icons.Filled.RecordVoiceOver,
                                    contentDescription = "Select Reciter",
                                    size = 40.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("online_audio_reciter_button")
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                OnlineAudioPlayerBar(
                    playbackState = playbackState,
                    quranFontFamily = settings.quranFontFamily,
                    showSpeedMenu = showSpeedMenu,
                    onToggleSpeedMenu = { showSpeedMenu = it },
                    onSelectSpeed = { viewModel.setQuranPlaybackSpeed(it) },
                    onOpenReciterPicker = { showReciterSheet = true },
                    onPrevious = { viewModel.previousQuranAyah() },
                    onPlayPause = {
                        if (playbackState.currentAyah == null) {
                            val firstAyah = activeSurahAyahs.firstOrNull()
                            if (firstAyah != null) {
                                viewModel.quranAudioPlayerManager.setSurahAyahs(activeSurahAyahs)
                                viewModel.playQuranAyah(selectedSurahForAudio, firstAyah, 0)
                            }
                        } else {
                            viewModel.toggleQuranPlayPause()
                        }
                    },
                    onNext = { viewModel.nextQuranAyah() },
                    onStopAndSave = {
                        viewModel.stopQuranAudio()
                        Toast.makeText(context, "Exact audio position saved", Toast.LENGTH_SHORT).show()
                    },
                    onSeek = { viewModel.seekQuranAudio(it) },
                    onRetry = {
                        val s = playbackState.currentSurah ?: selectedSurahForAudio
                        val a = playbackState.currentAyah ?: activeSurahAyahs.firstOrNull()
                        if (a != null) viewModel.playQuranAyah(s, a, playbackState.currentPositionMs)
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(innerPadding)
            ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Exact Resume Card (visible whenever user has previously stopped/paused audio)
                if (settings.hasSavedAudioProgress) {
                    item {
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("resume_online_audio_card"),
                            borderColor = GoldAccentDark.copy(alpha = 0.55f),
                            contentPadding = PaddingValues(18.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Continue Exactly Where You Stopped",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Surah ${savedSurah.nameEnglish} (${savedSurah.nameArabic})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = true
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Juz ${settings.savedAudioJuzNumber} • Ayah ${settings.savedAudioAyahNumber} • Position ${formatAudioMs(settings.savedAudioPositionMs)}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = true
                                )
                                Text(
                                    text = "Reciter: ${savedReciter.name}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    softWrap = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        selectedSurahForAudio = savedSurah
                                        selectedBrowseTab = 2
                                        viewModel.resumeSavedOnlineQuranAudio()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("resume_saved_audio_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Resume from ${formatAudioMs(settings.savedAudioPositionMs)}", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Reciter Card
                item {
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReciterSheet = true }
                            .testTag("active_reciter_card"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Reciter: ${playbackState.selectedReciter.name}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        softWrap = true
                                    )
                                    Text(
                                        text = playbackState.selectedReciter.styleDescription,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                            }
                            Text(
                                text = "Change",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Mode Tabs: iOS 26 Liquid Glass Segmented Control
                item {
                    LiquidGlassSegmentedTabs(
                        tabs = listOf("Surahs (114)", "Juz (30)", "Ayahs (${selectedSurahForAudio.number})"),
                        selectedIndex = selectedBrowseTab,
                        onSelect = { selectedBrowseTab = it }
                    )
                }

                when (selectedBrowseTab) {
                    0 -> {
                        item {
                            LiquidGlassTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "Search Surah to listen...",
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        items(filteredSurahs, key = { it.number }) { surah ->
                            val isPlayingThisSurah = playbackState.currentSurah?.number == surah.number && playbackState.isPlaying
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSurahForAudio = surah
                                        selectedBrowseTab = 2
                                        scope.launch {
                                            val ayahs = viewModel.quranRepository.getAyahsForSurah(surah)
                                            activeSurahAyahs = ayahs
                                            viewModel.quranAudioPlayerManager.setSurahAyahs(ayahs)
                                            ayahs.firstOrNull()?.let { viewModel.playQuranAyah(surah, it, 0) }
                                        }
                                    },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isPlayingThisSurah) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingThisSurah) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
                                            contentDescription = "Play Surah",
                                            tint = if (isPlayingThisSurah) Color.White else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${surah.number}. ${surah.nameEnglish}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${surah.numberOfAyahs} Ayahs • Juz ${surah.juzNumber}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = surah.nameArabic,
                                        fontFamily = resolveQuranFontFamily(settings.quranFontFamily),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        items(IslamicDataSource.JUZ_DEFINITIONS, key = { it.juzNumber }) { juzInfo ->
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val juzAyahs = viewModel.quranRepository.getAyahsForJuz(juzInfo.juzNumber)
                                            if (juzAyahs.isNotEmpty()) {
                                                val firstAyah = juzAyahs.first()
                                                val startSurah = IslamicDataSource.SURAHS.find { it.number == firstAyah.surahNumber }
                                                    ?: IslamicDataSource.SURAHS[0]
                                                selectedSurahForAudio = startSurah
                                                activeSurahAyahs = juzAyahs
                                                selectedBrowseTab = 2
                                                viewModel.quranAudioPlayerManager.setSurahAyahs(juzAyahs)
                                                viewModel.playQuranAyah(startSurah, firstAyah, 0)
                                            }
                                        }
                                    },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${juzInfo.juzNumber}",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Juz ${juzInfo.juzNumber} • ${juzInfo.nameArabic}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${juzInfo.nameEnglish} • Starts Surah ${juzInfo.startSurahNumber}:${juzInfo.startAyahNumber}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            softWrap = true
                                        )
                                    }
                                    Icon(Icons.Filled.PlayCircle, contentDescription = "Play Juz", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    2 -> {
                        items(activeSurahAyahs, key = { "${it.surahNumber}_${it.numberInSurah}" }) { ayah ->
                            val isCurrent = playbackState.currentAyah?.surahNumber == ayah.surahNumber &&
                                playbackState.currentAyah?.numberInSurah == ayah.numberInSurah
                            val parentSurah = IslamicDataSource.SURAHS.find { it.number == ayah.surahNumber } ?: selectedSurahForAudio

                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.quranAudioPlayerManager.setSurahAyahs(activeSurahAyahs)
                                        viewModel.playQuranAyah(parentSurah, ayah, 0)
                                    },
                                borderColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Surah ${parentSurah.nameEnglish} • Ayah ${ayah.numberInSurah} (Juz ${ayah.juz})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (isCurrent && playbackState.isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                            contentDescription = "Play Ayah",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = ayah.arabicText,
                                        fontFamily = resolveQuranFontFamily(settings.quranFontFamily),
                                        fontSize = settings.arabicFontSize.coerceAtMost(28f).sp,
                                        lineHeight = (settings.arabicFontSize.coerceAtMost(28f) * 1.75f).sp,
                                        textAlign = TextAlign.Right,
                                        softWrap = true,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (settings.showTranslation && ayah.englishTranslation.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = ayah.englishTranslation,
                                            fontSize = 13.sp,
                                            lineHeight = 20.sp,
                                            softWrap = true,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (showReciterSheet) {
        ModalBottomSheet(onDismissRequest = { showReciterSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Select Quran Reciter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Authentic high-clarity Murattal recitations streamed online.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                ReciterVoicePacks.RECITERS.forEach { reciter ->
                    val isSelected = playbackState.selectedReciter.id == reciter.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.selectReciterVoice(reciter)
                                showReciterSheet = false
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(reciter.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(reciter.arabicName, fontFamily = AmiriArabicFontFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                Text(reciter.styleDescription, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineAudioPlayerBar(
    playbackState: com.example.audio.QuranPlaybackState,
    quranFontFamily: String,
    showSpeedMenu: Boolean,
    onToggleSpeedMenu: (Boolean) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onOpenReciterPicker: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStopAndSave: () -> Unit,
    onSeek: (Float) -> Unit,
    onRetry: () -> Unit
) {
    val palette = rememberLiquidGlassPalette()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        contentAlignment = Alignment.Center
    ) {
        val isCompactWidth = maxWidth < 360.dp
        val outerHPad = if (isCompactWidth) 10.dp else 12.dp

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = outerHPad, vertical = 8.dp)
                .testTag("online_quran_player_bar"),
            shape = RoundedCornerShape(26.dp),
            color = palette.dockFill,
            border = BorderStroke(
                width = 1.2.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(palette.rimTopLeft, palette.rimMid, palette.rimBottomRight)
                )
            ),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompactWidth) 12.dp else 16.dp, vertical = 10.dp)
            ) {
                AnimatedVisibility(visible = playbackState.errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = playbackState.errorMessage ?: "Connection error",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                                softWrap = true
                            )
                            TextButton(onClick = onRetry) {
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Row 1: Now Playing Surah/Ayah + Compact Reciter & Speed Pills (never collides with transport buttons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        val liveAyah = playbackState.currentAyah
                        if (liveAyah != null) {
                            val surahName = playbackState.currentSurah?.nameEnglish ?: "Quran"
                            Text(
                                text = "Surah $surahName • Ayah ${liveAyah.numberInSurah}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = liveAyah.arabicText,
                                fontFamily = resolveQuranFontFamily(quranFontFamily),
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Right,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "Online Quran Player",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = playbackState.selectedReciter.name,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .defaultMinSize(minHeight = 34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenReciterPicker),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = playbackState.selectedReciter.name.substringBefore(" "),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box {
                            Surface(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .defaultMinSize(minWidth = 44.dp, minHeight = 34.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onToggleSpeedMenu(true) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${playbackState.playbackSpeed}x",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { onToggleSpeedMenu(false) }) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            onSelectSpeed(speed)
                                            onToggleSpeedMenu(false)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Row 2: Seekbar + Live Timestamps
                if (playbackState.isPlaying || playbackState.isPaused || playbackState.durationMs > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatAudioMs(playbackState.currentPositionMs),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Slider(
                            value = playbackState.playbackProgress,
                            onValueChange = onSeek,
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .padding(horizontal = 6.dp)
                        )
                        Text(
                            text = formatAudioMs(playbackState.durationMs),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Row 3: Centered Transport Controls (guaranteed fit on 320dp–tablets without clipping)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = playbackState.currentAyah != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "Previous Ayah",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("online_audio_play_pause")
                    ) {
                        if (playbackState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNext,
                        enabled = playbackState.currentAyah != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Next Ayah",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = onStopAndSave,
                        enabled = playbackState.isPlaying || playbackState.isPaused,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("online_audio_stop_save")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop & Save Position",
                            tint = if (playbackState.isPlaying || playbackState.isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatAudioMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
}
