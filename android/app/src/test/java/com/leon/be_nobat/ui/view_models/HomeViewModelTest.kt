package com.leon.be_nobat.ui.view_models

import com.leon.be_nobat.data.remote.ApiItems
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.model.BookingInput
import com.leon.be_nobat.domain.model.Branch
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.model.BusinessDetail
import com.leon.be_nobat.domain.model.Category
import com.leon.be_nobat.domain.model.Provider
import com.leon.be_nobat.domain.model.PublicReview
import com.leon.be_nobat.domain.model.ReviewCreated
import com.leon.be_nobat.domain.model.ReviewInput
import com.leon.be_nobat.domain.model.Service
import com.leon.be_nobat.domain.model.ServiceCatalogItem
import com.leon.be_nobat.domain.model.Slot
import com.leon.be_nobat.domain.model.SlotsResponse
import com.leon.be_nobat.domain.repository.BookingRepository
import com.leon.be_nobat.domain.repository.BusinessRepository
import com.leon.be_nobat.domain.repository.FavoriteRepository
import com.leon.be_nobat.domain.usecase.GetSlotsUseCase
import com.leon.be_nobat.domain.usecase.ListBranchesUseCase
import com.leon.be_nobat.domain.usecase.ListBranchServicesUseCase
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

    @Test
    fun `loadTodaySlots fetches available slots from services`() = runTest(testDispatcher) {
        val b1 = Business(id = "biz-1", name = "مرکز اول", category = "زیبایی")
        val branch = Branch(id = "branch-1", businessId = "biz-1", name = "شعبه اصلی")
        val service = Service(id = "srv-1", businessId = "biz-1", name = "کوتاهی مو", durationMinutes = 30, price = 100000.0)
        val slot = Slot(resourceId = "res-1", resourceName = "علی", startsAt = "2099-01-01T18:00:00", endsAt = "2099-01-01T18:30:00")

        val fakeBusinessRepo = object : BusinessRepository {
            override suspend fun listBusinesses(q: String?, city: String?, category: String?, page: Int, pageSize: Int): Result<ApiPage<Business>> =
                Result.success(ApiPage(listOf(b1), 1, 10, 1))
            override suspend fun getBusiness(id: String): Result<BusinessDetail> = TODO()
            override suspend fun listBusinessReviews(id: String, page: Int, pageSize: Int): Result<ApiPage<PublicReview>> = TODO()
            override suspend fun listBranches(businessId: String): Result<ApiItems<Branch>> =
                Result.success(ApiItems(listOf(branch)))
            override suspend fun listBranchServices(businessId: String, branchId: String): Result<ApiItems<Service>> =
                Result.success(ApiItems(listOf(service)))
            override suspend fun listProviders(businessId: String, branchId: String, serviceIds: String): Result<ApiItems<Provider>> = TODO()
            override suspend fun getCategories(): Result<ApiItems<Category>> = TODO()
            override suspend fun getCatalog(page: Int, pageSize: Int): Result<ApiPage<ServiceCatalogItem>> = TODO()
        }

        val fakeBookingRepo = object : BookingRepository {
            override suspend fun getSlots(businessId: String, branchId: String, serviceIds: String, date: String, resourceId: String?): Result<SlotsResponse> =
                Result.success(SlotsResponse(slots = listOf(slot)))
            override suspend fun createAppointment(input: BookingInput): Result<Appointment> = TODO()
            override suspend fun listAppointments(status: String?, page: Int, pageSize: Int): Result<ApiPage<Appointment>> = TODO()
            override suspend fun getAppointment(id: String): Result<Appointment> = TODO()
            override suspend fun cancelAppointment(id: String): Result<Appointment> = TODO()
            override suspend fun createReview(id: String, input: ReviewInput): Result<ReviewCreated> = TODO()
        }

        val listBusinesses = ListBusinessesUseCase(fakeBusinessRepo)
        val listBranches = ListBranchesUseCase(fakeBusinessRepo)
        val listBranchServices = ListBranchServicesUseCase(fakeBusinessRepo)
        val getSlots = GetSlotsUseCase(fakeBookingRepo)

        val viewModel = HomeViewModel(
            listBusinesses = listBusinesses,
            toggleFavorite = ToggleFavoriteUseCase(object : FavoriteRepository {
                override suspend fun listFavorites(page: Int, pageSize: Int) = Result.success(ApiPage<Business>(emptyList(), 1, 10, 0))
                override suspend fun addFavorite(businessId: String) = Result.success(Unit)
                override suspend fun removeFavorite(businessId: String) = Result.success(Unit)
            }),
            listBranchesUseCase = listBranches,
            listBranchServicesUseCase = listBranchServices,
            getSlotsUseCase = getSlots,
        )
        advanceUntilIdle()

        viewModel.loadTodaySlots(forTomorrow = true)
        advanceUntilIdle()

        val slots = viewModel.state.value.todaySlots
        assertEquals(1, slots.size)
        assertEquals("biz-1", slots[0].businessId)
        assertEquals("مرکز اول", slots[0].businessName)
        assertEquals("srv-1", slots[0].serviceId)
        assertEquals("کوتاهی مو", slots[0].serviceName)
        assertEquals(com.leon.be_nobat.helpers.PersianUtils.toPersianDigits("18:00"), slots[0].timeLabel)
    }

    @Test
    fun `setTodaySlotsFilter filters slots by category and city`() = runTest(testDispatcher) {
        val b1 = Business(id = "biz-1", name = "مرکز اول", category = "زیبایی", city = "تهران")
        val b2 = Business(id = "biz-2", name = "مرکز دوم", category = "پزشکی", city = "اصفهان")
        val branch1 = Branch(id = "branch-1", businessId = "biz-1", name = "شعبه ۱")
        val branch2 = Branch(id = "branch-2", businessId = "biz-2", name = "شعبه ۲")
        val srv1 = Service(id = "srv-1", businessId = "biz-1", name = "سرویس ۱")
        val srv2 = Service(id = "srv-2", businessId = "biz-2", name = "سرویس ۲")
        val slot1 = Slot(startsAt = "2099-01-01T10:00:00")
        val slot2 = Slot(startsAt = "2099-01-01T11:00:00")

        val fakeBusinessRepo = object : BusinessRepository {
            override suspend fun listBusinesses(q: String?, city: String?, category: String?, page: Int, pageSize: Int) =
                Result.success(ApiPage(listOf(b1, b2), 1, 10, 2))
            override suspend fun getBusiness(id: String) = TODO()
            override suspend fun listBusinessReviews(id: String, page: Int, pageSize: Int) = TODO()
            override suspend fun listBranches(businessId: String) =
                Result.success(ApiItems(listOf(if (businessId == "biz-1") branch1 else branch2)))
            override suspend fun listBranchServices(businessId: String, branchId: String) =
                Result.success(ApiItems(listOf(if (businessId == "biz-1") srv1 else srv2)))
            override suspend fun listProviders(businessId: String, branchId: String, serviceIds: String) = TODO()
            override suspend fun getCategories() = TODO()
            override suspend fun getCatalog(page: Int, pageSize: Int) = TODO()
        }

        val fakeBookingRepo = object : BookingRepository {
            override suspend fun getSlots(businessId: String, branchId: String, serviceIds: String, date: String, resourceId: String?) =
                Result.success(SlotsResponse(slots = listOf(if (businessId == "biz-1") slot1 else slot2)))
            override suspend fun createAppointment(input: BookingInput) = TODO()
            override suspend fun listAppointments(status: String?, page: Int, pageSize: Int) = TODO()
            override suspend fun getAppointment(id: String) = TODO()
            override suspend fun cancelAppointment(id: String) = TODO()
            override suspend fun createReview(id: String, input: ReviewInput) = TODO()
        }

        val viewModel = HomeViewModel(
            listBusinesses = ListBusinessesUseCase(fakeBusinessRepo),
            toggleFavorite = ToggleFavoriteUseCase(object : FavoriteRepository {
                override suspend fun listFavorites(page: Int, pageSize: Int) = Result.success(ApiPage<Business>(emptyList(), 1, 10, 0))
                override suspend fun addFavorite(businessId: String) = Result.success(Unit)
                override suspend fun removeFavorite(businessId: String) = Result.success(Unit)
            }),
            listBranchesUseCase = ListBranchesUseCase(fakeBusinessRepo),
            listBranchServicesUseCase = ListBranchServicesUseCase(fakeBusinessRepo),
            getSlotsUseCase = GetSlotsUseCase(fakeBookingRepo),
        )
        advanceUntilIdle()

        viewModel.loadTodaySlots(forTomorrow = true)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.todaySlotsRaw.size)
        assertEquals(2, viewModel.state.value.todaySlots.size)

        // Filter by category
        viewModel.setTodaySlotsFilter(category = "زیبایی", city = null)
        assertEquals(1, viewModel.state.value.todaySlots.size)
        assertEquals("زیبایی", viewModel.state.value.todaySlots[0].category)

        // Filter by city
        viewModel.setTodaySlotsFilter(category = null, city = "اصفهان")
        assertEquals(1, viewModel.state.value.todaySlots.size)
        assertEquals("اصفهان", viewModel.state.value.todaySlots[0].city)

        // Clear filter
        viewModel.clearTodaySlotsFilter()
        assertEquals(2, viewModel.state.value.todaySlots.size)
    }
}
