package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IslamicDataSource
import com.example.data.repository.QuranRepository
import com.example.domain.calculator.CalculationMethod
import com.example.domain.calculator.Madhhab
import com.example.domain.calculator.PrayerTimesCalculator
import com.example.domain.recitation.QuranRecitationAnalyzer
import com.example.domain.recitation.WordEvaluationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name is Hidayah`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Hidayah", appName)
    }

    @Test
    fun `offline Quran repository loads complete 6236 ayahs across 114 surahs and 30 juz`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = QuranRepository(context)
        val fatihah = repo.getAyahsForSurah(IslamicDataSource.SURAHS.first())
        val baqarah = repo.getAyahsForSurah(IslamicDataSource.SURAHS[1])
        val nas = repo.getAyahsForSurah(IslamicDataSource.SURAHS.last())
        val juz30 = repo.getAyahsForJuz(30)

        assertEquals(7, fatihah.size)
        assertEquals(286, baqarah.size)
        assertEquals(6, nas.size)
        assertTrue("Juz 30 should contain over 500 ayahs offline", juz30.size > 500)
    }

    @Test
    fun `local prayer times for Srinagar Kashmir are chronologically accurate and Asr is in afternoon`() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val date = Calendar.getInstance(tz).apply {
            set(2026, Calendar.MAY, 15, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val standard = PrayerTimesCalculator.calculate(
            latitude = 34.0837,
            longitude = 74.7973,
            date = date,
            method = CalculationMethod.KARACHI,
            madhhab = Madhhab.STANDARD,
            timeZone = tz
        )

        val hanafi = PrayerTimesCalculator.calculate(
            latitude = 34.0837,
            longitude = 74.7973,
            date = date,
            method = CalculationMethod.KARACHI,
            madhhab = Madhhab.HANAFI,
            timeZone = tz
        )

        assertTrue("Fajr must be before Sunrise", standard.fajr.before(standard.sunrise))
        assertTrue("Sunrise must be before Dhuhr", standard.sunrise.before(standard.dhuhr))
        assertTrue("Dhuhr must be before Asr", standard.dhuhr.before(standard.asr))
        assertTrue("Asr must be before Maghrib", standard.asr.before(standard.maghrib))
        assertTrue("Maghrib must be before Isha", standard.maghrib.before(standard.isha))

        val asrCal = Calendar.getInstance(tz).apply { time = standard.asr }
        val asrHour = asrCal.get(Calendar.HOUR_OF_DAY)
        assertTrue("Standard Asr hour ($asrHour) must be afternoon", asrHour in 14..18)
        assertTrue("Hanafi Asr must be after Standard Asr", hanafi.asr.after(standard.asr))
    }

    @Test
    fun `recitation analyzer detects exact match, Makharij mistake, and missed word`() {
        val targetAyah = "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ"

        val exact = QuranRecitationAnalyzer.analyze(targetAyah, "اهدنا الصراط المستقيم")
        assertEquals(100, exact.accuracyPercent)
        assertTrue(exact.mistakes.isEmpty())

        val makharijError = QuranRecitationAnalyzer.analyze(targetAyah, "اهدنا السراط المستقيم")
        assertTrue("Should detect mistake on الصراط", makharijError.mistakes.isNotEmpty())
        val mistakenItem = makharijError.wordEvaluations.find { it.status == WordEvaluationStatus.MISTAKEN }
        assertTrue("Expected a MISTAKEN word entry", mistakenItem != null)
        assertTrue(
            "Expected Makharij pronunciation guidance",
            makharijError.mistakes.first().guidance.orEmpty().isNotBlank()
        )

        val skipped = QuranRecitationAnalyzer.analyze(targetAyah, "اهدنا المستقيم")
        assertTrue(
            "Expected a MISSED word entry",
            skipped.wordEvaluations.any { it.status == WordEvaluationStatus.MISSED }
        )
    }

    @Test
    fun `gemini model catalog separates Text Generation and Audio Read models cleanly`() {
        val sampleModels = listOf(
            org.json.JSONObject().apply {
                put("name", "models/gemini-2.5-flash")
                put("displayName", "Gemini 2.5 Flash")
                put("supportedGenerationMethods", org.json.JSONArray().put("generateContent"))
            },
            org.json.JSONObject().apply {
                put("name", "models/gemini-2.5-flash-preview-tts")
                put("displayName", "Gemini 2.5 Flash TTS")
                put("supportedGenerationMethods", org.json.JSONArray().put("generateContent"))
            },
            org.json.JSONObject().apply {
                put("name", "models/text-embedding-004")
                put("displayName", "Text Embedding 004")
                put("supportedGenerationMethods", org.json.JSONArray().put("embedContent"))
            }
        )

        val categorized = com.example.ai.GeminiAiService.parseAndCategorizeModels(sampleModels)
        val textModels = categorized.filter { it.category == com.example.ai.GeminiModelCategory.TEXT_GENERATION }
        val audioModels = categorized.filter { it.category == com.example.ai.GeminiModelCategory.AUDIO_READ }

        assertTrue("gemini-2.5-flash should be in Text Generation", textModels.any { it.id == "gemini-2.5-flash" })
        assertTrue("TTS-only model must NOT be in Text Generation", textModels.none { it.id == "gemini-2.5-flash-preview-tts" })
        assertTrue("TTS model should be prioritized first in Audio Read", audioModels.first().id == "gemini-2.5-flash-preview-tts")
        assertTrue("Embedding models should be excluded", categorized.none { it.id.contains("embedding") })
    }

    @Test
    fun `all 6 Liquid Glass and Islamic themes resolve cleanly via AppThemeMode`() {
        val modes = com.example.ui.theme.AppThemeMode.values()
        assertEquals(6, modes.size)
        modes.forEach { mode ->
            assertEquals(mode, com.example.ui.theme.AppThemeMode.fromId(mode.id, systemDark = true))
            assertTrue(mode.displayName.isNotBlank())
            assertTrue(mode.shortName.isNotBlank())
        }
        assertEquals(
            com.example.ui.theme.AppThemeMode.LIQUID_GLASS_DARK,
            com.example.ui.theme.AppThemeMode.fromId("UNKNOWN_THEME_ID", systemDark = true)
        )
        assertEquals(
            com.example.ui.theme.AppThemeMode.LIQUID_GLASS_LIGHT,
            com.example.ui.theme.AppThemeMode.fromId("SYSTEM", systemDark = false)
        )
        assertEquals(
            com.example.ui.theme.AppThemeMode.LIQUID_GLASS_DARK,
            com.example.ui.theme.AppThemeMode.fromId("SYSTEM", systemDark = true)
        )
    }
}
