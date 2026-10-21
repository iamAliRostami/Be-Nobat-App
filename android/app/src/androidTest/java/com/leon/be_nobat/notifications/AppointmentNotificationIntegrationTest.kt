package com.leon.be_nobat.notifications

import android.content.ContextWrapper
import android.app.NotificationManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.interfaces.ICryptoManager
import com.leon.be_nobat.domain.model.*
import com.leon.be_nobat.domain.repository.BookingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppointmentNotificationIntegrationTest {
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    private class Fixture {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(original.cacheDir, "notice-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(original) { override fun getNoBackupFilesDir(): File = directory }
        val tokens = TokenManager(MemoryPreferences(), Json, object : ICryptoManager {
            override fun encrypt(threshold: String) = threshold
            override fun decrypt(encryptedData: String) = encryptedData
        })
        var items = listOf(Appointment(id = "appointment", status = "Pending", trackingCode = UUID.randomUUID().toString()))
        var failSecondPage = false
        var switchDuringRequest: (suspend () -> Unit)? = null
        val unsupported = Proxy.newProxyInstance(BookingRepository::class.java.classLoader,
            arrayOf(BookingRepository::class.java)) { _, method, _ -> error("Unexpected ${method.name}") } as BookingRepository
        val bookings = object : BookingRepository by unsupported {
            override suspend fun listAppointments(status: String?, page: Int, pageSize: Int): Result<ApiPage<Appointment>> {
                switchDuringRequest?.also { switchDuringRequest = null; it() }
                if (failSecondPage && page == 2) return Result.failure(java.io.IOException("offline"))
                return Result.success(ApiPage(items = items.drop(page - 1).take(1), page = page, pageSize = 1, total = items.size))
            }
        }
        val monitor = AppointmentNotificationMonitor(context, tokens, bookings)
        suspend fun login(id: String) { tokens.save(MobileSession(accessToken = "token-$id", user = User(id = id))) }
        suspend fun cleanup() {
            val manager = original.getSystemService(NotificationManager::class.java)
            val files = directory.listFiles().orEmpty()
            files.filter { it.extension == "json" }.forEach { file ->
                val state = Json.decodeFromString<AppointmentNoticeState>(file.readText())
                state.notices.forEach { manager.cancel(it.id, 0) }
            }
            directory.deleteRecursively()
        }
    }
    @Test fun changedStatusPersistsOnceAcrossMonitorRecreationAndLogout() = runBlocking {
        val f = Fixture()
        try {
            f.login("customer-a")
            f.monitor.sync()
            assertTrue(f.monitor.inbox().isEmpty())
            f.items = f.items.map { it.copy(status = "Confirmed") }
            f.monitor.sync(); f.monitor.sync()
            val notice = f.monitor.inbox().single()
            assertTrue(notice.body.contains(f.items.single().trackingCode))
            val reopened = AppointmentNotificationMonitor(f.context, f.tokens, f.bookings)
            assertEquals(notice.id, reopened.inbox().single().id)
            f.tokens.clear()
            assertTrue(reopened.inbox().isEmpty())
            f.login("customer-b")
            assertTrue(reopened.inbox().isEmpty())
            reopened.sync()
            assertTrue(reopened.inbox().isEmpty())
            f.login("customer-a")
            assertEquals(notice.id, reopened.inbox().single().id)
        } finally { f.cleanup() }
    }
    @Test fun failingLaterPageDoesNotConsumeChangesAndRecoveryIncludesAllPages() = runBlocking {
        val f = Fixture()
        try {
            f.login("customer-a")
            f.items = listOf(Appointment(id = "first"), Appointment(id = "second"))
            f.monitor.sync()
            f.items = f.items.map { it.copy(status = "Cancelled") }
            f.failSecondPage = true
            assertTrue(runCatching { f.monitor.sync() }.isFailure)
            assertTrue(f.monitor.inbox().isEmpty())
            f.failSecondPage = false
            f.monitor.sync()
            assertEquals(setOf("first", "second"), f.monitor.inbox().map { it.appointmentId }.toSet())
        } finally { f.cleanup() }
    }
    @Test fun accountChangedDuringRequestCannotPublishPreviousCustomersData() = runBlocking {
        val f = Fixture()
        try {
            f.login("customer-a"); f.monitor.sync()
            f.items = f.items.map { it.copy(status = "NoShow") }
            f.switchDuringRequest = { f.login("customer-b") }
            f.monitor.sync()
            assertTrue(f.monitor.inbox().isEmpty())
            f.login("customer-a")
            assertTrue(f.monitor.inbox().isEmpty())
            f.monitor.sync()
            assertEquals(1, f.monitor.inbox().size)
        } finally { f.cleanup() }
    }
}
