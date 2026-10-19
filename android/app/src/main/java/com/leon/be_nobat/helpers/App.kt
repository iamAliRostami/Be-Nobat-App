package com.leon.be_nobat.helpers

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.leon.be_nobat.data.local.ThemeManager
import com.leon.be_nobat.di.module.localStorageModule
import com.leon.be_nobat.di.module.networkModule
import com.leon.be_nobat.di.module.repositoryModule
import com.leon.be_nobat.di.module.useCaseModule
import com.leon.be_nobat.di.module.viewModelModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

const val PREFERENCES_NAME = "setting_prefs"

class App : Application() {
    companion object {
        const val TAG = "BeNobat"
        const val API_TAG = "BeNobat.Api"
    }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                listOf(
                    localStorageModule,
                    networkModule,
                    repositoryModule,
                    useCaseModule,
                    viewModelModule,
                )
            )
        }
        val preferences: ThemeManager = get()
        runBlocking {
            val savedTheme = preferences.themeMode.first()
            AppCompatDelegate.setDefaultNightMode(savedTheme)
            val savedLanguage = preferences.language.first()
            if (!savedLanguage.isNullOrBlank()) {
                AppLocaleManager.currentLanguage = savedLanguage
                val current = AppCompatDelegate.getApplicationLocales()
                if (current.isEmpty || current.get(0)?.language != savedLanguage) {
                    AppCompatDelegate.setApplicationLocales(
                        androidx.core.os.LocaleListCompat.forLanguageTags(savedLanguage)
                    )
                }
            } else {
                AppLocaleManager.currentLanguage = "fa"
            }
        }
    }
}