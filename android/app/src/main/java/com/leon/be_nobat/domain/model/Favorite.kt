package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Favorite(
    @SerialName("user_id") val userId: String,
    @SerialName("business_id") val businessId: String?,
    @SerialName("resource_id") val resourceId: String?
) : BaseDomain()