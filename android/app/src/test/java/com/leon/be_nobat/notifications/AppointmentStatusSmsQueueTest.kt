package com.leon.be_nobat.notifications

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.domain.model.ManagementStatusInput
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.domain.usecase.AdminListAppointmentsUseCase
import com.leon.be_nobat.domain.usecase.AdminSetAppointmentStatusUseCase
import com.leon.be_nobat.ui.view_models.admin.AdminCalendarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentStatusSmsQueueTest {
    private val dispatcher = StandardTestDispatcher()
    private var current = ManagementAppointment(id = "a", status = "Pending", customerPhoneNumber = "09121234567")
    private var failure = false
    private var calls = 0
    private lateinit var vm: AdminCalendarViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val unsupported = Proxy.newProxyInstance(ManagementRepository::class.java.classLoader,
            arrayOf(ManagementRepository::class.java)) { _, method, _ -> error("Unexpected ${method.name}") } as ManagementRepository
        val repo = object : ManagementRepository by unsupported {
            override suspend fun listAppointments(businessId: String?, branchId: String?, resourceId: String?, customerId: String?, date: String?, status: String?, page: Int, pageSize: Int): Result<ApiPage<ManagementAppointment>> =
                Result.success(ApiPage(items = listOf(current), total = 1))
            override suspend fun setAppointmentStatus(id: String, input: ManagementStatusInput): Result<ManagementAppointment> {
                calls++
                delay(100)
                return if (failure) Result.failure(IllegalStateException("offline"))
                    else Result.success(current.copy(status = input.status).also { current = it })
            }
        }
        vm = AdminCalendarViewModel(AdminListAppointmentsUseCase(repo), AdminSetAppointmentStatusUseCase(repo))
        vm.load(); dispatcher.scheduler.advanceUntilIdle()
    }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun `only successful changed status is queued and duplicate taps make one request`() {
        vm.changeStatus("a", "Confirmed"); vm.changeStatus("a", "Confirmed")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("Confirmed", vm.smsQueue.value.single().status)
        vm.changeStatus("a", "Confirmed"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        vm.acknowledgeSms("a")
        assertTrue(vm.smsQueue.value.isEmpty())
    }
    @Test fun `failed update never queues a message or changes displayed status`() {
        failure = true; vm.changeStatus("a", "Confirmed"); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.smsQueue.value.isEmpty())
        assertEquals("Pending", vm.state.value.items.single().status)
        assertEquals("offline", vm.state.value.error)
    }
    @Test fun `two changes to same appointment keep separate messages while permission is pending`() {
        vm.changeStatus("a", "Confirmed"); dispatcher.scheduler.advanceUntilIdle()
        vm.changeStatus("a", "Completed"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.smsQueue.value.size)
        vm.acknowledgeSms("a")
        assertEquals("Completed", vm.smsQueue.value.single().status)
    }
}
