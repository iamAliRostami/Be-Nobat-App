package com.leon.be_nobat.data.remote

import android.os.Build
import com.leon.be_nobat.BuildConfig

/**
 * Configuration for the BeNobat REST API (.NET backend at the api v1 base path).
 *
 * Automatically maps loopback hosts (127.0.0.1 / localhost) to 10.0.2.2 when
 * running on an Android emulator so networking works seamlessly with or without
 * adb reverse.
 */
object BeNobatApiConfig {

    private val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.MODEL.contains("google_sdk") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for x86") ||
        Build.MODEL.contains("sdk_gphone") ||
        Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu") ||
        Build.PRODUCT.contains("sdk_gphone") ||
        Build.PRODUCT.contains("emulator") ||
        Build.MANUFACTURER.contains("Genymotion") ||
        (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }

    private fun normalizeLoopback(url: String): String {
        return if (isEmulator) {
            url.replace("://127.0.0.1:", "://10.0.2.2:")
               .replace("://localhost:", "://10.0.2.2:")
        } else url
    }

    /** Absolute base URL ending with `/api/v1`, e.g. `http://10.0.2.2:8080/api/v1`. */
    val apiBaseUrl: String by lazy {
        normalizeLoopback(BuildConfig.API_BASE_URL.trimEnd('/'))
    }

    /** Absolute base URL of the web host (used for /media/avatar/{id} download). */
    val mediaBaseUrl: String by lazy {
        normalizeLoopback(BuildConfig.MEDIA_BASE_URL.trimEnd('/'))
    }

    /** API version label (informational). */
    val apiVersion: String = BuildConfig.API_VERSION

    /**
     * Resolve a relative path (e.g. `/media/avatar/...?v=...`) against the
     * media host. Absolute URLs are returned unchanged.
     */
    fun resolveMediaUrl(relative: String?): String? {
        if (relative.isNullOrBlank()) return null
        val url = if (relative.startsWith("http://") || relative.startsWith("https://")) {
            relative
        } else {
            val withSlash = if (relative.startsWith("/")) relative else "/$relative"
            "$mediaBaseUrl$withSlash"
        }
        return normalizeLoopback(url)
    }
}
