package com.leon.be_nobat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateInfo(
    val hasUpdate: Boolean = false,
    val isForceUpdate: Boolean = false,
    val versionCode: Int = 0,
    val versionName: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val bazaarPackageName: String = "com.leon.be_nobat",
    val bazaarUrl: String = "bazaar://details?id=com.leon.be_nobat",
    val fileSize: Long? = null,
)
