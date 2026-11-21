package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Bundled local Google Fonts (offline-first, no runtime font downloads)
val BrandDisplayFontFamily = FontFamily(
    Font(R.font.cinzel, FontWeight.Normal),
    Font(R.font.cinzel, FontWeight.SemiBold),
    Font(R.font.cinzel, FontWeight.Bold)
)

val BodyUiFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val AmiriArabicFontFamily = FontFamily(
    Font(R.font.amiri, FontWeight.Normal),
    Font(R.font.amiri, FontWeight.Medium),
    Font(R.font.amiri, FontWeight.Bold)
)

val ScheherazadeArabicFontFamily = FontFamily(
    Font(R.font.scheherazade_new, FontWeight.Normal),
    Font(R.font.scheherazade_new, FontWeight.Medium),
    Font(R.font.scheherazade_new, FontWeight.Bold)
)

val NotoNaskhArabicFontFamily = FontFamily(
    Font(R.font.noto_naskh_arabic, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic, FontWeight.Medium),
    Font(R.font.noto_naskh_arabic, FontWeight.Bold)
)

val LateefArabicFontFamily = FontFamily(
    Font(R.font.lateef, FontWeight.Normal),
    Font(R.font.lateef, FontWeight.Medium),
    Font(R.font.lateef, FontWeight.Bold)
)

// Natural Islamic Typography with enhanced readability for Arabic script and Latin UI
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = BrandDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.2.sp
    ),
    displayMedium = TextStyle(
        fontFamily = BrandDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.2.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.1.sp
    ),
    titleLarge = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    )
)

// Dedicated Arabic text styling for Quran, Hadith, Duas, and Prayer headers
val QuranArabicTextStyle = TextStyle(
    fontFamily = ScheherazadeArabicFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 26.sp,
    lineHeight = 48.sp,
    letterSpacing = 0.sp
)

val HadithArabicTextStyle = TextStyle(
    fontFamily = AmiriArabicFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 38.sp,
    letterSpacing = 0.sp
)

enum class QuranFontOption(
    val id: String,
    val displayName: String,
    val styleDescription: String,
    val fontFamily: FontFamily
) {
    UTHMANI("Uthmani", "Uthmani Hafs (Madinah)", "Authentic Madinah Mushaf Calligraphy", ScheherazadeArabicFontFamily),
    AMIRI("Amiri", "Amiri Classical Naskh", "Elegant Arabic Typography & Clear Vowels", AmiriArabicFontFamily),
    SCHEHERAZADE("Scheherazade", "Scheherazade New", "Traditional Full-Diacritic Arabic Script", ScheherazadeArabicFontFamily),
    INDOPAK("IndoPak", "Indo-Pak Naskh (Lateef)", "Prominent Subcontinent Nastaliq-Naskh Flow", LateefArabicFontFamily),
    STANDARD("Standard", "Noto Naskh Arabic", "High-Legibility Modern Arabic Type", NotoNaskhArabicFontFamily)
}

fun resolveQuranFontFamily(fontId: String): FontFamily {
    return QuranFontOption.entries.find { it.id.equals(fontId, ignoreCase = true) }?.fontFamily
        ?: ScheherazadeArabicFontFamily
}
