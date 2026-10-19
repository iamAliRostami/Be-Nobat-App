package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Resource(
    @SerialName("user_id") val userId: String?,
    val name: String?,
    val type: String,
    val image: String?,
    val status: Boolean,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("deleted_by") val deletedBy: String?
) : BaseDomain()