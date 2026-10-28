package com.leon.be_nobat.domain.repository

import com.leon.be_nobat.data.remote.ApiPage
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

/**
 * Platform admin endpoints (`api v1 platform`). Visible only to
 * `PlatformAdmin`-role users.
 */
interface PlatformRepository {
    // businesses
    suspend fun listBusinesses(
        q: String? = null,
        includeInactive: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementBusiness>>

    suspend fun createBusiness(input: ManagementBusinessInput): Result<ManagementBusiness>
    suspend fun updateBusiness(id: String, input: ManagementBusinessInput): Result<ManagementBusiness>
    suspend fun setBusinessActive(id: String, active: Boolean): Result<ManagementBusiness>
    suspend fun deleteBusiness(id: String): Result<Unit>

    // catalog
    suspend fun listCatalog(
        q: String? = null,
        category: String? = null,
        includeInactive: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementCatalog>>

    suspend fun createCatalogItem(input: ManagementCatalogInput): Result<ManagementCatalog>
    suspend fun updateCatalogItem(id: String, input: ManagementCatalogInput): Result<ManagementCatalog>
    suspend fun deleteCatalogItem(id: String): Result<Unit>

    // categories
    suspend fun listCategories(
        kind: String? = null,
        includeInactive: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 100,
    ): Result<ApiPage<ManagementCategory>>

    suspend fun createCategory(input: ManagementCategoryInput): Result<ManagementCategory>
    suspend fun updateCategory(id: String, input: ManagementCategoryInput): Result<ManagementCategory>
    suspend fun deleteCategory(id: String): Result<Unit>

    // users
    suspend fun listUsers(
        q: String? = null,
        role: String? = null,
        active: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<ManagementUser>>

    suspend fun getUser(id: String): Result<ManagementUser>
    suspend fun createUser(input: ManagementUserInput): Result<ManagementCreatedUser>
    suspend fun updateUser(id: String, input: ManagementUserInput): Result<ManagementUser>
    suspend fun setUserRoles(id: String, input: ManagementRolesInput): Result<ManagementUser>
    suspend fun setUserActive(id: String, active: Boolean): Result<ManagementUser>
    suspend fun resetUserPassword(id: String): Result<PlatformResetPasswordResponse>

    // platform pricing
    suspend fun getPlatformPricing(): Result<com.leon.be_nobat.domain.model.PlatformPricing>
    suspend fun updatePlatformPricing(input: com.leon.be_nobat.domain.model.PlatformPricingInput): Result<com.leon.be_nobat.domain.model.PlatformPricing>

    // business requests
    suspend fun listBusinessRequests(
        status: String? = null,
        q: String? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): Result<ApiPage<com.leon.be_nobat.domain.model.BusinessRequest>>

    suspend fun approveBusinessRequest(id: String): Result<com.leon.be_nobat.domain.model.BusinessRequest>
    suspend fun rejectBusinessRequest(id: String, input: com.leon.be_nobat.domain.model.ReviewBusinessRequestInput): Result<com.leon.be_nobat.domain.model.BusinessRequest>

    // branch approval
    suspend fun approveBranch(id: String): Result<com.leon.be_nobat.domain.model.ManagementBranch>
}
