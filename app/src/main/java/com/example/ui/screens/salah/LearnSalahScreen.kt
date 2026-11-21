package com.example.ui.screens.salah

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
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LearnSalahData
import com.example.data.local.SalahStep
import com.example.ui.MainViewModel
import com.example.ui.components.IslamicBannerCard
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassIconButton
import com.example.ui.components.LiquidGlassSegmentedTabs
import com.example.ui.components.LiquidGlassTopBarSurface
import com.example.ui.theme.*

/**
 * Dedicated Learn Salah section for beginners with step-by-step Salah guidance,
 * Arabic recitations, transliteration, meanings, pronunciation tips, Wudu checklist,
 * and 5-Prayer Rakah reference chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnSalahScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.userSettings.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Step-by-Step, 1: Wudu, 2: Rakah Guide
    var expandedStepNumber by remember { mutableStateOf<Int?>(1) }
    val completedWuduSteps = remember { mutableStateMapOf<Int, Boolean>() }

    LiquidGlassBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LiquidGlassTopBarSurface {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Learn Salah",
                                    fontFamily = BrandDisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Beginner Step-by-Step Prayer Guide",
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
                                    modifier = Modifier.testTag("learn_salah_back_button")
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
                    .background(Color.Transparent)
                    .padding(innerPadding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    LiquidGlassSegmentedTabs(
                        tabs = listOf("Salah Steps (12)", "Wudu Guide", "5 Prayers"),
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 14.dp, bottom = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            IslamicBannerCard(
                                title = "How to Perform Salah",
                                subtitle = "Follow each posture calmly with authentic Arabic recitations, transliteration, English meanings, and pronunciation tips.",
                                arabicGreeting = "وَأَقِيمُوا الصَّلَاةَ",
                                icon = Icons.Filled.School
                            )
                        }

                        items(LearnSalahData.SALAH_STEPS, key = { it.stepNumber }) { step ->
                            val isExpanded = expandedStepNumber == step.stepNumber
                            SalahStepCard(
                                step = step,
                                quranFontFamily = settings.quranFontFamily,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedStepNumber = if (isExpanded) null else step.stepNumber
                                },
                                onNextStep = {
                                    if (step.stepNumber < LearnSalahData.SALAH_STEPS.size) {
                                        expandedStepNumber = step.stepNumber + 1
                                    }
                                }
                            )
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 14.dp, bottom = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            LiquidGlassCard(contentPadding = PaddingValues(18.dp)) {
                                Column {
                                    Text(
                                        text = "Wudu (Ablution) Checklist",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Purification before Salah. Tap each step as you learn and practice.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                            }
                        }

                        items(LearnSalahData.WUDU_STEPS, key = { it.number }) { wudu ->
                            val isDone = completedWuduSteps[wudu.number] == true
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { completedWuduSteps[wudu.number] = !isDone },
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(top = 2.dp).size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Step ${wudu.number}: ${wudu.title}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            softWrap = true
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = wudu.detail,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            softWrap = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 14.dp, bottom = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(LearnSalahData.FIVE_PRAYERS_RAKAH_TABLE, key = { it.prayerName }) { info ->
                            LiquidGlassCard(contentPadding = PaddingValues(18.dp)) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = info.prayerName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = info.arabicName,
                                            fontFamily = AmiriArabicFontFamily,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    RakahDetailRow("Sunnah Before", info.sunnahBefore)
                                    RakahDetailRow("Fard (Obligatory)", info.fard, highlight = true)
                                    RakahDetailRow("Sunnah After", info.sunnahAfter)
                                    RakahDetailRow("Witr / Extra", info.witrOrExtra)
                                    RakahDetailRow("Recitation Voice", info.recitationVoice)
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
private fun RakahDetailRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SalahStepCard(
    step: SalahStep,
    quranFontFamily: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNextStep: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("salah_step_card_${step.stepNumber}"),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${step.stepNumber}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true
                    )
                    Text(
                        text = step.arabicTitle,
                        fontFamily = AmiriArabicFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = step.postureSummary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary,
                softWrap = true
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = step.instructions,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Arabic Recitation Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Arabic Recitation:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = step.arabicRecitation,
                            fontFamily = resolveQuranFontFamily(quranFontFamily),
                            fontSize = 22.sp,
                            lineHeight = 40.sp,
                            textAlign = TextAlign.Right,
                            softWrap = true,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Transliteration:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = step.transliteration,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Meaning:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = step.englishMeaning,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Pronunciation Guidance
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp).size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pronunciation Guidance",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = step.pronunciationTip,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = true
                            )
                        }
                    }
                }

                if (step.stepNumber < LearnSalahData.SALAH_STEPS.size) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = onNextStep,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Next Step", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
