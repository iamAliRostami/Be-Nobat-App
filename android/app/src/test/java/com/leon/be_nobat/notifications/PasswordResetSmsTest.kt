package com.leon.be_nobat.notifications

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.*
import com.leon.be_nobat.domain.repository.PlatformRepository
import com.leon.be_nobat.domain.usecase.*
import com.leon.be_nobat.ui.view_models.platform.PlatformAccessViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetSmsTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = ManagementUser(id = "user-a", displayName = "علی", phoneNumber = "09121234567", email = "ali@example.test")
    private var calls = 0
    private var fail = false
    private var wrongUser = false
    private lateinit var vm: PlatformAccessViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val unused = Proxy.newProxyInstance(PlatformRepository::class.java.classLoader,
            arrayOf(PlatformRepository::class.java)) { _, method, _ -> error("Unexpected ${method.name}") } as PlatformRepository
        val repo = object : PlatformRepository by unused {
            override suspend fun listUsers(q: String?, role: String?, active: Boolean?, page: Int, pageSize: Int): Result<ApiPage<ManagementUser>> =
                Result.success(ApiPage(items = listOf(user), total = 1))
            override suspend fun resetUserPassword(id: String): Result<PlatformResetPasswordResponse> {
                calls++
                delay(100)
                return if (fail) Result.failure(IllegalStateException("offline")) else
                    Result.success(PlatformResetPasswordResponse(if (wrongUser) "other-user" else id, "Temp!Abc123"))
            }
        }
        vm = PlatformAccessViewModel(PlatformListUsersUseCase(repo), PlatformCreateUserUseCase(repo),
            PlatformUpdateUserUseCase(repo), PlatformSetUserRolesUseCase(repo), PlatformToggleUserActiveUseCase(repo),
            PlatformResetPasswordUseCase(repo))
        dispatcher.scheduler.advanceUntilIdle()
    }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun `successful reset queues exact returned password and selected recipient once`() {
        vm.resetPassword(user.id); vm.resetPassword(user.id)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        val sms = requireNotNull(vm.passwordSms.value)
        assertEquals(user.id, sms.userId)
        assertEquals(user.phoneNumber, sms.phoneNumber)
        assertEquals("Temp!Abc123", sms.password)
        assertTrue(sms.body().contains(user.email!!))
        assertTrue(sms.body().contains("پس از ورود"))
        assertFalse(sms.toString().contains(sms.password))
        vm.resetPassword(user.id); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        assertSame(sms, vm.takePasswordSms())
        assertNull(vm.takePasswordSms())
        assertNull(vm.state.value.temporaryPassword)
    }
    @Test fun `reset failure never queues a password message`() {
        fail = true; vm.resetPassword(user.id); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.passwordSms.value)
        assertNull(vm.state.value.temporaryPassword)
        assertEquals("offline", vm.state.value.error)
    }
    @Test fun `response for another user cannot be sent to selected phone`() {
        wrongUser = true; vm.resetPassword(user.id); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.passwordSms.value)
        assertNotNull(vm.state.value.error)
    }
    @Test fun `searching or clearing the display message does not lose recipient while permission is pending`() {
        vm.resetPassword(user.id); dispatcher.scheduler.advanceUntilIdle()
        vm.smsPermissionPending = true
        vm.setQuery("not in list"); vm.clearMessage()
        assertTrue(vm.state.value.filteredItems.isEmpty())
        val sms = requireNotNull(vm.takePasswordSms())
        assertEquals(user.phoneNumber, sms.phoneNumber)
        assertEquals("Temp!Abc123", sms.password)
    }
    @Test fun `unknown user cannot reset or send an unrelated password`() {
        vm.resetPassword("unknown"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, calls)
        assertNull(vm.passwordSms.value)
    }
}
