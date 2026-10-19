package com.leon.be_nobat.domain.repository

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.data.remote.ApiItems
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.domain.model.ManagementAvailability
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBranchService
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementContext
import com.leon.be_nobat.domain.model.ManagementCustomerReview
import com.leon.be_nobat.domain.model.ManagementDashboard
import com.leon.be_nobat.domain.model.ManagementMembership
import com.leon.be_nobat.domain.model.ManagementPerson
import com.leon.be_nobat.domain.model.ManagementResource
import com.leon.be_nobat.domain.model.ManagementReview
import com.leon.be_nobat.domain.model.ManagementService
import com.leon.be_nobat.domain.model.ManagementStatusInput
import com.leon.be_nobat.domain.model.ManagementAvailabilityInput
import com.leon.be_nobat.domain.model.ManagementBranchInput
import com.leon.be_nobat.domain.model.ManagementBusinessInput
import com.leon.be_nobat.domain.model.ManagementMembershipInput
import com.leon.be_nobat.domain.model.ManagementResourceInput
import com.leon.be_nobat.domain.model.ManagementServiceInput
import com.leon.be_nobat.domain.model.ManagementModerationInput
import com.leon.be_nobat.domain.model.ManagementReplyInput
import com.leon.be_nobat.domain.model.ManagementCustomerReviewInput
import com.leon.be_nobat.domain.model.ManagementResourceServicesInput
import com.leon.be_nobat.domain.model.ManagementBranchServiceInput

/**
 * Admin/business-management endpoints (`api v1 admin`). Visible to
 * PlatformAdmin + Owner + Manager + Staff (Staff only for appointment reads
 * and status changes on their own assignments).
 */
interface ManagementRepository {
    // overview
    suspend fun dashboard(): Result<ManagementDashboard>
    suspend fun context(): Result<ManagementContext>

    // businesses (PUT only — creation is Platform-only)
    suspend fun listBusinesses(q: String? = null, page: Int = 1, pageSize: Int = 25): Result<ApiPage<ManagementBusiness>>
    suspend fun getBusiness(id: String): Result<ManagementBusiness>
    suspend fun updateBusiness(id: String, input: ManagementBusinessInput): Result<ManagementBusiness>

    // branches
    suspend fun listBranches(
        businessId: String? = null,
        q: String? = null,
        includeInactive: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementBranch>>

    suspend fun createBranch(input: ManagementBranchInput): Result<ManagementBranch>
    suspend fun getBranch(id: String): Result<ManagementBranch>
    suspend fun updateBranch(id: String, input: ManagementBranchInput): Result<ManagementBranch>
    suspend fun setBranchActive(id: String, active: Boolean): Result<ManagementBranch>
    suspend fun deleteBranch(id: String): Result<Unit>

    // resources
    suspend fun listResources(
        branchId: String? = null,
        businessId: String? = null,
        includeInactive: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementResource>>

    suspend fun createResource(input: ManagementResourceInput): Result<ManagementResource>
    suspend fun updateResource(id: String, input: ManagementResourceInput): Result<ManagementResource>
    suspend fun setResourceActive(id: String, active: Boolean): Result<ManagementResource>
    suspend fun deleteResource(id: String): Result<Unit>

    // memberships
    suspend fun listMemberships(
        branchId: String? = null,
        userId: String? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementMembership>>

    suspend fun createMembership(input: ManagementMembershipInput): Result<ManagementMembership>
    suspend fun updateMembership(id: String, input: ManagementMembershipInput): Result<ManagementMembership>
    suspend fun deleteMembership(id: String): Result<Unit>

    // people
    suspend fun listTeam(q: String? = null, branchId: String? = null, page: Int = 1, pageSize: Int = 25): Result<ApiPage<ManagementPerson>>
    suspend fun listCustomers(q: String? = null, branchId: String? = null, page: Int = 1, pageSize: Int = 25): Result<ApiPage<ManagementPerson>>

    // services
    suspend fun listServices(
        businessId: String? = null,
        branchId: String? = null,
        q: String? = null,
        includeInactive: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementService>>

    suspend fun getService(id: String): Result<ManagementService>
    suspend fun createService(input: ManagementServiceInput): Result<ManagementService>
    suspend fun updateService(id: String, input: ManagementServiceInput): Result<ManagementService>
    suspend fun setServiceActive(id: String, active: Boolean): Result<ManagementService>
    suspend fun deleteService(id: String): Result<Unit>

    // branch-service links
    suspend fun listBranchServices(branchId: String, page: Int = 1, pageSize: Int = 25): Result<ApiPage<ManagementBranchService>>
    suspend fun upsertBranchService(branchId: String, serviceId: String, input: ManagementBranchServiceInput): Result<ManagementBranchService>
    suspend fun deleteBranchService(branchId: String, serviceId: String): Result<Unit>

    // resource-service qualifications
    suspend fun listResourceServices(resourceId: String): Result<ManagementResourceServicesInput>
    suspend fun setResourceServices(resourceId: String, input: ManagementResourceServicesInput): Result<ManagementResourceServicesInput>

    // availability
    suspend fun listAvailability(
        businessId: String? = null,
        branchId: String? = null,
        resourceId: String? = null,
        from: String? = null,
        to: String? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementAvailability>>

    suspend fun createAvailability(input: ManagementAvailabilityInput): Result<ManagementAvailability>
    suspend fun updateAvailability(id: String, input: ManagementAvailabilityInput): Result<ManagementAvailability>
    suspend fun deleteAvailability(id: String): Result<Unit>

    // appointments
    suspend fun listAppointments(
        businessId: String? = null,
        branchId: String? = null,
        resourceId: String? = null,
        customerId: String? = null,
        date: String? = null,
        status: String? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementAppointment>>

    suspend fun getAppointment(id: String): Result<ManagementAppointment>
    suspend fun setAppointmentStatus(id: String, input: ManagementStatusInput): Result<ManagementAppointment>

    // reviews
    suspend fun listReviews(
        businessId: String? = null,
        branchId: String? = null,
        customerId: String? = null,
        status: String? = null,
        rating: Int? = null,
        includeInactive: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementReview>>

    suspend fun moderateReview(id: String, input: ManagementModerationInput): Result<ManagementReview>
    suspend fun replyReview(id: String, input: ManagementReplyInput): Result<ManagementReview>
    suspend fun setReviewActive(id: String, active: Boolean): Result<ManagementReview>
    suspend fun deleteReview(id: String): Result<Unit>

    // customer reviews (private ratings of customers)
    suspend fun listCustomerReviews(
        businessId: String? = null,
        branchId: String? = null,
        customerId: String? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementCustomerReview>>

    suspend fun listPendingCustomerReviews(page: Int = 1, pageSize: Int = 25): Result<ApiPage<ManagementAppointment>>
    suspend fun createCustomerReview(input: ManagementCustomerReviewInput): Result<ManagementCustomerReview>
    suspend fun updateCustomerReview(id: String, input: ManagementCustomerReviewInput): Result<ManagementCustomerReview>
    suspend fun deleteCustomerReview(id: String): Result<Unit>
}
