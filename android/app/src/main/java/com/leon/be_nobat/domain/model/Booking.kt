package com.leon.be_nobat.domain.model

import kotlinx.serialization.Serializable

/**
 * Slots + appointment + customer booking DTOs.
 */

@Serializable
data class SlotsResponse(
    val date: String = "",
    val timeZoneId: String = "Asia/Tehran",
    val totalDurationMinutes: Int = 0,
    val totalPrice: Double = 0.0,
    val currency: String = "IRR",
    val slots: List<Slot> = emptyList(),
)

@Serializable
data class Slot(
    val resourceId: String = "",
    val resourceName: String = "",
    val startsAt: String = "",
    val endsAt: String = "",
)

@Serializable
data class Appointment(
    val id: String = "",
    val businessId: String = "",
    val businessName: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val timeZoneId: String = "Asia/Tehran",
    val serviceNames: List<String> = emptyList(),
    val resourceId: String? = null,
    val resourceName: String? = null,
    val startsAt: String = "",
    val endsAt: String = "",
    val status: String = "Pending", // "Pending" | "Confirmed" | "Completed" | "Cancelled" | "NoShow"
    val finalPrice: Double = 0.0,
    val currency: String = "IRR",
    val customerNote: String = "",
    val trackingCode: String = "",
    val canCancel: Boolean = false,
    val cancellationNoticeMinutes: Int = 120,
    val cancellationDeadline: String? = null,
    val canReview: Boolean = false,
    val reviewId: String? = null,
) {
    val isPending: Boolean get() = status == "Pending"
    val isConfirmed: Boolean get() = status == "Confirmed"
    val isCompleted: Boolean get() = status == "Completed"
    val isCancelled: Boolean get() = status == "Cancelled"
    val isNoShow: Boolean get() = status == "NoShow"
    val isActive: Boolean get() = isPending || isConfirmed
}

@Serializable
data class ReviewCreated(
    val id: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val status: String = "Pending",
    val createdAt: String = "",
)

/** Booking request payload (constructed in the booking wizard). */
@Serializable
data class BookingInput(
    val businessId: String,
    val branchId: String,
    val serviceIds: List<String>,
    val resourceId: String? = null,
    val startsAt: String,
    val phoneNumber: String? = null,
    val customerNote: String? = null,
    val termsAccepted: Boolean,
    val expectedPrice: Double? = null,
    val expectedDurationMinutes: Int? = null,
)
