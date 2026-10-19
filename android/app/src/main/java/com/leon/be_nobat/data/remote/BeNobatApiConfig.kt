package com.leon.be_nobat.data.remote

import com.leon.be_nobat.BuildConfig

/**
 * Configuration for the BeNobat REST API (.NET backend at the api v1 base path).
 *
 * The mobile API contract is documented at `docs/mobile-api.md` in the web
 * repository. All mobile routes live under the api v1 group and use a custom
 * opaque Bearer token (the MobileBearer scheme on the server).
 */
object BeNobatApiConfig {
    /** Absolute base URL ending with `/api/v1`, e.g. `http://10.0.2.2:8080/api/v1`. */
    val apiBaseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

    /** Absolute base URL of the web host (used for /media/avatar/{id} download). */
    val mediaBaseUrl: String = BuildConfig.MEDIA_BASE_URL.trimEnd('/')

    /** API version label (informational). */
    val apiVersion: String = BuildConfig.API_VERSION

    /**
     * Resolve a relative path (e.g. `/media/avatar/...?v=...`) against the
     * media host. Absolute URLs are returned unchanged.
     */
    fun resolveMediaUrl(relative: String?): String? {
        if (relative.isNullOrBlank()) return null
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        val withSlash = if (relative.startsWith("/")) relative else "/$relative"
        return "$mediaBaseUrl$withSlash"
    }
}
