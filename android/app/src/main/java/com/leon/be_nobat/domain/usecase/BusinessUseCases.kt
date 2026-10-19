package com.leon.be_nobat.domain.usecase

import com.leon.be_nobat.data.remote.ApiItems
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.model.BusinessDetail
import com.leon.be_nobat.domain.model.Branch
import com.leon.be_nobat.domain.model.Category
import com.leon.be_nobat.domain.model.Provider
import com.leon.be_nobat.domain.model.PublicReview
import com.leon.be_nobat.domain.model.Service
import com.leon.be_nobat.domain.model.ServiceCatalogItem
import com.leon.be_nobat.domain.repository.BusinessRepository

/** Thin pass-through use cases for public discovery endpoints. */
class ListBusinessesUseCase(private val repo: BusinessRepository) {
    suspend operator fun invoke(
        q: String? = null, city: String? = null, category: String? = null,
        page: Int = 1, pageSize: Int = 20,
    ): Result<ApiPage<Business>> = repo.listBusinesses(q, city, category, page, pageSize)
}

class GetBusinessUseCase(private val repo: BusinessRepository) {
    suspend operator fun invoke(id: String): Result<BusinessDetail> = repo.getBusiness(id)
}

class ListBusinessReviewsUseCase(private val repo: BusinessRepository) {
    suspend operator fun invoke(id: String, page: Int = 1, pageSize: Int = 20): Result<ApiPage<PublicReview>> =
        repo.listBusinessReviews(id, page, pageSize)
}

class ListBranchesUseCase(private val repo: BusinessRepository) {
    suspend operator fun invoke(businessId: String): Result<ApiItems<Branch>> = repo.listBranches(businessId)
}

class ListBranchServicesUseCase(private val repo: BusinessRepository) {
    suspend operator fun invoke(businessId: String, branchId: String): Result<ApiItems<Service>> =
        repo.listBranchServices(businessId, branchId)
}

class ListProvidersUseCase(private val repo: BusinessRepository) {
    suspend operator fun invoke(businessId: String, branchId: String, serviceIds: String): Result<ApiItems<Provider>> =
        repo.listProviders(businessId, branchId, serviceIds)
}

class GetCategoriesUseCase(private val repo: BusinessRepository) {
    suspend operator fun invoke(): Result<ApiItems<Category>> = repo.getCategories()
}

class GetCatalogUseCase(private val repo: BusinessRepository) {
    suspend operator fun invoke(page: Int = 1, pageSize: Int = 30): Result<ApiPage<ServiceCatalogItem>> =
        repo.getCatalog(page, pageSize)
}
