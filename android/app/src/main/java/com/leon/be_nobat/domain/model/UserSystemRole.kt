package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSystemRole(
    @SerialName("user_id") val userId: String?,
    @SerialName("role_id") val roleId: String?,
    val status: String?,
    @SerialName("assigned_by") val assignedBy: String,
    @SerialName("assigned_at") val assignedAt: String?,
    @SerialName("expires_at") val expiresAt: String?
) : BaseDomain()