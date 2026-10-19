package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceAssignment(
    @SerialName("branch_service_id") val branchServiceId: String,
    @SerialName("resource_assignment_id") val resourceAssignmentId: String?,
    @SerialName("price_overide") val priceOveride: String?,
    @SerialName("duration_override") val durationOverride: Double?,
    val status: String,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("deleted_by") val deletedBy: String?
) : BaseDomain()