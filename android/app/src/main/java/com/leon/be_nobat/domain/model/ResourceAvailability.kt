package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResourceAvailability(
    @SerialName("resource_assignment_id") val resourceAssignmentId: String?,
    @SerialName("day_of_week") val dayOfWeek: Double?,
    @SerialName("open_time") val openTime: String?,
    @SerialName("close_time") val closeTime: String?,
    val status: String?
) : BaseDomain()