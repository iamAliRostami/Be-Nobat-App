package com.leon.be_nobat.domain.repository

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

interface BusinessRepository {
    suspend fun listBusinesses(
        q: String? = null,
        city: String? = null,
        category: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<ApiPage<Business>>

    suspend fun getBusiness(id: String): Result<BusinessDetail>

    suspend fun listBusinessReviews(
        id: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<ApiPage<PublicReview>>

    suspend fun listBranches(businessId: String): Result<ApiItems<Branch>>

    suspend fun listBranchServices(
        businessId: String,
        branchId: String,
    ): Result<ApiItems<Service>>

    suspend fun listProviders(
        businessId: String,
        branchId: String,
        serviceIds: String,
    ): Result<ApiItems<Provider>>

    suspend fun getCategories(): Result<ApiItems<Category>>

    suspend fun getCatalog(page: Int = 1, pageSize: Int = 30): Result<ApiPage<ServiceCatalogItem>>

    suspend fun findBusinessIdByBranch(branchId: String): String? = null
}
