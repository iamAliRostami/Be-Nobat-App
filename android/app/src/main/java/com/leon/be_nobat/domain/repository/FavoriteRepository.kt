package com.leon.be_nobat.domain.repository

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Business

/** Customer favorites endpoints (`/api/v1/favorites`). */
interface FavoriteRepository {
    suspend fun listFavorites(page: Int = 1, pageSize: Int = 20): Result<ApiPage<Business>>
    suspend fun addFavorite(businessId: String): Result<Unit>
    suspend fun removeFavorite(businessId: String): Result<Unit>
}
