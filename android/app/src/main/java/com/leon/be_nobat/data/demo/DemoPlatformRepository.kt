package com.leon.be_nobat.data.demo

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.domain.model.*
import com.leon.be_nobat.domain.repository.PlatformRepository

class DemoPlatformRepository(private val dataStore: DemoDataStore, private val tokenManager: TokenManager) : PlatformRepository {
    private suspend fun requireAdmin() {
        val id = requireNotNull(tokenManager.currentSession()).user.id
        dataStore.read { s -> require(s.users.any { it.id == id && it.active && "PlatformAdmin" in it.roles }) }
    }

    override suspend fun listBusinesses(q: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementBusiness>> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.read { s ->
            s.businesses.filter {
                it.name.matchesQuery(q) && (includeInactive == true || it.active)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun createBusiness(input: ManagementBusinessInput): Result<ManagementBusiness> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        val actor = requireNotNull(tokenManager.currentSession()).user
        dataStore.update { s ->
            val id = demoId()
            require(input.name.isNotBlank())
            val value = ManagementBusiness(
                id = id,
                name = input.name,
                slug = input.slug,
                category = input.category,
                city = input.city,
                description = input.description,
                requiresApproval = input.requiresApproval,
                active = true
            )

            val branch = ManagementBranch(id = demoId(), businessId = id, name = input.firstBranchName, active = true)
            val ownerId = input.ownerId ?: actor.id
            val ownerUser = s.users.firstOrNull { it.id == ownerId }
            val ownerDisplayName = ownerUser?.displayName ?: actor.displayName
            val ownerEmail = ownerUser?.email ?: actor.email
            val membership = ManagementMembership(
                id = demoId(),
                branchId = branch.id,
                userId = ownerId,
                role = "Owner",
                displayName = ownerDisplayName,
                email = ownerEmail
            )

            s.copy(
                businesses = listOf(value) + s.businesses,
                branches = s.branches + branch,
                memberships = s.memberships + membership
            ) to value
        }
    }

    override suspend fun updateBusiness(id: String, input: ManagementBusinessInput): Result<ManagementBusiness> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val old = s.businesses.first { it.id == id }
            require(input.name.isNotBlank())
            val value = old.copy(
                name = input.name,
                slug = input.slug,
                category = input.category,
                city = input.city,
                description = input.description,
                requiresApproval = input.requiresApproval
            )
            s.copy(businesses = s.businesses.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun setBusinessActive(id: String, active: Boolean): Result<ManagementBusiness> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val value = s.businesses.first { it.id == id }.copy(active = active)
            s.copy(businesses = s.businesses.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun deleteBusiness(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            require(s.businesses.any { it.id == id })
            require(!(s.branches.any { it.businessId == id } || s.services.any { it.businessId == id } || s.appointments.any { it.businessId == id })) {
                "ابتدا وابستگی‌ها را حذف کنید یا این مورد را غیرفعال کنید."
            }
            s.copy(businesses = s.businesses.filterNot { it.id == id }, favorites = s.favorites - id) to Unit
        }
    }

    override suspend fun listCatalog(q: String?, category: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementCatalog>> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.read { s ->
            s.catalog.filter {
                it.name.matchesQuery(q) &&
                (category == null || it.category == category) &&
                (includeInactive == true || it.active)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun createCatalogItem(input: ManagementCatalogInput): Result<ManagementCatalog> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val id = demoId()
            require(input.name.isNotBlank() && input.suggestedDurationMinutes > 0)
            val value = ManagementCatalog(
                id = id,
                name = input.name,
                slug = input.slug,
                category = input.category,
                description = input.description,
                suggestedDurationMinutes = input.suggestedDurationMinutes,
                isPublished = input.isPublished,
                active = true
            )
            s.copy(catalog = listOf(value) + s.catalog) to value
        }
    }

    override suspend fun updateCatalogItem(id: String, input: ManagementCatalogInput): Result<ManagementCatalog> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val old = s.catalog.first { it.id == id }
            require(input.name.isNotBlank() && input.suggestedDurationMinutes > 0)
            val value = old.copy(
                name = input.name,
                slug = input.slug,
                category = input.category,
                description = input.description,
                suggestedDurationMinutes = input.suggestedDurationMinutes,
                isPublished = input.isPublished
            )
            s.copy(catalog = s.catalog.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun deleteCatalogItem(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            require(s.catalog.any { it.id == id })
            s.copy(catalog = s.catalog.filterNot { it.id == id }) to Unit
        }
    }

    override suspend fun listCategories(kind: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementCategory>> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.read { s ->
            s.categories.filter {
                (kind == null || it.kind == kind) &&
                (includeInactive == true || it.active)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun createCategory(input: ManagementCategoryInput): Result<ManagementCategory> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val id = demoId()
            require(input.name.isNotBlank())
            val value = ManagementCategory(
                id = id,
                name = input.name,
                kind = input.kind,
                sortOrder = input.sortOrder,
                active = input.isActive
            )
            s.copy(categories = listOf(value) + s.categories) to value
        }
    }

    override suspend fun updateCategory(id: String, input: ManagementCategoryInput): Result<ManagementCategory> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val old = s.categories.first { it.id == id }
            require(input.name.isNotBlank())
            val value = old.copy(
                name = input.name,
                kind = input.kind,
                sortOrder = input.sortOrder,
                active = input.isActive
            )
            s.copy(categories = s.categories.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            require(s.categories.any { it.id == id })
            s.copy(categories = s.categories.filterNot { it.id == id }) to Unit
        }
    }

    override suspend fun listUsers(q: String?, role: String?, active: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementUser>> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.read { s ->
            s.users.filter {
                it.displayName.matchesQuery(q) &&
                (role == null || role in it.roles) &&
                (active == null || it.active == active)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun getUser(id: String): Result<ManagementUser> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.read { s -> s.users.first { it.id == id } }
    }

    override suspend fun createUser(input: ManagementUserInput): Result<ManagementCreatedUser> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val id = demoId()
            require(input.displayName.isNotBlank())
            require(s.users.none { it.id != id && it.email.equals(input.email, true) })
            val value = ManagementUser(
                id = id,
                displayName = input.displayName,
                email = input.email,
                phoneNumber = input.phoneNumber,
                roles = listOf(input.role),
                active = true
            )
            val temporaryPassword = "Demo1234!"
            s.copy(
                users = listOf(value) + s.users,
                passwords = s.passwords + (id to temporaryPassword)
            ) to ManagementCreatedUser(
                id = id,
                displayName = value.displayName,
                email = value.email,
                phoneNumber = value.phoneNumber,
                temporaryPassword = temporaryPassword
            )
        }
    }

    override suspend fun updateUser(id: String, input: ManagementUserInput): Result<ManagementUser> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val old = s.users.first { it.id == id }
            require(input.displayName.isNotBlank())
            require(s.users.none { it.id != id && it.email.equals(input.email, true) })
            val value = old.copy(
                displayName = input.displayName,
                email = input.email,
                phoneNumber = input.phoneNumber,
                roles = listOf(input.role)
            )
            s.copy(users = s.users.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun setUserRoles(id: String, input: ManagementRolesInput): Result<ManagementUser> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val value = s.users.first { it.id == id }.copy(roles = input.roles)
            s.copy(users = s.users.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun setUserActive(id: String, active: Boolean): Result<ManagementUser> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val value = s.users.first { it.id == id }.copy(active = active)
            s.copy(users = s.users.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun resetUserPassword(id: String): Result<PlatformResetPasswordResponse> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            require(s.users.any { it.id == id })
            s.copy(passwords = s.passwords + (id to "Demo1234!")) to PlatformResetPasswordResponse(
                userId = id,
                temporaryPassword = "Demo1234!"
            )
        }
    }
}
