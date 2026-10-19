package com.leon.be_nobat.data.demo

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.domain.model.*
import com.leon.be_nobat.domain.repository.ManagementRepository

class DemoManagementRepository(private val dataStore: DemoDataStore, private val tokenManager: TokenManager) : ManagementRepository {
    private suspend fun requireActor(manageBusiness: Boolean) {
        val id = requireNotNull(tokenManager.currentSession()).user.id
        dataStore.read { s ->
            val u = s.users.first { it.id == id && it.active }
            require(u.roles.any { it in if (manageBusiness) listOf("PlatformAdmin", "Owner", "Manager") else listOf("PlatformAdmin", "Owner", "Manager", "Staff") })
        }
    }

    private suspend fun <T> read(block: (DemoDatabaseState) -> T): T {
        val id = requireNotNull(tokenManager.currentSession()).user.id
        return dataStore.read { state -> block(state.visibleTo(id)) }
    }

    private suspend fun <T> update(block: (DemoDatabaseState) -> Pair<DemoDatabaseState, T>): T {
        val id = requireNotNull(tokenManager.currentSession()).user.id
        return dataStore.update { state ->
            val visible = state.visibleTo(id)
            val (next, result) = block(visible)
            state.mergeVisible(visible, next) to result
        }
    }

    override suspend fun dashboard(): Result<ManagementDashboard> = runCatching {
        simulateNetworkDelay()
        read { s ->
            ManagementDashboard(
                businesses = s.businesses.count { it.active },
                branches = s.branches.count { it.active },
                appointments = s.appointments.size,
                pending = s.appointments.count { it.status == "Pending" },
                upcoming = s.appointments.count { it.status in listOf("Pending", "Confirmed") && it.startsAt >= demoNow() },
                completed = s.appointments.count { it.status == "Completed" },
                revenue = s.appointments.filter { it.status == "Completed" }.sumOf { it.finalPrice },
                pendingReviews = s.reviews.count { it.status == "Pending" }
            )
        }
    }

    override suspend fun context(): Result<ManagementContext> = runCatching {
        simulateNetworkDelay()
        val user = requireNotNull(tokenManager.currentSession()).user
        read { s ->
            ManagementContext(
                userId = user.id,
                roles = user.roles,
                isPlatformAdmin = user.isPlatformAdmin,
                canManageBusiness = user.roles.any { it in listOf("PlatformAdmin", "Owner", "Manager") },
                canManageAppointments = user.isStaff,
                memberships = s.memberships.filter { it.userId == user.id }.map { m ->
                    val b = s.branches.first { it.id == m.branchId }
                    ManagementContextMembership(
                        branchId = b.id,
                        businessId = b.businessId,
                        branchName = b.name,
                        role = m.role,
                        canManageBusiness = m.role != "Staff",
                        canManageAppointments = true
                    )
                }
            )
        }
    }

    override suspend fun listBusinesses(q: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementBusiness>> = runCatching {
        simulateNetworkDelay()
        read { s -> s.businesses.filter { it.name.matchesQuery(q) }.demoPage(page, pageSize) }
    }

    override suspend fun getBusiness(id: String): Result<ManagementBusiness> = runCatching {
        simulateNetworkDelay()
        read { s -> s.businesses.first { it.id == id } }
    }

    override suspend fun updateBusiness(id: String, input: ManagementBusinessInput): Result<ManagementBusiness> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
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

    override suspend fun listBranches(businessId: String?, q: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementBranch>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            s.branches.filter {
                (businessId == null || it.businessId == businessId) &&
                it.name.matchesQuery(q) &&
                (includeInactive == true || it.active)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun createBranch(input: ManagementBranchInput): Result<ManagementBranch> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        val actor = requireNotNull(tokenManager.currentSession()).user
        update { s ->
            val resolvedBusinessId = if (input.businessId.isNotBlank() && s.businesses.any { it.id == input.businessId }) {
                input.businessId
            } else {
                s.memberships.firstOrNull { it.userId == actor.id }?.let { m -> s.branches.firstOrNull { it.id == m.branchId }?.businessId }
                    ?: s.businesses.firstOrNull()?.id
                    ?: throw IllegalArgumentException("کسب‌وکاری یافت نشد")
            }
            require(input.openHour in 0..23 && input.closeHour in 1..24 && input.openHour < input.closeHour)
            val id = demoId()
            val value = ManagementBranch(
                id = id,
                businessId = resolvedBusinessId,
                name = input.name,
                address = input.address,
                timeZoneId = input.timeZoneId,
                openHour = input.openHour,
                closeHour = input.closeHour,
                active = true
            )
            s.copy(
                branches = listOf(value) + s.branches,
                memberships = s.memberships + ManagementMembership(
                    id = demoId(),
                    branchId = value.id,
                    userId = actor.id,
                    role = "Manager",
                    displayName = actor.displayName,
                    email = actor.email
                )
            ) to value
        }
    }

    override suspend fun getBranch(id: String): Result<ManagementBranch> = runCatching {
        simulateNetworkDelay()
        read { s -> s.branches.first { it.id == id } }
    }

    override suspend fun updateBranch(id: String, input: ManagementBranchInput): Result<ManagementBranch> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val old = s.branches.first { it.id == id }
            val resolvedBusinessId = if (input.businessId.isNotBlank() && s.businesses.any { it.id == input.businessId }) input.businessId else old.businessId
            require(input.openHour in 0..23 && input.closeHour in 1..24 && input.openHour < input.closeHour)
            val value = old.copy(
                businessId = resolvedBusinessId,
                name = input.name,
                address = input.address,
                timeZoneId = input.timeZoneId,
                openHour = input.openHour,
                closeHour = input.closeHour
            )
            s.copy(branches = s.branches.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun setBranchActive(id: String, active: Boolean): Result<ManagementBranch> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val value = s.branches.first { it.id == id }.copy(active = active)
            s.copy(branches = s.branches.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun deleteBranch(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            require(s.branches.any { it.id == id })
            require(!(s.resources.any { it.branchId == id } || s.appointments.any { it.branchId == id })) {
                "ابتدا وابستگی‌ها را حذف کنید یا این مورد را غیرفعال کنید."
            }
            s.copy(
                branches = s.branches.filterNot { it.id == id },
                memberships = s.memberships.filterNot { it.branchId == id },
                branchServices = s.branchServices.filterNot { it.branchId == id },
                availabilities = s.availabilities.filterNot { it.branchId == id },
                services = s.services.map { it.copy(branchIds = it.branchIds - id) }
            ) to Unit
        }
    }

    override suspend fun listResources(branchId: String?, businessId: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementResource>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            s.resources.filter {
                (branchId == null || it.branchId == branchId) &&
                (businessId == null || s.branches.any { b -> b.id == it.branchId && b.businessId == businessId }) &&
                (includeInactive == true || it.active)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun createResource(input: ManagementResourceInput): Result<ManagementResource> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val id = demoId()
            require(s.branches.any { it.id == input.branchId })
            require(input.name.isNotBlank())
            val value = ManagementResource(
                id = id,
                branchId = input.branchId,
                name = input.name,
                kind = input.kind,
                userId = input.userId,
                active = true
            )
            s.copy(resources = listOf(value) + s.resources) to value
        }
    }

    override suspend fun updateResource(id: String, input: ManagementResourceInput): Result<ManagementResource> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val old = s.resources.first { it.id == id }
            require(s.branches.any { it.id == input.branchId })
            require(input.name.isNotBlank())
            val value = old.copy(branchId = input.branchId, name = input.name, kind = input.kind, userId = input.userId)
            s.copy(resources = s.resources.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun setResourceActive(id: String, active: Boolean): Result<ManagementResource> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val value = s.resources.first { it.id == id }.copy(active = active)
            s.copy(resources = s.resources.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun deleteResource(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            require(s.resources.any { it.id == id })
            require(!s.appointments.any { it.resourceId == id }) {
                "ابتدا وابستگی‌ها را حذف کنید یا این مورد را غیرفعال کنید."
            }
            s.copy(
                resources = s.resources.filterNot { it.id == id },
                resourceServices = s.resourceServices - id,
                availabilities = s.availabilities.filterNot { it.resourceId == id },
                services = s.services.map { it.copy(resourceIds = it.resourceIds - id) }
            ) to Unit
        }
    }

    override suspend fun listMemberships(branchId: String?, userId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementMembership>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            s.memberships.filter {
                (branchId == null || it.branchId == branchId) &&
                (userId == null || it.userId == userId)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun createMembership(input: ManagementMembershipInput): Result<ManagementMembership> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val id = demoId()
            require(s.branches.any { it.id == input.branchId })
            require(s.users.any { it.id == input.userId })
            require(s.memberships.none { it.id != id && it.branchId == input.branchId && it.userId == input.userId })
            val user = s.users.first { it.id == input.userId }
            val value = ManagementMembership(
                id = id,
                branchId = input.branchId,
                userId = input.userId,
                role = input.role,
                displayName = user.displayName,
                email = user.email
            )
            s.copy(memberships = listOf(value) + s.memberships) to value
        }
    }

    override suspend fun updateMembership(id: String, input: ManagementMembershipInput): Result<ManagementMembership> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val old = s.memberships.first { it.id == id }
            require(s.branches.any { it.id == input.branchId })
            require(s.users.any { it.id == input.userId })
            require(s.memberships.none { it.id != id && it.branchId == input.branchId && it.userId == input.userId })
            val user = s.users.first { it.id == input.userId }
            val value = old.copy(
                branchId = input.branchId,
                userId = input.userId,
                role = input.role,
                displayName = user.displayName,
                email = user.email
            )
            s.copy(memberships = s.memberships.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun deleteMembership(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            require(s.memberships.any { it.id == id })
            s.copy(memberships = s.memberships.filterNot { it.id == id }) to Unit
        }
    }

    override suspend fun listTeam(q: String?, branchId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementPerson>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            val ids = s.memberships.filter { branchId == null || it.branchId == branchId }.map { it.userId }
            s.users.filter { it.id in ids && it.displayName.matchesQuery(q) }
                .map { ManagementPerson(it.id, it.displayName, it.email, it.phoneNumber, it.active) }
                .demoPage(page, pageSize)
        }
    }

    override suspend fun listCustomers(q: String?, branchId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementPerson>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            val ids = s.appointments.filter { branchId == null || it.branchId == branchId }.map { it.customerId }
            s.users.filter { it.id in ids && it.displayName.matchesQuery(q) }
                .map { ManagementPerson(it.id, it.displayName, it.email, it.phoneNumber, it.active) }
                .demoPage(page, pageSize)
        }
    }

    override suspend fun listServices(businessId: String?, branchId: String?, q: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementService>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            s.services.filter {
                (businessId == null || it.businessId == businessId) &&
                (branchId == null || branchId in it.branchIds) &&
                it.name.matchesQuery(q) &&
                (includeInactive == true || it.active)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun getService(id: String): Result<ManagementService> = runCatching {
        simulateNetworkDelay()
        read { s -> s.services.first { it.id == id } }
    }

    override suspend fun createService(input: ManagementServiceInput): Result<ManagementService> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        val actor = requireNotNull(tokenManager.currentSession()).user
        update { s ->
            val id = demoId()
            val resolvedBusinessId = if (input.businessId.isNotBlank() && s.businesses.any { it.id == input.businessId }) {
                input.businessId
            } else {
                s.memberships.firstOrNull { it.userId == actor.id }?.let { m -> s.branches.firstOrNull { it.id == m.branchId }?.businessId }
                    ?: s.businesses.firstOrNull()?.id
                    ?: throw IllegalArgumentException("کسب‌وکاری یافت نشد")
            }
            require(input.durationMinutes > 0 && input.price >= 0)

            val resolvedBranchIds = if (input.branchId != null) {
                listOf(input.branchId)
            } else {
                val bIds = s.branches.filter { it.businessId == resolvedBusinessId && it.active }.map { it.id }
                if (bIds.isNotEmpty()) bIds else s.branches.filter { it.businessId == resolvedBusinessId }.map { it.id }
            }
            require(resolvedBranchIds.isNotEmpty()) { "حداقل یک شعبه برای ارائه خدمت لازم است." }

            val resolvedResourceIds = (input.resourceIds ?: s.resources.filter { it.branchId in resolvedBranchIds && it.active }.map { it.id }).distinct()

            val value = ManagementService(
                id = id,
                businessId = resolvedBusinessId,
                name = input.name,
                description = input.description,
                durationMinutes = input.durationMinutes,
                price = input.price,
                currency = input.currency,
                catalogItemId = input.catalogItemId,
                branchIds = resolvedBranchIds,
                resourceIds = resolvedResourceIds,
                active = true
            )

            val links = value.branchIds.map { branchId ->
                ManagementBranchService(
                    id = "${branchId}_${id}",
                    branchId = branchId,
                    serviceId = id,
                    price = s.branchServices.firstOrNull { it.branchId == branchId && it.serviceId == id }?.price,
                    effectivePrice = s.branchServices.firstOrNull { it.branchId == branchId && it.serviceId == id }?.price ?: value.price,
                    serviceName = value.name
                )
            }

            val newResourceServices = s.resourceServices.toMutableMap()
            for (rid in resolvedResourceIds) {
                newResourceServices[rid] = ((newResourceServices[rid] ?: emptyList()) + id).distinct()
            }

            s.copy(
                services = listOf(value) + s.services,
                branchServices = s.branchServices.filterNot { it.serviceId == id } + links,
                resourceServices = newResourceServices
            ) to value
        }
    }

    override suspend fun updateService(id: String, input: ManagementServiceInput): Result<ManagementService> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val old = s.services.first { it.id == id }
            val resolvedBusinessId = if (input.businessId.isNotBlank() && s.businesses.any { it.id == input.businessId }) input.businessId else old.businessId
            require(input.durationMinutes > 0 && input.price >= 0)

            val resolvedBranchIds = if (input.branchId != null) {
                listOf(input.branchId)
            } else {
                old.branchIds
            }

            val resolvedResourceIds = input.resourceIds ?: old.resourceIds

            val value = old.copy(
                businessId = resolvedBusinessId,
                name = input.name,
                description = input.description,
                durationMinutes = input.durationMinutes,
                price = input.price,
                currency = input.currency,
                catalogItemId = input.catalogItemId,
                branchIds = resolvedBranchIds,
                resourceIds = resolvedResourceIds
            )

            val links = value.branchIds.map { branchId ->
                ManagementBranchService(
                    id = "${branchId}_${id}",
                    branchId = branchId,
                    serviceId = id,
                    price = s.branchServices.firstOrNull { it.branchId == branchId && it.serviceId == id }?.price,
                    effectivePrice = s.branchServices.firstOrNull { it.branchId == branchId && it.serviceId == id }?.price ?: value.price,
                    serviceName = value.name
                )
            }

            val newResourceServices = if (input.resourceIds == null) s.resourceServices else {
                s.resourceServices.mapValues { (rid, ids) -> if (rid in value.resourceIds) (ids + id).distinct() else ids - id } +
                    value.resourceIds.associateWith { rid -> ((s.resourceServices[rid] ?: emptyList()) + id).distinct() }
            }

            s.copy(
                services = s.services.map { if (it.id == id) value else it },
                branchServices = s.branchServices.filterNot { it.serviceId == id } + links,
                resourceServices = newResourceServices
            ) to value
        }
    }

    override suspend fun setServiceActive(id: String, active: Boolean): Result<ManagementService> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val value = s.services.first { it.id == id }.copy(active = active)
            s.copy(services = s.services.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun deleteService(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            require(s.services.any { it.id == id })
            require(!s.appointments.any { it.serviceId == id }) {
                "ابتدا وابستگی‌ها را حذف کنید یا این مورد را غیرفعال کنید."
            }
            s.copy(
                services = s.services.filterNot { it.id == id },
                branchServices = s.branchServices.filterNot { it.serviceId == id },
                resourceServices = s.resourceServices.mapValues { it.value - id }
            ) to Unit
        }
    }

    override suspend fun listBranchServices(branchId: String, page: Int, pageSize: Int): Result<ApiPage<ManagementBranchService>> = runCatching {
        simulateNetworkDelay()
        read { s -> s.branchServices.filter { it.branchId == branchId }.demoPage(page, pageSize) }
    }

    override suspend fun upsertBranchService(branchId: String, serviceId: String, input: ManagementBranchServiceInput): Result<ManagementBranchService> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val service = s.services.first { it.id == serviceId }
            require(s.branches.any { it.id == branchId && it.businessId == service.businessId })
            require(input.price == null || input.price >= 0)
            val value = ManagementBranchService(
                id = "${branchId}_${serviceId}",
                branchId = branchId,
                serviceId = serviceId,
                price = input.price,
                effectivePrice = input.price ?: service.price,
                serviceName = service.name
            )
            s.copy(
                branchServices = s.branchServices.filterNot { it.branchId == branchId && it.serviceId == serviceId } + value,
                services = s.services.map { if (it.id == serviceId) it.copy(branchIds = (it.branchIds + branchId).distinct()) else it }
            ) to value
        }
    }

    override suspend fun deleteBranchService(branchId: String, serviceId: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            s.copy(
                branchServices = s.branchServices.filterNot { it.branchId == branchId && it.serviceId == serviceId },
                services = s.services.map { if (it.id == serviceId) it.copy(branchIds = it.branchIds - branchId) else it }
            ) to Unit
        }
    }

    override suspend fun listResourceServices(resourceId: String): Result<ManagementResourceServicesInput> = runCatching {
        simulateNetworkDelay()
        read { s ->
            require(s.resources.any { it.id == resourceId })
            ManagementResourceServicesInput(s.resourceServices[resourceId].orEmpty())
        }
    }

    override suspend fun setResourceServices(resourceId: String, input: ManagementResourceServicesInput): Result<ManagementResourceServicesInput> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val r = s.resources.first { it.id == resourceId }
            require(input.serviceIds.all { id -> s.services.any { it.id == id && r.branchId in it.branchIds } })
            s.copy(
                resourceServices = s.resourceServices + (resourceId to input.serviceIds.distinct()),
                services = s.services.map { service ->
                    service.copy(resourceIds = if (service.id in input.serviceIds) (service.resourceIds + resourceId).distinct() else service.resourceIds - resourceId)
                }
            ) to input
        }
    }

    override suspend fun listAvailability(businessId: String?, branchId: String?, resourceId: String?, from: String?, to: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementAvailability>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            s.availabilities.filter {
                (businessId == null || it.businessId == businessId) &&
                (branchId == null || it.branchId == branchId) &&
                (resourceId == null || it.resourceId == resourceId) &&
                (from == null || (it.effectiveDate ?: "") >= from) &&
                (to == null || (it.effectiveDate ?: "") <= to)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun createAvailability(input: ManagementAvailabilityInput): Result<ManagementAvailability> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val id = demoId()
            val resolvedBusinessId = if (input.businessId.isNotBlank() && s.businesses.any { it.id == input.businessId }) {
                input.businessId
            } else {
                input.branchId?.let { bid -> s.branches.firstOrNull { it.id == bid }?.businessId }
                    ?: s.businesses.firstOrNull()?.id
                    ?: throw IllegalArgumentException("کسب‌وکاری یافت نشد")
            }
            require(input.startsAt < input.endsAt)
            require(input.branchId == null || s.branches.any { it.id == input.branchId && it.businessId == resolvedBusinessId })
            val value = ManagementAvailability(
                id = id,
                businessId = resolvedBusinessId,
                effectiveDate = input.effectiveDate,
                startsAt = input.startsAt,
                endsAt = input.endsAt,
                branchId = input.branchId,
                resourceId = input.resourceId,
                isAvailable = input.isAvailable
            )
            s.copy(availabilities = listOf(value) + s.availabilities) to value
        }
    }

    override suspend fun updateAvailability(id: String, input: ManagementAvailabilityInput): Result<ManagementAvailability> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val old = s.availabilities.first { it.id == id }
            val resolvedBusinessId = if (input.businessId.isNotBlank() && s.businesses.any { it.id == input.businessId }) input.businessId else old.businessId
            require(input.startsAt < input.endsAt)
            require(input.branchId == null || s.branches.any { it.id == input.branchId && it.businessId == resolvedBusinessId })
            val value = old.copy(
                businessId = resolvedBusinessId,
                effectiveDate = input.effectiveDate,
                startsAt = input.startsAt,
                endsAt = input.endsAt,
                branchId = input.branchId,
                resourceId = input.resourceId,
                isAvailable = input.isAvailable
            )
            s.copy(availabilities = s.availabilities.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun deleteAvailability(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            require(s.availabilities.any { it.id == id })
            s.copy(availabilities = s.availabilities.filterNot { it.id == id }) to Unit
        }
    }

    override suspend fun listAppointments(businessId: String?, branchId: String?, resourceId: String?, customerId: String?, date: String?, status: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementAppointment>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            s.appointments.filter {
                (businessId == null || it.businessId == businessId) &&
                (branchId == null || it.branchId == branchId) &&
                (resourceId == null || it.resourceId == resourceId) &&
                (customerId == null || it.customerId == customerId) &&
                (date == null || it.startsAt.startsWith(date)) &&
                (status == null || it.status == status)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun getAppointment(id: String): Result<ManagementAppointment> = runCatching {
        simulateNetworkDelay()
        read { s -> s.appointments.first { it.id == id } }
    }

    override suspend fun setAppointmentStatus(id: String, input: ManagementStatusInput): Result<ManagementAppointment> = runCatching {
        simulateNetworkDelay()
        requireActor(false)
        update { s ->
            require(input.status in listOf("Pending", "Confirmed", "Completed", "Cancelled", "NoShow"))
            val old = s.appointments.first { it.id == id }
            val value = old.copy(status = input.status)
            s.copy(appointments = s.appointments.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun listReviews(businessId: String?, branchId: String?, customerId: String?, status: String?, rating: Int?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementReview>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            s.reviews.filter {
                (businessId == null || it.businessId == businessId) &&
                (branchId == null || it.branchId == branchId) &&
                (customerId == null || it.customerId == customerId) &&
                reviewStatusMatches(it.status, status) &&
                (rating == null || it.rating == rating) &&
                (includeInactive == true || it.active)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun moderateReview(id: String, input: ManagementModerationInput): Result<ManagementReview> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            require(input.status in listOf("Pending", "Approved", "Published", "Rejected"))
            val normalized = if (input.status == "Published") "Approved" else input.status
            val value = s.reviews.first { it.id == id }.copy(status = normalized, managerReply = input.managerReply)
            s.copy(reviews = s.reviews.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun replyReview(id: String, input: ManagementReplyInput): Result<ManagementReview> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val value = s.reviews.first { it.id == id }.copy(managerReply = input.managerReply)
            s.copy(reviews = s.reviews.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun setReviewActive(id: String, active: Boolean): Result<ManagementReview> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            val value = s.reviews.first { it.id == id }.copy(active = active)
            s.copy(reviews = s.reviews.map { if (it.id == id) value else it }) to value
        }
    }

    override suspend fun deleteReview(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            require(s.reviews.any { it.id == id })
            s.copy(reviews = s.reviews.filterNot { it.id == id }) to Unit
        }
    }

    override suspend fun listCustomerReviews(businessId: String?, branchId: String?, customerId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementCustomerReview>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            s.customerReviews.filter {
                (businessId == null || it.businessId == businessId) &&
                (branchId == null || it.branchId == branchId) &&
                (customerId == null || it.customerId == customerId)
            }.demoPage(page, pageSize)
        }
    }

    override suspend fun listPendingCustomerReviews(page: Int, pageSize: Int): Result<ApiPage<ManagementAppointment>> = runCatching {
        simulateNetworkDelay()
        read { s ->
            s.appointments.filter { it.status == "Completed" && s.customerReviews.none { r -> r.appointmentId == it.id } }.demoPage(page, pageSize)
        }
    }

    override suspend fun createCustomerReview(input: ManagementCustomerReviewInput): Result<ManagementCustomerReview> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        val author = requireNotNull(tokenManager.currentSession()).user
        update { s ->
            val a = s.appointments.first { it.id == input.appointmentId }
            require(a.status == "Completed" && input.rating in 1..5)
            val id = demoId()
            require(s.customerReviews.none { it.appointmentId == a.id && it.id != id })
            val value = ManagementCustomerReview(
                id = id,
                appointmentId = a.id,
                businessId = a.businessId,
                branchId = a.branchId,
                customerId = a.customerId,
                customerName = a.customerName,
                authorId = author.id,
                authorName = author.displayName,
                rating = input.rating,
                comment = input.comment,
                createdAt = demoNow()
            )
            s.copy(customerReviews = s.customerReviews.filterNot { it.id == id } + value) to value
        }
    }

    override suspend fun updateCustomerReview(id: String, input: ManagementCustomerReviewInput): Result<ManagementCustomerReview> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        val author = requireNotNull(tokenManager.currentSession()).user
        update { s ->
            val a = s.appointments.first { it.id == input.appointmentId }
            require(a.status == "Completed" && input.rating in 1..5)
            require(s.customerReviews.any { it.id == id })
            require(s.customerReviews.none { it.appointmentId == a.id && it.id != id })
            val value = ManagementCustomerReview(
                id = id,
                appointmentId = a.id,
                businessId = a.businessId,
                branchId = a.branchId,
                customerId = a.customerId,
                customerName = a.customerName,
                authorId = author.id,
                authorName = author.displayName,
                rating = input.rating,
                comment = input.comment,
                createdAt = demoNow()
            )
            s.copy(customerReviews = s.customerReviews.filterNot { it.id == id } + value) to value
        }
    }

    override suspend fun deleteCustomerReview(id: String): Result<Unit> = runCatching {
        simulateNetworkDelay()
        requireActor(true)
        update { s ->
            require(s.customerReviews.any { it.id == id })
            s.copy(customerReviews = s.customerReviews.filterNot { it.id == id }) to Unit
        }
    }
}
