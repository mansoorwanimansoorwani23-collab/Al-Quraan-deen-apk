package com.example.ui.screens.prayer

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.AdhanAudioPlayer
import com.example.data.local.IslamicDataSource
import com.example.domain.calculator.CalculationMethod
import com.example.domain.calculator.Madhhab
import com.example.domain.calculator.PrayerType
import com.example.ui.MainViewModel
import com.example.ui.components.IslamicBannerCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassDialog
import com.example.ui.components.LocalBottomDockPadding
import com.example.ui.components.SectionHeader
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(
    viewModel: MainViewModel,
    onOpenLearnSalah: () -> Unit = {}
) {
    val context = LocalContext.current
    val homeState by viewModel.homeUiState.collectAsState()
    val settings by viewModel.userSettings.collectAsState()
    val isPlayingAdhan by AdhanAudioPlayer.isPlayingFlow.collectAsState()
    val playingAdhanName by AdhanAudioPlayer.currentAdhanNameFlow.collectAsState()

    val timeZone = remember(settings.timeZoneId) { TimeZone.getTimeZone(settings.timeZoneId) }
    val timeFormat = remember(settings.is24HourFormat, settings.timeZoneId) {
        SimpleDateFormat(if (settings.is24HourFormat) "HH:mm" else "hh:mm a", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
    }
    val hourMin24Format = remember(settings.timeZoneId) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
    }

    var showCityPicker by remember { mutableStateOf(false) }
    var showMethodPicker by remember { mutableStateOf(false) }
    var showCustomTimesDialog by remember { mutableStateOf(false) }
    var selectedFilterPrayer by remember { mutableStateOf("ALL") }
    var expandedPrayerName by remember { mutableStateOf<String?>(null) }
    var isDetectingGps by remember { mutableStateOf(false) }

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
                "Location permission denied. You can select your city manually.",
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

    val result = homeState.prayerTimes
    val bottomDockPadding = LocalBottomDockPadding.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = bottomDockPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Location & Calculation Info Banner (Liquid Glass with safe padding)
        item {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showCityPicker = true }
                                .padding(vertical = 4.dp)
                                .testTag("select_prayer_city_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = "Selected location",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = listOf(settings.cityName, settings.countryName)
                                    .filter { it.isNotBlank() }
                                    .joinToString(", "),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Select City",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 12H / 24H Quick Toggle Chip
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.toggle24HourFormat() }
                                    .testTag("toggle_12_24_hour_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (settings.is24HourFormat) "24H" else "12H",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showCustomTimesDialog = true },
                                modifier = Modifier.testTag("custom_prayer_times_button")
                            ) {
                                Icon(
                                    imageVector = if (settings.useCustomPrayerTimes) Icons.Filled.EditCalendar else Icons.Outlined.EditCalendar,
                                    contentDescription = "Custom Prayer Times",
                                    tint = if (settings.useCustomPrayerTimes) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { showMethodPicker = true },
                                modifier = Modifier.testTag("calculation_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Calculation Settings",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Coordinates, Timezone & GPS Detect Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.4f°, %.4f° • %s",
                                    settings.latitude,
                                    settings.longitude,
                                    settings.timeZoneId
                                ),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = true
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (settings.useCustomPrayerTimes) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "Custom Mosque Timetable Active",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "${settings.calculationMethod.displayName} • ${settings.madhhab.name} Asr",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    softWrap = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledTonalButton(
                            onClick = { if (!isDetectingGps) requestGpsOrDetect() },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("prayer_detect_gps_button")
                        ) {
                            if (isDetectingGps) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.MyLocation,
                                    contentDescription = "Detect GPS Location",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isDetectingGps) "Locating..." else "Use GPS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Live Next Prayer Hero
        if (result != null) {
            item {
                val totalSec = (result.timeRemainingMillis / 1000).coerceAtLeast(0)
                val hours = totalSec / 3600
                val minutes = (totalSec % 3600) / 60
                val seconds = totalSec % 60
                val formattedCountdown = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

                IslamicBannerCard(
                    title = "Next: ${result.currentOrNextPrayer.displayName} (${result.currentOrNextPrayer.arabicName})",
                    subtitle = "Time remaining: $formattedCountdown • Adhan at ${timeFormat.format(result.nextPrayerTime)}",
                    arabicGreeting = "حَيَّ عَلَى الصَّلَاةِ • حَيَّ عَلَى الْفَلَاحِ",
                    icon = Icons.Filled.AccessTime
                )
            }

            // Learn Salah Dedicated Launcher Card
            item {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLearnSalah() }
                        .testTag("prayer_open_learn_salah_card"),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MenuBook,
                                    contentDescription = "Learn Salah",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Learn Salah — Step-by-Step Guide",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = true
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Beginner guide with Wudu, Arabic recitations, transliteration, meanings & Rakah chart",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = true
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Open Learn Salah",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quick Selection & Filter Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filterOptions = listOf(
                        "ALL" to "All Schedule",
                        "FAJR" to "Fajr (الفجر)",
                        "DHUHR" to "Dhuhr (الظهر)",
                        "ASR" to "Asr (العصر)",
                        "MAGHRIB" to "Maghrib (المغرب)",
                        "ISHA" to "Isha (العشاء)",
                        "QIYAM" to "Tahajjud (القيام)"
                    )
                    items(filterOptions) { (key, label) ->
                        val isSelected = selectedFilterPrayer == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilterPrayer = key },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("prayer_filter_$key")
                        )
                    }
                }
            }

            // Daily 5 Obligatory Prayers + Sunrise + Tahajjud list
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SectionHeader(
                            title = "Today's Prayer Schedule",
                            subtitle = "${homeState.hijriDate?.formatShort() ?: ""} • Adhan: ${settings.adhanSoundName}"
                        )
                    }
                    TextButton(
                        onClick = { showCustomTimesDialog = true },
                        modifier = Modifier.testTag("edit_adhan_link")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit / Adhan", fontSize = 12.sp)
                    }
                }
            }

            val prayerList = listOf(
                PrayerRowData(
                    key = "FAJR",
                    name = "Fajr",
                    arabic = "الفجر",
                    rakahs = "2 Sunnah + 2 Fard",
                    time = result.fajr,
                    customTimeStr = settings.customFajrTime,
                    isNext = result.currentOrNextPrayer == PrayerType.FAJR,
                    icon = Icons.Filled.WbTwilight,
                    notifEnabled = settings.fajrNotification,
                    onToggleNotif = { viewModel.updatePrayerNotification("fajr", !settings.fajrNotification) }
                ),
                PrayerRowData(
                    key = "SUNRISE",
                    name = "Sunrise (Shuruq)",
                    arabic = "الشروق",
                    rakahs = "Duha time begins",
                    time = result.sunrise,
                    customTimeStr = "",
                    isNext = false,
                    icon = Icons.Filled.WbSunny,
                    notifEnabled = false,
                    onToggleNotif = {}
                ),
                PrayerRowData(
                    key = "DHUHR",
                    name = "Dhuhr",
                    arabic = "الظهر",
                    rakahs = "4 Sunnah + 4 Fard + 2 Sunnah",
                    time = result.dhuhr,
                    customTimeStr = settings.customDhuhrTime,
                    isNext = result.currentOrNextPrayer == PrayerType.DHUHR,
                    icon = Icons.Filled.LightMode,
                    notifEnabled = settings.dhuhrNotification,
                    onToggleNotif = { viewModel.updatePrayerNotification("dhuhr", !settings.dhuhrNotification) }
                ),
                PrayerRowData(
                    key = "ASR",
                    name = "Asr",
                    arabic = "العصر",
                    rakahs = "4 Fard",
                    time = result.asr,
                    customTimeStr = settings.customAsrTime,
                    isNext = result.currentOrNextPrayer == PrayerType.ASR,
                    icon = Icons.Filled.FilterDrama,
                    notifEnabled = settings.asrNotification,
                    onToggleNotif = { viewModel.updatePrayerNotification("asr", !settings.asrNotification) }
                ),
                PrayerRowData(
                    key = "MAGHRIB",
                    name = "Maghrib (Iftar)",
                    arabic = "المغرب",
                    rakahs = "3 Fard + 2 Sunnah",
                    time = result.maghrib,
                    customTimeStr = settings.customMaghribTime,
                    isNext = result.currentOrNextPrayer == PrayerType.MAGHRIB,
                    icon = Icons.Filled.Bedtime,
                    notifEnabled = settings.maghribNotification,
                    onToggleNotif = { viewModel.updatePrayerNotification("maghrib", !settings.maghribNotification) }
                ),
                PrayerRowData(
                    key = "ISHA",
                    name = "Isha",
                    arabic = "العشاء",
                    rakahs = "4 Fard + 2 Sunnah + 3 Witr",
                    time = result.isha,
                    customTimeStr = settings.customIshaTime,
                    isNext = result.currentOrNextPrayer == PrayerType.ISHA,
                    icon = Icons.Filled.NightsStay,
                    notifEnabled = settings.ishaNotification,
                    onToggleNotif = { viewModel.updatePrayerNotification("isha", !settings.ishaNotification) }
                )
            )

            val filteredList = when (selectedFilterPrayer) {
                "ALL" -> prayerList
                "QIYAM" -> emptyList()
                else -> prayerList.filter { it.key == selectedFilterPrayer }
            }

            items(filteredList, key = { it.key }) { prayer ->
                val isExpanded = expandedPrayerName == prayer.key
                PrayerTimeCard(
                    prayer = prayer,
                    timeFormat = timeFormat,
                    isExpanded = isExpanded,
                    onCardClick = {
                        expandedPrayerName = if (isExpanded) null else prayer.key
                    },
                    onEditTime = {
                        showCustomTimesDialog = true
                    }
                )
            }

            // Midnight & Tahajjud Special Times
            if (selectedFilterPrayer == "ALL" || selectedFilterPrayer == "QIYAM") {
                item {
                    SectionHeader(title = "Night Worship & Tahajjud (Qiyam)")
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_night_worship"),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Islamic Midnight",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        softWrap = true
                                    )
                                    Text(
                                        text = "Halfway between Maghrib & Fajr",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                                Text(
                                    text = timeFormat.format(result.midnight),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Last Third of Night (Tahajjud)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        softWrap = true
                                    )
                                    Text(
                                        text = "Prime time for Dua & Istighfar",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                                Text(
                                    text = timeFormat.format(result.lastThirdOfNight),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Prayer & Real Adhan Times Dialog
    if (showCustomTimesDialog && result != null) {
        var editUseCustom by remember { mutableStateOf(settings.useCustomPrayerTimes) }
        var editFajr by remember { mutableStateOf(settings.customFajrTime.ifBlank { hourMin24Format.format(result.fajr) }) }
        var editDhuhr by remember { mutableStateOf(settings.customDhuhrTime.ifBlank { hourMin24Format.format(result.dhuhr) }) }
        var editAsr by remember { mutableStateOf(settings.customAsrTime.ifBlank { hourMin24Format.format(result.asr) }) }
        var editMaghrib by remember { mutableStateOf(settings.customMaghribTime.ifBlank { hourMin24Format.format(result.maghrib) }) }
        var editIsha by remember { mutableStateOf(settings.customIshaTime.ifBlank { hourMin24Format.format(result.isha) }) }
        var selectedAdhanSound by remember { mutableStateOf(settings.adhanSoundName) }

        LiquidGlassDialog(
            onDismissRequest = {
                AdhanAudioPlayer.stopAdhan()
                showCustomTimesDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EditCalendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Prayer Times & Real Adhan", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Enable Custom Times", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Override astronomical calculations with your local mosque timetable.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                        softWrap = true
                                    )
                                }
                                Switch(
                                    checked = editUseCustom,
                                    onCheckedChange = { editUseCustom = it },
                                    modifier = Modifier.testTag("toggle_custom_prayer_times_switch")
                                )
                            }
                        }
                    }

                    item {
                        Text("Prayer Times (24h format HH:mm):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item { CustomTimeInputRow(label = "Fajr", timeValue = editFajr, enabled = editUseCustom, onTimeChange = { editFajr = it }) }
                    item { CustomTimeInputRow(label = "Dhuhr", timeValue = editDhuhr, enabled = editUseCustom, onTimeChange = { editDhuhr = it }) }
                    item { CustomTimeInputRow(label = "Asr", timeValue = editAsr, enabled = editUseCustom, onTimeChange = { editAsr = it }) }
                    item { CustomTimeInputRow(label = "Maghrib", timeValue = editMaghrib, enabled = editUseCustom, onTimeChange = { editMaghrib = it }) }
                    item { CustomTimeInputRow(label = "Isha", timeValue = editIsha, enabled = editUseCustom, onTimeChange = { editIsha = it }) }

                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Authentic Real Adhan Voice:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    items(AdhanAudioPlayer.ADHAN_VOICES) { voice ->
                        val isSelected = selectedAdhanSound.equals(voice.name, ignoreCase = true)
                        val isThisPlaying = isPlayingAdhan && playingAdhanName == voice.name
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { selectedAdhanSound = voice.name }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = voice.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        softWrap = true
                                    )
                                    Text(
                                        text = voice.subtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = true
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (isThisPlaying) {
                                                AdhanAudioPlayer.stopAdhan()
                                            } else {
                                                AdhanAudioPlayer.playAdhanSound(context, voice.name)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isThisPlaying) Icons.Filled.StopCircle else Icons.Filled.PlayCircle,
                                            contentDescription = if (isThisPlaying) "Stop Adhan preview" else "Preview ${voice.name}",
                                            tint = if (isThisPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val defFajr = hourMin24Format.format(result.fajr)
                                val defDhuhr = hourMin24Format.format(result.dhuhr)
                                val defAsr = hourMin24Format.format(result.asr)
                                val defMaghrib = hourMin24Format.format(result.maghrib)
                                val defIsha = hourMin24Format.format(result.isha)
                                viewModel.resetCustomPrayerTimes(defFajr, defDhuhr, defAsr, defMaghrib, defIsha)
                                editUseCustom = false
                                editFajr = defFajr
                                editDhuhr = defDhuhr
                                editAsr = defAsr
                                editMaghrib = defMaghrib
                                editIsha = defIsha
                                Toast.makeText(context, "Reset to local astronomical calculation", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("reset_custom_times_button")
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset to Astronomical Calculator")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AdhanAudioPlayer.stopAdhan()
                        viewModel.setUseCustomPrayerTimes(editUseCustom)
                        viewModel.updateCustomPrayerTime("fajr", editFajr)
                        viewModel.updateCustomPrayerTime("dhuhr", editDhuhr)
                        viewModel.updateCustomPrayerTime("asr", editAsr)
                        viewModel.updateCustomPrayerTime("maghrib", editMaghrib)
                        viewModel.updateCustomPrayerTime("isha", editIsha)
                        viewModel.updateAdhanSound(selectedAdhanSound)
                        showCustomTimesDialog = false
                        Toast.makeText(context, "Prayer times & Adhan updated", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("save_custom_prayer_times_button")
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        AdhanAudioPlayer.stopAdhan()
                        showCustomTimesDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // City Picker Dialog with GPS Auto-Detect
    if (showCityPicker) {
        LiquidGlassDialog(
            onDismissRequest = { showCityPicker = false },
            title = { Text("Select City / Local Coordinates", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    item {
                        FilledTonalButton(
                            onClick = {
                                showCityPicker = false
                                requestGpsOrDetect()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .testTag("city_picker_gps_button")
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Detect My Current GPS Location")
                        }
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(IslamicDataSource.POPULAR_CITIES) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectCity(city)
                                    showCityPicker = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(city.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, softWrap = true)
                                Text(
                                    "${city.country} • ${city.timeZoneId}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = true
                                )
                            }
                            if (settings.cityName == city.name) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCityPicker = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Calculation Method Dialog
    if (showMethodPicker) {
        LiquidGlassDialog(
            onDismissRequest = { showMethodPicker = false },
            title = { Text("Calculation Method & Madhhab", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                    item {
                        Text("Calculation Authority:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    items(CalculationMethod.entries) { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateCalculationMethod(method) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = method.displayName,
                                fontSize = 13.sp,
                                fontWeight = if (settings.calculationMethod == method) FontWeight.Bold else FontWeight.Normal,
                                softWrap = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (settings.calculationMethod == method) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Asr Juristic Method (Madhhab):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    items(Madhhab.entries) { madhhab ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateMadhhab(madhhab) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (madhhab == Madhhab.STANDARD) "Standard (Shafi'i, Maliki, Hanbali)" else "Hanafi (Later Asr)",
                                fontSize = 13.sp,
                                fontWeight = if (settings.madhhab == madhhab) FontWeight.Bold else FontWeight.Normal,
                                softWrap = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (settings.madhhab == madhhab) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMethodPicker = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun CustomTimeInputRow(
    label: String,
    timeValue: String,
    enabled: Boolean,
    onTimeChange: (String) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        OutlinedButton(
            onClick = {
                if (enabled) {
                    val parts = timeValue.split(":")
                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    TimePickerDialog(context, { _, hourOfDay, minute ->
                        onTimeChange(String.format(Locale.US, "%02d:%02d", hourOfDay, minute))
                    }, h, m, true).show()
                }
            },
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(timeValue.ifBlank { "--:--" }, fontWeight = FontWeight.Bold)
        }
    }
}

data class PrayerRowData(
    val key: String,
    val name: String,
    val arabic: String,
    val rakahs: String,
    val time: Date,
    val customTimeStr: String,
    val isNext: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val notifEnabled: Boolean,
    val onToggleNotif: () -> Unit
)

@Composable
fun PrayerTimeCard(
    prayer: PrayerRowData,
    timeFormat: SimpleDateFormat,
    isExpanded: Boolean = false,
    onCardClick: () -> Unit = {},
    onEditTime: () -> Unit = {}
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("prayer_card_${prayer.name.lowercase().substringBefore(" ")}"),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (prayer.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = prayer.icon,
                            contentDescription = prayer.name,
                            tint = if (prayer.isNext) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = prayer.name,
                                fontWeight = if (prayer.isNext) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = if (prayer.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                softWrap = true
                            )
                            if (prayer.isNext) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "NEXT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = prayer.arabic,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeFormat.format(prayer.time),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (prayer.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    if (prayer.key != "SUNRISE") {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = prayer.onToggleNotif,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("prayer_notif_${prayer.key.lowercase()}")
                        ) {
                            Icon(
                                imageVector = if (prayer.notifEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsOff,
                                contentDescription = "Toggle ${prayer.name} Adhan Notification",
                                tint = if (prayer.notifEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Expanded Prayer Information & Actions
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Structure: ${prayer.rakahs}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true,
                            modifier = Modifier.weight(1f)
                        )

                        if (prayer.key != "SUNRISE") {
                            TextButton(
                                onClick = onEditTime,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Customize", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
