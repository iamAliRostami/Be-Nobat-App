package com.leon.be_nobat.helpers

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

object AppLocaleManager {
    @Volatile
    var currentLanguage: String = "fa"

    fun isEnglish(): Boolean {
        if (currentLanguage.equals("en", ignoreCase = true)) return true
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            val lang = locales.get(0)?.language
            if (!lang.isNullOrBlank()) return lang.equals("en", ignoreCase = true)
        }
        return Locale.getDefault().language.equals("en", ignoreCase = true)
    }

    fun isPersian(): Boolean {
        if (currentLanguage.equals("fa", ignoreCase = true)) return true
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            val lang = locales.get(0)?.language
            if (!lang.isNullOrBlank()) return lang.equals("fa", ignoreCase = true)
        }
        return Locale.getDefault().language.equals("fa", ignoreCase = true)
    }
}
