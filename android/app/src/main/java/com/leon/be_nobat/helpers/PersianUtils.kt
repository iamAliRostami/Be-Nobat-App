package com.leon.be_nobat.helpers

import java.text.NumberFormat
import java.util.Locale

object PersianUtils {

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    fun isEnglishLocale(): Boolean = AppLocaleManager.isEnglish()

    fun toEnglishDigits(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val chars = text.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            when (c) {
                in '۰'..'۹' -> chars[i] = '0' + (c - '۰')
                in '٠'..'٩' -> chars[i] = '0' + (c - '٠')
            }
        }
        return String(chars)
    }

    fun toPersianDigits(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        if (isEnglishLocale()) {
            return toEnglishDigits(text)
        }
        val chars = text.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            when (c) {
                in '0'..'9' -> chars[i] = PERSIAN_DIGITS[c - '0']
                in '٠'..'٩' -> chars[i] = PERSIAN_DIGITS[c - '٠']
            }
        }
        return String(chars)
    }

    fun toPersianDigits(number: Number?): String {
        if (number == null) return ""
        return if (isEnglishLocale()) {
            number.toString()
        } else {
            toPersianDigits(number.toString())
        }
    }

    fun formatNumber(number: Number?): String {
        if (number == null) return ""
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(number.toLong())
        return if (isEnglishLocale()) formatted else toPersianDigits(formatted)
    }

    fun formatPrice(amount: Number?, currency: String? = null): String {
        if (amount == null) return ""
        val raw = amount.toDouble()
        val curClean = currency?.trim()?.uppercase()
        val isEn = isEnglishLocale()
        val (tomanAmount, label) = when {
            curClean == "IRT" || curClean == "TOMAN" || currency?.trim() == "تومان" -> raw to (if (isEn) "Toman" else "تومان")
            curClean == "USD" || curClean == "$" -> raw to "$"
            curClean == "EUR" || curClean == "€" -> raw to "€"
            else -> (raw / 10.0) to (if (isEn) "Toman" else "تومان")
        }
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(tomanAmount.toLong())
        return if (isEn) {
            "$formatted $label"
        } else {
            "${toPersianDigits(formatted)} $label"
        }
    }

    fun formatDuration(minutes: Int): String {
        return if (isEnglishLocale()) {
            "$minutes min"
        } else {
            "${toPersianDigits(minutes)} دقیقه"
        }
    }
}

fun String?.toPersianDigits(): String = PersianUtils.toPersianDigits(this)
fun String?.toEnglishDigits(): String = PersianUtils.toEnglishDigits(this)
fun Number?.toPersianDigits(): String = PersianUtils.toPersianDigits(this)
fun Number?.toEnglishDigits(): String = this?.toString() ?: ""
fun Number?.formatPersianNumber(): String = PersianUtils.formatNumber(this)
fun Number?.formatPersianPrice(currency: String? = null): String = PersianUtils.formatPrice(this, currency)
fun Int.formatPersianDuration(): String = PersianUtils.formatDuration(this)
