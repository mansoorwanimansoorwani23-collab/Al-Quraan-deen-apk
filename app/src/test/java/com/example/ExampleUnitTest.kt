package com.example

import com.example.audio.AdhanAudioPlayer
import com.example.audio.ReciterVoicePacks
import com.example.data.local.IslamicDataSource
import com.example.data.local.LearnSalahData
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun reciterVoicePacks_containsAuthenticReciters() {
        val reciters = ReciterVoicePacks.RECITERS
        assertTrue(reciters.isNotEmpty())
        assertTrue(reciters.any { it.id == "mishary" })
        assertTrue(reciters.any { it.id == "sudais" })
        assertTrue(reciters.any { it.id == "muaiqly" })
        assertTrue(reciters.any { it.id == "ghamdi" })
        assertTrue(reciters.any { it.id == "husary" })
        assertEquals("mishary", ReciterVoicePacks.DEFAULT_RECITER.id)
    }

    @Test
    fun adhanVoices_onlyContainAuthenticRealAdhans() {
        val voices = AdhanAudioPlayer.ADHAN_VOICES
        assertTrue(voices.size >= 5)
        assertTrue(voices.none { it.name.contains("Beep", ignoreCase = true) || it.name.contains("Silent", ignoreCase = true) })
        assertTrue(voices.any { it.name.contains("Makkah", ignoreCase = true) })
        assertTrue(voices.any { it.name.contains("Fajr", ignoreCase = true) })
    }

    @Test
    fun learnSalahData_containsFullBeginnerGuide() {
        assertEquals(12, LearnSalahData.SALAH_STEPS.size)
        assertTrue(LearnSalahData.WUDU_STEPS.size >= 7)
        assertEquals(5, LearnSalahData.FIVE_PRAYERS_RAKAH_TABLE.size)
    }

    @Test
    fun islamicDataSource_containsAll114Surahs30JuzAndKashmirCities() {
        assertEquals(114, IslamicDataSource.SURAHS.size)
        assertEquals(30, IslamicDataSource.JUZ_LIST.size)
        assertTrue(IslamicDataSource.POPULAR_CITIES.any { it.name.contains("Srinagar", ignoreCase = true) })
        assertTrue(IslamicDataSource.POPULAR_CITIES.any { it.name.contains("Pushpora", ignoreCase = true) })
    }
}
