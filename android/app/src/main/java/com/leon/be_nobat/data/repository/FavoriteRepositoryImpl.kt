package com.leon.be_nobat.data.repository

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.repository.FavoriteRepository

class FavoriteRepositoryImpl(
    private val api: BeNobatApiClient,
) : FavoriteRepository {
    override suspend fun listFavorites(page: Int, pageSize: Int): Result<ApiPage<Business>> =
        api.listFavorites(page, pageSize)

    override suspend fun addFavorite(businessId: String): Result<Unit> = api.addFavorite(businessId)
    override suspend fun removeFavorite(businessId: String): Result<Unit> = api.removeFavorite(businessId)
}
