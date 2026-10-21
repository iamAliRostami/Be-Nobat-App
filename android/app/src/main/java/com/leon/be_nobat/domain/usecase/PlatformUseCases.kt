package com.leon.be_nobat.domain.usecase

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
import com.leon.be_nobat.domain.model.PlatformResetPasswordResponse
import com.leon.be_nobat.domain.repository.PlatformRepository

class PlatformListBusinessesUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(
        q: String? = null, includeInactive: Boolean? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementBusiness>> = repo.listBusinesses(q, includeInactive, page, pageSize)
}

class PlatformCreateBusinessUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(input: ManagementBusinessInput): Result<ManagementBusiness> = repo.createBusiness(input)
}

class PlatformUpdateBusinessUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String, input: ManagementBusinessInput): Result<ManagementBusiness> = repo.updateBusiness(id, input)
}

class PlatformToggleBusinessActiveUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String, active: Boolean): Result<ManagementBusiness> = repo.setBusinessActive(id, active)
}

class PlatformDeleteBusinessUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repo.deleteBusiness(id)
}

class PlatformListCatalogUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(
        q: String? = null, category: String? = null, includeInactive: Boolean? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementCatalog>> = repo.listCatalog(q, category, includeInactive, page, pageSize)
}

class PlatformCreateCatalogUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(input: ManagementCatalogInput): Result<ManagementCatalog> = repo.createCatalogItem(input)
}

class PlatformUpdateCatalogUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String, input: ManagementCatalogInput): Result<ManagementCatalog> = repo.updateCatalogItem(id, input)
}

class PlatformDeleteCatalogUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repo.deleteCatalogItem(id)
}

class PlatformListCategoriesUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(
        kind: String? = null, includeInactive: Boolean? = null, page: Int = 1, pageSize: Int = 100,
    ): Result<ApiPage<ManagementCategory>> = repo.listCategories(kind, includeInactive, page, pageSize)
}

class PlatformCreateCategoryUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(input: ManagementCategoryInput): Result<ManagementCategory> = repo.createCategory(input)
}

class PlatformUpdateCategoryUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String, input: ManagementCategoryInput): Result<ManagementCategory> = repo.updateCategory(id, input)
}

class PlatformDeleteCategoryUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repo.deleteCategory(id)
}

class PlatformListUsersUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(
        q: String? = null, role: String? = null, active: Boolean? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<ManagementUser>> = repo.listUsers(q, role, active, page, pageSize)
}

class PlatformCreateUserUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(input: ManagementUserInput): Result<ManagementCreatedUser> = repo.createUser(input)
}

class PlatformUpdateUserUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String, input: ManagementUserInput): Result<ManagementUser> = repo.updateUser(id, input)
}

class PlatformSetUserRolesUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String, roles: List<String>): Result<ManagementUser> =
        repo.setUserRoles(id, com.leon.be_nobat.domain.model.ManagementRolesInput(roles))
}

class PlatformToggleUserActiveUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String, active: Boolean): Result<ManagementUser> = repo.setUserActive(id, active)
}

class PlatformResetPasswordUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String): Result<PlatformResetPasswordResponse> = repo.resetUserPassword(id)
}

class PlatformListBusinessRequestsUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(
        status: String? = null, q: String? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<ApiPage<com.leon.be_nobat.domain.model.BusinessRequest>> = repo.listBusinessRequests(status, q, page, pageSize)
}

class PlatformApproveBusinessRequestUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String) = repo.approveBusinessRequest(id)
}

class PlatformRejectBusinessRequestUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String, note: String?) =
        repo.rejectBusinessRequest(id, com.leon.be_nobat.domain.model.ReviewBusinessRequestInput(note))
}

class PlatformApproveBranchUseCase(private val repo: PlatformRepository) {
    suspend operator fun invoke(id: String) = repo.approveBranch(id)
}
