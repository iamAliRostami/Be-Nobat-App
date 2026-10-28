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
            }.map { it.copy(firstBranchId = it.firstBranchId ?: s.branches.firstOrNull { b -> b.businessId == it.id && b.active }?.id) }.demoPage(page, pageSize)
        }
    }

    override suspend fun createBusiness(input: ManagementBusinessInput): Result<ManagementBusiness> = runCatching {
        require(com.leon.be_nobat.domain.CancellationPolicy.valid(input.cancellationNoticeMinutes)) { "مهلت لغو معتبر نیست." }
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
                cancellationNoticeMinutes = input.cancellationNoticeMinutes ?: 120,
                active = true
            )

            val weeklyHours = input.firstBranchWeeklyHours ?: WeeklyHours.defaults()
            require(WeeklyHours.validate(weeklyHours) == null)
            val branch = ManagementBranch(id = demoId(), businessId = id, name = input.firstBranchName.ifBlank { "شعبه مرکزی" }, address = input.firstBranchAddress.orEmpty(), openHour = 8, closeHour = 20, weeklyHours = weeklyHours, active = true, businessName = input.name)
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
                businesses = listOf(value.copy(firstBranchId = branch.id)) + s.businesses,
                branches = s.branches + branch,
                memberships = s.memberships + membership
            ) to value.copy(firstBranchId = branch.id)
        }
    }

    override suspend fun updateBusiness(id: String, input: ManagementBusinessInput): Result<ManagementBusiness> = runCatching {
        require(com.leon.be_nobat.domain.CancellationPolicy.valid(input.cancellationNoticeMinutes)) { "مهلت لغو معتبر نیست." }
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val old = s.businesses.firstOrNull { it.id == id } ?: error("کسب‌وکار مورد نظر یافت نشد.")
            require(input.name.isNotBlank())
            val value = old.copy(
                name = input.name,
                slug = input.slug,
                category = input.category,
                city = input.city,
                description = input.description,
                requiresApproval = input.requiresApproval,
                cancellationNoticeMinutes = input.cancellationNoticeMinutes ?: old.cancellationNoticeMinutes
            )
            var branches = s.branches
            if (input.firstBranchWeeklyHours != null || input.firstBranchAddress != null) {
                input.firstBranchWeeklyHours?.let { require(WeeklyHours.validate(it) == null) }
                val branch = branches.firstOrNull { it.id == old.firstBranchId && it.active } ?: branches.firstOrNull { it.businessId == id && it.active }
                    ?: ManagementBranch(id = demoId(), businessId = id, name = "شعبه مرکزی", openHour = 8, closeHour = 20, active = true, businessName = input.name)
                val updated = branch.copy(name = input.firstBranchName.ifBlank { "شعبه مرکزی" }, address = input.firstBranchAddress ?: branch.address,
                    weeklyHours = input.firstBranchWeeklyHours ?: branch.weeklyHours, businessName = input.name)
                branches = if (branches.any { it.id == branch.id }) branches.map { if (it.id == branch.id) updated else it } else branches + updated
            }
            s.copy(businesses = s.businesses.map { if (it.id == id) value else it }, branches = branches) to value.copy(firstBranchId = old.firstBranchId ?: branches.firstOrNull { it.businessId == id && it.active }?.id)
        }
    }

    override suspend fun setBusinessActive(id: String, active: Boolean): Result<ManagementBusiness> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val old = s.businesses.firstOrNull { it.id == id } ?: error("کسب‌وکار مورد نظر یافت نشد.")
            val value = old.copy(active = active)
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
            val old = s.catalog.firstOrNull { it.id == id } ?: error("آیتم کاتالوگ یافت نشد.")
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
            val old = s.categories.firstOrNull { it.id == id } ?: error("دسته‌بندی یافت نشد.")
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
                (it.displayName.matchesQuery(q) || it.email?.matchesQuery(q) == true || it.phoneNumber?.matchesQuery(q) == true) &&
                (role == null || role in it.roles) &&
                (active == null || it.active == active)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun getUser(id: String): Result<ManagementUser> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.read { s -> s.users.firstOrNull { it.id == id } ?: error("کاربر یافت نشد.") }
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
            val old = s.users.firstOrNull { it.id == id } ?: error("کاربر یافت نشد.")
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
            val old = s.users.firstOrNull { it.id == id } ?: error("کاربر یافت نشد.")
            val value = old.copy(roles = input.roles)
            s.copy(users = s.users.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun setUserActive(id: String, active: Boolean): Result<ManagementUser> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val old = s.users.firstOrNull { it.id == id } ?: error("کاربر یافت نشد.")
            val value = old.copy(active = active)
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

    override suspend fun getPlatformPricing(): Result<PlatformPricing> = runCatching {
        simulateNetworkDelay()
        dataStore.read { it.platformPricing }
    }

    override suspend fun updatePlatformPricing(input: PlatformPricingInput): Result<PlatformPricing> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        require(input.baseBusinessPrice >= 0 && input.extraBranchPrice >= 0) { "مبالغ تعرفه نمی‌توانند منفی باشند." }
        dataStore.update { s ->
            val updated = s.platformPricing.copy(
                baseBusinessPrice = input.baseBusinessPrice,
                extraBranchPrice = input.extraBranchPrice,
                cardNumber = input.cardNumber.trim(),
                iban = input.iban.trim(),
                accountHolder = input.accountHolder.trim(),
                paymentInstructions = input.paymentInstructions.trim(),
            )
            s.copy(platformPricing = updated) to updated
        }
    }

    override suspend fun listBusinessRequests(
        status: String?,
        q: String?,
        page: Int,
        pageSize: Int,
    ): Result<ApiPage<BusinessRequest>> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.read { s ->
            val userMap = s.users.associate { it.id to it }
            s.businessRequests.filter { r ->
                (status.isNullOrBlank() || r.status.equals(status, ignoreCase = true)) &&
                (q.isNullOrBlank() || r.businessName.matchesQuery(q) || r.category.matchesQuery(q) || r.city.matchesQuery(q) || r.contactPhone.matchesQuery(q) || (r.userDisplayName ?: userMap[r.userId]?.displayName ?: "").matchesQuery(q))
            }.map { r ->
                val u = userMap[r.userId]
                r.copy(
                    userDisplayName = r.userDisplayName ?: u?.displayName,
                    userPhoneNumber = r.userPhoneNumber ?: u?.phoneNumber ?: r.contactPhone
                )
            }.sortedByDescending { it.createdAt }.demoPage(page, pageSize)
        }
    }

    override suspend fun approveBusinessRequest(id: String): Result<BusinessRequest> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val req = s.businessRequests.firstOrNull { it.id == id } ?: error("درخواست یافت نشد.")
            require(req.status == "Pending") { "درخواست قبلاً بررسی شده است." }

            val bizId = demoId()
            val branchId = demoId()
            val user = s.users.firstOrNull { it.id == req.userId }

            val business = ManagementBusiness(
                id = bizId,
                name = req.businessName,
                slug = req.businessName.lowercase().replace(" ", "-"),
                category = req.category,
                city = req.city,
                description = req.description,
                requiresApproval = false,
                active = true,
                firstBranchId = branchId,
            )

            val branch = ManagementBranch(
                id = branchId,
                businessId = bizId,
                name = req.firstBranchName.ifBlank { "شعبه مرکزی" },
                address = req.firstBranchAddress,
                timeZoneId = "Asia/Tehran",
                openHour = 9,
                closeHour = 18,
                weeklyHours = WeeklyHours.defaults(),
                active = true,
                isApproved = true,
                businessName = req.businessName,
            )

            val membership = ManagementMembership(
                id = demoId(),
                branchId = branchId,
                userId = req.userId,
                role = "Owner",
                displayName = user?.displayName ?: req.userDisplayName ?: "کاربر",
                email = user?.email,
            )

            val updatedUsers = s.users.map { u ->
                if (u.id == req.userId) {
                    val newRoles = if ("Owner" !in u.roles) u.roles + "Owner" else u.roles
                    u.copy(roles = newRoles)
                } else u
            }

            val approvedReq = req.copy(
                status = "Approved",
                approvedBusinessId = bizId,
            )

            s.copy(
                businesses = listOf(business) + s.businesses,
                branches = listOf(branch) + s.branches,
                memberships = s.memberships + membership,
                users = updatedUsers,
                businessRequests = s.businessRequests.map { if (it.id == id) approvedReq else it }
            ) to approvedReq
        }
    }

    override suspend fun rejectBusinessRequest(id: String, input: ReviewBusinessRequestInput): Result<BusinessRequest> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val req = s.businessRequests.firstOrNull { it.id == id } ?: error("درخواست یافت نشد.")
            require(req.status == "Pending") { "درخواست قبلاً بررسی شده است." }

            val rejectedReq = req.copy(
                status = "Rejected",
                reviewNote = input.note?.ifBlank { "درخواست توسط مدیریت سامانه رد شد." } ?: "درخواست توسط مدیریت سامانه رد شد."
            )

            s.copy(
                businessRequests = s.businessRequests.map { if (it.id == id) rejectedReq else it }
            ) to rejectedReq
        }
    }

    override suspend fun approveBranch(id: String): Result<ManagementBranch> = runCatching {
        simulateNetworkDelay()
        requireAdmin()
        dataStore.update { s ->
            val branch = s.branches.firstOrNull { it.id == id } ?: error("شعبه یافت نشد.")
            val approved = branch.copy(isApproved = true)
            s.copy(branches = s.branches.map { if (it.id == id) approved else it }) to approved
        }
    }
}
