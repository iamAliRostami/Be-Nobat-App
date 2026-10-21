package com.leon.be_nobat.data.repository

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.domain.model.ManagementAvailability
import com.leon.be_nobat.domain.model.ManagementAvailabilityInput
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBranchInput
import com.leon.be_nobat.domain.model.ManagementBranchService
import com.leon.be_nobat.domain.model.ManagementBranchServiceInput
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementBusinessInput
import com.leon.be_nobat.domain.model.ManagementContext
import com.leon.be_nobat.domain.model.ManagementCustomerReview
import com.leon.be_nobat.domain.model.ManagementCustomerReviewInput
import com.leon.be_nobat.domain.model.ManagementDashboard
import com.leon.be_nobat.domain.model.ManagementMembership
import com.leon.be_nobat.domain.model.ManagementMembershipInput
import com.leon.be_nobat.domain.model.ManagementModerationInput
import com.leon.be_nobat.domain.model.ManagementPerson
import com.leon.be_nobat.domain.model.ManagementReplyInput
import com.leon.be_nobat.domain.model.ManagementResource
import com.leon.be_nobat.domain.model.ManagementResourceInput
import com.leon.be_nobat.domain.model.ManagementResourceServicesInput
import com.leon.be_nobat.domain.model.ManagementReview
import com.leon.be_nobat.domain.model.ManagementService
import com.leon.be_nobat.domain.model.ManagementServiceInput
import com.leon.be_nobat.domain.model.ManagementStatusInput
import com.leon.be_nobat.domain.model.ManagementTeamMemberInput
import com.leon.be_nobat.domain.model.ManagementCustomerInput
import com.leon.be_nobat.domain.repository.ManagementRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Implements admin/business-management endpoints against the .NET mobile API.
 *
 * The mapping is a thin layer that:
 *  1. Encodes each typed input ([ManagementBranchInput], etc.) into a JSON
 *     object using kotlinx-serialization.
 *  2. Delegates to [BeNobatApiClient] for the actual HTTP call.
 *
 * The JSON encoder reuses the project's Koin-provided `Json` instance, which
 * is configured with `ignoreUnknownKeys = true` and `encodeDefaults = true`,
 * so optional fields are emitted as null/empty rather than dropped.
 */
class ManagementRepositoryImpl(
    private val api: BeNobatApiClient,
    private val json: Json,
) : ManagementRepository {

    // ---------- overview ----------
    override suspend fun dashboard(): Result<ManagementDashboard> = api.adminDashboard()
    override suspend fun analytics(
        businessId: String?,
        branchId: String?,
        resourceId: String?,
        from: String?,
        to: String?,
    ): Result<com.leon.be_nobat.domain.model.ManagementAnalyticsSummary> =
        api.adminAnalytics(businessId, branchId, resourceId, from, to)
    override suspend fun context(): Result<ManagementContext> = api.adminContext()

    // ---------- businesses ----------
    override suspend fun listBusinesses(q: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementBusiness>> =
        api.adminListBusinesses(q, page, pageSize)

    override suspend fun getBusiness(id: String): Result<ManagementBusiness> = api.adminGetBusiness(id)

    override suspend fun updateBusiness(id: String, input: ManagementBusinessInput): Result<ManagementBusiness> =
        api.adminUpdateBusiness(id, encode(input))

    // ---------- branches ----------
    override suspend fun listBranches(
        businessId: String?, q: String?, includeInactive: Boolean?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementBranch>> = api.adminListBranches(businessId, q, includeInactive, page, pageSize)

    override suspend fun createBranch(input: ManagementBranchInput): Result<ManagementBranch> =
        api.adminCreateBranch(encode(input))

    override suspend fun getBranch(id: String): Result<ManagementBranch> = api.adminGetBranch(id)

    override suspend fun updateBranch(id: String, input: ManagementBranchInput): Result<ManagementBranch> =
        api.adminUpdateBranch(id, encode(input))

    override suspend fun setBranchActive(id: String, active: Boolean): Result<ManagementBranch> =
        api.adminSetBranchActive(id, encodeActive(active))

    override suspend fun deleteBranch(id: String): Result<Unit> = api.adminDeleteBranch(id)
    override suspend fun approveBranch(id: String): Result<ManagementBranch> = api.platformApproveBranch(id)

    // ---------- resources ----------
    override suspend fun listResources(
        branchId: String?, businessId: String?, includeInactive: Boolean?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementResource>> =
        api.adminListResources(branchId, businessId, includeInactive, page, pageSize)

    override suspend fun createResource(input: ManagementResourceInput): Result<ManagementResource> =
        api.adminCreateResource(encode(input))

    override suspend fun updateResource(id: String, input: ManagementResourceInput): Result<ManagementResource> =
        api.adminUpdateResource(id, encode(input))

    override suspend fun setResourceActive(id: String, active: Boolean): Result<ManagementResource> =
        api.adminSetResourceActive(id, encodeActive(active))

    override suspend fun deleteResource(id: String): Result<Unit> = api.adminDeleteResource(id)

    // ---------- memberships ----------
    override suspend fun listMemberships(
        branchId: String?, userId: String?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementMembership>> = api.adminListMemberships(branchId, userId, page, pageSize)

    override suspend fun createMembership(input: ManagementMembershipInput): Result<ManagementMembership> =
        api.adminCreateMembership(encode(input))

    override suspend fun updateMembership(id: String, input: ManagementMembershipInput): Result<ManagementMembership> =
        api.adminUpdateMembership(id, encode(input))

    override suspend fun deleteMembership(id: String): Result<Unit> = api.adminDeleteMembership(id)

    // ---------- people ----------
    override suspend fun listTeam(q: String?, branchId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementPerson>> =
        api.adminListTeam(q, branchId, page, pageSize)

    override suspend fun listCustomers(q: String?, branchId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementPerson>> =
        api.adminListCustomers(q, branchId, page, pageSize)

    override suspend fun createTeamMember(input: ManagementTeamMemberInput): Result<ManagementPerson> =
        api.adminCreateTeamMember(encode(input))

    override suspend fun createCustomer(input: ManagementCustomerInput): Result<ManagementPerson> =
        api.adminCreateCustomer(encode(input))

    override suspend fun setPersonActive(id: String, active: Boolean): Result<ManagementPerson> =
        api.adminSetPersonActive(id, encodeActive(active))

    // ---------- services ----------
    override suspend fun listServices(
        businessId: String?, branchId: String?, q: String?, includeInactive: Boolean?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementService>> = api.adminListServices(businessId, branchId, q, includeInactive, page, pageSize)

    override suspend fun getService(id: String): Result<ManagementService> = api.adminGetService(id)

    override suspend fun createService(input: ManagementServiceInput): Result<ManagementService> =
        api.adminCreateService(encode(input))

    override suspend fun updateService(id: String, input: ManagementServiceInput): Result<ManagementService> =
        api.adminUpdateService(id, encode(input))

    override suspend fun setServiceActive(id: String, active: Boolean): Result<ManagementService> =
        api.adminSetServiceActive(id, encodeActive(active))

    override suspend fun deleteService(id: String): Result<Unit> = api.adminDeleteService(id)

    // ---------- branch-service links ----------
    override suspend fun listBranchServices(branchId: String, page: Int, pageSize: Int): Result<ApiPage<ManagementBranchService>> =
        api.adminListBranchServices(branchId, page, pageSize)

    override suspend fun upsertBranchService(
        branchId: String, serviceId: String, input: ManagementBranchServiceInput,
    ): Result<ManagementBranchService> =
        api.adminUpsertBranchService(branchId, serviceId, encode(input))

    override suspend fun deleteBranchService(branchId: String, serviceId: String): Result<Unit> =
        api.adminDeleteBranchService(branchId, serviceId)

    // ---------- resource-service qualifications ----------
    override suspend fun listResourceServices(resourceId: String): Result<ManagementResourceServicesInput> =
        api.adminListResourceServices(resourceId)

    override suspend fun setResourceServices(
        resourceId: String, input: ManagementResourceServicesInput,
    ): Result<ManagementResourceServicesInput> =
        api.adminSetResourceServices(resourceId, encode(input))

    // ---------- availability ----------
    override suspend fun listAvailability(
        businessId: String?, branchId: String?, resourceId: String?, from: String?, to: String?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementAvailability>> =
        api.adminListAvailability(businessId, branchId, resourceId, from, to, page, pageSize)

    override suspend fun createAvailability(input: ManagementAvailabilityInput): Result<ManagementAvailability> =
        api.adminCreateAvailability(encode(input))

    override suspend fun updateAvailability(id: String, input: ManagementAvailabilityInput): Result<ManagementAvailability> =
        api.adminUpdateAvailability(id, encode(input))

    override suspend fun deleteAvailability(id: String): Result<Unit> = api.adminDeleteAvailability(id)

    // ---------- appointments ----------
    override suspend fun listAppointments(
        businessId: String?, branchId: String?, resourceId: String?, customerId: String?,
        date: String?, status: String?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementAppointment>> =
        api.adminListAppointments(businessId, branchId, resourceId, customerId, date, status, page, pageSize)

    override suspend fun getAppointment(id: String): Result<ManagementAppointment> = api.adminGetAppointment(id)

    override suspend fun setAppointmentStatus(id: String, input: ManagementStatusInput): Result<ManagementAppointment> =
        api.adminSetAppointmentStatus(id, encode(input))

    // ---------- reviews ----------
    override suspend fun listReviews(
        businessId: String?, branchId: String?, customerId: String?, status: String?, rating: Int?,
        includeInactive: Boolean?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementReview>> =
        api.adminListReviews(businessId, branchId, customerId, status, rating, includeInactive, page, pageSize)

    override suspend fun moderateReview(id: String, input: ManagementModerationInput): Result<ManagementReview> =
        api.adminModerateReview(id, encode(input))

    override suspend fun replyReview(id: String, input: ManagementReplyInput): Result<ManagementReview> =
        api.adminReplyReview(id, encode(input))

    override suspend fun setReviewActive(id: String, active: Boolean): Result<ManagementReview> =
        api.adminSetReviewActive(id, encodeActive(active))

    override suspend fun deleteReview(id: String): Result<Unit> = api.adminDeleteReview(id)

    // ---------- customer reviews ----------
    override suspend fun listCustomerReviews(
        businessId: String?, branchId: String?, customerId: String?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementCustomerReview>> =
        api.adminListCustomerReviews(businessId, branchId, customerId, page, pageSize)

    override suspend fun listPendingCustomerReviews(page: Int, pageSize: Int): Result<ApiPage<ManagementAppointment>> =
        api.adminListPendingCustomerReviews(page, pageSize)

    override suspend fun createCustomerReview(input: ManagementCustomerReviewInput): Result<ManagementCustomerReview> =
        api.adminCreateCustomerReview(encode(input))

    override suspend fun updateCustomerReview(id: String, input: ManagementCustomerReviewInput): Result<ManagementCustomerReview> =
        api.adminUpdateCustomerReview(id, encode(input))

    override suspend fun deleteCustomerReview(id: String): Result<Unit> = api.adminDeleteCustomerReview(id)

    // ---------- helpers ----------
    private fun encodeActive(active: Boolean): JsonObject = buildJsonObject { put("active", active) }

    private inline fun <reified T> encode(value: T): JsonObject =
        json.encodeToJsonElement(value) as JsonObject
}
