package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResourceAssignment(
    @SerialName("resource_id") val resourceId: String?,
    @SerialName("branch_id") val branchId: String?,
    val title: String?,
    val status: String?,
    @SerialName("start_date") val startDate: String?,
    @SerialName("end_date") val endDate: String?,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("deleted_by") val deletedBy: String?
) : BaseDomain()