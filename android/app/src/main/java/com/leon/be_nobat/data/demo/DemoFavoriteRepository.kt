package com.leon.be_nobat.data.demo

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.repository.FavoriteRepository

class DemoFavoriteRepository(
    private val dataStore: DemoDataStore,
    private val tokenManager: com.leon.be_nobat.data.local.TokenManager,
) : FavoriteRepository {

    override suspend fun listFavorites(page: Int, pageSize: Int): Result<ApiPage<Business>> = runCatching {
        simulateNetworkDelay(150, 280)
        val uid = tokenManager.currentSession()?.user?.id ?: "guest"
        dataStore.read { state ->
            val favBusinesses = state.businesses
                .filter { it.active && (state.userFavorites[uid] ?: if (uid == "user_customer_1") state.favorites else emptyList()).contains(it.id) }

            val total = favBusinesses.size
            val start = ((page - 1) * pageSize).coerceAtLeast(0)
            val paged = if (start < total) favBusinesses.drop(start).take(pageSize) else emptyList()

            val mapped = paged.map { mb ->
                val branchCount = state.branches.count { it.businessId == mb.id && it.active }
                val serviceCount = state.services.count { it.businessId == mb.id && it.active }
                val reviews = state.reviews.filter { it.businessId == mb.id && it.active && it.status in listOf("Approved", "Published") }
                val rating = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else null
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
                    reviewCount = reviews.size,
                )
            }
            ApiPage(items = mapped, page = page, pageSize = pageSize, total = total)
        }
    }

    override suspend fun addFavorite(businessId: String): Result<Unit> = runCatching {
        simulateNetworkDelay(120, 220)
        val uid = tokenManager.currentSession()?.user?.id ?: "guest"
        dataStore.update { state ->
            val favorites = state.userFavorites[uid] ?: if (uid == "user_customer_1") state.favorites else emptyList()
            val updated = if (favorites.contains(businessId)) favorites else favorites + businessId
            state.copy(userFavorites = state.userFavorites + (uid to updated)) to Unit
        }
    }

    override suspend fun removeFavorite(businessId: String): Result<Unit> = runCatching {
        simulateNetworkDelay(120, 220)
        val uid = tokenManager.currentSession()?.user?.id ?: "guest"
        dataStore.update { state ->
            val favorites = state.userFavorites[uid] ?: if (uid == "user_customer_1") state.favorites else emptyList()
            val updated = favorites.filter { it != businessId }
            state.copy(userFavorites = state.userFavorites + (uid to updated)) to Unit
        }
    }
}
