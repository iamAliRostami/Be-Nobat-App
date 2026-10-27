package com.leon.be_nobat.domain.usecase

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.domain.model.ManagementAvailability
import com.leon.be_nobat.domain.model.ManagementAvailabilityInput
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBranchInput
import com.leon.be_nobat.domain.model.ManagementBranchService
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementContext
import com.leon.be_nobat.domain.model.ManagementCustomerReview
import com.leon.be_nobat.domain.model.ManagementCustomerReviewInput
import com.leon.be_nobat.domain.model.ManagementDashboard
import com.leon.be_nobat.domain.model.ManagementMembership
import com.leon.be_nobat.domain.model.ManagementMembershipInput
import com.leon.be_nobat.domain.model.ManagementModerationInput
import com.leon.be_nobat.domain.model.ManagementPerson
import com.leon.be_nobat.domain.model.ManagementResource
import com.leon.be_nobat.domain.model.ManagementResourceInput
import com.leon.be_nobat.domain.model.ManagementResourceServicesInput
import com.leon.be_nobat.domain.model.ManagementReview
import com.leon.be_nobat.domain.model.ManagementService
import com.leon.be_nobat.domain.model.ManagementServiceInput
import com.leon.be_nobat.domain.model.ManagementStatusInput
import com.leon.be_nobat.domain.repository.ManagementRepository

class AdminDashboardUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(): Result<ManagementDashboard> = repo.dashboard()
}

class AdminAnalyticsUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(
        businessId: String? = null,
        branchId: String? = null,
        resourceId: String? = null,
        from: String? = null,
        to: String? = null,
    ): Result<com.leon.be_nobat.domain.model.ManagementAnalyticsSummary> =
        repo.analytics(businessId, branchId, resourceId, from, to)
}

class AdminContextUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(): Result<ManagementContext> = repo.context()
}

class AdminListBusinessesUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(q: String? = null, page: Int = 1, pageSize: Int = 25): Result<ApiPage<ManagementBusiness>> =
        repo.listBusinesses(q, page, pageSize)
}

class AdminListBranchesUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(
        businessId: String? = null, q: String? = null, includeInactive: Boolean? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementBranch>> = repo.listBranches(businessId, q, includeInactive, page, pageSize)
}

class AdminCreateBranchUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(input: ManagementBranchInput): Result<ManagementBranch> = repo.createBranch(input)
}

class AdminUpdateBranchUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String, input: ManagementBranchInput): Result<ManagementBranch> = repo.updateBranch(id, input)
}

class AdminToggleBranchActiveUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String, active: Boolean): Result<ManagementBranch> = repo.setBranchActive(id, active)
}

class AdminDeleteBranchUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repo.deleteBranch(id)
}

class AdminListResourcesUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(
        branchId: String? = null, businessId: String? = null, includeInactive: Boolean? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementResource>> = repo.listResources(branchId, businessId, includeInactive, page, pageSize)
}

class AdminListMembershipsUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(
        branchId: String? = null, userId: String? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementMembership>> = repo.listMemberships(branchId, userId, page, pageSize)
}

class AdminCreateMembershipUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(input: ManagementMembershipInput): Result<ManagementMembership> = repo.createMembership(input)
}

class AdminDeleteMembershipUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repo.deleteMembership(id)
}

class AdminListTeamUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(q: String? = null, branchId: String? = null, page: Int = 1, pageSize: Int = 25): Result<ApiPage<ManagementPerson>> =
        repo.listTeam(q, branchId, page, pageSize)
}

class AdminListCustomersUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(q: String? = null, branchId: String? = null, page: Int = 1, pageSize: Int = 25): Result<ApiPage<ManagementPerson>> =
        repo.listCustomers(q, branchId, page, pageSize)
}

class AdminCreateTeamMemberUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(input: com.leon.be_nobat.domain.model.ManagementTeamMemberInput): Result<ManagementPerson> =
        repo.createTeamMember(input)
}

class AdminCreateCustomerUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(input: com.leon.be_nobat.domain.model.ManagementCustomerInput): Result<ManagementPerson> =
        repo.createCustomer(input)
}

class AdminTogglePersonActiveUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String, active: Boolean): Result<ManagementPerson> =
        repo.setPersonActive(id, active)
}

class AdminListServicesUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(
        businessId: String? = null, branchId: String? = null, q: String? = null, includeInactive: Boolean? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementService>> = repo.listServices(businessId, branchId, q, includeInactive, page, pageSize)
}

class AdminCreateServiceUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(input: ManagementServiceInput): Result<ManagementService> = repo.createService(input)
}

class AdminUpdateServiceUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String, input: ManagementServiceInput): Result<ManagementService> = repo.updateService(id, input)
}

class AdminToggleServiceActiveUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String, active: Boolean): Result<ManagementService> = repo.setServiceActive(id, active)
}

class AdminListAppointmentsUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(
        businessId: String? = null, branchId: String? = null, resourceId: String? = null,
        customerId: String? = null, date: String? = null, status: String? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementAppointment>> =
        repo.listAppointments(businessId, branchId, resourceId, customerId, date, status, page, pageSize)
}

class AdminSetAppointmentStatusUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String, status: String): Result<ManagementAppointment> =
        repo.setAppointmentStatus(id, ManagementStatusInput(status))
}

class AdminListReviewsUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(
        businessId: String? = null, branchId: String? = null, status: String? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementReview>> = repo.listReviews(businessId, branchId, null, status, null, null, page, pageSize)
}

class AdminModerateReviewUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String, input: ManagementModerationInput): Result<ManagementReview> = repo.moderateReview(id, input)
}

class AdminReplyReviewUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String, reply: String?): Result<ManagementReview> = repo.replyReview(id, com.leon.be_nobat.domain.model.ManagementReplyInput(reply))
}

class AdminListAvailabilityUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(
        businessId: String? = null, branchId: String? = null, resourceId: String? = null,
        from: String? = null, to: String? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementAvailability>> = repo.listAvailability(businessId, branchId, resourceId, from, to, page, pageSize)
}

class AdminCreateAvailabilityUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(input: ManagementAvailabilityInput): Result<ManagementAvailability> = repo.createAvailability(input)
}

class AdminUpdateAvailabilityUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String, input: ManagementAvailabilityInput): Result<ManagementAvailability> = repo.updateAvailability(id, input)
}

class AdminDeleteAvailabilityUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repo.deleteAvailability(id)
}

class AdminListResourceServicesUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(resourceId: String): Result<ManagementResourceServicesInput> = repo.listResourceServices(resourceId)
}

class AdminListCustomerReviewsUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(
        businessId: String? = null, branchId: String? = null, customerId: String? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementCustomerReview>> = repo.listCustomerReviews(businessId, branchId, customerId, page, pageSize)
}

class AdminCreateCustomerReviewUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(input: ManagementCustomerReviewInput): Result<ManagementCustomerReview> = repo.createCustomerReview(input)
}

class AdminListPendingCustomerReviewsUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(page: Int = 1, pageSize: Int = 25): Result<ApiPage<ManagementAppointment>> =
        repo.listPendingCustomerReviews(page, pageSize)
}

class AdminApproveBranchUseCase(private val repo: ManagementRepository) {
    suspend operator fun invoke(id: String): Result<com.leon.be_nobat.domain.model.ManagementBranch> =
        repo.approveBranch(id)
}
