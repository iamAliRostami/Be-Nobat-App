package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Appointment(
    @SerialName("branch_id") val branchId: String?,
    @SerialName("client_user_id") val clientUserId: String?,
    val start: String,
    val end: String,
    val status: String,
    @SerialName("total_price") val totalPrice: String,
    @SerialName("discount_amount") val discountAmount: String?,
    @SerialName("final_price") val finalPrice: String,
    val notes: String?
) : BaseDomain()