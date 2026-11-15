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
        val jy2 = jy - 979
        val jm2 = jm - 1
        val jd2 = jd - 1

        var jDayNo = 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33 + 3) / 4)
        for (i in 0 until jm2) {
            jDayNo += if (i < 6) 31 else 30
        }
        jDayNo += jd2

        var gDayNo = jDayNo + 79

        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097

        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524

            if (gDayNo >= 365) {
                gDayNo++
            } else {
                leap = false
            }
        }

        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461

        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }

        val isLeapYear = ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))
        val gDaysInMonth = intArrayOf(
            31,
            if (isLeapYear) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31
        )

        var gm = 0
        while (gm < 12 && gDayNo >= gDaysInMonth[gm]) {
            gDayNo -= gDaysInMonth[gm]
            gm++
        }

        val gd = gDayNo + 1
        return Triple(gy, gm + 1, gd)
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

    fun formatCalendarToIso(cal: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(cal.time)
    }

    fun parseTimeToIso(dateIso: String, timeHHmm: String): String {
        val cal = Calendar.getInstance(TEHRAN_ZONE)
        val dateParts = dateIso.split("-").mapNotNull { it.toIntOrNull() }
        if (dateParts.size == 3) {
            cal.set(dateParts[0], dateParts[1] - 1, dateParts[2])
        }
        val timeParts = timeHHmm.split(":").mapNotNull { it.toIntOrNull() }
        if (timeParts.size >= 2) {
            cal.set(Calendar.HOUR_OF_DAY, timeParts[0])
            cal.set(Calendar.MINUTE, timeParts[1])
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        return formatCalendarToIso(cal)
    }
}
