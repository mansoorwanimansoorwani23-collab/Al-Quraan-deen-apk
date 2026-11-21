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
import com.example.ai.AiProvider
import com.example.ai.AiVoicePlaybackState
import com.example.ai.AiVoicePlayer
import com.example.ai.GeminiAiService
import com.example.ai.GeminiModelCategory
import com.example.ai.GeminiModelInfo
import com.example.ai.GeminiModelsFetchResult
import com.example.ai.MultiAiProviderService
import com.example.ai.MultiAiTextResult
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val aiVoicePlayer = AiVoicePlayer(application)
    val aiVoiceState: StateFlow<AiVoicePlaybackState> = aiVoicePlayer.state

    private var aiTextJob: Job? = null

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

    fun saveGeminiApiKeyAndFetchModels(
        apiKey: String,
        onCompleted: ((Boolean) -> Unit)? = null
    ) {
        val trimmed = apiKey.trim()
        viewModelScope.launch {
            preferencesRepository.updateGeminiApiKey(trimmed)
            if (trimmed.isBlank()) {
                _availableGeminiModels.value = emptyList()
                _geminiModelFetchError.value = null
                preferencesRepository.updateCachedGeminiModelsJson("")
                onCompleted?.invoke(false)
            } else {
                preferencesRepository.updateActiveAiProvider(AiProvider.GEMINI)
                MultiAiProviderService.clearKeyCooldown(trimmed)
                val ok = fetchGeminiModelsInternal(trimmed)
                onCompleted?.invoke(ok)
            }
        }
    }

    fun selectAiProvider(provider: AiProvider) {
        viewModelScope.launch {
            preferencesRepository.updateActiveAiProvider(provider)
        }
    }

    fun toggleAutoPlayAiVoiceReply(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateAutoPlayAiVoiceReply(enabled)
        }
    }

    fun saveProviderKeysAndModel(
        provider: AiProvider,
        key1: String,
        key2: String,
        key3: String,
        selectedModel: String,
        makeActive: Boolean = true,
        onCompleted: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            preferencesRepository.updateProviderKeysAndModel(
                provider = provider,
                key1 = key1,
                key2 = key2,
                key3 = key3,
                modelId = selectedModel
            )
            listOf(key1, key2, key3).forEach { MultiAiProviderService.clearKeyCooldown(it) }
            val configuredCount = listOf(key1, key2, key3).count { it.trim().isNotBlank() }
            if (makeActive && configuredCount > 0) {
                preferencesRepository.updateActiveAiProvider(provider)
            }

            if (provider == AiProvider.GEMINI) {
                val firstGeminiKey = listOf(key1, key2, key3).firstOrNull { it.trim().isNotBlank() }?.trim().orEmpty()
                if (firstGeminiKey.isNotBlank()) {
                    val ok = fetchGeminiModelsInternal(firstGeminiKey)
                    onCompleted?.invoke(ok, if (ok) "Saved $configuredCount Gemini key(s) & loaded models" else "Saved $configuredCount Gemini key(s)")
                    return@launch
                }
            }
            onCompleted?.invoke(
                true,
                if (configuredCount > 0) "Saved $configuredCount ${provider.shortName} key(s) • $selectedModel"
                else "Cleared ${provider.shortName} keys"
            )
        }
    }

    /**
     * Option 3: 1-Tap Auto-Save for an API key detected from the Android clipboard.
     * Places the key into the first empty slot (1, 2, or 3) for the detected provider.
     */
    fun saveDetectedClipboardApiKey(
        provider: AiProvider,
        apiKey: String,
        onCompleted: (slotNumber: Int, loadedGeminiModels: Boolean) -> Unit
    ) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val current = userSettings.value
            val slots = current.getKeysForProvider(provider)
            val targetSlot = when {
                slots[0].isBlank() -> 1
                slots[1].isBlank() -> 2
                slots[2].isBlank() -> 3
                else -> 3
            }
            preferencesRepository.updateProviderKeySlot(provider, targetSlot, trimmed)
            MultiAiProviderService.clearKeyCooldown(trimmed)
            if (!current.hasAnyAiApiKey() || current.getConfiguredKeysForProvider(AiProvider.fromId(current.activeAiProvider)).isEmpty()) {
                preferencesRepository.updateActiveAiProvider(provider)
            }
            var loadedModels = false
            if (provider == AiProvider.GEMINI && _availableGeminiModels.value.isEmpty()) {
                loadedModels = fetchGeminiModelsInternal(trimmed)
            }
            onCompleted(targetSlot, loadedModels)
        }
    }

    fun refreshGeminiModels(onCompleted: ((Boolean) -> Unit)? = null) {
        val key = MultiAiProviderService.resolveBestGeminiKey(userSettings.value)
        if (key.isBlank()) {
            _geminiModelFetchError.value = "Enter at least one Gemini API key first."
            onCompleted?.invoke(false)
            return
        }
        viewModelScope.launch {
            val ok = fetchGeminiModelsInternal(key)
            onCompleted?.invoke(ok)
        }
    }

    private suspend fun fetchGeminiModelsInternal(apiKey: String): Boolean {
        _isFetchingGeminiModels.value = true
        _geminiModelFetchError.value = null
        var succeeded = false
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
                succeeded = true
            }
            is GeminiModelsFetchResult.Error -> {
                _geminiModelFetchError.value = result.message
            }
        }
        _isFetchingGeminiModels.value = false
        return succeeded
    }

    fun applyAiSetupSelections(
        textModelId: String,
        audioModelId: String,
        voiceName: String
    ) {
        viewModelScope.launch {
            if (textModelId.isNotBlank()) {
                preferencesRepository.updateSelectedGeminiTextModel(textModelId)
            }
            if (audioModelId.isNotBlank()) {
                preferencesRepository.updateSelectedGeminiAudioModel(audioModelId)
            }
            if (voiceName.isNotBlank()) {
                preferencesRepository.updateSelectedGeminiVoice(voiceName)
            }
        }
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
        stopAiTextGeneration()
        aiVoicePlayer.stop()
        aiVoicePlayer.clearPreloadedAudio()
        _availableGeminiModels.value = emptyList()
        _geminiModelFetchError.value = null
        _aiChatMessages.value = emptyList()
        viewModelScope.launch {
            preferencesRepository.clearGeminiConfig()
        }
    }

    /**
     * Pre-warms HTTP/2 TLS sockets to configured AI providers and pre-initializes on-device TTS
     * so first text token and first spoken word arrive noticeably faster.
     */
    fun warmUpAiNetwork() {
        val settings = userSettings.value
        viewModelScope.launch(Dispatchers.IO) {
            MultiAiProviderService.warmUpConfiguredProviders(settings)
        }
        aiVoicePlayer.prewarmEngine()
    }

    private fun resolveAudioModels(): Pair<String, String?> {
        val settings = userSettings.value
        val allAudioModels = _availableGeminiModels.value.filter { it.category == GeminiModelCategory.AUDIO_READ }
        val primary = settings.selectedGeminiAudioModel.ifBlank {
            allAudioModels.firstOrNull()?.id.orEmpty()
        }
        val dedicatedFallback = allAudioModels.firstOrNull {
            val l = it.id.lowercase()
            l.contains("tts") || l.contains("native-audio")
        }?.id
        return primary to dedicatedFallback
    }

    fun sendAiChatPrompt(
        prompt: String,
        isVoiceMessage: Boolean = false
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return

        stopAiTextGeneration()

        val settings = userSettings.value
        val activeProvider = AiProvider.fromId(settings.activeAiProvider)
        val geminiFallbackModel = _availableGeminiModels.value
            .firstOrNull { it.category == GeminiModelCategory.TEXT_GENERATION }?.id.orEmpty()
        val initialModel = settings.getSelectedModelForProvider(activeProvider)
        val bestGeminiTtsKey = MultiAiProviderService.resolveBestGeminiKey(settings)
        val (audioModel, dedicatedTtsFallback) = resolveAudioModels()
        val voice = settings.selectedGeminiVoice
        val shouldAutoSpeakReply = isVoiceMessage || settings.autoPlayAiVoiceReply

        val now = System.currentTimeMillis()
        val userMsg = AiChatMessage(
            id = "u_$now",
            isUser = true,
            text = cleanPrompt,
            isVoiceMessage = isVoiceMessage
        )
        val assistantMsgId = "a_${now + 1}"
        val streamingPlaceholder = AiChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "",
            modelUsed = initialModel,
            isStreaming = true,
            providerLabel = "${activeProvider.shortName} • Key 1"
        )

        val historyBefore = _aiChatMessages.value
        _aiChatMessages.value = historyBefore + userMsg + streamingPlaceholder
        _isAiGeneratingText.value = true

        aiTextJob = viewModelScope.launch {
            var earlyVoicePreloadTriggered = false
            try {
                val res = MultiAiProviderService.generateTextStreamingWithFallback(
                    settings = settings,
                    geminiFallbackModelId = geminiFallbackModel,
                    userPrompt = cleanPrompt,
                    recentHistory = historyBefore,
                    onActiveAttemptChanged = { provider, keySlot, modelId ->
                        updateChatMessage(assistantMsgId) { msg ->
                            msg.copy(
                                modelUsed = modelId,
                                providerLabel = "${provider.shortName} • Key $keySlot"
                            )
                        }
                    },
                    onPartialText = { partial ->
                        updateChatMessage(assistantMsgId) { msg ->
                            msg.copy(text = partial, isStreaming = true)
                        }
                        // Start preloading Gemini voice in parallel as soon as first sentence arrives
                        if (!earlyVoicePreloadTriggered &&
                            bestGeminiTtsKey.isNotBlank() &&
                            partial.length >= 130 &&
                            partial.any { it == '.' || it == '!' || it == '?' || it == '।' || it == '\n' }
                        ) {
                            earlyVoicePreloadTriggered = true
                            aiVoicePlayer.preloadMessageVoice(
                                messageId = assistantMsgId,
                                rawText = partial,
                                apiKey = bestGeminiTtsKey,
                                audioModelId = audioModel,
                                fallbackTtsModelId = dedicatedTtsFallback,
                                voiceName = voice,
                                isFinalText = false
                            )
                        }
                    }
                )
                when (res) {
                    is MultiAiTextResult.Success -> {
                        val badge = buildString {
                            append(res.provider.shortName)
                            append(" • Key ")
                            append(res.keySlot)
                            if (res.usedFallback) append(" (Fallback)")
                        }
                        updateChatMessage(assistantMsgId) { msg ->
                            msg.copy(
                                text = res.text,
                                modelUsed = res.modelId,
                                providerLabel = badge,
                                isStreaming = false
                            )
                        }
                        if (shouldAutoSpeakReply) {
                            // Voice message reply (or Auto-Voice enabled): speak answer aloud automatically!
                            aiVoicePlayer.playOrToggleMessage(
                                textToRead = res.text,
                                messageId = assistantMsgId,
                                apiKey = bestGeminiTtsKey,
                                audioModelId = audioModel,
                                fallbackTtsModelId = dedicatedTtsFallback,
                                voiceName = voice
                            )
                        } else if (bestGeminiTtsKey.isNotBlank()) {
                            // Preload voice silently in background so tapping Listen is instant!
                            aiVoicePlayer.preloadMessageVoice(
                                messageId = assistantMsgId,
                                rawText = res.text,
                                apiKey = bestGeminiTtsKey,
                                audioModelId = audioModel,
                                fallbackTtsModelId = dedicatedTtsFallback,
                                voiceName = voice,
                                isFinalText = true
                            )
                        }
                    }
                    is MultiAiTextResult.Error -> {
                        updateChatMessage(assistantMsgId) { msg ->
                            msg.copy(
                                text = if (msg.text.isNotBlank()) msg.text else "Error: ${res.message}",
                                isStreaming = false
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                _aiChatMessages.update { list ->
                    list.mapNotNull { msg ->
                        if (msg.id == assistantMsgId) {
                            if (msg.text.isBlank()) null else msg.copy(isStreaming = false)
                        } else msg
                    }
                }
            } finally {
                _isAiGeneratingText.value = false
            }
        }
    }

    private fun updateChatMessage(id: String, transform: (AiChatMessage) -> AiChatMessage) {
        _aiChatMessages.update { list ->
            val idx = list.indexOfLast { it.id == id }
            if (idx < 0) list
            else list.toMutableList().apply { this[idx] = transform(this[idx]) }
        }
    }

    fun stopAiTextGeneration() {
        MultiAiProviderService.cancelActiveGeneration()
        aiTextJob?.cancel()
        aiTextJob = null
        _isAiGeneratingText.value = false
        _aiChatMessages.update { list ->
            list.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
        }
    }

    fun clearAiChat() {
        stopAiTextGeneration()
        aiVoicePlayer.stop()
        aiVoicePlayer.clearPreloadedAudio()
        _aiChatMessages.value = emptyList()
    }

    fun readTextWithGeminiAudio(textToRead: String, messageId: String? = null) {
        val cleanText = textToRead.trim()
        if (cleanText.isBlank()) return

        val settings = userSettings.value
        val bestGeminiKey = MultiAiProviderService.resolveBestGeminiKey(settings)
        val (audioModel, dedicatedTtsFallback) = resolveAudioModels()
        aiVoicePlayer.playOrToggleMessage(
            textToRead = cleanText,
            messageId = messageId,
            apiKey = bestGeminiKey,
            audioModelId = audioModel,
            fallbackTtsModelId = dedicatedTtsFallback,
            voiceName = settings.selectedGeminiVoice
        )
    }

    fun togglePauseResumeAiVoice() {
        aiVoicePlayer.togglePauseResume()
    }

    fun rewindAiVoice(deltaMs: Long = 5_000L) {
        aiVoicePlayer.seekByMs(-deltaMs)
    }

    fun forwardAiVoice(deltaMs: Long = 5_000L) {
        aiVoicePlayer.seekByMs(deltaMs)
    }

    fun seekAiVoice(fraction: Float) {
        aiVoicePlayer.seekToFraction(fraction)
    }

    fun cycleAiVoiceSpeed() {
        aiVoicePlayer.cyclePlaybackSpeed()
    }

    fun stopAiAudioPlayback() {
        aiVoicePlayer.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopAiTextGeneration()
        aiVoicePlayer.release()
        quranAudioPlayerManager.release()
    }
}
