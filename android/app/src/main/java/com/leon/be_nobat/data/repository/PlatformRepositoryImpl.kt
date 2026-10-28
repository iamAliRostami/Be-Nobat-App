package com.leon.be_nobat.data.repository

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementBusinessInput
import com.leon.be_nobat.domain.model.ManagementCatalog
import com.leon.be_nobat.domain.model.ManagementCatalogInput
import com.leon.be_nobat.domain.model.ManagementCategory
import com.leon.be_nobat.domain.model.ManagementCategoryInput
import com.leon.be_nobat.domain.model.ManagementCreatedUser
import com.leon.be_nobat.domain.model.ManagementUser
import com.leon.be_nobat.domain.model.ManagementUserInput
import com.leon.be_nobat.domain.model.ManagementRolesInput
import com.leon.be_nobat.domain.model.PlatformResetPasswordResponse
import com.leon.be_nobat.domain.repository.PlatformRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Implements `api v1 platform` endpoints (PlatformAdmin only) against
 * the .NET mobile API.
 */
class PlatformRepositoryImpl(
    private val api: BeNobatApiClient,
    private val json: Json,
) : PlatformRepository {

    // businesses
    override suspend fun listBusinesses(
        q: String?, includeInactive: Boolean?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementBusiness>> = api.platformListBusinesses(q, includeInactive, page, pageSize)

    override suspend fun createBusiness(input: ManagementBusinessInput): Result<ManagementBusiness> =
        api.platformCreateBusiness(encode(input))

    override suspend fun updateBusiness(id: String, input: ManagementBusinessInput): Result<ManagementBusiness> =
        api.platformUpdateBusiness(id, encode(input))

    override suspend fun setBusinessActive(id: String, active: Boolean): Result<ManagementBusiness> =
        api.platformSetBusinessActive(id, encodeActive(active))

    override suspend fun deleteBusiness(id: String): Result<Unit> = api.platformDeleteBusiness(id)

    // catalog
    override suspend fun listCatalog(
        q: String?, category: String?, includeInactive: Boolean?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementCatalog>> = api.platformListCatalog(q, category, includeInactive, page, pageSize)

    override suspend fun createCatalogItem(input: ManagementCatalogInput): Result<ManagementCatalog> =
        api.platformCreateCatalogItem(encode(input))

    override suspend fun updateCatalogItem(id: String, input: ManagementCatalogInput): Result<ManagementCatalog> =
        api.platformUpdateCatalogItem(id, encode(input))

    override suspend fun deleteCatalogItem(id: String): Result<Unit> = api.platformDeleteCatalogItem(id)

    // categories
    override suspend fun listCategories(
        kind: String?, includeInactive: Boolean?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementCategory>> = api.platformListCategories(kind, includeInactive, page, pageSize)

    override suspend fun createCategory(input: ManagementCategoryInput): Result<ManagementCategory> =
        api.platformCreateCategory(encode(input))

    override suspend fun updateCategory(id: String, input: ManagementCategoryInput): Result<ManagementCategory> =
        api.platformUpdateCategory(id, encode(input))

    override suspend fun deleteCategory(id: String): Result<Unit> = api.platformDeleteCategory(id)

    // users
    override suspend fun listUsers(
        q: String?, role: String?, active: Boolean?, page: Int, pageSize: Int,
    ): Result<ApiPage<ManagementUser>> = api.platformListUsers(q, role, active, page, pageSize)

    override suspend fun getUser(id: String): Result<ManagementUser> = api.platformGetUser(id)

    override suspend fun createUser(input: ManagementUserInput): Result<ManagementCreatedUser> =
        api.platformCreateUser(encode(input))

    override suspend fun updateUser(id: String, input: ManagementUserInput): Result<ManagementUser> =
        api.platformUpdateUser(id, encode(input))

    override suspend fun setUserRoles(id: String, input: ManagementRolesInput): Result<ManagementUser> =
        api.platformSetUserRoles(id, encode(input))

    override suspend fun setUserActive(id: String, active: Boolean): Result<ManagementUser> =
        api.platformSetUserActive(id, encodeActive(active))

    override suspend fun resetUserPassword(id: String): Result<PlatformResetPasswordResponse> =
        api.platformResetUserPassword(id)

    override suspend fun getPlatformPricing(): Result<com.leon.be_nobat.domain.model.PlatformPricing> =
        api.getPlatformPricingAdmin()

    override suspend fun updatePlatformPricing(input: com.leon.be_nobat.domain.model.PlatformPricingInput): Result<com.leon.be_nobat.domain.model.PlatformPricing> =
        api.updatePlatformPricingAdmin(encode(input))

    override suspend fun listBusinessRequests(
        status: String?, q: String?, page: Int, pageSize: Int,
    ): Result<ApiPage<com.leon.be_nobat.domain.model.BusinessRequest>> =
        api.platformListBusinessRequests(status, q, page, pageSize)

    override suspend fun approveBusinessRequest(id: String): Result<com.leon.be_nobat.domain.model.BusinessRequest> =
        api.platformApproveBusinessRequest(id)

    override suspend fun rejectBusinessRequest(
        id: String, input: com.leon.be_nobat.domain.model.ReviewBusinessRequestInput,
    ): Result<com.leon.be_nobat.domain.model.BusinessRequest> =
        api.platformRejectBusinessRequest(id, encode(input))

    override suspend fun approveBranch(id: String): Result<com.leon.be_nobat.domain.model.ManagementBranch> =
        api.platformApproveBranch(id)

    private fun encodeActive(active: Boolean): JsonObject = buildJsonObject { put("active", active) }

    private inline fun <reified T> encode(value: T): JsonObject =
        json.encodeToJsonElement(value) as JsonObject
}
