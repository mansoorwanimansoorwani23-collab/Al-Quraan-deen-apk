package com.example.ui.screens.quran

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.IslamicDataSource
import com.example.data.model.Ayah
import com.example.data.model.Surah
import com.example.ui.MainViewModel
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LiquidGlassIconButton
import com.example.ui.components.LiquidGlassSegmentedTabs
import com.example.ui.components.LiquidGlassTopBarSurface
import com.example.ui.components.rememberLiquidGlassPalette
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 100% Pure Offline Holy Quran Mushaf Reader — Zero Audio, Zero Video, Zero Distractions.
 * Features:
 * - Default Decorated Plain-Text Mushaf Mode: Ornate gilded manuscript frame, Surah calligraphy
 *   header arch, traditional Bismillah banner, selectable Arabic Quran calligraphy fonts, and
 *   inline golden Ayah end medallions (۝١، ۝٢...) for serene uninterrupted reading.
 * - Optional Verse-by-Verse Study Mode for bookmarking, copying, and reading translations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahReaderScreen(
    initialSurah: Surah? = null,
    initialJuzNumber: Int? = null,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val ayahs by viewModel.selectedSurahAyahs.collectAsState()
    val isLoading by viewModel.isLoadingOfflineQuran.collectAsState()
    val selectedSurahState by viewModel.selectedSurah.collectAsState()
    val settings by viewModel.userSettings.collectAsState()
    val bookmarks by viewModel.allBookmarks.collectAsState()

    val currentSurah = initialSurah ?: selectedSurahState ?: IslamicDataSource.SURAHS[0]

    // 0 = Decorated Plain-Text Mushaf Page (Default), 1 = Verse-by-Verse Study
    var readingViewMode by remember { mutableStateOf(0) }
    var showFontDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialSurah, initialJuzNumber) {
        if (initialJuzNumber != null) {
            viewModel.selectJuz(initialJuzNumber)
        } else if (initialSurah != null) {
            viewModel.selectSurah(initialSurah)
        }
    }

    // Automatically persist local offline reading progress as the user scrolls
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIndex, ayahs, readingViewMode) {
        if (ayahs.isNotEmpty()) {
            val ayahIndex = if (readingViewMode == 0) {
                (firstVisibleIndex * 12).coerceIn(0, ayahs.lastIndex)
            } else {
                (firstVisibleIndex - 1).coerceIn(0, ayahs.lastIndex)
            }
            val visibleAyah = ayahs.getOrNull(ayahIndex)
            if (visibleAyah != null) {
                viewModel.saveOfflineReadingProgress(visibleAyah.surahNumber, visibleAyah.numberInSurah)
            }
        }
    }

    // Chunk Ayahs into balanced decorated Mushaf pages (12 Ayahs per framed page for smooth LazyColumn performance)
    val mushafPages = remember(ayahs) {
        ayahs.chunked(12)
    }

    LiquidGlassBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LiquidGlassTopBarSurface {
                    TopAppBar(
                        navigationIcon = {
                            Box(modifier = Modifier.padding(start = 10.dp, end = 6.dp)) {
                                LiquidGlassIconButton(
                                    onClick = onBack,
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    size = 40.dp,
                                    modifier = Modifier.testTag("surah_reader_back_button")
                                )
                            }
                        },
                        title = {
                            Column {
                                if (initialJuzNumber != null) {
                                    Text(
                                        text = "Juz $initialJuzNumber",
                                        fontFamily = BrandDisplayFontFamily,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${ayahs.size} Ayahs • Pure Mushaf Reading",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "Surah ${currentSurah.nameEnglish}",
                                        fontFamily = BrandDisplayFontFamily,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${currentSurah.nameArabic} • ${currentSurah.numberOfAyahs} Ayahs • Juz ${currentSurah.juzNumber}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            Box(modifier = Modifier.padding(end = 10.dp)) {
                                LiquidGlassIconButton(
                                    onClick = { showFontDialog = true },
                                    icon = Icons.Filled.FormatSize,
                                    contentDescription = "Quran Font Settings",
                                    size = 40.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("reader_font_settings_button")
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Pure Reading Mode Toggle Pill (Mushaf Plain-Text vs Verse Study)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    LiquidGlassSegmentedTabs(
                        tabs = listOf("Mushaf Page (Plain Text)", "Verse Study"),
                        selectedIndex = readingViewMode,
                        onSelect = { readingViewMode = it }
                    )
                }

                if (isLoading && ayahs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LiquidGlassCard(
                            modifier = Modifier.widthIn(max = 260.dp),
                            contentPadding = PaddingValues(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Opening Holy Quran...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Decorated Illuminated Manuscript Surah / Juz Banner
                        item(key = "decorated_mushaf_header") {
                            IlluminatedSurahHeader(
                                surah = currentSurah,
                                juzNumber = initialJuzNumber,
                                quranFontFamily = settings.quranFontFamily
                            )
                        }

                        if (readingViewMode == 0) {
                            // ================= DECORATED PLAIN-TEXT MUSHAF PAGES =================
                            items(
                                count = mushafPages.size,
                                key = { idx -> "mushaf_page_$idx" }
                            ) { pageIdx ->
                                val pageAyahs = mushafPages[pageIdx]
                                DecoratedMushafPageCard(
                                    pageAyahs = pageAyahs,
                                    pageNumberLabel = "Page ${pageAyahs.firstOrNull()?.page ?: (pageIdx + 1)} • Juz ${pageAyahs.firstOrNull()?.juz ?: currentSurah.juzNumber}",
                                    arabicFontSize = settings.arabicFontSize,
                                    quranFontFamily = settings.quranFontFamily
                                )
                            }
                        } else {
                            // ================= VERSE-BY-VERSE STUDY MODE =================
                            items(ayahs, key = { "${it.surahNumber}_${it.numberInSurah}" }) { ayah ->
                                val isBookmarked = bookmarks.any { it.id == "quran_${ayah.surahNumber}_${ayah.numberInSurah}" }
                                val parentSurah = IslamicDataSource.SURAHS.find { it.number == ayah.surahNumber } ?: currentSurah

                                OfflineAyahCard(
                                    ayah = ayah,
                                    parentSurah = parentSurah,
                                    showSurahBadge = initialJuzNumber != null && ayah.numberInSurah == 1,
                                    arabicFontSize = settings.arabicFontSize,
                                    quranFontFamily = settings.quranFontFamily,
                                    showTranslation = settings.showTranslation,
                                    showTransliteration = settings.showTransliteration,
                                    isBookmarked = isBookmarked,
                                    onSaveProgress = {
                                        viewModel.saveOfflineReadingProgress(ayah.surahNumber, ayah.numberInSurah)
                                        Toast.makeText(
                                            context,
                                            "Reading progress saved: ${parentSurah.nameEnglish} Ayah ${ayah.numberInSurah}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onToggleBookmark = {
                                        viewModel.toggleBookmark(
                                            type = "QURAN",
                                            id = "quran_${ayah.surahNumber}_${ayah.numberInSurah}",
                                            title = "Surah ${parentSurah.nameEnglish} : ${ayah.numberInSurah}",
                                            subtitle = parentSurah.englishTranslation,
                                            arSnippet = ayah.arabicText.take(60),
                                            enSnippet = ayah.englishTranslation.take(60),
                                            destData = "${parentSurah.number}"
                                        )
                                    },
                                    onCopyText = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText(
                                            "Ayah",
                                            "${ayah.arabicText}\n\n\"${ayah.englishTranslation}\"\n(Quran ${ayah.surahNumber}:${ayah.numberInSurah})"
                                        )
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Ayah copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFontDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("Quran Calligraphy & Reading", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Arabic Quran Font",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    QuranFontOption.entries.forEach { fontOption ->
                        val isSelected = settings.quranFontFamily.equals(fontOption.id, ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        viewModel.preferencesRepository.updateQuranFontFamily(fontOption.id)
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                            border = if (isSelected) BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fontOption.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = fontOption.styleDescription,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "بِسْمِ اللَّهِ",
                                    fontFamily = fontOption.fontFamily,
                                    fontSize = 19.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Arabic Font Size: ${settings.arabicFontSize.toInt()}sp",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = settings.arabicFontSize,
                        onValueChange = { coroutineScope.launch { viewModel.preferencesRepository.updateArabicFontSize(it) } },
                        valueRange = 20f..42f,
                        steps = 10
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Translation (Verse View)", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = settings.showTranslation,
                            onCheckedChange = { coroutineScope.launch { viewModel.preferencesRepository.updateShowTranslation(it) } }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Transliteration (Verse View)", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = settings.showTransliteration,
                            onCheckedChange = { coroutineScope.launch { viewModel.preferencesRepository.updateShowTransliteration(it) } }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFontDialog = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Decorated Illuminated Islamic Manuscript Surah Header with gilded filigree border & Bismillah.
 */
@Composable
private fun IlluminatedSurahHeader(
    surah: Surah,
    juzNumber: Int?,
    quranFontFamily: String
) {
    val palette = rememberLiquidGlassPalette()
    val isDark = LocalAppThemeMode.current.isDark
    val gold = if (isDark) GoldAccentDark else GoldAccentLight
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("decorated_surah_header"),
        shape = RoundedCornerShape(26.dp),
        color = palette.surfaceFill,
        border = BorderStroke(
            width = 1.4.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    gold.copy(alpha = 0.85f),
                    Color.White.copy(alpha = if (isDark) 0.45f else 0.95f),
                    primary.copy(alpha = 0.65f),
                    gold.copy(alpha = 0.85f)
                )
            )
        ),
        shadowElevation = palette.shadowElevation
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Ornamental double Islamic manuscript border + corner rosettes
            Canvas(modifier = Modifier.matchParentSize()) {
                val inset = 9.dp.toPx()
                drawRoundRect(
                    color = gold.copy(alpha = if (isDark) 0.36f else 0.45f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(19.dp.toPx(), 19.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
                val dotRadius = 3.2.dp.toPx()
                val cornerInset = 18.dp.toPx()
                listOf(
                    Offset(cornerInset, cornerInset),
                    Offset(size.width - cornerInset, cornerInset),
                    Offset(cornerInset, size.height - cornerInset),
                    Offset(size.width - cornerInset, size.height - cornerInset)
                ).forEach { pt ->
                    drawCircle(color = gold.copy(alpha = 0.72f), radius = dotRadius, center = pt)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                gold.copy(alpha = if (isDark) 0.12f else 0.14f),
                                Color.Transparent,
                                primary.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (juzNumber != null) "الْجُزْءُ ${toArabicIndicDigits(juzNumber)}" else "سُورَةُ ${surah.nameArabic}",
                    fontFamily = resolveQuranFontFamily(quranFontFamily),
                    fontSize = 30.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = gold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (juzNumber != null) {
                        "Juz $juzNumber • Complete Offline Holy Quran"
                    } else {
                        "${surah.nameEnglish} • ${surah.englishTranslation} • ${surah.revelationType} (${surah.numberOfAyahs} Ayahs)"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Ornamental Bismillah Divider
                if (juzNumber != null || surah.number != 9) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(0.84f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = gold.copy(alpha = 0.45f))
                        Text(
                            text = " ✦ ",
                            color = gold,
                            fontSize = 12.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = gold.copy(alpha = 0.45f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                        fontFamily = resolveQuranFontFamily(quranFontFamily),
                        fontSize = 26.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Decorated Plain-Text Mushaf Page Card:
 * Renders pure flowing Arabic Quran calligraphy framed inside an ornate double-bordered
 * Liquid Glass manuscript page with golden inline Ayah end medallions (۝١، ۝٢...).
 */
@Composable
private fun DecoratedMushafPageCard(
    pageAyahs: List<Ayah>,
    pageNumberLabel: String,
    arabicFontSize: Float,
    quranFontFamily: String
) {
    val palette = rememberLiquidGlassPalette()
    val isDark = LocalAppThemeMode.current.isDark
    val gold = if (isDark) GoldAccentDark else GoldAccentLight
    val primary = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val resolvedFont = resolveQuranFontFamily(quranFontFamily)

    val mushafText = remember(pageAyahs, gold, textColor) {
        buildAnnotatedString {
            pageAyahs.forEachIndexed { idx, ayah ->
                withStyle(SpanStyle(color = textColor)) {
                    append(ayah.arabicText.trim())
                }
                withStyle(SpanStyle(color = gold, fontWeight = FontWeight.Bold)) {
                    append("  \u06DD${toArabicIndicDigits(ayah.numberInSurah)}  ")
                }
                if (idx < pageAyahs.lastIndex) {
                    append(" ")
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mushaf_plain_text_page"),
        shape = RoundedCornerShape(26.dp),
        color = palette.surfaceFill,
        border = BorderStroke(
            width = 1.25.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    gold.copy(alpha = 0.65f),
                    palette.rimTopLeft,
                    primary.copy(alpha = 0.35f),
                    gold.copy(alpha = 0.55f)
                )
            )
        ),
        shadowElevation = palette.shadowElevation
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle ornamental double inner Mushaf border
            Canvas(modifier = Modifier.matchParentSize()) {
                val outerInset = 8.dp.toPx()
                val innerInset = 12.dp.toPx()
                drawRoundRect(
                    color = gold.copy(alpha = if (isDark) 0.28f else 0.38f),
                    topLeft = Offset(outerInset, outerInset),
                    size = Size(size.width - outerInset * 2, size.height - outerInset * 2),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawRoundRect(
                    color = primary.copy(alpha = if (isDark) 0.16f else 0.20f),
                    topLeft = Offset(innerInset, innerInset),
                    size = Size(size.width - innerInset * 2, size.height - innerInset * 2),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.topSheen.copy(alpha = if (isDark) 0.08f else 0.50f),
                                Color.Transparent,
                                gold.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Text(
                    text = mushafText,
                    fontFamily = resolvedFont,
                    fontSize = arabicFontSize.coerceIn(22f, 40f).sp,
                    lineHeight = (arabicFontSize.coerceIn(22f, 40f) * 2.0f).sp,
                    textAlign = TextAlign.Justify,
                    style = LocalTextStyle.current.copy(
                        textDirection = TextDirection.Rtl
                    ),
                    softWrap = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = gold.copy(alpha = 0.30f))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = gold.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, gold.copy(alpha = 0.42f)),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = pageNumberLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = gold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.weight(1f), color = gold.copy(alpha = 0.30f))
                }
            }
        }
    }
}

private fun toArabicIndicDigits(number: Int): String {
    val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return number.toString().map { ch ->
        if (ch in '0'..'9') arabicDigits[ch - '0'] else ch
    }.joinToString("")
}

@Composable
private fun OfflineAyahCard(
    ayah: Ayah,
    parentSurah: Surah,
    showSurahBadge: Boolean,
    arabicFontSize: Float,
    quranFontFamily: String,
    showTranslation: Boolean,
    showTransliteration: Boolean,
    isBookmarked: Boolean,
    onSaveProgress: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCopyText: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ayah_card_${ayah.surahNumber}_${ayah.numberInSurah}"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showSurahBadge) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "Surah ${parentSurah.number}. ${parentSurah.nameEnglish} (${parentSurah.nameArabic})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = toArabicIndicDigits(ayah.numberInSurah),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ayah ${ayah.numberInSurah} • Juz ${ayah.juz} • Page ${ayah.page}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSaveProgress, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = "Mark Last Read",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onCopyText, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) GoldAccentDark else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = ayah.arabicText,
                fontFamily = resolveQuranFontFamily(quranFontFamily),
                fontSize = arabicFontSize.sp,
                lineHeight = (arabicFontSize * 1.9f).sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Right,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
                softWrap = true,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            if (showTransliteration && ayah.transliteration.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = ayah.transliteration,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                    softWrap = true
                )
            }

            if (showTranslation && ayah.englishTranslation.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = ayah.englishTranslation,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true
                )
            }
        }
    }
}
