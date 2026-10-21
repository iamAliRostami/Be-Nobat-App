package com.leon.be_nobat.helpers

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class JalaliDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    val monthName: String
        get() = if (PersianUtils.isEnglishLocale()) {
            JalaliDateHelper.ENGLISH_PERSIAN_MONTHS.getOrElse(month - 1) { "" }
        } else {
            JalaliDateHelper.PERSIAN_MONTHS.getOrElse(month - 1) { "" }
        }

    fun toFormattedString(): String {
        val y = PersianUtils.toPersianDigits(year)
        val m = PersianUtils.toPersianDigits("%02d".format(Locale.US, month))
        val d = PersianUtils.toPersianDigits("%02d".format(Locale.US, day))
        return "$y/$m/$d"
    }

    fun toDisplayString(): String {
        return "${PersianUtils.toPersianDigits(day)} $monthName ${PersianUtils.toPersianDigits(year)}"
    }

    fun toIsoDate(): String {
        val (gy, gm, gd) = JalaliDateHelper.jalaliToGregorian(year, month, day)
        return "%04d-%02d-%02d".format(Locale.US, gy, gm, gd)
    }
}

object JalaliDateHelper {

    val PERSIAN_MONTHS = arrayOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    val ENGLISH_PERSIAN_MONTHS = arrayOf(
        "Farvardin", "Ordibehesht", "Khordad",
        "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar",
        "Dey", "Bahman", "Esfand"
    )

    val WEEKDAYS = arrayOf(
        "شنبه", "یکشنبه", "دوشنبه",
        "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
    )

    val ENGLISH_WEEKDAYS = arrayOf(
        "Sat", "Sun", "Mon",
        "Tue", "Wed", "Thu", "Fri"
    )

    fun getWeekdayName(weekdayIdx: Int): String {
        return if (PersianUtils.isEnglishLocale()) {
            ENGLISH_WEEKDAYS.getOrElse(weekdayIdx) { "" }
        } else {
            WEEKDAYS.getOrElse(weekdayIdx) { "" }
        }
    }

    fun translateDayOfWeek(day: String?): String {
        if (day.isNullOrBlank()) return ""
        return when (day.trim().lowercase()) {
            "saturday", "sat" -> "شنبه"
            "sunday", "sun" -> "یکشنبه"
            "monday", "mon" -> "دوشنبه"
            "tuesday", "tue" -> "سه‌شنبه"
            "wednesday", "wed" -> "چهارشنبه"
            "thursday", "thu" -> "پنج‌شنبه"
            "friday", "fri" -> "جمعه"
            else -> day
        }
    }


    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) (gy + 1) else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += ((days - 1) / 365)
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return JalaliDate(jy, jm, jd)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        var days = (jy + 1595) * 365 + ((jy + 1595) / 33) * 8 + (((jy + 1595) % 33 + 3) / 4) + jd
        days += if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val gd: Int
        val gm: Int
        val salA = intArrayOf(0, 31, if ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var m = 0
        while (m < 13 && days >= salA[m]) {
            days -= salA[m]
            m++
        }
        gm = m
        gd = days + 1
        return Triple(gy, gm, gd)
    }

    fun getTodayJalali(): JalaliDate {
        val cal = Calendar.getInstance()
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun getTodayIso(): String {
        val cal = Calendar.getInstance()
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private val TEHRAN_ZONE = java.util.TimeZone.getTimeZone("Asia/Tehran")

    fun parseIsoToCalendar(iso: String?): Calendar? {
        if (iso.isNullOrBlank()) return null
        return try {
            val clean = iso.trim()
            val formats = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
            )
            for (fmt in formats) {
                try {
                    val sdf = SimpleDateFormat(fmt, Locale.US)
                    if (fmt.contains("X") || fmt.contains("'Z'")) {
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                    } else {
                        sdf.timeZone = TEHRAN_ZONE
                    }
                    val date = sdf.parse(clean)
                    if (date != null) {
                        return Calendar.getInstance(TEHRAN_ZONE).apply { time = date }
                    }
                } catch (_: Exception) {}
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun formatIsoToJalali(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        val cal = parseIsoToCalendar(isoDate)
        if (cal != null) {
            val j = gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            return j.toFormattedString()
        }
        return PersianUtils.toPersianDigits(isoDate)
    }

    fun formatIsoToJalaliDisplay(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        val cal = parseIsoToCalendar(isoDate)
        if (cal != null) {
            val j = gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            val weekdayIdx = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> 0
                Calendar.SUNDAY -> 1
                Calendar.MONDAY -> 2
                Calendar.TUESDAY -> 3
                Calendar.WEDNESDAY -> 4
                Calendar.THURSDAY -> 5
                Calendar.FRIDAY -> 6
                else -> 0
            }
            val weekday = getWeekdayName(weekdayIdx)
            return "$weekday ${j.toDisplayString()}"
        }
        return PersianUtils.toPersianDigits(isoDate)
    }

    fun formatIsoTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val cal = parseIsoToCalendar(iso)
        if (cal != null) {
            val h = "%02d".format(Locale.US, cal.get(Calendar.HOUR_OF_DAY))
            val m = "%02d".format(Locale.US, cal.get(Calendar.MINUTE))
            return PersianUtils.toPersianDigits("$h:$m")
        }
        return PersianUtils.toPersianDigits(iso)
    }

    fun formatIsoDateTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val date = formatIsoToJalali(iso)
        val time = formatIsoTime(iso)
        val atSeparator = if (PersianUtils.isEnglishLocale()) "at" else "ساعت"
        return if (time.isNotBlank() && time != date) "$date $atSeparator $time" else date
    }

    fun formatIsoDateTimeFull(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val cal = parseIsoToCalendar(iso)
        if (cal != null) {
            val j = gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            val time = formatIsoTime(iso)
            val y = PersianUtils.toPersianDigits(j.year)
            val d = PersianUtils.toPersianDigits(j.day)
            val comma = if (PersianUtils.isEnglishLocale()) "," else "،"
            val atSeparator = if (PersianUtils.isEnglishLocale()) "at" else "ساعت"
            return if (time.isNotBlank()) {
                "$d ${j.monthName}$comma $y $atSeparator $time"
            } else {
                "$d ${j.monthName}$comma $y"
            }
        }
        return formatIsoDateTime(iso)
    }
}
