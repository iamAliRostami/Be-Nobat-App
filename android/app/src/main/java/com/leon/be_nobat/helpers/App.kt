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
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

const val PREFERENCES_NAME = "setting_prefs"

class App : Application(), ImageLoaderFactory {
    companion object {
        const val TAG = "BeNobat"
        const val API_TAG = "BeNobat.Api"
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Bypass any global or emulator HTTP/SOCKS proxy for local development hosts
        try {
            val systemDefault = ProxySelector.getDefault()
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    val host = uri?.host.orEmpty().lowercase()
                    if (host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost") {
                        return listOf(Proxy.NO_PROXY)
                    }
                    return systemDefault?.select(uri) ?: listOf(Proxy.NO_PROXY)
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                    systemDefault?.connectFailed(uri, sa, ioe)
                }
            })
        } catch (_: Exception) {}

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
        get<com.leon.be_nobat.notifications.AppointmentNotificationMonitor>().start(this)
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