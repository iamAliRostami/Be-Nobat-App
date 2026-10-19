package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    @SerialName("user_id") val userId: String?,
    val title: String?,
    val body: String?,
    val type: String?,
    @SerialName("reference_id") val referenceId: String?,
    val status: String?,
    @SerialName("sent_at") val sentAt: String
) : BaseDomain()