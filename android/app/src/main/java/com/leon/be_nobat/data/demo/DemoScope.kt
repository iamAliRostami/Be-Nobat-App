package com.leon.be_nobat.data.demo

internal fun DemoDatabaseState.visibleTo(userId: String): DemoDatabaseState {
    val user = users.first { it.id == userId && it.active }
    if ("PlatformAdmin" in user.roles) return this
    require(user.roles.any { it in listOf("Owner", "Manager", "Staff") }) { "دسترسی مدیریت لازم است." }
    val branchIds = memberships.filter { it.userId == userId }.map { it.branchId }.toSet()
    val businessIds = branches.filter { it.id in branchIds }.map { it.businessId }.toSet()
    val staffOnly = user.roles.none { it in listOf("Owner", "Manager") }
    val resourceIds = resources.filter { it.branchId in branchIds && (!staffOnly || it.userId == userId) }.map { it.id }.toSet()
    return copy(
        businesses = businesses.filter { it.id in businessIds },
        branches = branches.filter { it.id in branchIds },
        services = services.filter { it.businessId in businessIds },
        resources = resources.filter { it.id in resourceIds },
        memberships = memberships.filter { it.branchId in branchIds },
        branchServices = branchServices.filter { it.branchId in branchIds },
        availabilities = availabilities.filter { it.businessId in businessIds && (it.branchId == null || it.branchId in branchIds) },
        appointments = appointments.filter { it.branchId in branchIds && (!staffOnly || it.resourceId in resourceIds) },
        reviews = reviews.filter { it.branchId in branchIds },
        customerReviews = customerReviews.filter { it.branchId in branchIds },
        resourceServices = resourceServices.filterKeys { it in resourceIds },
    )
}

private fun <T> merge(all: List<T>, visible: List<T>, next: List<T>, id: (T) -> String): List<T> {
    val oldIds = visible.map(id).toSet()
    return all.filterNot { id(it) in oldIds } + next
}

internal fun DemoDatabaseState.mergeVisible(old: DemoDatabaseState, next: DemoDatabaseState) = copy(
    businesses = merge(businesses, old.businesses, next.businesses) { it.id },
    branches = merge(branches, old.branches, next.branches) { it.id },
    services = merge(services, old.services, next.services) { it.id },
    resources = merge(resources, old.resources, next.resources) { it.id },
    memberships = merge(memberships, old.memberships, next.memberships) { it.id },
    branchServices = merge(branchServices, old.branchServices, next.branchServices) { it.id },
    availabilities = merge(availabilities, old.availabilities, next.availabilities) { it.id },
    appointments = merge(appointments, old.appointments, next.appointments) { it.id },
    reviews = merge(reviews, old.reviews, next.reviews) { it.id },
    customerReviews = merge(customerReviews, old.customerReviews, next.customerReviews) { it.id },
    resourceServices = (resourceServices - old.resourceServices.keys) + next.resourceServices,
)
