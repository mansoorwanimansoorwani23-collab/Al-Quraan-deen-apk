package com.example.ui.screens.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.IslamicDataSource
import com.example.data.model.Surah
import com.example.ui.MainViewModel
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassSegmentedTabs
import com.example.ui.components.LiquidGlassTextField
import com.example.ui.components.LocalBottomDockPadding
import com.example.ui.theme.*

/**
 * Pure Original Holy Quran Reading Screen — 100% reading-only offline Mushaf
 * (all 114 Surahs, 30 Juz, 6,236 authentic Uthmani Ayahs).
 * Zero audio, video, or distractions inside the Quran tab.
 */
@Composable
fun QuranScreen(
    viewModel: MainViewModel,
    onOpenSurahReader: (Surah?, Int?) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Surah, 1: Juz, 2: Bookmarks
    val searchQuery by viewModel.quranSearchQuery.collectAsState()
    val lastRead by viewModel.lastRead.collectAsState()
    val bookmarks by viewModel.allBookmarks.collectAsState()
    val settings by viewModel.userSettings.collectAsState()

    val filteredSurahs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            IslamicDataSource.SURAHS
        } else {
            IslamicDataSource.SURAHS.filter {
                it.nameEnglish.contains(searchQuery, ignoreCase = true) ||
                    it.englishTranslation.contains(searchQuery, ignoreCase = true) ||
                    it.nameArabic.contains(searchQuery) ||
                    it.number.toString() == searchQuery.trim()
            }
        }
    }

    val bookmarkCount = remember(bookmarks) { bookmarks.count { it.type == "QURAN" } }
    val bottomDockPadding = LocalBottomDockPadding.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .imePadding()
    ) {
        // Pure Liquid Glass Quran Header + Search + iOS 26 Segmented Pill Tabs (Zero audio/video)
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Al-Qur'an Al-Kareem",
                            fontFamily = BrandDisplayFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "114 Surahs • 30 Juz • Pure Offline Mushaf",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, GoldAccentDark.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "القرآن الكريم",
                            fontFamily = AmiriArabicFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LiquidGlassTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateQuranSearchQuery(it) },
                    placeholder = "Search Surah by name or number...",
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateQuranSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quran_search_bar")
                )

                Spacer(modifier = Modifier.height(10.dp))

                LiquidGlassSegmentedTabs(
                    tabs = listOf("Surah (114)", "Juz (30)", "Bookmarks ($bookmarkCount)"),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }
        }

        // Last Reading Progress Banner
        if (lastRead != null && selectedTab == 0) {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable {
                        val surah = IslamicDataSource.SURAHS.find { it.number == lastRead!!.surahNumber } ?: IslamicDataSource.SURAHS[0]
                        onOpenSurahReader(surah, null)
                    }
                    .testTag("quran_continue_reading_card"),
                borderColor = GoldAccentDark.copy(alpha = 0.45f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.AutoStories,
                            contentDescription = null,
                            tint = GoldAccentDark
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Continue Reading • Ayah ${lastRead!!.ayahNumber}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Surah ${lastRead!!.surahNameEnglish} (${lastRead!!.surahNameArabic})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = true
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Continue Reading",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomDockPadding + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSurahs, key = { it.number }) { surah ->
                        SurahListItem(
                            surah = surah,
                            quranFontFamily = settings.quranFontFamily,
                            onClick = { onOpenSurahReader(surah, null) }
                        )
                    }
                }
            }

            1 -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomDockPadding + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(IslamicDataSource.JUZ_DEFINITIONS, key = { it.juzNumber }) { juzInfo ->
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSurahReader(null, juzInfo.juzNumber) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OrnamentalNumberStar(number = juzInfo.juzNumber)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Juz ${juzInfo.juzNumber} • ${juzInfo.nameArabic}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        softWrap = true
                                    )
                                    Text(
                                        text = "${juzInfo.nameEnglish} • Starts Surah ${juzInfo.startSurahNumber}:${juzInfo.startAyahNumber}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                val quranBookmarks = bookmarks.filter { it.type == "QURAN" }
                if (quranBookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LiquidGlassCard(contentPadding = PaddingValues(24.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Quran bookmarks yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Switch to Verse View while reading and tap the bookmark icon on any Ayah to save it locally here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomDockPadding + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(quranBookmarks, key = { it.id }) { b ->
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val surahNum = b.destinationData.toIntOrNull() ?: 1
                                        val surah = IslamicDataSource.SURAHS.find { it.number == surahNum } ?: IslamicDataSource.SURAHS[0]
                                        onOpenSurahReader(surah, null)
                                    },
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(b.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = b.arabicSnippet,
                                            fontFamily = resolveQuranFontFamily(settings.quranFontFamily),
                                            fontSize = 18.sp,
                                            lineHeight = 30.sp,
                                            textAlign = TextAlign.Right,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.toggleBookmark(
                                                "QURAN",
                                                b.id,
                                                b.title,
                                                b.subtitle,
                                                b.arabicSnippet,
                                                b.englishSnippet,
                                                b.destinationData
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Filled.BookmarkRemove, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
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

/**
 * Traditional 8-pointed Islamic Rub el Hizb star emblem for Surah/Juz numbers.
 */
@Composable
fun OrnamentalNumberStar(
    number: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val gold = GoldAccentDark

    Box(
        modifier = modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val side = size.minDimension * 0.66f
            val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            val squareSize = Size(side, side)
            drawRect(
                color = primary.copy(alpha = 0.14f),
                topLeft = topLeft,
                size = squareSize
            )
            drawRect(
                color = gold.copy(alpha = 0.72f),
                topLeft = topLeft,
                size = squareSize,
                style = Stroke(width = 1.2.dp.toPx())
            )
            rotate(degrees = 45f) {
                drawRect(
                    color = primary.copy(alpha = 0.14f),
                    topLeft = topLeft,
                    size = squareSize
                )
                drawRect(
                    color = primary.copy(alpha = 0.65f),
                    topLeft = topLeft,
                    size = squareSize,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
        Text(
            text = "$number",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SurahListItem(
    surah: Surah,
    quranFontFamily: String = "Uthmani",
    onClick: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("surah_item_${surah.number}"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrnamentalNumberStar(number = surah.number)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.nameEnglish,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )
                Text(
                    text = "${surah.englishTranslation} • ${surah.numberOfAyahs} verses",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (surah.revelationType == "Meccan") GoldBadgeBg.copy(alpha = 0.85f) else GreenBadgeBg.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = surah.revelationType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (surah.revelationType == "Meccan") GoldBadge else GreenBadge,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = surah.nameArabic,
                fontFamily = resolveQuranFontFamily(quranFontFamily),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Right
            )
        }
    }
}
