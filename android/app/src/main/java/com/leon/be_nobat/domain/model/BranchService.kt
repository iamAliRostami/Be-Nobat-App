package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BranchService(
    @SerialName("branch_id") val branchId: String?,
    @SerialName("service_id") val serviceId: String?,
    val price: Double?,
    val duration: Double?,
    val status: String?,
    @SerialName("requires_approval") val requiresApproval: Boolean,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("deleted_by") val deletedBy: String?
) : BaseDomain()