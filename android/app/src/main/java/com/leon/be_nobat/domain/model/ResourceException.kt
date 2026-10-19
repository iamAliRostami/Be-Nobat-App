package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResourceException(
    @SerialName("branch_id") val branchId: String?,
    @SerialName("resource_assignment_id") val resourceAssignmentId: String?,
    @SerialName("start_datetime") val startDatetime: String?,
    @SerialName("end_datetime") val endDatetime: String?,
    val effect: String?,
    val type: String?,
    val reason: String?,
    val status: String?
) : BaseDomain()