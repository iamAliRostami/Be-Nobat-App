package com.leon.be_nobat.domain.usecase

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.repository.FavoriteRepository

class ListFavoritesUseCase(private val repo: FavoriteRepository) {
    suspend operator fun invoke(page: Int = 1, pageSize: Int = 20): Result<ApiPage<Business>> =
        repo.listFavorites(page, pageSize)
}

class ToggleFavoriteUseCase(private val repo: FavoriteRepository) {
    suspend operator fun invoke(businessId: String, isCurrentlyFavorite: Boolean): Result<Unit> =
        if (isCurrentlyFavorite) repo.removeFavorite(businessId) else repo.addFavorite(businessId)
}
