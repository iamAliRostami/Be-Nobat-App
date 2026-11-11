package com.leon.be_nobat.helpers

import android.os.Build
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AppDateFormatter {

    fun localizeDigits(input: String, lang: String = "fa"): String {
        val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
        val arabicDigits = "٠١٢٣٤٥٦٧٨٩"
        val target = when (lang) {
            "fa" -> persianDigits
            "ar" -> arabicDigits
            else -> return input
        }
        val sb = java.lang.StringBuilder()
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(target[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatNumber(number: Number, lang: String = "fa"): String {
        return localizeDigits(number.toString(), lang)
    }

    fun parseToMillis(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        val clean = isoString.trim()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return OffsetDateTime.parse(clean).toInstant().toEpochMilli()
            } catch (_: Exception) {}
            try {
                return Instant.parse(clean).toEpochMilli()
            } catch (_: Exception) {}
            try {
                val noFraction = clean.substringBefore(".")
                return LocalDateTime.parse(noFraction).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {}
        }
        return parseLegacy(clean)
    }

    private fun parseLegacy(clean: String): Long? {
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(clean)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    fun formatDate(millis: Long, lang: String = "fa"): String {
        return when (lang) {
            "fa" -> {
                val cal = Calendar.getInstance().apply { timeInMillis = millis }
                val gy = cal.get(Calendar.YEAR)
                val gm = cal.get(Calendar.MONTH) + 1
                val gd = cal.get(Calendar.DAY_OF_MONTH)
                val j = JalaliDateHelper.gregorianToJalali(gy, gm, gd)
                val raw = String.format(Locale.US, "%04d/%02d/%02d", j.year, j.month, j.day)
                localizeDigits(raw, "fa")
            }
            "ar" -> {
                try {
                    val cal = android.icu.util.Calendar.getInstance(android.icu.util.ULocale("ar@calendar=islamic"))
                    cal.timeInMillis = millis
                    val y = cal.get(android.icu.util.Calendar.YEAR)
                    val m = cal.get(android.icu.util.Calendar.MONTH) + 1
                    val d = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
                    val raw = String.format(Locale.US, "%04d/%02d/%02d هـ", y, m, d)
                    localizeDigits(raw, "ar")
                } catch (_: Throwable) {
                    fallbackFormatDate(millis, lang)
                }
            }
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = millis }
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                val d = cal.get(Calendar.DAY_OF_MONTH)
                String.format(Locale.US, "%04d/%02d/%02d", y, m, d)
            }
        }
    }

    private fun fallbackFormatDate(millis: Long, lang: String): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val raw = String.format(Locale.US, "%04d/%02d/%02d", y, m, d)
        return localizeDigits(raw, lang)
    }

    fun formatDateTime(isoString: String?, lang: String = "fa"): String {
        if (isoString.isNullOrBlank()) return "—"
        val millis = parseToMillis(isoString)
        if (millis == null) {
            val clean = isoString.substringBefore(".").replace("Z", "")
            return localizeDigits(clean, lang)
        }
        return formatDateTime(millis, lang)
    }

    fun formatDateTime(millis: Long, lang: String = "fa"): String {
        val localCal = Calendar.getInstance().apply { timeInMillis = millis }
        val hour = localCal.get(Calendar.HOUR_OF_DAY)
        val minute = localCal.get(Calendar.MINUTE)
        val timeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
        val dateStr = formatDate(millis, lang)
        val combined = "$dateStr - $timeStr"
        return localizeDigits(combined, lang)
    }
}
