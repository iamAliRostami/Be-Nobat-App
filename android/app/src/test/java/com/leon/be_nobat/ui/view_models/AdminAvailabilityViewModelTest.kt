package com.leon.be_nobat.ui.view_models

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.*
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.domain.usecase.AdminCreateAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminDeleteAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminListAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateAvailabilityUseCase
import com.leon.be_nobat.ui.view_models.admin.AdminAvailabilityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class AdminAvailabilityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private var businesses = listOf<ManagementBusiness>()
    private var branches = listOf<ManagementBranch>()
    private var resources = listOf<ManagementResource>()
    private var availabilities = listOf<ManagementAvailability>()

    private lateinit var fakeRepo: ManagementRepository
    private lateinit var viewModel: AdminAvailabilityViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        businesses = listOf(
            ManagementBusiness(id = "biz1", name = "کلینیک رویال", active = true),
            ManagementBusiness(id = "biz2", name = "سالن زیبایی آوا", active = true),
        )

        branches = listOf(
            ManagementBranch(id = "branch1", businessId = "biz1", name = "شعبه مرکزی رویال", active = true),
            ManagementBranch(id = "branch2", businessId = "biz1", name = "شعبه غرب رویال", active = true),
            ManagementBranch(id = "branch3", businessId = "biz2", name = "شعبه آوا", active = true),
        )

        resources = listOf(
            ManagementResource(id = "res1", branchId = "branch1", name = "دکتر علی رضایی", kind = "staff", active = true),
            ManagementResource(id = "res2", branchId = "branch2", name = "دکتر مریم احمدی", kind = "staff", active = true),
            ManagementResource(id = "res3", branchId = "branch3", name = "سارا اکبری", kind = "staff", active = true),
        )

        availabilities = listOf(
            ManagementAvailability(
                id = "rule1",
                businessId = "biz1",
                businessName = "کلینیک رویال",
                branchId = "branch1",
                branchName = "شعبه مرکزی رویال",
                resourceId = "res1",
                resourceName = "دکتر علی رضایی",
                effectiveDate = "2099-01-01",
                dayOfWeek = "Saturday",
                startsAt = "09:00:00",
                endsAt = "13:00:00",
                isAvailable = true,
            ),
            ManagementAvailability(
                id = "rule2",
                businessId = "biz1",
                businessName = "کلینیک رویال",
                branchId = "branch2",
                branchName = "شعبه غرب رویال",
                resourceId = "res2",
                resourceName = "دکتر مریم احمدی",
                effectiveDate = "2099-01-02",
                dayOfWeek = "Sunday",
                startsAt = "14:00:00",
                endsAt = "18:00:00",
                isAvailable = true,
            ),
            ManagementAvailability(
                id = "rule3",
                businessId = "biz2",
                businessName = "سالن زیبایی آوا",
                branchId = "branch3",
                branchName = "شعبه آوا",
                resourceId = "res3",
                resourceName = "سارا اکبری",
                effectiveDate = "2099-01-03",
                dayOfWeek = "Monday",
                startsAt = "10:00:00",
                endsAt = "16:00:00",
                isAvailable = true,
            ),
            ManagementAvailability(
                id = "rule4_past",
                businessId = "biz1",
                businessName = "کلینیک رویال",
                branchId = "branch1",
                branchName = "شعبه مرکزی رویال",
                resourceId = "res1",
                resourceName = "دکتر علی رضایی",
                effectiveDate = "2020-01-01",
                dayOfWeek = "Wednesday",
                startsAt = "08:00:00",
                endsAt = "12:00:00",
                isAvailable = true,
            ),
        )

        val repo = FakeManagementRepo()
        repo.businesses = businesses
        repo.branches = branches
        repo.resources = resources
        repo.availabilities = availabilities
        fakeRepo = repo

        viewModel = AdminAvailabilityViewModel(
            list = AdminListAvailabilityUseCase(fakeRepo),
            create = AdminCreateAvailabilityUseCase(fakeRepo),
            update = AdminUpdateAvailabilityUseCase(fakeRepo),
            delete = AdminDeleteAvailabilityUseCase(fakeRepo),
            management = fakeRepo,
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load loads all items, options and filters to active tab`() {
        val s = viewModel.state.value
        assertEquals(4, s.items.size)
        // Default tab is "active", rule4_past is in 2020 so filteredItems contains 3 future rules
        assertEquals(3, s.filteredItems.size)
        assertEquals(3, s.total)
        assertEquals(2, s.businesses.size)
        assertEquals(3, s.branches.size)
        assertEquals(3, s.resources.size)
        assertFalse(s.hasFilters)
    }

    @Test
    fun `switching to past tab filters to past rules`() {
        viewModel.setTab("past")
        val s = viewModel.state.value
        assertEquals(1, s.filteredItems.size)
        assertEquals("rule4_past", s.filteredItems[0].id)
    }

    @Test
    fun `setBusinessFilter filters items to chosen business`() {
        viewModel.setBusinessFilter("biz1")
        val s = viewModel.state.value
        assertEquals("biz1", s.selectedBusinessId)
        assertEquals("کلینیک رویال", s.selectedBusinessName)
        assertTrue(s.hasFilters)
        assertEquals(2, s.filteredItems.size)
        assertTrue(s.filteredItems.all { it.businessId == "biz1" })
        assertEquals(2, s.filterBranches.size)
    }

    @Test
    fun `setBranchFilter filters items to chosen branch`() {
        viewModel.setBusinessFilter("biz1")
        viewModel.setBranchFilter("branch1")
        val s = viewModel.state.value
        assertEquals("branch1", s.selectedBranchId)
        assertEquals("شعبه مرکزی رویال", s.selectedBranchName)
        assertEquals(1, s.filteredItems.size)
        assertEquals("rule1", s.filteredItems[0].id)
    }

    @Test
    fun `setResourceFilter filters items to chosen resource`() {
        viewModel.setResourceFilter("res3")
        val s = viewModel.state.value
        assertEquals("res3", s.selectedResourceId)
        assertEquals("سارا اکبری", s.selectedResourceName)
        assertEquals(1, s.filteredItems.size)
        assertEquals("rule3", s.filteredItems[0].id)
    }

    @Test
    fun `changing business filter resets branch and resource filters`() {
        viewModel.setBusinessFilter("biz1")
        viewModel.setBranchFilter("branch1")
        viewModel.setResourceFilter("res1")

        assertEquals("branch1", viewModel.state.value.selectedBranchId)
        assertEquals("res1", viewModel.state.value.selectedResourceId)

        // Change business to biz2
        viewModel.setBusinessFilter("biz2")
        val s = viewModel.state.value
        assertEquals("biz2", s.selectedBusinessId)
        assertNull(s.selectedBranchId)
        assertNull(s.selectedResourceId)
        assertEquals(1, s.filteredItems.size)
        assertEquals("rule3", s.filteredItems[0].id)
    }

    @Test
    fun `changing branch filter resets resource filter`() {
        viewModel.setBusinessFilter("biz1")
        viewModel.setBranchFilter("branch1")
        viewModel.setResourceFilter("res1")

        // Change branch to branch2
        viewModel.setBranchFilter("branch2")
        val s = viewModel.state.value
        assertEquals("branch2", s.selectedBranchId)
        assertNull(s.selectedResourceId)
        assertEquals(1, s.filteredItems.size)
        assertEquals("rule2", s.filteredItems[0].id)
    }

    @Test
    fun `clearFilters resets all filter selections`() {
        viewModel.setBusinessFilter("biz1")
        viewModel.setBranchFilter("branch1")
        assertTrue(viewModel.state.value.hasFilters)

        viewModel.clearFilters()
        val s = viewModel.state.value
        assertFalse(s.hasFilters)
        assertNull(s.selectedBusinessId)
        assertNull(s.selectedBranchId)
        assertNull(s.selectedResourceId)
        assertEquals(3, s.filteredItems.size)
    }

    @Test
    fun `searchQuery filters items across name and dates`() {
        viewModel.setSearchQuery("مریم")
        val s = viewModel.state.value
        assertEquals(1, s.filteredItems.size)
        assertEquals("rule2", s.filteredItems[0].id)

        viewModel.setSearchQuery("")
        assertEquals(3, viewModel.state.value.filteredItems.size)
    }

    @Test
    fun `setFilters sets business, branch and resource simultaneously`() {
        viewModel.setFilters("biz1", "branch2", "res2")
        val s = viewModel.state.value
        assertEquals("biz1", s.selectedBusinessId)
        assertEquals("branch2", s.selectedBranchId)
        assertEquals("res2", s.selectedResourceId)
        assertEquals(1, s.filteredItems.size)
        assertEquals("rule2", s.filteredItems[0].id)
    }

    private class FakeManagementRepo : ManagementRepository {
        var businesses = listOf<ManagementBusiness>()
        var branches = listOf<ManagementBranch>()
        var resources = listOf<ManagementResource>()
        var availabilities = listOf<ManagementAvailability>()

        override suspend fun dashboard(): Result<ManagementDashboard> = TODO()
        override suspend fun analytics(
            businessId: String?,
            branchId: String?,
            resourceId: String?,
            from: String?,
            to: String?,
        ): Result<com.leon.be_nobat.domain.model.ManagementAnalyticsSummary> = TODO()
        override suspend fun context(): Result<ManagementContext> = TODO()

        override suspend fun listBusinesses(q: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementBusiness>> =
            Result.success(ApiPage(businesses, businesses.size, 1, 100))
        override suspend fun getBusiness(id: String): Result<ManagementBusiness> = TODO()
        override suspend fun updateBusiness(id: String, input: ManagementBusinessInput): Result<ManagementBusiness> = TODO()

        override suspend fun listBranches(
            businessId: String?,
            q: String?,
            includeInactive: Boolean?,
            page: Int,
            pageSize: Int,
        ): Result<ApiPage<ManagementBranch>> {
            var items = branches
            if (businessId != null) items = items.filter { it.businessId == businessId }
            return Result.success(ApiPage(items, items.size, 1, 100))
        }

        override suspend fun createBranch(input: ManagementBranchInput): Result<ManagementBranch> = TODO()
        override suspend fun getBranch(id: String): Result<ManagementBranch> = TODO()
        override suspend fun updateBranch(id: String, input: ManagementBranchInput): Result<ManagementBranch> = TODO()
        override suspend fun setBranchActive(id: String, active: Boolean): Result<ManagementBranch> = TODO()
        override suspend fun deleteBranch(id: String): Result<Unit> = TODO()
        override suspend fun approveBranch(id: String): Result<ManagementBranch> = TODO()

        override suspend fun listResources(
            branchId: String?,
            businessId: String?,
            includeInactive: Boolean?,
            page: Int,
            pageSize: Int,
        ): Result<ApiPage<ManagementResource>> {
            var items = resources
            if (branchId != null) items = items.filter { it.branchId == branchId }
            return Result.success(ApiPage(items, items.size, 1, 100))
        }

        override suspend fun createResource(input: ManagementResourceInput): Result<ManagementResource> = TODO()
        override suspend fun updateResource(id: String, input: ManagementResourceInput): Result<ManagementResource> = TODO()
        override suspend fun setResourceActive(id: String, active: Boolean): Result<ManagementResource> = TODO()
        override suspend fun deleteResource(id: String): Result<Unit> = TODO()

        override suspend fun listMemberships(branchId: String?, userId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementMembership>> = TODO()
        override suspend fun createMembership(input: ManagementMembershipInput): Result<ManagementMembership> = TODO()
        override suspend fun updateMembership(id: String, input: ManagementMembershipInput): Result<ManagementMembership> = TODO()
        override suspend fun deleteMembership(id: String): Result<Unit> = TODO()

        override suspend fun listTeam(q: String?, branchId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementPerson>> = TODO()
        override suspend fun listCustomers(q: String?, branchId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementPerson>> = TODO()
        override suspend fun createTeamMember(input: ManagementTeamMemberInput): Result<ManagementPerson> = TODO()
        override suspend fun createCustomer(input: ManagementCustomerInput): Result<ManagementPerson> = TODO()
        override suspend fun setPersonActive(id: String, active: Boolean): Result<ManagementPerson> = TODO()

        override suspend fun listServices(businessId: String?, branchId: String?, q: String?, includeInactive: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementService>> = TODO()
        override suspend fun getService(id: String): Result<ManagementService> = TODO()
        override suspend fun createService(input: ManagementServiceInput): Result<ManagementService> = TODO()
        override suspend fun updateService(id: String, input: ManagementServiceInput): Result<ManagementService> = TODO()
        override suspend fun setServiceActive(id: String, active: Boolean): Result<ManagementService> = TODO()
        override suspend fun deleteService(id: String): Result<Unit> = TODO()

        override suspend fun listBranchServices(branchId: String, page: Int, pageSize: Int): Result<ApiPage<ManagementBranchService>> = TODO()
        override suspend fun upsertBranchService(branchId: String, serviceId: String, input: ManagementBranchServiceInput): Result<ManagementBranchService> = TODO()
        override suspend fun deleteBranchService(branchId: String, serviceId: String): Result<Unit> = TODO()

        override suspend fun listResourceServices(resourceId: String): Result<ManagementResourceServicesInput> = TODO()
        override suspend fun setResourceServices(resourceId: String, input: ManagementResourceServicesInput): Result<ManagementResourceServicesInput> = TODO()

        override suspend fun listAvailability(
            businessId: String?,
            branchId: String?,
            resourceId: String?,
            from: String?,
            to: String?,
            page: Int,
            pageSize: Int,
        ): Result<ApiPage<ManagementAvailability>> {
            var items = availabilities
            if (businessId != null) items = items.filter { it.businessId == businessId }
            if (branchId != null) items = items.filter { it.branchId == branchId }
            if (resourceId != null) items = items.filter { it.resourceId == resourceId }
            return Result.success(ApiPage(items, items.size, 1, 100))
        }

        override suspend fun createAvailability(input: ManagementAvailabilityInput): Result<ManagementAvailability> =
            Result.success(availabilities.first())
        override suspend fun updateAvailability(id: String, input: ManagementAvailabilityInput): Result<ManagementAvailability> =
            Result.success(availabilities.first())
        override suspend fun deleteAvailability(id: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun listAppointments(
            businessId: String?,
            branchId: String?,
            resourceId: String?,
            customerId: String?,
            date: String?,
            status: String?,
            page: Int,
            pageSize: Int,
        ): Result<ApiPage<ManagementAppointment>> = TODO()

        override suspend fun getAppointment(id: String): Result<ManagementAppointment> = TODO()
        override suspend fun setAppointmentStatus(id: String, input: ManagementStatusInput): Result<ManagementAppointment> = TODO()

        override suspend fun listReviews(
            businessId: String?,
            branchId: String?,
            customerId: String?,
            status: String?,
            rating: Int?,
            includeInactive: Boolean?,
            page: Int,
            pageSize: Int,
        ): Result<ApiPage<ManagementReview>> = TODO()

        override suspend fun moderateReview(id: String, input: ManagementModerationInput): Result<ManagementReview> = TODO()
        override suspend fun replyReview(id: String, input: ManagementReplyInput): Result<ManagementReview> = TODO()
        override suspend fun setReviewActive(id: String, active: Boolean): Result<ManagementReview> = TODO()
        override suspend fun deleteReview(id: String): Result<Unit> = TODO()

        override suspend fun listCustomerReviews(businessId: String?, branchId: String?, customerId: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementCustomerReview>> = TODO()
        override suspend fun listPendingCustomerReviews(page: Int, pageSize: Int): Result<ApiPage<ManagementAppointment>> = TODO()
        override suspend fun createCustomerReview(input: ManagementCustomerReviewInput): Result<ManagementCustomerReview> = TODO()
        override suspend fun updateCustomerReview(id: String, input: ManagementCustomerReviewInput): Result<ManagementCustomerReview> = TODO()
        override suspend fun deleteCustomerReview(id: String): Result<Unit> = TODO()
    }
}
