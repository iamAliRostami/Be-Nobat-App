package com.leon.be_nobat.ui.view_models

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
import com.leon.be_nobat.domain.repository.FavoriteRepository
import com.leon.be_nobat.domain.usecase.ListBusinessesUseCase
import com.leon.be_nobat.domain.usecase.ListFavoritesUseCase
import com.leon.be_nobat.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `favorites are placed at the top of the list`() = runTest(testDispatcher) {
        val b1 = Business(id = "biz-1", name = "مرکز اول")
        val b2 = Business(id = "biz-2", name = "مرکز دوم")
        val b3 = Business(id = "biz-3", name = "مرکز سوم")

        val fakeBusinessRepo = object : BusinessRepository {
            override suspend fun listBusinesses(q: String?, city: String?, category: String?, page: Int, pageSize: Int): Result<ApiPage<Business>> =
                Result.success(ApiPage(listOf(b1, b2, b3), 1, 10, 3))
            override suspend fun getBusiness(id: String): Result<BusinessDetail> = TODO()
            override suspend fun listBusinessReviews(id: String, page: Int, pageSize: Int): Result<ApiPage<PublicReview>> = TODO()
            override suspend fun listBranches(businessId: String): Result<ApiItems<Branch>> = TODO()
            override suspend fun listBranchServices(businessId: String, branchId: String): Result<ApiItems<Service>> = TODO()
            override suspend fun listProviders(businessId: String, branchId: String, serviceIds: String): Result<ApiItems<Provider>> = TODO()
            override suspend fun getCategories(): Result<ApiItems<Category>> = TODO()
            override suspend fun getCatalog(page: Int, pageSize: Int): Result<ApiPage<ServiceCatalogItem>> = TODO()
        }

        val fakeFavoriteRepo = object : FavoriteRepository {
            override suspend fun listFavorites(page: Int, pageSize: Int): Result<ApiPage<Business>> =
                Result.success(ApiPage(listOf(b2), 1, 10, 1))
            override suspend fun addFavorite(businessId: String): Result<Unit> = Result.success(Unit)
            override suspend fun removeFavorite(businessId: String): Result<Unit> = Result.success(Unit)
        }

        val listBusinesses = ListBusinessesUseCase(fakeBusinessRepo)
        val toggleFavorite = ToggleFavoriteUseCase(fakeFavoriteRepo)
        val listFavorites = ListFavoritesUseCase(fakeFavoriteRepo)

        val viewModel = HomeViewModel(listBusinesses, toggleFavorite, listFavorites, null)
        advanceUntilIdle()

        val items = viewModel.state.value.items
        assertEquals(3, items.size)
        // biz-2 must be at index 0 because it is a favorite
        assertEquals("biz-2", items[0].id)
        assertTrue(items.map { it.id }.containsAll(listOf("biz-1", "biz-2", "biz-3")))
    }
}
