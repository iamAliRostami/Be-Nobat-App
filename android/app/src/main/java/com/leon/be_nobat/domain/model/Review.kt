package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Review(
    @SerialName("appointment_service_id") val appointmentServiceId: String?,
    @SerialName("user_id") val userId: String?,
    val rating: Double,
    val comment: String?
) : BaseDomain()