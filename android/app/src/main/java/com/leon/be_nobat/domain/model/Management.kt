package com.leon.be_nobat.domain.model

import kotlinx.serialization.Serializable

/**
 * Management (admin) DTOs — `api v1 admin`.
 */

@Serializable
data class ManagementDashboard(
    val businesses: Int = 0,
    val branches: Int = 0,
    val appointments: Int = 0,
    val pending: Int = 0,
    val upcoming: Int = 0,
    val completed: Int = 0,
    val revenue: Double = 0.0,
    val pendingReviews: Int = 0,
)

@Serializable
data class ManagementContext(
    val userId: String = "",
    val roles: List<String> = emptyList(),
    val isPlatformAdmin: Boolean = false,
    val canManageBusiness: Boolean = false,
    val canManageAppointments: Boolean = false,
    val memberships: List<ManagementContextMembership> = emptyList(),
)

@Serializable
data class ManagementContextMembership(
    val branchId: String = "",
    val businessId: String = "",
    val branchName: String = "",
    val role: String = "Staff",
    val canManageBusiness: Boolean = false,
    val canManageAppointments: Boolean = false,
)

@Serializable
data class ManagementBusiness(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val category: String = "",
    val city: String = "",
    val description: String = "",
    val requiresApproval: Boolean = false,
    val active: Boolean = false,
    val firstBranchId: String? = null,
)

@Serializable
data class ManagementBranch(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val address: String = "",
    val timeZoneId: String = "Asia/Tehran",
    val openHour: Int = 9,
    val closeHour: Int = 18,
    val active: Boolean = false,
    val isApproved: Boolean = true,
    val weeklyHours: List<WeeklyTimeRange>? = null,
    val businessName: String? = null,
)

@Serializable
data class ManagementResource(
    val id: String = "",
    val branchId: String = "",
    val name: String = "",
    val kind: String = "staff",
    val userId: String? = null,
    val active: Boolean = false,
)

@Serializable
data class ManagementMembership(
    val id: String = "",
    val branchId: String = "",
    val userId: String = "",
    val role: String = "Staff",
    val displayName: String = "",
    val email: String? = null,
)

@Serializable
data class ManagementPerson(
    val id: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val active: Boolean = false,
    val visits: Int = 0,
    val rating: Double? = null,
    val role: String? = null,
    val branchName: String? = null,
)

@Serializable
data class ManagementService(
    val id: String = "",
    val businessId: String = "",
    val catalogItemId: String? = null,
    val name: String = "",
    val description: String = "",
    val durationMinutes: Int = 30,
    val price: Double = 0.0,
    val currency: String = "IRR",
    val branchIds: List<String> = emptyList(),
    val resourceIds: List<String> = emptyList(),
    val active: Boolean = false,
)

@Serializable
data class ManagementBranchService(
    val id: String = "",
    val branchId: String = "",
    val serviceId: String = "",
    val price: Double? = null,
    val effectivePrice: Double = 0.0,
    val serviceName: String = "",
)

@Serializable
data class ManagementAvailability(
    val id: String = "",
    val businessId: String = "",
    val branchId: String? = null,
    val resourceId: String? = null,
    val effectiveDate: String? = null,
    val dayOfWeek: String = "Monday",
    val startsAt: String = "09:00:00",
    val endsAt: String = "18:00:00",
    val isAvailable: Boolean = true,
    val businessName: String? = null,
    val branchName: String? = null,
    val resourceName: String? = null,
)

@Serializable
data class ManagementAppointment(
    val id: String = "",
    val branchId: String = "",
    val businessId: String = "",
    val serviceId: String = "",
    val resourceId: String? = null,
    val customerId: String = "",
    val startsAt: String = "",
    val endsAt: String = "",
    val status: String = "Pending",
    val finalPrice: Double = 0.0,
    val currency: String = "IRR",
    val customerNote: String = "",
    val branchName: String = "",
    val serviceName: String = "",
    val resourceName: String? = null,
    val customerName: String = "",
    val customerEmail: String? = null,
    val customerPhoneNumber: String? = null,
    val businessName: String = "",
    val timeZoneId: String = "Asia/Tehran",
    val serviceNames: List<String> = emptyList(),
    val trackingCode: String = "",
)

@Serializable
data class ManagementReview(
    val id: String = "",
    val businessId: String = "",
    val branchId: String? = null,
    val customerId: String = "",
    val appointmentId: String? = null,
    val rating: Int = 0,
    val comment: String = "",
    val managerReply: String? = null,
    val status: String = "Pending",
    val customerName: String = "",
    val active: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class ManagementCustomerReview(
    val id: String = "",
    val appointmentId: String = "",
    val businessId: String = "",
    val branchId: String = "",
    val customerId: String = "",
    val authorId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val customerName: String = "",
    val authorName: String = "",
    val createdAt: String = "",
)

@Serializable
data class ManagementCatalog(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val category: String = "",
    val description: String = "",
    val suggestedDurationMinutes: Int = 30,
    val isPublished: Boolean = true,
    val active: Boolean = false,
)

@Serializable
data class ManagementCategory(
    val id: String = "",
    val name: String = "",
    val kind: String = "Business",
    val sortOrder: Int = 0,
    val active: Boolean = false,
)

@Serializable
data class ManagementUser(
    val id: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val active: Boolean = false,
    val roles: List<String> = emptyList(),
)

@Serializable
data class ManagementCreatedUser(
    val id: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val temporaryPassword: String = "",
)

@Serializable
data class PlatformResetPasswordResponse(
    val userId: String = "",
    val temporaryPassword: String = "",
)

@Serializable
data class BusinessRequest(
    val id: String = "",
    val userId: String = "",
    val businessName: String = "",
    val category: String = "عمومی",
    val city: String = "تهران",
    val description: String = "",
    val contactPhone: String = "",
    val firstBranchName: String = "شعبه مرکزی",
    val firstBranchAddress: String = "",
    val status: String = "Pending",
    val reviewNote: String? = null,
    val approvedBusinessId: String? = null,
    val createdAt: String = "",
    val userDisplayName: String? = null,
    val userPhoneNumber: String? = null,
)
