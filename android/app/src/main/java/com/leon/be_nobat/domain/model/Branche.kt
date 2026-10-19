package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Branche(
    @SerialName("business_id") val businessId: String?,
    val name: String?,
    val code: String,
    val phone: String,
    val address: String?,
    val latlng: String?,
    val status: String,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("deleted_by") val deletedBy: String?
) : BaseDomain()