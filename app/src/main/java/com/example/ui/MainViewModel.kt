package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiChatMessage
import com.example.ai.GeminiAiService
import com.example.ai.GeminiAudioResult
import com.example.ai.GeminiModelCategory
import com.example.ai.GeminiModelInfo
import com.example.ai.GeminiModelsFetchResult
import com.example.ai.GeminiTextResult
import com.example.audio.QuranAudioPlayerManager
import com.example.audio.QuranPlaybackState
import com.example.audio.ReciterVoice
import com.example.audio.ReciterVoicePacks
import com.example.data.local.AppDatabase
import com.example.data.local.BookmarkEntity
import com.example.data.local.DeenDayEntity
import com.example.data.local.IslamicDataSource
import com.example.data.model.*
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import com.example.data.repository.DeenRepository
import com.example.data.repository.QuranRepository
import com.example.domain.calculator.*
import com.example.location.CompassSensorManager
import com.example.notification.PrayerAlarmScheduler
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class HomeUiState(
    val gregorianDateText: String = "",
    val hijriDate: HijriDate? = null,
    val prayerTimes: PrayerTimesResult? = null,
    val deenScore: Int = 0,
    val todayDeenRecord: DeenDayEntity? = null,
    val dailyAyah: Ayah? = null,
    val dailyAyahSurahName: String = "",
    val dailyHadith: Hadith? = null,
    val dailyDua: DuaAzkar? = null,
    val userSettings: UserSettings = UserSettings()
)

data class QiblaUiState(
    val qiblaBearing: Float = 0f,
    val distanceKm: Double = 0.0,
    val deviceAzimuth: Float = 0f,
    val isAlignedWithKaaba: Boolean = false,
    val accuracy: Int = 3
)

data class TasbeehUiState(
    val currentCount: Int = 0,
    val targetCount: Int = 33,
    val selectedDhikr: String = "SubhanAllah",
    val todayTotal: Int = 0,
    val streakDays: Int = 3
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val deenRepository = DeenRepository(db)
    val preferencesRepository = UserPreferencesRepository(application)
    val quranRepository = QuranRepository(application)
    private val compassSensorManager = CompassSensorManager(application)
    val quranAudioPlayerManager = QuranAudioPlayerManager(
        context = application,
        preferencesRepository = preferencesRepository,
        quranRepository = quranRepository
    )

    val userSettings = preferencesRepository.userSettingsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserSettings()
    )

    val quranPlaybackState: StateFlow<QuranPlaybackState> = quranAudioPlayerManager.playbackState

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _qiblaUiState = MutableStateFlow(QiblaUiState())
    val qiblaUiState: StateFlow<QiblaUiState> = _qiblaUiState.asStateFlow()

    private val _tasbeehUiState = MutableStateFlow(TasbeehUiState())
    val tasbeehUiState: StateFlow<TasbeehUiState> = _tasbeehUiState.asStateFlow()

    // Offline Quran State
    val surahsList = IslamicDataSource.SURAHS
    private val _selectedSurah = MutableStateFlow<Surah?>(IslamicDataSource.SURAHS[0])
    val selectedSurah: StateFlow<Surah?> = _selectedSurah.asStateFlow()

    private val _selectedSurahAyahs = MutableStateFlow<List<Ayah>>(IslamicDataSource.AYAHS_BY_SURAH[1] ?: emptyList())
    val selectedSurahAyahs: StateFlow<List<Ayah>> = _selectedSurahAyahs.asStateFlow()

    private val _isLoadingOfflineQuran = MutableStateFlow(false)
    val isLoadingOfflineQuran: StateFlow<Boolean> = _isLoadingOfflineQuran.asStateFlow()

    private val _quranSearchQuery = MutableStateFlow("")
    val quranSearchQuery: StateFlow<String> = _quranSearchQuery.asStateFlow()

    // Hadith State
    val hadithsList = IslamicDataSource.HADITHS
    private val _selectedHadithCategory = MutableStateFlow("All")
    val selectedHadithCategory: StateFlow<String> = _selectedHadithCategory.asStateFlow()

    private val _hadithSearchQuery = MutableStateFlow("")
    val hadithSearchQuery: StateFlow<String> = _hadithSearchQuery.asStateFlow()

    // Dua & Azkar State
    val duasList = IslamicDataSource.DUAS_AND_AZKAR
    private val _selectedDuaCategory = MutableStateFlow("Morning")
    val selectedDuaCategory: StateFlow<String> = _selectedDuaCategory.asStateFlow()

    // Bookmarks and Last Read
    val allBookmarks = deenRepository.getAllBookmarks().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val lastRead = deenRepository.getLastRead().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val todayDeenRecord = deenRepository.getTodayDeenRecord().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val recentDeenRecords = deenRepository.getRecentDeenRecords(30).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    val completedFastsCount = deenRepository.getCompletedFastsCount().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        0
    )

    // Global Search State
    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _globalSearchFilter = MutableStateFlow("ALL")
    val globalSearchFilter: StateFlow<String> = _globalSearchFilter.asStateFlow()

    // Gemini AI State (End-User API Key + Auto-Discovered Models)
    private val _availableGeminiModels = MutableStateFlow<List<GeminiModelInfo>>(emptyList())
    val availableGeminiModels: StateFlow<List<GeminiModelInfo>> = _availableGeminiModels.asStateFlow()

    private val _isFetchingGeminiModels = MutableStateFlow(false)
    val isFetchingGeminiModels: StateFlow<Boolean> = _isFetchingGeminiModels.asStateFlow()

    private val _geminiModelFetchError = MutableStateFlow<String?>(null)
    val geminiModelFetchError: StateFlow<String?> = _geminiModelFetchError.asStateFlow()

    private val _aiChatMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiChatMessages: StateFlow<List<AiChatMessage>> = _aiChatMessages.asStateFlow()

    private val _isAiGeneratingText = MutableStateFlow(false)
    val isAiGeneratingText: StateFlow<Boolean> = _isAiGeneratingText.asStateFlow()

    private val _isAiGeneratingAudio = MutableStateFlow(false)
    val isAiGeneratingAudio: StateFlow<Boolean> = _isAiGeneratingAudio.asStateFlow()

    private val _isAiAudioPlaying = MutableStateFlow(false)
    val isAiAudioPlaying: StateFlow<Boolean> = _isAiAudioPlaying.asStateFlow()

    private val _aiAudioStatusMessage = MutableStateFlow<String?>(null)
    val aiAudioStatusMessage: StateFlow<String?> = _aiAudioStatusMessage.asStateFlow()

    private var fallbackTts: TextToSpeech? = null
    private var isFallbackTtsReady = false

    init {
        val dailyAyahItem = IslamicDataSource.AYAHS_BY_SURAH[2]?.find { it.numberInSurah == 255 }
            ?: IslamicDataSource.AYAHS_BY_SURAH[1]?.first()
        val dailyHadithItem = IslamicDataSource.HADITHS.first()
        val dailyDuaItem = IslamicDataSource.DUAS_AND_AZKAR.first()

        // Restore preferred reciter & cached Gemini models on startup
        viewModelScope.launch {
            val initialSettings = preferencesRepository.userSettingsFlow.first()
            val savedReciter = ReciterVoicePacks.RECITERS.find { it.id == initialSettings.selectedReciterId }
            if (savedReciter != null) {
                quranAudioPlayerManager.selectReciter(savedReciter)
            }
            if (initialSettings.cachedGeminiModelsJson.isNotBlank()) {
                _availableGeminiModels.value = GeminiAiService.deserializeModelsFromJson(initialSettings.cachedGeminiModelsJson)
            }
            if (initialSettings.geminiApiKey.isNotBlank() && _availableGeminiModels.value.isEmpty()) {
                fetchGeminiModelsInternal(initialSettings.geminiApiKey)
            }
        }

        // Preload Surah Al-Fatihah offline
        viewModelScope.launch {
            val fatihah = quranRepository.getAyahsForSurah(IslamicDataSource.SURAHS[0])
            if (fatihah.isNotEmpty()) {
                _selectedSurahAyahs.value = fatihah
            }
        }

        viewModelScope.launch {
            while (true) {
                updateDashboard(dailyAyahItem, dailyHadithItem, dailyDuaItem)
                delay(1000)
            }
        }

        // Observe compass azimuth for Qibla
        viewModelScope.launch {
            compassSensorManager.azimuthFlow.collect { azimuth ->
                val settings = userSettings.value
                val qiblaResult = QiblaCalculator.calculate(settings.latitude, settings.longitude)
                val diff = kotlin.math.abs(azimuth - qiblaResult.bearing)
                val isAligned = diff <= 3f || diff >= 357f

                _qiblaUiState.value = QiblaUiState(
                    qiblaBearing = qiblaResult.bearing,
                    distanceKm = qiblaResult.distanceKm,
                    deviceAzimuth = azimuth,
                    isAlignedWithKaaba = isAligned,
                    accuracy = compassSensorManager.accuracyFlow.value
                )
            }
        }

        // Observe Tasbeeh total for today
        viewModelScope.launch {
            deenRepository.getTodayTasbeehTotal().collect { total ->
                _tasbeehUiState.update { it.copy(todayTotal = total ?: 0) }
            }
        }
    }

    private fun updateDashboard(dailyAyahItem: Ayah?, dailyHadithItem: Hadith, dailyDuaItem: DuaAzkar) {
        val settings = userSettings.value
        val tz = TimeZone.getTimeZone(settings.timeZoneId)
        val now = Calendar.getInstance(tz)

        val gregFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).apply {
            timeZone = tz
        }
        val gregText = gregFormat.format(now.time)

        val hijri = HijriCalendarCalculator.calculateHijriDate(now, settings.hijriAdjustment)

        val rawPrayerRes = PrayerTimesCalculator.calculate(
            latitude = settings.latitude,
            longitude = settings.longitude,
            date = now,
            method = settings.calculationMethod,
            madhhab = settings.madhhab,
            highLatRule = settings.highLatitudeRule,
            timeZone = tz
        )

        val prayerRes = PrayerTimesCalculator.applyCustomTimes(
            calculated = rawPrayerRes,
            useCustom = settings.useCustomPrayerTimes,
            customFajr = settings.customFajrTime,
            customDhuhr = settings.customDhuhrTime,
            customAsr = settings.customAsrTime,
            customMaghrib = settings.customMaghribTime,
            customIsha = settings.customIshaTime,
            timeZone = tz,
            baseDate = now
        )

        val todayRec = todayDeenRecord.value

        _homeUiState.value = HomeUiState(
            gregorianDateText = gregText,
            hijriDate = hijri,
            prayerTimes = prayerRes,
            deenScore = todayRec?.calculateScore() ?: 0,
            todayDeenRecord = todayRec,
            dailyAyah = dailyAyahItem,
            dailyAyahSurahName = "Al-Baqarah (2:255)",
            dailyHadith = dailyHadithItem,
            dailyDua = dailyDuaItem,
            userSettings = settings
        )
    }

    fun startCompass() = compassSensorManager.startListening()
    fun stopCompass() = compassSensorManager.stopListening()

    // 100% Offline Quran Reading actions (never requires internet/API)
    fun selectSurah(surah: Surah) {
        _selectedSurah.value = surah
        _isLoadingOfflineQuran.value = true

        viewModelScope.launch {
            deenRepository.setLastRead(surah.number, 1, surah.nameArabic, surah.nameEnglish)
            val fullAyahs = quranRepository.getAyahsForSurah(surah)
            if (_selectedSurah.value?.number == surah.number) {
                _selectedSurahAyahs.value = fullAyahs
            }
            _isLoadingOfflineQuran.value = false
        }
    }

    fun selectJuz(juzNumber: Int) {
        val juzInfo = IslamicDataSource.JUZ_DEFINITIONS.find { it.juzNumber == juzNumber }
            ?: IslamicDataSource.JUZ_DEFINITIONS[0]
        val matchingSurah = IslamicDataSource.SURAHS.firstOrNull { it.number == juzInfo.startSurahNumber }
            ?: IslamicDataSource.SURAHS[0]
        _selectedSurah.value = matchingSurah
        _isLoadingOfflineQuran.value = true

        viewModelScope.launch {
            deenRepository.setLastRead(
                matchingSurah.number,
                juzInfo.startAyahNumber,
                matchingSurah.nameArabic,
                matchingSurah.nameEnglish
            )
            val fullAyahs = quranRepository.getAyahsForJuz(juzNumber)
            _selectedSurahAyahs.value = fullAyahs
            _isLoadingOfflineQuran.value = false
        }
    }

    fun saveOfflineReadingProgress(surahNumber: Int, ayahNumber: Int) {
        val surah = IslamicDataSource.SURAHS.find { it.number == surahNumber } ?: return
        viewModelScope.launch {
            deenRepository.setLastRead(surah.number, ayahNumber, surah.nameArabic, surah.nameEnglish)
        }
    }

    // Separate Online Quran Audio actions
    fun playQuranAyah(surah: Surah, ayah: Ayah, startPositionMs: Int = 0) {
        quranAudioPlayerManager.playAyah(surah, ayah, startPositionMs)
    }

    fun resumeSavedOnlineQuranAudio() {
        val s = userSettings.value
        if (s.hasSavedAudioProgress) {
            quranAudioPlayerManager.resumeSavedProgress(
                surahNumber = s.savedAudioSurahNumber,
                ayahNumber = s.savedAudioAyahNumber,
                positionMs = s.savedAudioPositionMs,
                reciterId = s.savedAudioReciterId
            )
        }
    }

    fun toggleQuranPlayPause() {
        quranAudioPlayerManager.togglePlayPause()
    }

    fun stopQuranAudio() {
        quranAudioPlayerManager.stopAndSave()
    }

    fun nextQuranAyah() {
        quranAudioPlayerManager.nextAyah()
    }

    fun previousQuranAyah() {
        quranAudioPlayerManager.previousAyah()
    }

    fun selectReciterVoice(reciter: ReciterVoice) {
        quranAudioPlayerManager.selectReciter(reciter)
        viewModelScope.launch {
            preferencesRepository.updateSelectedReciter(reciter.id)
        }
    }

    fun setQuranPlaybackSpeed(speed: Float) {
        quranAudioPlayerManager.setPlaybackSpeed(speed)
    }

    fun seekQuranAudio(progress: Float) {
        quranAudioPlayerManager.seekTo(progress)
    }

    fun updateQuranSearchQuery(query: String) {
        _quranSearchQuery.value = query
    }

    // Hadith actions
    fun selectHadithCategory(category: String) {
        _selectedHadithCategory.value = category
    }

    fun updateHadithSearchQuery(query: String) {
        _hadithSearchQuery.value = query
    }

    // Dua actions
    fun selectDuaCategory(category: String) {
        _selectedDuaCategory.value = category
    }

    // Tasbeeh actions
    fun incrementTasbeeh() {
        val current = _tasbeehUiState.value.currentCount + 1
        val target = _tasbeehUiState.value.targetCount

        if (userSettings.value.vibrationOnTasbeeh) {
            triggerHaptic(if (target > 0 && current % target == 0) 80L else 30L)
        }

        _tasbeehUiState.update { it.copy(currentCount = current) }

        viewModelScope.launch {
            deenRepository.addTasbeehCount(_tasbeehUiState.value.selectedDhikr, 1, target)
        }
    }

    fun resetTasbeeh() {
        _tasbeehUiState.update { it.copy(currentCount = 0) }
    }

    fun setTasbeehTarget(target: Int) {
        _tasbeehUiState.update { it.copy(targetCount = target) }
    }

    fun setTasbeehDhikr(dhikr: String) {
        _tasbeehUiState.update { it.copy(selectedDhikr = dhikr, currentCount = 0) }
    }

    // Deen Tracker Actions
    fun togglePrayer(prayerName: String) {
        viewModelScope.launch {
            deenRepository.togglePrayer(prayerName)
        }
    }

    fun updateQuranMinutes(minutes: Int) {
        viewModelScope.launch {
            deenRepository.updateQuranMinutes(minutes)
        }
    }

    fun incrementQuranReading() {
        viewModelScope.launch {
            deenRepository.incrementQuranReading()
        }
    }

    fun updateQuranReadingsCount(count: Int) {
        viewModelScope.launch {
            deenRepository.updateQuranReadingsCount(count)
        }
    }

    fun updateFastingStatus(status: String) {
        viewModelScope.launch {
            deenRepository.updateFastingStatus(status)
        }
    }

    // Bookmarking
    fun toggleBookmark(type: String, id: String, title: String, subtitle: String, arSnippet: String, enSnippet: String, destData: String) {
        viewModelScope.launch {
            val existing = allBookmarks.value.find { it.id == id }
            if (existing != null) {
                deenRepository.removeBookmark(id)
            } else {
                deenRepository.toggleBookmark(
                    BookmarkEntity(
                        id = id,
                        type = type,
                        title = title,
                        subtitle = subtitle,
                        arabicSnippet = arSnippet,
                        englishSnippet = enSnippet,
                        destinationData = destData
                    )
                )
            }
        }
    }

    private fun reschedulePrayerAlarms() {
        PrayerAlarmScheduler.scheduleAllAlarms(getApplication())
    }

    // Location & Calculation Updates (automatically reschedule Adhan alarms)
    fun selectCity(city: CityLocation) {
        viewModelScope.launch {
            preferencesRepository.updateLocation(
                city = city.name,
                country = city.country,
                lat = city.latitude,
                lng = city.longitude,
                tz = city.timeZoneId,
                isGps = false
            )
            reschedulePrayerAlarms()
        }
    }

    fun setGpsLocation(lat: Double, lng: Double, cityName: String = "My Location", regionName: String = "") {
        viewModelScope.launch {
            preferencesRepository.updateLocation(
                city = cityName,
                country = regionName,
                lat = lat,
                lng = lng,
                tz = TimeZone.getDefault().id,
                isGps = true
            )
            reschedulePrayerAlarms()
        }
    }

    /**
     * Fetches real GPS coordinates via FusedLocationProviderClient, resolves city/state
     * via Geocoder, and saves exact local coordinates & device timezone for accurate prayer times.
     */
    @SuppressLint("MissingPermission")
    fun detectCurrentGpsLocation(context: Context, onResult: (Boolean, String) -> Unit) {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        resolveAndSaveLocation(context, location.latitude, location.longitude, onResult)
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { lastLoc ->
                                if (lastLoc != null) {
                                    resolveAndSaveLocation(context, lastLoc.latitude, lastLoc.longitude, onResult)
                                } else {
                                    onResult(false, "Unable to get GPS fix. Ensure Location/GPS is enabled on your phone.")
                                }
                            }
                            .addOnFailureListener { e ->
                                onResult(false, "Location detection failed: ${e.localizedMessage ?: "Unknown error"}")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    onResult(false, "Location detection failed: ${e.localizedMessage ?: "Unknown error"}")
                }
        } catch (e: Exception) {
            onResult(false, "Location permission required to detect GPS coordinates.")
        }
    }

    private fun resolveAndSaveLocation(
        context: Context,
        lat: Double,
        lng: Double,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val (city, region) = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
                    val addr = addresses?.firstOrNull()
                    val resolvedCity = addr?.locality
                        ?: addr?.subAdminArea
                        ?: addr?.adminArea
                        ?: "Local GPS"
                    val resolvedRegion = listOfNotNull(addr?.adminArea, addr?.countryName)
                        .distinct()
                        .joinToString(", ")
                    resolvedCity to resolvedRegion
                } catch (_: Exception) {
                    "Local GPS" to String.format(Locale.US, "%.3f, %.3f", lat, lng)
                }
            }
            val localTz = TimeZone.getDefault().id
            preferencesRepository.updateLocation(
                city = city,
                country = region,
                lat = lat,
                lng = lng,
                tz = localTz,
                isGps = true
            )
            reschedulePrayerAlarms()
            onResult(true, "Location updated: $city ($localTz)")
        }
    }

    fun updateCalculationMethod(method: CalculationMethod) {
        viewModelScope.launch {
            preferencesRepository.updateCalculationMethod(method)
            reschedulePrayerAlarms()
        }
    }

    fun updateMadhhab(madhhab: Madhhab) {
        viewModelScope.launch {
            preferencesRepository.updateMadhhab(madhhab)
            reschedulePrayerAlarms()
        }
    }

    // Global Search
    fun updateGlobalSearchQuery(query: String) {
        _globalSearchQuery.value = query
    }

    fun setGlobalSearchFilter(filter: String) {
        _globalSearchFilter.value = filter
    }

    // Custom Prayer & Adhan Times
    fun setUseCustomPrayerTimes(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateUseCustomPrayerTimes(enabled)
            reschedulePrayerAlarms()
        }
    }

    fun updateCustomPrayerTime(prayerKey: String, time: String) {
        viewModelScope.launch {
            preferencesRepository.updateCustomPrayerTime(prayerKey, time)
            reschedulePrayerAlarms()
        }
    }

    fun updateCustomAdhanTime(prayerKey: String, time: String) {
        viewModelScope.launch {
            preferencesRepository.updateCustomAdhanTime(prayerKey, time)
            reschedulePrayerAlarms()
        }
    }

    fun updateAdhanSound(soundName: String) {
        viewModelScope.launch {
            preferencesRepository.updateAdhanSound(soundName)
            reschedulePrayerAlarms()
        }
    }

    fun updatePrayerNotification(prayerName: String, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePrayerNotification(prayerName, enabled)
            reschedulePrayerAlarms()
        }
    }

    fun update24HourFormat(is24Hour: Boolean) {
        viewModelScope.launch {
            preferencesRepository.update24HourFormat(is24Hour)
        }
    }

    fun toggle24HourFormat() {
        viewModelScope.launch {
            val current = userSettings.value.is24HourFormat
            preferencesRepository.update24HourFormat(!current)
        }
    }

    fun resetCustomPrayerTimes(defaultFajr: String, defaultDhuhr: String, defaultAsr: String, defaultMaghrib: String, defaultIsha: String) {
        viewModelScope.launch {
            preferencesRepository.resetCustomPrayerTimes(defaultFajr, defaultDhuhr, defaultAsr, defaultMaghrib, defaultIsha)
            reschedulePrayerAlarms()
        }
    }

    fun triggerHaptic(durationMs: Long = 40L) {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
        }
    }

    // ================= APP THEME & GEMINI AI ACTIONS =================

    fun updateAppTheme(themeId: String) {
        viewModelScope.launch {
            preferencesRepository.updateAppTheme(themeId)
        }
    }

    fun saveGeminiApiKeyAndFetchModels(apiKey: String) {
        val trimmed = apiKey.trim()
        viewModelScope.launch {
            preferencesRepository.updateGeminiApiKey(trimmed)
            if (trimmed.isBlank()) {
                _availableGeminiModels.value = emptyList()
                _geminiModelFetchError.value = null
                preferencesRepository.updateCachedGeminiModelsJson("")
            } else {
                fetchGeminiModelsInternal(trimmed)
            }
        }
    }

    fun refreshGeminiModels() {
        val key = userSettings.value.geminiApiKey
        if (key.isBlank()) {
            _geminiModelFetchError.value = "Enter your Gemini API key first."
            return
        }
        viewModelScope.launch {
            fetchGeminiModelsInternal(key)
        }
    }

    private suspend fun fetchGeminiModelsInternal(apiKey: String) {
        _isFetchingGeminiModels.value = true
        _geminiModelFetchError.value = null
        when (val result = GeminiAiService.fetchAvailableModels(apiKey)) {
            is GeminiModelsFetchResult.Success -> {
                _availableGeminiModels.value = result.models
                preferencesRepository.updateCachedGeminiModelsJson(result.rawCacheJson)

                val currentSettings = userSettings.value
                val textModels = result.models.filter { it.category == GeminiModelCategory.TEXT_GENERATION }
                val audioModels = result.models.filter { it.category == GeminiModelCategory.AUDIO_READ }

                if (textModels.isNotEmpty() && textModels.none { it.id == currentSettings.selectedGeminiTextModel }) {
                    preferencesRepository.updateSelectedGeminiTextModel(textModels.first().id)
                }
                if (audioModels.isNotEmpty() && audioModels.none { it.id == currentSettings.selectedGeminiAudioModel }) {
                    preferencesRepository.updateSelectedGeminiAudioModel(audioModels.first().id)
                }
            }
            is GeminiModelsFetchResult.Error -> {
                _geminiModelFetchError.value = result.message
            }
        }
        _isFetchingGeminiModels.value = false
    }

    fun selectGeminiTextModel(modelId: String) {
        viewModelScope.launch {
            preferencesRepository.updateSelectedGeminiTextModel(modelId)
        }
    }

    fun selectGeminiAudioModel(modelId: String) {
        viewModelScope.launch {
            preferencesRepository.updateSelectedGeminiAudioModel(modelId)
        }
    }

    fun selectGeminiVoice(voiceName: String) {
        viewModelScope.launch {
            preferencesRepository.updateSelectedGeminiVoice(voiceName)
        }
    }

    fun clearGeminiConfig() {
        stopAiAudioPlayback()
        _availableGeminiModels.value = emptyList()
        _geminiModelFetchError.value = null
        _aiChatMessages.value = emptyList()
        viewModelScope.launch {
            preferencesRepository.clearGeminiConfig()
        }
    }

    fun sendAiChatPrompt(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return
        val settings = userSettings.value
        val apiKey = settings.geminiApiKey
        val textModel = settings.selectedGeminiTextModel.ifBlank {
            _availableGeminiModels.value.firstOrNull { it.category == GeminiModelCategory.TEXT_GENERATION }?.id.orEmpty()
        }

        val userMsg = AiChatMessage(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = cleanPrompt
        )
        val historyBefore = _aiChatMessages.value
        _aiChatMessages.value = historyBefore + userMsg
        _isAiGeneratingText.value = true

        viewModelScope.launch {
            when (val res = GeminiAiService.generateText(apiKey, textModel, cleanPrompt, historyBefore)) {
                is GeminiTextResult.Success -> {
                    _aiChatMessages.value = _aiChatMessages.value + AiChatMessage(
                        id = "a_${System.currentTimeMillis()}",
                        isUser = false,
                        text = res.text,
                        modelUsed = res.modelId
                    )
                }
                is GeminiTextResult.Error -> {
                    _aiChatMessages.value = _aiChatMessages.value + AiChatMessage(
                        id = "e_${System.currentTimeMillis()}",
                        isUser = false,
                        text = "Error: ${res.message}",
                        modelUsed = textModel
                    )
                }
            }
            _isAiGeneratingText.value = false
        }
    }

    fun clearAiChat() {
        _aiChatMessages.value = emptyList()
    }

    fun readTextWithGeminiAudio(textToRead: String) {
        val cleanText = textToRead.trim()
        if (cleanText.isBlank()) return

        stopAiAudioPlayback()
        val settings = userSettings.value
        val apiKey = settings.geminiApiKey
        val audioModel = settings.selectedGeminiAudioModel.ifBlank {
            _availableGeminiModels.value.firstOrNull { it.category == GeminiModelCategory.AUDIO_READ }?.id.orEmpty()
        }
        val voice = settings.selectedGeminiVoice

        _isAiGeneratingAudio.value = true
        _aiAudioStatusMessage.value = "Preparing AI Audio Read ($audioModel)..."

        viewModelScope.launch {
            when (val res = GeminiAiService.generateAudioRead(apiKey, audioModel, cleanText, voice)) {
                is GeminiAudioResult.NativePcmAudio -> {
                    _isAiGeneratingAudio.value = false
                    _isAiAudioPlaying.value = true
                    _aiAudioStatusMessage.value = "Playing Gemini Audio (${res.modelId} • $voice)"
                    GeminiAiService.playPcmAudio(res.pcmBytes, res.sampleRateHz) {
                        _isAiAudioPlaying.value = false
                        _aiAudioStatusMessage.value = null
                    }
                }
                is GeminiAudioResult.FallbackToDeviceTts -> {
                    _isAiGeneratingAudio.value = false
                    _aiAudioStatusMessage.value = res.note
                    speakWithDeviceTts(res.textToSpeak)
                }
                is GeminiAudioResult.Error -> {
                    _isAiGeneratingAudio.value = false
                    _isAiAudioPlaying.value = false
                    _aiAudioStatusMessage.value = res.message
                }
            }
        }
    }

    private fun speakWithDeviceTts(text: String) {
        val app = getApplication<Application>()
        if (fallbackTts == null) {
            fallbackTts = TextToSpeech(app) { status ->
                isFallbackTtsReady = status == TextToSpeech.SUCCESS
                if (isFallbackTtsReady) {
                    fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isAiAudioPlaying.value = true
                        }
                        override fun onDone(utteranceId: String?) {
                            _isAiAudioPlaying.value = false
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isAiAudioPlaying.value = false
                        }
                    })
                    fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hidayah_ai_tts")
                }
            }
        } else if (isFallbackTtsReady) {
            _isAiAudioPlaying.value = true
            fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hidayah_ai_tts")
        }
    }

    fun stopAiAudioPlayback() {
        GeminiAiService.stopPcmAudio()
        runCatching { fallbackTts?.stop() }
        _isAiGeneratingAudio.value = false
        _isAiAudioPlaying.value = false
        _aiAudioStatusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        quranAudioPlayerManager.release()
        GeminiAiService.stopPcmAudio()
        runCatching { fallbackTts?.shutdown() }
    }
}
