package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.IslamicDataSource
import com.example.data.model.Ayah
import com.example.data.model.Surah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * 100% Offline Complete Holy Quran Repository.
 * Loads all 114 Surahs, 30 Juz, and all 6,236 authentic Uthmani Arabic Ayahs
 * + Sahih International English translations directly from the local bundled asset
 * (`assets/quran_complete.tsv.gz`, which AGP decompresses to `quran_complete.tsv` in APKs)
 * with zero API calls required.
 */
class QuranRepository(private val context: Context) {

    companion object {
        private const val TAG = "QuranRepository"
        private val OFFLINE_ASSET_CANDIDATES = listOf(
            "quran_complete.tsv",
            "quran_complete.tsv.gz"
        )

        @Volatile
        private var isLoaded = false
        private val loadMutex = Mutex()
        private val surahAyahsMap = HashMap<Int, List<Ayah>>(128)
        private val juzAyahsMap = HashMap<Int, List<Ayah>>(32)
    }

    /**
     * Opens the bundled offline Quran asset regardless of whether AGP/AAPT2 decompressed
     * `quran_complete.tsv.gz` into `quran_complete.tsv` (standard Android APK behavior)
     * or left the `.gz` file intact (e.g. JVM unit tests).
     */
    private fun openOfflineQuranStream(): InputStream {
        var lastError: FileNotFoundException? = null
        var rawStream: InputStream? = null

        for (candidate in OFFLINE_ASSET_CANDIDATES) {
            try {
                rawStream = context.assets.open(candidate)
                break
            } catch (e: FileNotFoundException) {
                lastError = e
            }
        }

        val stream = rawStream ?: throw (lastError ?: FileNotFoundException("Offline Quran asset not found"))
        val buffered = BufferedInputStream(stream, 8192)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()

        val isGzip = (b1 == 0x1F && b2 == 0x8B)
        return if (isGzip) GZIPInputStream(buffered) else buffered
    }

    private suspend fun ensureOfflineQuranLoaded() {
        if (isLoaded) return
        loadMutex.withLock {
            if (isLoaded) return
            withContext(Dispatchers.IO) {
                try {
                    val tempSurahMap = HashMap<Int, MutableList<Ayah>>(128)
                    val tempJuzMap = HashMap<Int, MutableList<Ayah>>(32)

                    openOfflineQuranStream().use { decodedStream ->
                        BufferedReader(InputStreamReader(decodedStream, Charsets.UTF_8)).use { reader ->
                            var line = reader.readLine()
                            while (line != null) {
                                val parts = line.split('\t')
                                if (parts.size >= 7) {
                                    val surahNum = parts[0].toIntOrNull() ?: 1
                                    val ayahNum = parts[1].toIntOrNull() ?: 1
                                    val overallNum = parts[2].toIntOrNull() ?: 1
                                    val juzNum = parts[3].toIntOrNull() ?: 1
                                    val pageNum = parts[4].toIntOrNull() ?: 1
                                    val arabicText = parts[5].removePrefix("\uFEFF")
                                    val englishText = parts[6]

                                    // Preserve curated transliteration when available
                                    val curatedTranslit = IslamicDataSource.AYAHS_BY_SURAH[surahNum]
                                        ?.find { it.numberInSurah == ayahNum }
                                        ?.transliteration ?: ""

                                    val ayah = Ayah(
                                        numberInSurah = ayahNum,
                                        overallNumber = overallNum,
                                        surahNumber = surahNum,
                                        arabicText = arabicText,
                                        englishTranslation = englishText,
                                        transliteration = curatedTranslit,
                                        juz = juzNum,
                                        page = pageNum
                                    )

                                    tempSurahMap.getOrPut(surahNum) { ArrayList() }.add(ayah)
                                    tempJuzMap.getOrPut(juzNum) { ArrayList() }.add(ayah)
                                }
                                line = reader.readLine()
                            }
                        }
                    }

                    surahAyahsMap.clear()
                    surahAyahsMap.putAll(tempSurahMap)
                    juzAyahsMap.clear()
                    juzAyahsMap.putAll(tempJuzMap)
                    isLoaded = true
                    Log.i(TAG, "Offline Quran loaded locally: ${surahAyahsMap.size} Surahs, ${juzAyahsMap.size} Juz")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed loading bundled offline Quran asset", e)
                }
            }
        }
    }

    /**
     * Retrieves all authentic Ayahs for a Surah completely offline.
     */
    suspend fun getAyahsForSurah(surah: Surah): List<Ayah> = withContext(Dispatchers.IO) {
        ensureOfflineQuranLoaded()
        surahAyahsMap[surah.number]?.takeIf { it.isNotEmpty() }
            ?: IslamicDataSource.getAyahsForSurah(surah)
    }

    /**
     * Retrieves all authentic Ayahs for a Surah by Surah number completely offline.
     */
    suspend fun getAyahsForSurahNumber(surahNumber: Int): List<Ayah> {
        val surah = IslamicDataSource.SURAHS.find { it.number == surahNumber } ?: IslamicDataSource.SURAHS[0]
        return getAyahsForSurah(surah)
    }

    /**
     * Retrieves all authentic Ayahs for a Juz (1..30) sequentially completely offline.
     */
    suspend fun getAyahsForJuz(juzNumber: Int): List<Ayah> = withContext(Dispatchers.IO) {
        ensureOfflineQuranLoaded()
        juzAyahsMap[juzNumber]?.takeIf { it.isNotEmpty() }
            ?: IslamicDataSource.getAyahsForJuz(juzNumber)
    }

    /**
     * Searches across all 6,236 offline Ayahs (Arabic or English) with zero network required.
     */
    suspend fun searchOfflineAyahs(query: String, limit: Int = 40): List<Ayah> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        ensureOfflineQuranLoaded()
        val matches = ArrayList<Ayah>()
        for (sNum in 1..114) {
            val list = surahAyahsMap[sNum] ?: continue
            for (ayah in list) {
                if (ayah.englishTranslation.contains(q, ignoreCase = true) ||
                    ayah.arabicText.contains(q)
                ) {
                    matches.add(ayah)
                    if (matches.size >= limit) return@withContext matches
                }
            }
        }
        matches
    }
}
