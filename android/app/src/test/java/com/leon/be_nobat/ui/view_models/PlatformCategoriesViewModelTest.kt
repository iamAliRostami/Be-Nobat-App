package com.leon.be_nobat.ui.view_models

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.*
import com.leon.be_nobat.domain.repository.PlatformRepository
import com.leon.be_nobat.domain.usecase.*
import com.leon.be_nobat.ui.view_models.platform.PlatformCategoriesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformCategoriesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepo = FakePlatformRepository()

    private lateinit var viewModel: PlatformCategoriesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo.categories = listOf(
            ManagementCategory(id = "1", name = "پزشکی و سلامت", kind = "Business", sortOrder = 1, active = true),
            ManagementCategory(id = "2", name = "زیبایی و پوست", kind = "Business", sortOrder = 2, active = false),
            ManagementCategory(id = "3", name = "ویزیت عمومی", kind = "Service", sortOrder = 3, active = true),
            ManagementCategory(id = "4", name = "خدمات آرایشی", kind = "Service", sortOrder = 4, active = false),
        )
        viewModel = PlatformCategoriesViewModel(
            list = PlatformListCategoriesUseCase(fakeRepo),
            createUseCase = PlatformCreateCategoryUseCase(fakeRepo),
            updateUseCase = PlatformUpdateCategoryUseCase(fakeRepo),
            deleteUseCase = PlatformDeleteCategoryUseCase(fakeRepo),
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load populates all items`() {
        assertEquals(4, viewModel.state.value.items.size)
        assertEquals(4, viewModel.state.value.filteredItems.size)
    }

    @Test
    fun `setSelectedTab Business filters to business categories`() {
        viewModel.setSelectedTab("Business")
        val filtered = viewModel.state.value.filteredItems
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.kind == "Business" })
    }

    @Test
    fun `setSelectedTab Service filters to service categories`() {
        viewModel.setSelectedTab("Service")
        val filtered = viewModel.state.value.filteredItems
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.kind == "Service" })
    }

    @Test
    fun `setSelectedTab Active filters to active categories`() {
        viewModel.setSelectedTab("Active")
        val filtered = viewModel.state.value.filteredItems
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.active })
    }

    @Test
    fun `setSelectedTab Inactive filters to inactive categories`() {
        viewModel.setSelectedTab("Inactive")
        val filtered = viewModel.state.value.filteredItems
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { !it.active })
    }

    @Test
    fun `setQuery filters categories by name`() {
        viewModel.setQuery("پزشکی")
        val filtered = viewModel.state.value.filteredItems
        assertEquals(1, filtered.size)
        assertEquals("پزشکی و سلامت", filtered[0].name)
    }

    @Test
    fun `combined tab and search query filtering works correctly`() {
        viewModel.setSelectedTab("Business")
        viewModel.setQuery("پوست")
        val filtered = viewModel.state.value.filteredItems
        assertEquals(1, filtered.size)
        assertEquals("زیبایی و پوست", filtered[0].name)

        // Switching tab to Service with same query should yield empty
        viewModel.setSelectedTab("Service")
        assertEquals(0, viewModel.state.value.filteredItems.size)
    }

    @Test
    fun `toggleActive toggles category status and maintains filter`() = runTest {
        val cat = viewModel.state.value.items.first { it.id == "1" }
        viewModel.toggleActive(cat)
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = viewModel.state.value.items.first { it.id == "1" }
        assertEquals(false, updated.active)
    }

    private class FakePlatformRepository : PlatformRepository {
        var categories = listOf<ManagementCategory>()

        override suspend fun listBusinesses(q: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementBusiness>> =
            Result.success(ApiPage())
        override suspend fun createBusiness(input: ManagementBusinessInput): Result<ManagementBusiness> = TODO()
        override suspend fun updateBusiness(id: String, input: ManagementBusinessInput): Result<ManagementBusiness> = TODO()
        override suspend fun setBusinessActive(id: String, active: Boolean): Result<ManagementBusiness> = TODO()
        override suspend fun deleteBusiness(id: String): Result<Unit> = TODO()

        override suspend fun listCatalog(q: String?, category: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementCatalog>> =
            Result.success(ApiPage())
        override suspend fun createCatalogItem(input: ManagementCatalogInput): Result<ManagementCatalog> = TODO()
        override suspend fun updateCatalogItem(id: String, input: ManagementCatalogInput): Result<ManagementCatalog> = TODO()
        override suspend fun deleteCatalogItem(id: String): Result<Unit> = TODO()

        override suspend fun listCategories(kind: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementCategory>> {
            var items = categories
            if (!kind.isNullOrBlank()) items = items.filter { it.kind.equals(kind, ignoreCase = true) }
            if (includeInactive == false) items = items.filter { it.active }
            return Result.success(ApiPage(items = items, total = items.size))
        }

        override suspend fun createCategory(input: ManagementCategoryInput): Result<ManagementCategory> {
            val created = ManagementCategory(id = "new-${categories.size}", name = input.name, kind = input.kind, sortOrder = input.sortOrder, active = input.isActive)
            categories = listOf(created) + categories
            return Result.success(created)
        }

        override suspend fun updateCategory(id: String, input: ManagementCategoryInput): Result<ManagementCategory> {
            val updated = ManagementCategory(id = id, name = input.name, kind = input.kind, sortOrder = input.sortOrder, active = input.isActive)
            categories = categories.map { if (it.id == id) updated else it }
            return Result.success(updated)
        }

        override suspend fun deleteCategory(id: String): Result<Unit> {
            categories = categories.filterNot { it.id == id }
            return Result.success(Unit)
        }

        override suspend fun listUsers(q: String?, role: String?, active: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementUser>> =
            Result.success(ApiPage())
        override suspend fun getUser(id: String): Result<ManagementUser> = TODO()
        override suspend fun createUser(input: ManagementUserInput): Result<ManagementCreatedUser> = TODO()
        override suspend fun updateUser(id: String, input: ManagementUserInput): Result<ManagementUser> = TODO()
        override suspend fun setUserRoles(id: String, input: ManagementRolesInput): Result<ManagementUser> = TODO()
        override suspend fun setUserActive(id: String, active: Boolean): Result<ManagementUser> = TODO()
        override suspend fun resetUserPassword(id: String): Result<PlatformResetPasswordResponse> = TODO()

        override suspend fun listBusinessRequests(status: String?, q: String?, page: Int, pageSize: Int): Result<ApiPage<BusinessRequest>> =
            Result.success(ApiPage())
        override suspend fun approveBusinessRequest(id: String): Result<BusinessRequest> = TODO()
        override suspend fun rejectBusinessRequest(id: String, input: ReviewBusinessRequestInput): Result<BusinessRequest> = TODO()
        override suspend fun approveBranch(id: String): Result<ManagementBranch> = TODO()
    }
}
