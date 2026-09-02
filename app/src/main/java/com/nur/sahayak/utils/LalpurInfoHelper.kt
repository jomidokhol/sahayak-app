package com.nur.sahayak.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nur.sahayak.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlin.math.*

object LalpurInfoHelper {

    private const val LALPUR_LAT = 24.1833
    private const val LALPUR_LON = 88.9833
    private const val TIMEZONE = 6.0

    data class PrayerSchedule(
        val fajr: String,
        val dhuhr: String,
        val asr: String,
        val maghrib: String,
        val isha: String,
        val currentWaqtTitle: String,
        val currentWaqtEndTime: String,
        val nextWaqtTitle: String,
        val nextWaqtStartTime: String,
        val isDaytime: Boolean
    )

    fun calculateLalpurPrayers(calendar: Calendar = Calendar.getInstance()): PrayerSchedule {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val b = 2.0 * Math.PI * (dayOfYear - 81) / 365.0
        val eot = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val decl = 23.45 * sin(b) * Math.PI / 180.0

        val latRad = LALPUR_LAT * Math.PI / 180.0
        val noon = 12.0 - (LALPUR_LON / 15.0 - TIMEZONE) - eot / 60.0

        fun hourAngle(angle: Double): Double {
            val aRad = angle * Math.PI / 180.0
            val cosHA = (sin(aRad) - sin(latRad) * sin(decl)) / (cos(latRad) * cos(decl))
            val clamped = cosHA.coerceIn(-1.0, 1.0)
            return acos(clamped) * 180.0 / Math.PI / 15.0
        }

        fun asrHourAngle(): Double {
            val shadowFactor = 2.0 // Hanafi standard in Bangladesh
            val asrAngle = atan(1.0 / (shadowFactor + tan(abs(latRad - decl)))) * 180.0 / Math.PI
            return hourAngle(asrAngle)
        }

        val fajrHA = hourAngle(-18.0)
        val sunriseHA = hourAngle(-0.833)
        val asrHA = asrHourAngle()
        val maghribHA = sunriseHA
        val ishaHA = hourAngle(-18.0)

        val fajrTime = noon - fajrHA
        val sunriseTime = noon - sunriseHA
        val dhuhrTime = noon + (4.0 / 60.0)
        val asrTime = noon + asrHA
        val maghribTime = noon + maghribHA
        val ishaTime = noon + ishaHA

        fun formatHours(hours: Double): String {
            val normalized = (hours + 24.0) % 24.0
            val h = normalized.toInt()
            val m = ((normalized - h) * 60).toInt()
            val displayH = if (h % 12 == 0) 12 else h % 12
            val displayM = String.format("%02d", m)
            return toBanglaDigits("$displayH:$displayM")
        }

        val currentHourDecimal = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60.0

        val isDaytime = currentHourDecimal in sunriseTime..maghribTime

        var currWaqt = "এশা"
        var currEnd = formatHours(fajrTime)
        var nextWaqt = "ফজর"
        var nextStart = formatHours(fajrTime)

        when {
            currentHourDecimal < fajrTime -> {
                currWaqt = "তাহাজ্জুদ"
                currEnd = formatHours(fajrTime)
                nextWaqt = "ফজর"
                nextStart = formatHours(fajrTime)
            }
            currentHourDecimal < sunriseTime -> {
                currWaqt = "ফজর"
                currEnd = formatHours(sunriseTime)
                nextWaqt = "যোহর"
                nextStart = formatHours(dhuhrTime)
            }
            currentHourDecimal < dhuhrTime -> {
                currWaqt = "ইশরাক/চাশত"
                currEnd = formatHours(dhuhrTime)
                nextWaqt = "যোহর"
                nextStart = formatHours(dhuhrTime)
            }
            currentHourDecimal < asrTime -> {
                currWaqt = "যোহর"
                currEnd = formatHours(asrTime)
                nextWaqt = "আসর"
                nextStart = formatHours(asrTime)
            }
            currentHourDecimal < maghribTime -> {
                currWaqt = "আসর"
                currEnd = formatHours(maghribTime)
                nextWaqt = "মাগরিব"
                nextStart = formatHours(maghribTime)
            }
            currentHourDecimal < ishaTime -> {
                currWaqt = "মাগরিব"
                currEnd = formatHours(ishaTime)
                nextWaqt = "এশা"
                nextStart = formatHours(ishaTime)
            }
            else -> {
                currWaqt = "এশা"
                currEnd = formatHours(fajrTime)
                nextWaqt = "ফজর"
                nextStart = formatHours(fajrTime)
            }
        }

        return PrayerSchedule(
            fajr = formatHours(fajrTime),
            dhuhr = formatHours(dhuhrTime),
            asr = formatHours(asrTime),
            maghrib = formatHours(maghribTime),
            isha = formatHours(ishaTime),
            currentWaqtTitle = "$currWaqt চলছে",
            currentWaqtEndTime = "শেষ: $currEnd",
            nextWaqtTitle = "$nextWaqt ওয়াক্ত",
            nextWaqtStartTime = "শুরু: $nextStart",
            isDaytime = isDaytime
        )
    }

    fun fetchLalpurWeather(onResult: (temp: String, condition: String, iconRes: Int, isDay: Boolean) -> Unit) {
        Thread {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=24.1833&longitude=88.9833&current_weather=true")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    val current = json.getJSONObject("current_weather")
                    val tempC = current.getDouble("temperature").roundToInt()
                    val weatherCode = current.getInt("weathercode")
                    val isDay = current.getInt("is_day") == 1

                    val (conditionText, iconRes) = resolveWeatherMeta(weatherCode, isDay)

                    Handler(Looper.getMainLooper()).post {
                        onResult("${toBanglaDigits(tempC.toString())}° সে.", conditionText, iconRes, isDay)
                    }
                    return@Thread
                }
            } catch (e: Exception) {}

            val isDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 6..18
            val (conditionText, iconRes) = resolveWeatherMeta(0, isDay)

            Handler(Looper.getMainLooper()).post {
                onResult("৩১° সে.", conditionText, iconRes, isDay)
            }
        }.start()
    }

    private fun resolveWeatherMeta(code: Int, isDay: Boolean): Pair<String, Int> {
        return when (code) {
            0 -> if (isDay) "পরিষ্কার রোদ" to R.drawable.ic_weather_sun else "পরিষ্কার আকাশ" to R.drawable.ic_weather_moon
            1, 2, 3 -> if (isDay) "আংশিক মেঘলা" to R.drawable.ic_weather_partly_cloudy_day else "মেঘলা রাত" to R.drawable.ic_weather_cloudy
            45, 48 -> "কুয়াশাচ্ছন্ন" to R.drawable.ic_weather_fog
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> "বৃষ্টির সম্ভাবনা" to R.drawable.ic_weather_rain
            95, 96, 99 -> "বজ্রসহ বৃষ্টি" to R.drawable.ic_weather_thunder
            else -> if (isDay) "স্বাভাবিক রোদ" to R.drawable.ic_weather_sun else "স্বাভাবিক আবহাওয়া" to R.drawable.ic_weather_moon
        }
    }

    fun toBanglaDigits(str: String): String {
        val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder()
        for (c in str) {
            if (c in '0'..'9') {
                sb.append(banglaDigits[c - '0'])
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}
