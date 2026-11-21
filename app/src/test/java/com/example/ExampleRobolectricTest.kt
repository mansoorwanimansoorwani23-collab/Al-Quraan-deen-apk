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
        assertTrue("First ayah should not start with UTF-8 BOM", !fatihah.first().arabicText.startsWith("\uFEFF"))
        assertEquals(286, baqarah.size)
        assertTrue("Ayat al-Kursi should be present offline", baqarah[254].arabicText.contains("الْقَيُّومُ") || baqarah[254].arabicText.contains("ٱلْقَيُّومُ"))
        assertEquals(6, nas.size)
        assertTrue("Juz 30 should contain over 500 ayahs offline", juz30.size > 500)
        val totalAyahs = IslamicDataSource.SURAHS.sumOf { repo.getAyahsForSurah(it).size }
        assertEquals(6236, totalAyahs)
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

    @Test
    fun `gemini voice options include clear Male and Female labels and prioritize 3_1 and 3_8 flash`() {
        val voices = com.example.ai.GeminiAiService.VOICE_OPTIONS
        assertTrue(voices.any { it.gender == "Male" })
        assertTrue(voices.any { it.gender == "Female" })
        assertTrue(voices.all { it.gender == "Male" || it.gender == "Female" })

        val sampleModels = listOf(
            org.json.JSONObject().apply {
                put("name", "models/gemini-2.5-flash")
                put("displayName", "Gemini 2.5 Flash")
                put("supportedGenerationMethods", org.json.JSONArray().put("generateContent"))
            },
            org.json.JSONObject().apply {
                put("name", "models/gemini-3.1-flash")
                put("displayName", "Gemini 3.1 Flash")
                put("supportedGenerationMethods", org.json.JSONArray().put("generateContent"))
            },
            org.json.JSONObject().apply {
                put("name", "models/gemini-3.8-flash")
                put("displayName", "Gemini 3.8 Flash")
                put("supportedGenerationMethods", org.json.JSONArray().put("generateContent"))
            }
        )
        val parsed = com.example.ai.GeminiAiService.parseAndCategorizeModels(sampleModels)
        val textList = parsed.filter { it.category == com.example.ai.GeminiModelCategory.TEXT_GENERATION }
        val audioList = parsed.filter { it.category == com.example.ai.GeminiModelCategory.AUDIO_READ }
        assertEquals("gemini-3.1-flash", textList.first().id)
        assertEquals("gemini-3.8-flash", audioList.first().id)
    }

    @Test
    fun `fast voice chunking splits first chunk short for rapid playback and SSE parser skips thought parts`() {
        val longReply = "**Assalamu Alaikum!** This is the first short sentence for fast voice start. " +
            "Here is a second sentence with *markdown* formatting and `code` marks that should be cleaned properly. " +
            "और यह हिंदी का वाक्य है जो तीसरे हिस्से में आसानी से विभाजित होना चाहिए। " +
            "Finally, this fourth sentence ensures multi-chunk pipelining works seamlessly without cutting words."

        val chunks = com.example.ai.GeminiAiService.splitIntoSpeechChunks(longReply)
        assertTrue("Expected at least 2 chunks for pipelined speech", chunks.size >= 2)
        assertTrue("First chunk must be short (<=150 chars) for fast first audio", chunks.first().length <= 150)
        assertTrue("Markdown symbols should be stripped", chunks.none { it.contains("**") || it.contains("`") })

        val sseJson = """
            {
              "candidates": [{
                "content": {
                  "parts": [
                    { "thought": true, "text": "Internal reasoning..." },
                    { "text": "Bismillah! " },
                    { "text": "Here is your answer." }
                  ]
                }
              }]
            }
        """.trimIndent()
        val delta = com.example.ai.GeminiAiService.extractTextDeltaFromJson(sseJson)
        assertEquals("Bismillah! Here is your answer.", delta)
    }

    @Test
    fun `speech sanitizer strips slashes symbols and markdown so TTS never speaks slash or formatting noise`() {
        val noisy = "### Morning / Evening Dua\n**Step 1:** Read Surah Al-Ikhlas / Al-Falaq | Al-Nas (3 times) [Ref: Sahih] ✨\n" +
            "- Check https://example.com/test\\path for more info ---"
        val cleaned = com.example.ai.GeminiAiService.cleanTextForSpeech(noisy)

        assertTrue("Must not contain forward slash", !cleaned.contains("/"))
        assertTrue("Must not contain backslash", !cleaned.contains("\\"))
        assertTrue("Must not contain pipe or brackets", !cleaned.contains("|") && !cleaned.contains("[") && !cleaned.contains("]"))
        assertTrue("Must not contain markdown asterisks or hashes", !cleaned.contains("*") && !cleaned.contains("#"))
        assertTrue("Must not contain URLs", !cleaned.contains("https://"))
        assertTrue("Must not contain 'Read aloud' prefix", !cleaned.contains("Read aloud", ignoreCase = true))
        assertTrue("Must preserve meaningful spoken words", cleaned.contains("Morning") && cleaned.contains("Evening Dua"))
    }
}
