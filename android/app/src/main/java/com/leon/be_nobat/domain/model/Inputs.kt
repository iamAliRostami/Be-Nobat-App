package com.leon.be_nobat.domain.model

import kotlinx.serialization.Serializable

/**
 * Request payloads (input DTOs) sent to `api v1`.
 */

@Serializable
data class LoginInput(
    val email: String? = null,
    val password: String? = null,
)

@Serializable
data class RegisterInput(
    val displayName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val password: String? = null,
)

@Serializable
data class ProfileInput(
    val displayName: String? = null,
    val phoneNumber: String? = null,
)

@Serializable
data class PasswordInput(
    val currentPassword: String? = null,
    val newPassword: String? = null,
)

@Serializable
data class EmailInput(
    val email: String? = null,
    val currentPassword: String? = null,
)

@Serializable
data class AvatarInput(
    val contentType: String? = null,
    val dataBase64: String? = null,
)

@Serializable
data class ReviewInput(
    val rating: Int,
    val comment: String? = null,
)

// ---- Management input DTOs ----

@Serializable
data class ManagementActiveInput(val active: Boolean)

@Serializable
data class ManagementBusinessInput(
    val name: String,
    val slug: String = "",
    val category: String = "عمومی",
    val city: String = "تهران",
    val description: String = "",
    val requiresApproval: Boolean = true,
    val firstBranchName: String = "شعبه مرکزی",
    val ownerId: String? = null,
    val firstBranchAddress: String? = null,
    val firstBranchWeeklyHours: List<WeeklyTimeRange>? = null,
    val cancellationNoticeMinutes: Int? = null,
)

@Serializable
data class ManagementBranchInput(
    val businessId: String,
    val name: String,
    val address: String = "",
    val timeZoneId: String = "Asia/Tehran",
    val openHour: Int = 8,
    val closeHour: Int = 20,
    val weeklyHours: List<WeeklyTimeRange>? = null,
    val cancellationNoticeMinutes: Int? = null,
)

@Serializable
data class ManagementResourceInput(
    val branchId: String,
    val name: String,
    val kind: String = "staff",
    val userId: String? = null,
)

@Serializable
data class ManagementMembershipInput(
    val branchId: String,
    val userId: String,
    val role: String = "Staff",
)

@Serializable
data class ManagementServiceInput(
    val businessId: String,
    val name: String,
    val description: String = "",
    val durationMinutes: Int = 30,
    val price: Double = 0.0,
    val currency: String = "IRR",
    val catalogItemId: String? = null,
    val branchId: String? = null,
    val resourceIds: List<String>? = null,
    val cancellationNoticeMinutes: Int? = null,
)

@Serializable
data class ManagementBranchServiceInput(val price: Double? = null,
    val cancellationNoticeMinutes: Int? = null,
)

@Serializable
data class ManagementAvailabilityInput(
    val businessId: String,
    val effectiveDate: String,
    val startsAt: String,
    val endsAt: String,
    val branchId: String? = null,
    val resourceId: String? = null,
    val isAvailable: Boolean = true,
)

@Serializable
data class ManagementStatusInput(val status: String)

@Serializable
data class ManagementModerationInput(
    val status: String,
    val managerReply: String? = null,
)

@Serializable
data class ManagementReplyInput(val managerReply: String?)

@Serializable
data class ManagementCustomerReviewInput(
    val appointmentId: String,
    val rating: Int,
    val comment: String = "",
)

@Serializable
data class ManagementCatalogInput(
    val name: String,
    val slug: String,
    val category: String = "عمومی",
    val description: String = "",
    val suggestedDurationMinutes: Int = 30,
    val isPublished: Boolean = true,
)

@Serializable
data class ManagementCategoryInput(
    val name: String,
    val kind: String = "Business",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Serializable
data class ManagementUserInput(
    val displayName: String,
    val email: String,
    val phoneNumber: String,
    val role: String = "Customer",
)

@Serializable
data class ManagementRolesInput(val roles: List<String>)

@Serializable
data class ManagementTeamMemberInput(
    val name: String,
    val email: String,
    val phone: String? = null,
    val branchId: String,
    val role: String = "Staff",
)

@Serializable
data class ManagementCustomerInput(
    val name: String,
    val email: String,
    val phone: String? = null,
)

@Serializable
data class ManagementResourceServicesInput(val serviceIds: List<String>)

@Serializable
data class BusinessRequestInput(
    val businessName: String,
    val category: String = "عمومی",
    val city: String = "تهران",
    val description: String = "",
    val contactPhone: String = "",
    val firstBranchName: String = "شعبه مرکزی",
    val firstBranchAddress: String = "",
)

@Serializable
data class ReviewBusinessRequestInput(
    val note: String? = null,
)
