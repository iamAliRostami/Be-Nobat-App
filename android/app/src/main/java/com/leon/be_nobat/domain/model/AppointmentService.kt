package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppointmentService(
    @SerialName("appointment_id") val appointmentId: String?,
    @SerialName("service_assignment_id") val serviceAssignmentId: String?,
    val price: String,
    @SerialName("start_at") val startAt: String?,
    val duration: Double,
    val order: Double?,
    val status: String,
    val note: String?
) : BaseDomain()