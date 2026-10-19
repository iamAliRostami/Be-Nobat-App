package com.leon.be_nobat.ui.view_models.base

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.local.ThemeManager
import kotlinx.coroutines.launch

class MainViewModel(private val themeManager: ThemeManager) : ViewModel() {

    fun toggleTheme(isCurrentlyNight: Boolean) {
        viewModelScope.launch {
            val nextMode = if (isCurrentlyNight) {
                AppCompatDelegate.MODE_NIGHT_NO
            } else {
                AppCompatDelegate.MODE_NIGHT_YES
            }
            themeManager.save(nextMode)
            AppCompatDelegate.setDefaultNightMode(nextMode)
        }
    }

    fun switchLanguage(languageTag: String) {
        com.leon.be_nobat.helpers.AppLocaleManager.currentLanguage = languageTag
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(languageTag)
        )
        viewModelScope.launch {
            themeManager.saveLanguage(languageTag)
        }
    }
}