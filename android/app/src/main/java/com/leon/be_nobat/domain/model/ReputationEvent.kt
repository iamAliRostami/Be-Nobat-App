package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReputationEvent(
    @SerialName("subject_user_id") val subjectUserId: String?,
    @SerialName("appointment_id") val appointmentId: String?,
    @SerialName("source_user_id") val sourceUserId: String?,
    @SerialName("business_id") val businessId: String?,
    val type: String?,
    val score: Double?
) : BaseDomain()