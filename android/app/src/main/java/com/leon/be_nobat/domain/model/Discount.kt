package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Discount(
    @SerialName("business_id") val businessId: String?,
    val code: String?,
    val type: String?,
    val value: Double?,
    @SerialName("min_order_amount") val minOrderAmount: Double?,
    @SerialName("max_uses") val maxUses: Double?,
    @SerialName("used_count") val usedCount: Double?,
    @SerialName("valid_from") val validFrom: String?,
    @SerialName("valid_until") val validUntil: String?,
    @SerialName("applicable_services") val applicableServices: String?,
    val status: Boolean,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("deleted_by") val deletedBy: String?
) : BaseDomain()