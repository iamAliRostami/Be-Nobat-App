package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Businesse(
    val name: String?,
    val description: String?,
    val logo: String?,
    val phone: String?,
    @SerialName("owner_user_id") val ownerUserId: String?,
    val status: String,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("deleted_by") val deletedBy: String?
) : BaseDomain()