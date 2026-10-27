package com.leon.be_nobat.data.repository

import com.leon.be_nobat.data.remote.ApiItems
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.model.BusinessDetail
import com.leon.be_nobat.domain.model.Branch
import com.leon.be_nobat.domain.model.Category
import com.leon.be_nobat.domain.model.Provider
import com.leon.be_nobat.domain.model.PublicReview
import com.leon.be_nobat.domain.model.Service
import com.leon.be_nobat.domain.model.ServiceCatalogItem
import com.leon.be_nobat.domain.repository.BusinessRepository

/** Implements public discovery endpoints against the .NET mobile API. */
class BusinessRepositoryImpl(
    private val api: BeNobatApiClient,
) : BusinessRepository {
    override suspend fun listBusinesses(
        q: String?, city: String?, category: String?, page: Int, pageSize: Int,
    ): Result<ApiPage<Business>> = api.listBusinesses(q, city, category, page, pageSize)

    override suspend fun getBusiness(id: String): Result<BusinessDetail> = api.getBusiness(id)

    override suspend fun listBusinessReviews(id: String, page: Int, pageSize: Int): Result<ApiPage<PublicReview>> =
        api.listBusinessReviews(id, page, pageSize)

    override suspend fun listBranches(businessId: String): Result<ApiItems<Branch>> = api.listBranches(businessId)

    override suspend fun listBranchServices(businessId: String, branchId: String): Result<ApiItems<Service>> =
        api.listBranchServices(businessId, branchId)

    override suspend fun listProviders(
        businessId: String, branchId: String, serviceIds: String,
    ): Result<ApiItems<Provider>> = api.listProviders(businessId, branchId, serviceIds)

    override suspend fun getCategories(): Result<ApiItems<Category>> = api.getCategories()

    override suspend fun getCatalog(page: Int, pageSize: Int): Result<ApiPage<ServiceCatalogItem>> =
        api.getCatalog(page, pageSize)

    override suspend fun findBusinessIdByProvider(providerId: String): Pair<String, String>? {
        return try {
            val businesses = listBusinesses().getOrNull()?.items ?: emptyList()
            for (b in businesses) {
                val branches = listBranches(b.id).getOrNull()?.items ?: emptyList()
                for (br in branches) {
                    val services = listBranchServices(b.id, br.id).getOrNull()?.items ?: emptyList()
                    val sIds = services.firstOrNull()?.id ?: ""
                    val providers = listProviders(b.id, br.id, sIds).getOrNull()?.items ?: emptyList()
                    val match = providers.firstOrNull { it.id == providerId }
                    if (match != null) {
                        return b.id to br.id
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun findBusinessIdByBranch(branchId: String): String? {
        return null
    }
}
