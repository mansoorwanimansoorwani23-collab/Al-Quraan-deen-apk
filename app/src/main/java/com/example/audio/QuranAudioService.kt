package com.example.audio

import android.content.Context
import android.util.Log
import com.example.data.model.Ayah
import com.example.data.model.Surah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ReciterVoice(
    val id: String,
    val name: String,
    val arabicName: String,
    val cdnFolder: String,
    val styleDescription: String
)

object ReciterVoicePacks {
    val RECITERS = listOf(
        ReciterVoice(
            id = "mishary",
            name = "Mishary Rashid Alafasy",
            arabicName = "مشاري راشد العفاسي",
            cdnFolder = "Alafasy_128kbps",
            styleDescription = "Melodic, crystal clear, emotionally resonant Tajweed"
        ),
        ReciterVoice(
            id = "sudais",
            name = "Abdul Rahman Al-Sudais",
            arabicName = "عبد الرحمن السديس",
            cdnFolder = "Abdurrahmaan_As-Sudais_192kbps",
            styleDescription = "Grand Haram Makkah style, powerful & authoritative"
        ),
        ReciterVoice(
            id = "muaiqly",
            name = "Maher Al-Muaiqly",
            arabicName = "ماهر المعيقلي",
            cdnFolder = "MaherAlMuaiqly128kbps",
            styleDescription = "Calm, steady, and spiritually soothing tempo"
        ),
        ReciterVoice(
            id = "ghamdi",
            name = "Saad Al-Ghamdi",
            arabicName = "سعد الغامدي",
            cdnFolder = "Ghamadi_40kbps",
            styleDescription = "Gentle, reverent, and rhythmic flow"
        ),
        ReciterVoice(
            id = "husary",
            name = "Mahmoud Khalil Al-Husary",
            arabicName = "محمود خليل الحصري",
            cdnFolder = "Husary_128kbps",
            styleDescription = "Master standard classical Murattal & strict Tajweed"
        ),
        ReciterVoice(
            id = "shatri",
            name = "Abu Bakr Al-Shatri",
            arabicName = "أبو بكر الشاطري",
            cdnFolder = "Abu_Bakr_Ash-Shaatree_128kbps",
            styleDescription = "Deep, contemplative, and soulful recitation"
        )
    )

    val DEFAULT_RECITER = RECITERS[0]
}

sealed class QuranAudioResult {
    data class Success(val audioFile: File, val isFromAi: Boolean = false) : QuranAudioResult()
    data class Error(val message: String, val canRetry: Boolean = true) : QuranAudioResult()
}

object QuranAudioService {

    private const val TAG = "QuranAudioService"

    private val httpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 24
            maxRequestsPerHost = 8
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Audio Pipeline:
     * 1. Check local on-device cache for this Ayah and Reciter (instant 0ms hit).
     * 2. If not cached, stream & cache atomically with HTTP/2 keep-alive + CDN failover + retry.
     */
    suspend fun getAyahAudioFile(
        context: Context,
        surah: Surah,
        ayah: Ayah,
        reciter: ReciterVoice
    ): QuranAudioResult = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "quran_audio")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val cachedMp3 = File(cacheDir, "${reciter.id}_${surah.number}_${ayah.numberInSurah}.mp3")

        if (cachedMp3.exists() && cachedMp3.length() > 1000) {
            return@withContext QuranAudioResult.Success(cachedMp3, isFromAi = false)
        }

        try {
            return@withContext downloadAyahFromCdn(
                surahNumber = surah.number,
                ayahNumberInSurah = ayah.numberInSurah,
                reciter = reciter,
                outputFile = cachedMp3
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading recitation", e)
            return@withContext QuranAudioResult.Error(
                message = "Unable to load recitation for Ayah ${ayah.numberInSurah}. Please check your connection or retry.",
                canRetry = true
            )
        }
    }

    /**
     * Silently prefetches an upcoming Ayah into the local cache while the current Ayah plays,
     * eliminating buffering gaps between Ayahs.
     */
    suspend fun prefetchAyahAudio(
        context: Context,
        surahNumber: Int,
        ayahNumberInSurah: Int,
        reciter: ReciterVoice
    ) = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "quran_audio")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val cachedMp3 = File(cacheDir, "${reciter.id}_${surahNumber}_${ayahNumberInSurah}.mp3")
            if (cachedMp3.exists() && cachedMp3.length() > 1000) return@withContext
            downloadAyahFromCdn(surahNumber, ayahNumberInSurah, reciter, cachedMp3)
        } catch (_: Exception) {
            // Best-effort background prefetch
        }
    }

    private suspend fun downloadAyahFromCdn(
        surahNumber: Int,
        ayahNumberInSurah: Int,
        reciter: ReciterVoice,
        outputFile: File
    ): QuranAudioResult = withContext(Dispatchers.IO) {
        val sStr = String.format(java.util.Locale.US, "%03d", surahNumber)
        val aStr = String.format(java.util.Locale.US, "%03d", ayahNumberInSurah)

        val urls = listOf(
            "https://everyayah.com/data/${reciter.cdnFolder}/$sStr$aStr.mp3",
            "https://verses.quran.com/${reciter.cdnFolder}/$sStr$aStr.mp3"
        )

        var lastCode = 0
        for (url in urls) {
            for (attempt in 1..2) {
                try {
                    val request = Request.Builder().url(url).build()
                    httpClient.newCall(request).execute().use { response ->
                        lastCode = response.code
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null && bytes.size > 512) {
                                val tmpFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
                                FileOutputStream(tmpFile).use { fos -> fos.write(bytes) }
                                if (!tmpFile.renameTo(outputFile)) {
                                    tmpFile.copyTo(outputFile, overwrite = true)
                                    tmpFile.delete()
                                }
                                return@withContext QuranAudioResult.Success(outputFile, isFromAi = false)
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (attempt < 2) delay(250L)
                }
            }
        }

        return@withContext QuranAudioResult.Error("Failed to fetch recitation (HTTP $lastCode)")
    }
}
