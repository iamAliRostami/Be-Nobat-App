package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceCategory(
    @SerialName("business_id") val businessId: String?,
    val name: String?,
    @SerialName("sort_order") val sortOrder: Double?,
    @SerialName("parent_id") val parentId: String?,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("deleted_by") val deletedBy: String?
) : BaseDomain()