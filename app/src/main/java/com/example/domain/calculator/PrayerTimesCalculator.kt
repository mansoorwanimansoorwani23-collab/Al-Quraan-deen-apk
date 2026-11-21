package com.example.domain.calculator

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.*

enum class CalculationMethod(
    val displayName: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val isIshaFixedMinutes: Boolean = false,
    val ishaMinutesAfterMaghrib: Double = 0.0
) {
    MUSLIM_WORLD_LEAGUE("Muslim World League", 18.0, 17.0),
    ISNA("Islamic Society of North America (ISNA)", 15.0, 15.0),
    EGYPT("Egyptian General Authority of Survey", 19.5, 17.5),
    MAKKAH("Umm Al-Qura University, Makkah", 18.5, 0.0, true, 90.0),
    KARACHI("University of Islamic Sciences, Karachi", 18.0, 18.0),
    TEHRAN("Institute of Geophysics, Univ. of Tehran", 17.7, 14.0),
    GULF("Gulf Region / Dubai", 19.5, 0.0, true, 90.0),
    SINGAPORE("MUIS Singapore / Malaysia", 20.0, 18.0),
    DIYANET("Diyanet Turkey", 18.0, 17.0)
}

enum class Madhhab(val displayName: String, val shadowMultiplier: Double) {
    STANDARD("Standard (Shafi'i, Maliki, Hanbali)", 1.0),
    HANAFI("Hanafi", 2.0)
}

enum class HighLatitudeRule(val displayName: String) {
    MIDDLE_OF_NIGHT("Middle of the Night"),
    SEVENTH_OF_NIGHT("One-Seventh of the Night"),
    ANGLE_BASED("Angle Based")
}

data class PrayerTimesResult(
    val fajr: Date,
    val sunrise: Date,
    val dhuhr: Date,
    val asr: Date,
    val maghrib: Date,
    val isha: Date,
    val midnight: Date,
    val lastThirdOfNight: Date,
    val qiyam: Date,
    val currentOrNextPrayer: PrayerType,
    val nextPrayerTime: Date,
    val timeRemainingMillis: Long
)

enum class PrayerType(val displayName: String, val arabicName: String) {
    FAJR("Fajr", "الفجر"),
    SUNRISE("Sunrise", "الشروق"),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء"),
    QIYAM("Qiyam", "القيام")
}

object PrayerTimesCalculator {

    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Calendar = Calendar.getInstance(),
        method: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
        madhhab: Madhhab = Madhhab.STANDARD,
        highLatRule: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        timeZone: TimeZone = date.timeZone
    ): PrayerTimesResult {
        val localCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = date.timeInMillis
        }
        val year = localCal.get(Calendar.YEAR)
        val month = localCal.get(Calendar.MONTH) + 1
        val day = localCal.get(Calendar.DAY_OF_MONTH)

        // Julian date adjusted to local longitude noon for high-precision local solar coordinates
        val julianDate = calculateJulianDate(year, month, day) - (longitude / 360.0)
        val timezoneOffset = timeZone.getOffset(localCal.timeInMillis) / (1000.0 * 3600.0)

        // Solar coordinates at local solar noon (d = days since J2000.0 + 0.5 day offset to noon)
        val d = (julianDate + 0.5) - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(degToRad(g)) + 0.020 * sin(degToRad(2.0 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = fixAngle(radToDeg(atan2(cos(degToRad(e)) * sin(degToRad(l)), cos(degToRad(l))))) / 15.0
        val declination = radToDeg(asin(sin(degToRad(e)) * sin(degToRad(l))))

        // Normalize Equation of Time cleanly across the 0h/24h wrap boundary
        var equationOfTime = (q / 15.0) - ra
        while (equationOfTime > 12.0) equationOfTime -= 24.0
        while (equationOfTime < -12.0) equationOfTime += 24.0

        // Mid-Day (Dhuhr / Solar Transit)
        val solarNoon = fixHour(12.0 + timezoneOffset - (longitude / 15.0) - equationOfTime)

        // Sunrise & Sunset angles (sun center 0.8333 deg below horizon due to atmospheric refraction & solar radius)
        val sunAlt = -0.8333
        val sunriseHour = solarNoon - sunAngleHour(latitude, declination, sunAlt)
        val sunsetHour = solarNoon + sunAngleHour(latitude, declination, sunAlt)

        // Fajr (sun below horizon by fajrAngle)
        var fajrHour = solarNoon - sunAngleHour(latitude, declination, -method.fajrAngle)

        // Asr (sun ABOVE horizon when shadow = shadowMultiplier * height + noon shadow)
        val asrAlt = radToDeg(atan(1.0 / (madhhab.shadowMultiplier + tan(degToRad(abs(latitude - declination))))))
        val asrHour = solarNoon + sunAngleHour(latitude, declination, asrAlt)

        // Maghrib (at sunset)
        val maghribHour = sunsetHour

        // Isha
        var ishaHour = if (method.isIshaFixedMinutes) {
            maghribHour + (method.ishaMinutesAfterMaghrib / 60.0)
        } else {
            solarNoon + sunAngleHour(latitude, declination, -method.ishaAngle)
        }

        // High-latitude fallback adjustment if twilight never reaches target depression angle
        val nightFraction = (24.0 - sunsetHour + sunriseHour)
        if (fajrHour.isNaN() || fajrHour < 0.0 || (sunriseHour - fajrHour) > (nightFraction / 2.0)) {
            val portion = when (highLatRule) {
                HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
                HighLatitudeRule.SEVENTH_OF_NIGHT -> 1.0 / 7.0
                HighLatitudeRule.ANGLE_BASED -> method.fajrAngle / 60.0
            }
            fajrHour = sunriseHour - (portion * nightFraction)
        }

        if (ishaHour.isNaN() || ishaHour > 24.0 || (ishaHour - sunsetHour) > (nightFraction / 2.0)) {
            val portion = when (highLatRule) {
                HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
                HighLatitudeRule.SEVENTH_OF_NIGHT -> 1.0 / 7.0
                HighLatitudeRule.ANGLE_BASED -> method.ishaAngle / 60.0
            }
            ishaHour = sunsetHour + (portion * nightFraction)
        }

        val fajrDate = hourToDate(localCal, fajrHour, timeZone)
        val sunriseDate = hourToDate(localCal, sunriseHour, timeZone)
        val dhuhrDate = hourToDate(localCal, solarNoon + (1.0 / 60.0), timeZone) // 1 min safety margin past zenith
        val asrDate = hourToDate(localCal, asrHour, timeZone)
        val maghribDate = hourToDate(localCal, maghribHour + (1.0 / 60.0), timeZone)
        val ishaDate = hourToDate(localCal, ishaHour, timeZone)

        // Qiyam (last third of the night) and Islamic Midnight
        val nextFajrMillis = fajrDate.time + 24 * 3600 * 1000L
        val nightDuration = (nextFajrMillis - maghribDate.time).coerceAtLeast(1000L)
        val midnightDate = Date(maghribDate.time + (nightDuration / 2))
        val qiyamDate = Date(maghribDate.time + (2 * nightDuration / 3))

        // Determine current or next prayer
        val now = System.currentTimeMillis()
        val prayerList = listOf(
            PrayerType.FAJR to fajrDate,
            PrayerType.SUNRISE to sunriseDate,
            PrayerType.DHUHR to dhuhrDate,
            PrayerType.ASR to asrDate,
            PrayerType.MAGHRIB to maghribDate,
            PrayerType.ISHA to ishaDate
        )

        var nextPrayer = PrayerType.FAJR
        var nextTime = Date(fajrDate.time + 24 * 3600 * 1000L)

        for ((type, time) in prayerList) {
            if (time.time > now) {
                nextPrayer = type
                nextTime = time
                break
            }
        }

        val remaining = max(0L, nextTime.time - now)

        return PrayerTimesResult(
            fajr = fajrDate,
            sunrise = sunriseDate,
            dhuhr = dhuhrDate,
            asr = asrDate,
            maghrib = maghribDate,
            isha = ishaDate,
            midnight = midnightDate,
            lastThirdOfNight = qiyamDate,
            qiyam = qiyamDate,
            currentOrNextPrayer = nextPrayer,
            nextPrayerTime = nextTime,
            timeRemainingMillis = remaining
        )
    }

    fun applyCustomTimes(
        calculated: PrayerTimesResult,
        useCustom: Boolean,
        customFajr: String,
        customDhuhr: String,
        customAsr: String,
        customMaghrib: String,
        customIsha: String,
        timeZone: TimeZone = TimeZone.getDefault(),
        baseDate: Calendar = Calendar.getInstance(timeZone)
    ): PrayerTimesResult {
        if (!useCustom) return calculated

        val fajrDate = parseCustomTimeToDate(baseDate, customFajr, calculated.fajr, timeZone)
        val dhuhrDate = parseCustomTimeToDate(baseDate, customDhuhr, calculated.dhuhr, timeZone)
        val asrDate = parseCustomTimeToDate(baseDate, customAsr, calculated.asr, timeZone)
        val maghribDate = parseCustomTimeToDate(baseDate, customMaghrib, calculated.maghrib, timeZone)
        val ishaDate = parseCustomTimeToDate(baseDate, customIsha, calculated.isha, timeZone)

        val nextFajrMillis = fajrDate.time + 24 * 3600 * 1000L
        val nightDuration = (nextFajrMillis - maghribDate.time).coerceAtLeast(1000L)
        val midnightDate = Date(maghribDate.time + (nightDuration / 2))
        val qiyamDate = Date(maghribDate.time + (2 * nightDuration / 3))

        val now = System.currentTimeMillis()
        val prayerList = listOf(
            PrayerType.FAJR to fajrDate,
            PrayerType.SUNRISE to calculated.sunrise,
            PrayerType.DHUHR to dhuhrDate,
            PrayerType.ASR to asrDate,
            PrayerType.MAGHRIB to maghribDate,
            PrayerType.ISHA to ishaDate
        )

        var nextPrayer = PrayerType.FAJR
        var nextTime = Date(fajrDate.time + 24 * 3600 * 1000L)

        for ((type, time) in prayerList) {
            if (time.time > now) {
                nextPrayer = type
                nextTime = time
                break
            }
        }

        val remaining = max(0L, nextTime.time - now)

        return calculated.copy(
            fajr = fajrDate,
            dhuhr = dhuhrDate,
            asr = asrDate,
            maghrib = maghribDate,
            isha = ishaDate,
            midnight = midnightDate,
            lastThirdOfNight = qiyamDate,
            qiyam = qiyamDate,
            currentOrNextPrayer = nextPrayer,
            nextPrayerTime = nextTime,
            timeRemainingMillis = remaining
        )
    }

    private fun parseCustomTimeToDate(baseCal: Calendar, timeStr: String, fallback: Date, timeZone: TimeZone): Date {
        if (timeStr.isBlank()) return fallback
        try {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) {
                val h = parts[0].trim().toInt()
                val m = parts[1].trim().take(2).toInt()
                val c = Calendar.getInstance(timeZone)
                c.timeInMillis = baseCal.timeInMillis
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                return c.time
            }
        } catch (_: Exception) {}
        return fallback
    }

    private fun calculateJulianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunAngleHour(lat: Double, dec: Double, angle: Double): Double {
        val cosH = (sin(degToRad(angle)) - sin(degToRad(lat)) * sin(degToRad(dec))) /
            (cos(degToRad(lat)) * cos(degToRad(dec)))
        if (cosH > 1.0 || cosH < -1.0) return Double.NaN
        return radToDeg(acos(cosH)) / 15.0
    }

    private fun hourToDate(baseDate: Calendar, hourFraction: Double, timeZone: TimeZone): Date {
        val c = Calendar.getInstance(timeZone)
        c.timeInMillis = baseDate.timeInMillis
        val validHour = fixHour(hourFraction)
        val hour = validHour.toInt()
        val minuteFrac = (validHour - hour) * 60.0
        val minute = minuteFrac.toInt()
        val second = ((minuteFrac - minute) * 60.0).toInt()

        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, second)
        c.set(Calendar.MILLISECOND, 0)
        return c.time
    }

    private fun degToRad(deg: Double) = deg * (Math.PI / 180.0)
    private fun radToDeg(rad: Double) = rad * (180.0 / Math.PI)
    private fun fixAngle(angle: Double): Double {
        var a = angle - 360.0 * floor(angle / 360.0)
        if (a < 0) a += 360.0
        return a
    }
    private fun fixHour(hour: Double): Double {
        var h = hour - 24.0 * floor(hour / 24.0)
        if (h < 0) h += 24.0
        return h
    }
}
