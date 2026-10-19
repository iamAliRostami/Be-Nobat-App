package com.leon.be_nobat.data.remote

import com.leon.be_nobat.BuildConfig

object PocketBaseConfig {
    val baseUrl: String = BuildConfig.POCKET_BASE_URL.trimEnd('/')
    val loginPath: String = BuildConfig.AUTH_LOGIN_PATH.let { path ->
        if (path.startsWith('/')) path else "/$path"
    }

    val loginUrl: String get() = "$baseUrl$loginPath"
}
