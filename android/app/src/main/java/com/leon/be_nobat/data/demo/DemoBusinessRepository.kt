package com.leon.be_nobat.data.demo

import com.leon.be_nobat.data.remote.ApiItems
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Branch
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.model.BusinessDetail
import com.leon.be_nobat.domain.model.Category
import com.leon.be_nobat.domain.model.Provider
import com.leon.be_nobat.domain.model.PublicReview
import com.leon.be_nobat.domain.model.Service
import com.leon.be_nobat.domain.model.ServiceCatalogItem
import com.leon.be_nobat.domain.repository.BusinessRepository

class DemoBusinessRepository(
    private val dataStore: DemoDataStore,
) : BusinessRepository {

    override suspend fun listBusinesses(
        q: String?,
        city: String?,
        category: String?,
        page: Int,
        pageSize: Int,
    ): Result<ApiPage<Business>> = runCatching {
        simulateNetworkDelay(180, 320)
        dataStore.read { state ->
            val filtered = state.businesses
                .filter { it.active }
                .filter { biz ->
                    q.isNullOrBlank() || biz.name.contains(q, ignoreCase = true) || biz.description.contains(q, ignoreCase = true)
                }
                .filter { biz ->
                    city.isNullOrBlank() || biz.city.equals(city, ignoreCase = true)
                }
                .filter { biz ->
                    category.isNullOrBlank() || biz.category.equals(category, ignoreCase = true)
                }

            val total = filtered.size
            val start = ((page - 1) * pageSize).coerceAtLeast(0)
            val paged = if (start < total) filtered.drop(start).take(pageSize) else emptyList()

            val mapped = paged.map { mb ->
                val branchCount = state.branches.count { it.businessId == mb.id && it.active && it.isApproved }
                val serviceCount = state.services.count { it.businessId == mb.id && it.active }
                val bizReviews = state.reviews.filter { it.businessId == mb.id && it.active && it.status in listOf("Approved", "Published") }
                val rating = if (bizReviews.isNotEmpty()) bizReviews.map { it.rating }.average() else null
                Business(
                    id = mb.id,
                    name = mb.name,
                    slug = mb.slug,
                    category = mb.category,
                    city = mb.city,
                    description = mb.description,
                    requiresApproval = mb.requiresApproval,
                    branchCount = branchCount,
                    serviceCount = serviceCount,
                    rating = rating,
                    reviewCount = bizReviews.size,
                )
            }

            ApiPage(
                items = mapped,
                page = page,
                pageSize = pageSize,
                total = total,
            )
        }
    }

    override suspend fun getBusiness(id: String): Result<BusinessDetail> = runCatching {
        simulateNetworkDelay(120, 250)
        dataStore.read { state ->
            val mb = state.businesses.firstOrNull { it.id == id }
                ?: error("کسب‌وکار با شناسه $id یافت نشد.")

            val branches = state.branches.filter { it.businessId == id && it.active && it.isApproved }.map { b ->
                Branch(
                    id = b.id,
                    businessId = b.businessId,
                    name = b.name,
                    address = b.address,
                    timeZoneId = b.timeZoneId,
                    openHour = b.openHour,
                    closeHour = b.closeHour,
                )
            }

            val services = state.services.filter { it.businessId == id && it.active }.map { s ->
                Service(
                    id = s.id,
                    businessId = s.businessId,
                    name = s.name,
                    description = s.description,
                    durationMinutes = s.durationMinutes,
                    price = s.price,
                    currency = s.currency,
                )
            }

            val reviews = state.reviews.filter { it.businessId == id && it.active && it.status in listOf("Approved", "Published") }.map { r ->
                PublicReview(
                    id = r.id,
                    rating = r.rating,
                    comment = r.comment,
                    managerReply = r.managerReply,
                    customerName = r.customerName,
                    createdAt = r.createdAt,
                )
            }

            val rating = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else null

            BusinessDetail(
                id = mb.id,
                name = mb.name,
                slug = mb.slug,
                category = mb.category,
                city = mb.city,
                description = mb.description,
                requiresApproval = mb.requiresApproval,
                branchCount = branches.size,
                serviceCount = services.size,
                rating = rating,
                reviewCount = reviews.size,
                branches = branches,
                services = services,
                reviews = reviews,
            )
        }
    }

    override suspend fun listBusinessReviews(
        id: String,
        page: Int,
        pageSize: Int,
    ): Result<ApiPage<PublicReview>> = runCatching {
        simulateNetworkDelay(120, 250)
        dataStore.read { state ->
            val reviews = state.reviews.filter { it.businessId == id && it.active && it.status in listOf("Approved", "Published") }
            val total = reviews.size
            val start = ((page - 1) * pageSize).coerceAtLeast(0)
            val paged = if (start < total) reviews.drop(start).take(pageSize) else emptyList()
            val items = paged.map { r ->
                PublicReview(
                    id = r.id,
                    rating = r.rating,
                    comment = r.comment,
                    managerReply = r.managerReply,
                    customerName = r.customerName,
                    createdAt = r.createdAt,
                )
            }
            ApiPage(items = items, page = page, pageSize = pageSize, total = total)
        }
    }

    override suspend fun listBranches(businessId: String): Result<ApiItems<Branch>> = runCatching {
        simulateNetworkDelay(120, 250)
        dataStore.read { state ->
            val branches = state.branches.filter { it.businessId == businessId && it.active && it.isApproved }.map { b ->
                Branch(
                    id = b.id,
                    businessId = b.businessId,
                    name = b.name,
                    address = b.address,
                    timeZoneId = b.timeZoneId,
                    openHour = b.openHour,
                    closeHour = b.closeHour,
                )
            }
            ApiItems(items = branches)
        }
    }

    override suspend fun listBranchServices(
        businessId: String,
        branchId: String,
    ): Result<ApiItems<Service>> = runCatching {
        simulateNetworkDelay(120, 250)
        dataStore.read { state ->
            val services = state.services
                .filter { it.businessId == businessId && it.active && it.branchIds.contains(branchId) }
                .map { s ->
                    Service(
                        id = s.id,
                        businessId = s.businessId,
                        name = s.name,
                        description = s.description,
                        durationMinutes = s.durationMinutes,
                        price = state.branchServices.firstOrNull { it.branchId == branchId && it.serviceId == s.id }?.effectivePrice ?: s.price,
                        currency = s.currency,
                    )
                }
            ApiItems(items = services)
        }
    }

    override suspend fun listProviders(
        businessId: String,
        branchId: String,
        serviceIds: String,
    ): Result<ApiItems<Provider>> = runCatching {
        simulateNetworkDelay(120, 250)
        dataStore.read { state ->
            val neededServices = serviceIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val providers = state.resources
                .filter { it.branchId == branchId && it.active }
                .filter { res ->
                    if (neededServices.isEmpty()) true
                    else {
                        val qualified = state.resourceServices[res.id] ?: emptyList()
                        qualified.isEmpty() || qualified.containsAll(neededServices)
                    }
                }
                .map { res ->
                    Provider(
                        id = res.id,
                        branchId = res.branchId,
                        name = res.name,
                        kind = res.kind,
                        userId = res.userId,
                    )
                }
            ApiItems(items = providers)
        }
    }

    override suspend fun getCategories(): Result<ApiItems<Category>> = runCatching {
        simulateNetworkDelay(100, 200)
        dataStore.read { state ->
            val categories = state.categories.filter { it.active }.map { c ->
                Category(id = c.id, name = c.name, kind = c.kind, sortOrder = c.sortOrder)
            }
            ApiItems(items = categories)
        }
    }

    override suspend fun getCatalog(page: Int, pageSize: Int): Result<ApiPage<ServiceCatalogItem>> = runCatching {
        simulateNetworkDelay(120, 250)
        dataStore.read { state ->
            val catalog = state.catalog.filter { it.active && it.isPublished }
            val total = catalog.size
            val start = ((page - 1) * pageSize).coerceAtLeast(0)
            val paged = if (start < total) catalog.drop(start).take(pageSize) else emptyList()
            val items = paged.map { c ->
                ServiceCatalogItem(
                    id = c.id,
                    name = c.name,
                    slug = c.slug,
                    category = c.category,
                    description = c.description,
                    suggestedDurationMinutes = c.suggestedDurationMinutes,
                )
            }
            ApiPage(items = items, page = page, pageSize = pageSize, total = total)
        }
    }
}
