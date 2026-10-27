package com.leon.be_nobat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TodaySlotItem(
    val businessId: String = "",
    val businessName: String = "",
    val city: String = "",
    val category: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val serviceId: String = "",
    val serviceName: String = "",
    val serviceDurationMinutes: Int = 0,
    val price: Double = 0.0,
    val currency: String = "IRR",
    val resourceId: String = "",
    val resourceName: String = "",
    val startsAt: String = "",
    val endsAt: String = "",
    val timeLabel: String = "",
    val requiresApproval: Boolean = false,
)
