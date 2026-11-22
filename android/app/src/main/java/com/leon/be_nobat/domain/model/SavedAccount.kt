package com.leon.be_nobat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedAccount(
    val session: MobileSession,
    val managementContext: ManagementContext? = null,
    val lastActiveAt: Long = System.currentTimeMillis()
)
