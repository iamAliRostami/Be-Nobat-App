package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
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
    val cancellationNoticeMinutes: Int = 120,
    val avatarUrl: String? = null,
    val headerUrl: String? = null,
    val depositPercentage: Double? = null,
    val depositCardNumber: String? = null,
    val depositIban: String? = null,
    val depositAccountHolder: String? = null,
    val gallery: List<GalleryItem> = emptyList(),
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
    val cancellationNoticeMinutes: Int? = null,
    val avatarUrl: String? = null,
    val headerUrl: String? = null,
    val depositPercentage: Double? = null,
    val depositCardNumber: String? = null,
    val depositIban: String? = null,
    val depositAccountHolder: String? = null,
    val fee: Double? = null,
    val hasReceipt: Boolean = false,
    val receiptTrackingCode: String? = null,
    val receiptUploadedAt: String? = null,
    val receiptImageUrl: String? = null,
    val gallery: List<GalleryItem> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class ManagementResource(
    val id: String = "",
    val branchId: String = "",
    val name: String = "",
    val kind: String = "staff",
    val userId: String? = null,
    val active: Boolean = false,
    val avatarUrl: String? = null,
    val gallery: List<GalleryItem> = emptyList(),
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
    val cancellationNoticeMinutes: Int? = null,
)

@Serializable
data class ManagementBranchService(
    val id: String = "",
    val branchId: String = "",
    val serviceId: String = "",
    val price: Double? = null,
    val effectivePrice: Double = 0.0,
    val serviceName: String = "",
    val cancellationNoticeMinutes: Int? = null,
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
    val serviceIds: List<String> = emptyList(),
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
    val depositRequired: Boolean = false,
    val depositPercentage: Double = 0.0,
    val depositAmount: Double = 0.0,
    val depositTrackingCode: String? = null,
    val depositReceiptUrl: String? = null,
    val depositPaidAt: String? = null,
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
data class RequestedBranch(
    val name: String = "",
    val address: String = "",
)

@Serializable
data class PlatformPricing(
    val baseBusinessPrice: Double = 500_000.0,
    val extraBranchPrice: Double = 200_000.0,
    val cardNumber: String = "",
    val iban: String = "",
    val accountHolder: String = "",
    val paymentInstructions: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val phone: String = "",
    val supportPhone: String = "",
    val mobile: String = "",
    val email: String = "",
    val infoEmail: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val workingHoursWeekdays: String = "",
    val workingHoursThursdays: String = "",
    val workingHoursHolidays: String = "",
    val telegram: String = "",
    val instagram: String = "",
    val eitaa: String = "",
) {
    val bankCardNumber: String get() = cardNumber
    val bankIban: String get() = iban
    val bankAccountHolder: String get() = accountHolder
}

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
    val additionalBranches: List<RequestedBranch> = emptyList(),
    val basePrice: Double = 0.0,
    val extraBranchPrice: Double = 0.0,
    val totalBranchesCount: Int = 1,
    val totalFee: Double = 0.0,
    val receiptTrackingCode: String? = null,
    val receiptUploadedAt: String? = null,
    @SerialName("hasReceipt")
    val hasReceiptImage: Boolean = false,
    val receiptImageBase64: String? = null,
    val status: String = "Pending",
    val reviewNote: String? = null,
    val approvedBusinessId: String? = null,
    val createdAt: String = "",
    @SerialName("userName")
    val userName: String? = null,
    val userDisplayName: String? = null,
    @SerialName("userPhone")
    val userPhone: String? = null,
    val userPhoneNumber: String? = null,
    val userEmail: String? = null,
) {
    val hasReceipt: Boolean get() = hasReceiptImage || !receiptTrackingCode.isNullOrBlank() || !receiptImageBase64.isNullOrBlank()

    val applicantName: String?
        get() = userName?.trim().takeUnless { it.isNullOrBlank() }
            ?: userDisplayName?.trim().takeUnless { it.isNullOrBlank() }

    val applicantPhone: String?
        get() = userPhone?.trim().takeUnless { it.isNullOrBlank() }
            ?: userPhoneNumber?.trim().takeUnless { it.isNullOrBlank() }
            ?: contactPhone.trim().takeUnless { it.isBlank() }

    val applicantDisplayName: String
        get() {
            val name = applicantName
            val phone = applicantPhone
            return when {
                name != null && phone != null && !name.contains(phone) -> "$name ($phone)"
                name != null -> name
                phone != null -> phone
                !userEmail.isNullOrBlank() -> userEmail
                else -> "کاربر متقاضی"
            }
        }
}

@Serializable
data class ManagementAnalyticsSummary(
    val totalAppointments: Int = 0,
    val completedAppointments: Int = 0,
    val pendingAppointments: Int = 0,
    val confirmedAppointments: Int = 0,
    val cancelledAppointments: Int = 0,
    val noShowAppointments: Int = 0,
    val totalRevenue: Double = 0.0,
    val totalWorkMinutes: Long = 0L,
    val totalWorkHours: Double = 0.0,
    val byBusiness: List<ManagementBreakdownItem> = emptyList(),
    val byBranch: List<ManagementBreakdownItem> = emptyList(),
    val byStaff: List<ManagementBreakdownItem> = emptyList(),
    val trend: List<ManagementDailyTrendItem> = emptyList(),
)

@Serializable
data class ManagementBreakdownItem(
    val id: String = "",
    val name: String = "",
    val businessName: String? = null,
    val branchName: String? = null,
    val totalAppointments: Int = 0,
    val completedAppointments: Int = 0,
    val revenue: Double = 0.0,
    val workMinutes: Long = 0L,
)

@Serializable
data class ManagementDailyTrendItem(
    val date: String = "",
    val total: Int = 0,
    val completed: Int = 0,
    val revenue: Double = 0.0,
    val workMinutes: Long = 0L,
)

@Serializable
data class SystemMessageDto(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val priority: String = "Normal",
    val createdAt: String = "",
    val isRead: Boolean = false,
)

@Serializable
data class ContactMessageDto(
    val id: String = "",
    val name: String = "",
    val contactInfo: String = "",
    val subject: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    val adminReply: String? = null,
    val repliedAt: String? = null,
    val createdAt: String = "",
)

@Serializable
data class PlatformAnnouncementDto(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val priority: String = "Normal",
    val targetUserId: String? = null,
    val targetUserName: String? = null,
    val targetUserPhone: String? = null,
    val createdAt: String = "",
)

@Serializable
data class SidebarBadgesDto(
    val pendingAppointments: Int = 0,
    val pendingReviews: Int = 0,
    val pendingRequests: Int = 0,
    val unreadContactMessages: Int = 0,
    val unreadBroadcasts: Int = 0,
    val highestBroadcastPriority: String? = null,
)

@Serializable
data class AnnouncementCreateRequest(
    val title: String,
    val content: String,
    val priority: String = "Normal",
    val targetUserId: String? = null,
)

@Serializable
data class ContactReplyRequest(
    val reply: String,
)


