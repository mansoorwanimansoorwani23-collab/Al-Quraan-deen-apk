package com.example.domain.recitation

import kotlin.math.max
import kotlin.math.min

enum class WordEvaluationStatus {
    CORRECT,
    MISTAKEN,
    MISSED
}

data class WordRecitationFeedback(
    val expectedWord: String,
    val spokenWord: String?,
    val status: WordEvaluationStatus,
    val explanation: String? = null,
    val guidance: String? = null
)

data class RecitationAnalysisResult(
    val accuracyPercent: Int,
    val matchedWordsCount: Int,
    val totalExpectedWords: Int,
    val spokenTranscript: String,
    val wordEvaluations: List<WordRecitationFeedback>,
    val mistakes: List<WordRecitationFeedback>,
    val overallSummary: String
)

/**
 * On-device Arabic Quran Recitation Analyzer.
 * Normalizes Uthmani Quran script and spoken Arabic transcripts, aligns word sequences via
 * dynamic programming, identifies mistaken or skipped words, and explains common Arabic
 * Makharij (articulation point) errors with clear pronunciation guidance.
 */
object QuranRecitationAnalyzer {

    // Uthmani diacritics, vowel signs, tatweel, and Waqf/pause annotation marks
    private val DIACRITICS_REGEX = Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u0640\u06DD\u06DE\u06E9]")
    private val NON_ARABIC_SPACE_REGEX = Regex("[^\u0621-\u064A\\s]")

    private val CONFUSABLE_PAIRS = listOf(
        Triple('ص', 'س', "Heavy Saad (ص) vs. Light Seen (س)" to "Raise the back of the tongue (Tafkheem) for ص so it sounds deep and full, unlike the light whistling س."),
        Triple('ض', 'د', "Heavy Daad (ض) vs. Light Daal (د)" to "Press the side edge of the tongue against the upper back molars for ض instead of the front tip used for د."),
        Triple('ط', 'ت', "Heavy Taa (ط) vs. Light Taa (ت)" to "Keep the back of the tongue elevated with a deep, emphatic sound for ط rather than the light, airy ت."),
        Triple('ظ', 'ذ', "Heavy Dhaa (ظ) vs. Light Dhaal (ذ)" to "Place the tongue tip gently against the upper front teeth and elevate the back of the tongue for ظ."),
        Triple('ظ', 'ز', "Interdental Dhaa (ظ) vs. Zay (ز)" to "Let the tongue tip touch the edge of the upper front teeth for ظ instead of keeping it behind the teeth as in ز."),
        Triple('ذ', 'ز', "Interdental Dhaal (ذ) vs. Zay (ز)" to "Touch the tip of the tongue gently to the upper front teeth for ذ rather than buzzing behind the teeth like ز."),
        Triple('ث', 'س', "Interdental Thaa (ث) vs. Seen (س)" to "Place the tip of the tongue softly against the upper front teeth for ث (like 'th' in 'think'), not a sharp س."),
        Triple('ق', 'ك', "Deep Qaaf (ق) vs. Kaaf (ك)" to "Articulate ق from the deepest part of the tongue against the soft palate, further back than ك."),
        Triple('ح', 'ه', "Throat Haa (ح) vs. Soft Haa (ه)" to "Produce ح from the middle of the throat with a clean, crisp breathy friction rather than the soft chest ه."),
        Triple('ع', 'ا', "Deep Ayn (ع) vs. Alif/Hamzah (أ)" to "Constrict the middle throat smoothly for ع instead of a sharp glottal stop أ."),
        Triple('خ', 'ح', "Khaa (خ) vs. Haa (ح)" to "Articulate خ from the top of the throat with gentle friction, whereas ح is smooth from the middle throat."),
        Triple('غ', 'خ', "Ghayn (غ) vs. Khaa (خ)" to "Voice the vibration at the top of the throat for غ, unlike the unvoiced breathy خ.")
    )

    fun normalizeArabicWord(word: String): String {
        val stripped = word
            .replace(DIACRITICS_REGEX, "")
            .replace("ٱ", "ا")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ى", "ي")
            .replace("ة", "ه")
            .replace("ؤ", "و")
            .replace("ئ", "ي")
            .replace(NON_ARABIC_SPACE_REGEX, "")
            .trim()
        return stripped
    }

    fun tokenizeQuranAyah(arabicText: String): List<String> {
        return arabicText
            .trim()
            .split(Regex("\\s+"))
            .filter { token ->
                // Filter out standalone Quranic stop signs like ۖ ۗ ۘ ۚ ۞
                normalizeArabicWord(token).isNotEmpty()
            }
    }

    fun analyze(expectedAyahArabic: String, spokenTranscript: String): RecitationAnalysisResult {
        val expectedRaw = tokenizeQuranAyah(expectedAyahArabic)
        val expectedNorm = expectedRaw.map { normalizeArabicWord(it) }

        val spokenRaw = spokenTranscript
            .trim()
            .split(Regex("\\s+"))
            .filter { normalizeArabicWord(it).isNotEmpty() }
        val spokenNorm = spokenRaw.map { normalizeArabicWord(it) }

        if (expectedRaw.isEmpty()) {
            return RecitationAnalysisResult(
                accuracyPercent = 0,
                matchedWordsCount = 0,
                totalExpectedWords = 0,
                spokenTranscript = spokenTranscript,
                wordEvaluations = emptyList(),
                mistakes = emptyList(),
                overallSummary = "Select an Ayah to begin recitation practice."
            )
        }

        if (spokenRaw.isEmpty()) {
            val missedAll = expectedRaw.map { word ->
                WordRecitationFeedback(
                    expectedWord = word,
                    spokenWord = null,
                    status = WordEvaluationStatus.MISSED,
                    explanation = "No Arabic speech was detected for \"$word\".",
                    guidance = "Speak clearly into the microphone at a calm Murattal pace and recite the full Ayah."
                )
            }
            return RecitationAnalysisResult(
                accuracyPercent = 0,
                matchedWordsCount = 0,
                totalExpectedWords = expectedRaw.size,
                spokenTranscript = "",
                wordEvaluations = missedAll,
                mistakes = missedAll,
                overallSummary = "No recitation was heard. Tap the microphone and recite the Ayah clearly."
            )
        }

        // Needleman-Wunsch / Levenshtein alignment between expected words and spoken words
        val n = expectedNorm.size
        val m = spokenNorm.size
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) dp[i][0] = i * 2
        for (j in 0..m) dp[0][j] = j * 2

        for (i in 1..n) {
            for (j in 1..m) {
                val sim = wordSimilarity(expectedNorm[i - 1], spokenNorm[j - 1])
                val subCost = when {
                    sim >= 0.99 -> 0
                    sim >= 0.55 -> 1
                    else -> 3
                }
                dp[i][j] = min(
                    dp[i - 1][j - 1] + subCost,
                    min(dp[i - 1][j] + 2, dp[i][j - 1] + 2)
                )
            }
        }

        // Backtrack alignment
        val evaluations = mutableListOf<WordRecitationFeedback>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                val sim = wordSimilarity(expectedNorm[i - 1], spokenNorm[j - 1])
                val subCost = when {
                    sim >= 0.99 -> 0
                    sim >= 0.55 -> 1
                    else -> 3
                }
                if (dp[i][j] == dp[i - 1][j - 1] + subCost) {
                    val expDisplay = expectedRaw[i - 1]
                    val expKey = expectedNorm[i - 1]
                    val spkDisplay = spokenRaw[j - 1]
                    val spkKey = spokenNorm[j - 1]

                    if (isWordMatch(expKey, spkKey)) {
                        evaluations.add(
                            WordRecitationFeedback(
                                expectedWord = expDisplay,
                                spokenWord = spkDisplay,
                                status = WordEvaluationStatus.CORRECT
                            )
                        )
                    } else {
                        val (explanation, guidance) = diagnoseWordMistake(expDisplay, expKey, spkDisplay, spkKey)
                        evaluations.add(
                            WordRecitationFeedback(
                                expectedWord = expDisplay,
                                spokenWord = spkDisplay,
                                status = WordEvaluationStatus.MISTAKEN,
                                explanation = explanation,
                                guidance = guidance
                            )
                        )
                    }
                    i--
                    j--
                    continue
                }
            }
            if (i > 0 && dp[i][j] == dp[i - 1][j] + 2) {
                val expDisplay = expectedRaw[i - 1]
                evaluations.add(
                    WordRecitationFeedback(
                        expectedWord = expDisplay,
                        spokenWord = null,
                        status = WordEvaluationStatus.MISSED,
                        explanation = "Word \"$expDisplay\" was skipped or not recognized clearly.",
                        guidance = "Pause briefly before \"$expDisplay\" and pronounce every syllable distinctly from start to finish."
                    )
                )
                i--
            } else {
                // Extra spoken word ignored in expected word list
                j--
            }
        }

        evaluations.reverse()

        val matched = evaluations.count { it.status == WordEvaluationStatus.CORRECT }
        val accuracy = ((matched.toDouble() / expectedRaw.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        val mistakes = evaluations.filter { it.status != WordEvaluationStatus.CORRECT }

        val summary = when {
            mistakes.isEmpty() -> "MashaAllah! Your recitation matched all ${expectedRaw.size} words of this Ayah clearly."
            accuracy >= 80 -> "Very good recitation ($accuracy%). Review the ${mistakes.size} highlighted word(s) below and try again."
            accuracy >= 50 -> "Good effort ($accuracy%). Focus on the ${mistakes.size} highlighted word(s) and practice slowly."
            else -> "Keep practicing ($accuracy%). Listen to the reference Ayah first, then recite slowly word by word."
        }

        return RecitationAnalysisResult(
            accuracyPercent = accuracy,
            matchedWordsCount = matched,
            totalExpectedWords = expectedRaw.size,
            spokenTranscript = spokenTranscript,
            wordEvaluations = evaluations,
            mistakes = mistakes,
            overallSummary = summary
        )
    }

    private fun hasConfusableMakharijSwap(expectedNorm: String, spokenNorm: String): Boolean {
        for ((a, b, _) in CONFUSABLE_PAIRS) {
            val expHasA = expectedNorm.contains(a) && !spokenNorm.contains(a) && spokenNorm.contains(b)
            val expHasB = expectedNorm.contains(b) && !spokenNorm.contains(b) && spokenNorm.contains(a)
            if (expHasA || expHasB) return true
        }
        return false
    }

    private fun isWordMatch(expectedNorm: String, spokenNorm: String): Boolean {
        if (expectedNorm == spokenNorm) return true
        // Never excuse a known Makharij articulation error (e.g. ص vs س, ض vs د, ط vs ت, ق vs ك)
        if (hasConfusableMakharijSwap(expectedNorm, spokenNorm)) return false
        // Allow Uthmani orthography differences like omitted medial alif (الرحمن vs الرحمان, ذلك vs ذالك, السماوات vs السموت)
        val expNoAlif = expectedNorm.replace("ا", "")
        val spkNoAlif = spokenNorm.replace("ا", "")
        if (expNoAlif.isNotEmpty() && expNoAlif == spkNoAlif) return true
        // Allow small STT variance on longer words (>= 7 chars with 1 non-Makharij char diff)
        if (expectedNorm.length >= 7 && levenshtein(expectedNorm, spokenNorm) <= 1) return true
        return false
    }

    private fun diagnoseWordMistake(
        expectedDisplay: String,
        expectedNorm: String,
        spokenDisplay: String,
        spokenNorm: String
    ): Pair<String, String> {
        for ((a, b, info) in CONFUSABLE_PAIRS) {
            val expHasA = expectedNorm.contains(a) && !spokenNorm.contains(a) && spokenNorm.contains(b)
            val expHasB = expectedNorm.contains(b) && !spokenNorm.contains(b) && spokenNorm.contains(a)
            if (expHasA || expHasB) {
                return Pair(
                    "Pronounced \"$spokenDisplay\" instead of \"$expectedDisplay\" (${info.first}).",
                    info.second
                )
            }
        }

        return if (spokenNorm.length < expectedNorm.length) {
            Pair(
                "Word \"$expectedDisplay\" sounded shortened as \"$spokenDisplay\".",
                "Slow down on \"$expectedDisplay\" and pronounce every letter and final vowel clearly without rushing."
            )
        } else {
            Pair(
                "Recognized \"$spokenDisplay\" where the Ayah has \"$expectedDisplay\".",
                "Read \"$expectedDisplay\" carefully letter-by-letter, paying attention to its harakat (vowels) and articulation points (Makharij)."
            )
        }
    }

    private fun wordSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (levenshtein(a, b).toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val prev = IntArray(n + 1) { it }
        val curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            curr.copyInto(prev)
        }
        return prev[n]
    }
}
