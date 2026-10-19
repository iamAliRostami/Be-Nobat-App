package com.leon.be_nobat.data.demo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.domain.interfaces.ICryptoManager
import com.leon.be_nobat.domain.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DemoWorkflowTest {
    @get:Rule val temp = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun store() = DemoDataStore(temp.root, json)

    @Before fun setup() {
        demoNetworkDelayEnabled = false
    }
    // TokenManager behavior is covered separately. Avoid AndroidX FileStorage's
    // POSIX rename assumption on Windows; the demo database below still uses real disk IO.
    private fun tokens() = TokenManager(object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        private val value = kotlinx.coroutines.flow.MutableStateFlow<androidx.datastore.preferences.core.Preferences>(androidx.datastore.preferences.core.emptyPreferences())
        private val mutex = kotlinx.coroutines.sync.Mutex()
        override val data: kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> = value
        override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
            mutex.lock()
            return try { transform(value.value).also { value.value = it } } finally { mutex.unlock() }
        }
    }, json, object : ICryptoManager {
        override fun encrypt(threshold: String) = threshold
        override fun decrypt(encryptedData: String) = encryptedData
    })

    @Test fun `large seed has consistent references and current dates`() {
        val s = DemoSeedData.createInitialState()
        assertTrue(s.businesses.size >= 100)
        assertTrue(s.users.size >= 300)
        assertTrue(s.appointments.size >= 5000)
        assertEquals(s.appointments.size, s.appointments.map { it.id }.toSet().size)
        assertTrue(s.appointments.all { a -> s.branches.any { it.id == a.branchId && it.businessId == a.businessId } && s.users.any { it.id == a.customerId } && a.endsAt > a.startsAt })
        assertTrue(s.appointments.any { it.startsAt.take(10) == demoNow().take(10) })
        assertTrue(s.reviews.all { r -> s.appointments.any { it.id == r.appointmentId && it.customerId == r.customerId && it.status == "Completed" } })
    }

    @Test fun `CRUD is durable and updates public catalog`() = runBlocking {
        val db = store()
        val token = tokens()
        DemoAuthRepository(db, token).login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val platform = DemoPlatformRepository(db, token)
        val management = DemoManagementRepository(db, token)
        val business = platform.createBusiness(ManagementBusinessInput(name = "مرکز آزمون")).getOrThrow()
        assertEquals(business.id, platform.listBusinesses().getOrThrow().items.first().id)
        val branch = management.listBranches(businessId = business.id).getOrThrow().items.single()
        val service = management.createService(ManagementServiceInput(businessId = business.id, branchId = branch.id, name = "مشاوره", durationMinutes = 45, price = 100.0)).getOrThrow()
        management.updateService(service.id, ManagementServiceInput(businessId = business.id, branchId = branch.id, name = "مشاوره تخصصی", durationMinutes = 60, price = 150.0)).getOrThrow()
        val reopened = store()
        val public = DemoBusinessRepository(reopened)
        assertEquals(150.0, public.listBranchServices(business.id, branch.id).getOrThrow().items.single().price, 0.0)
        management.deleteService(service.id).getOrThrow()
        management.deleteBranch(branch.id).getOrThrow()
        platform.deleteBusiness(business.id).getOrThrow()
        assertFalse(store().read { s -> s.businesses.any { it.id == business.id } })
    }

    @Test fun `concurrent booking prevents overlaps and cancellation releases slot`() = runBlocking {
        val db = store(); val token = tokens(); val auth = DemoAuthRepository(db, token)
        auth.login("user@benobat.ir", "Demo1234!").getOrThrow()
        val repo = DemoBookingRepository(db, token)
        val date = demoFormat(System.currentTimeMillis() + 2 * 86400000L).take(10)
        val slots = repo.getSlots("biz_diplomat", "br_diplomat_1", "srv_dip_1", date, "res_dip_1").getOrThrow()
        val slot = slots.slots.first()
        val input = BookingInput("biz_diplomat", "br_diplomat_1", listOf("srv_dip_1"), "res_dip_1", slot.startsAt, termsAccepted = true)
        val attempts = listOf(async { repo.createAppointment(input) }, async { repo.createAppointment(input) }).awaitAll()
        assertEquals(1, attempts.count { it.isSuccess })
        val booked = attempts.first { it.isSuccess }.getOrThrow()
        assertEquals(slots.totalDurationMinutes * 60000L, demoMillis(booked.endsAt) - demoMillis(booked.startsAt))
        val remaining = repo.getSlots("biz_diplomat", "br_diplomat_1", "srv_dip_1", date, "res_dip_1").getOrThrow()
        assertFalse(remaining.slots.any { it.startsAt < booked.endsAt && it.endsAt > booked.startsAt })
        repo.cancelAppointment(booked.id).getOrThrow()
        assertTrue(repo.getSlots("biz_diplomat", "br_diplomat_1", "srv_dip_1", date, "res_dip_1").getOrThrow().slots.any { it.startsAt == slot.startsAt })
        assertEquals("Cancelled", store().read { s -> s.appointments.first { it.id == booked.id }.status })
    }

    @Test fun `reviews cannot be duplicated and publication requires approval`() = runBlocking {
        val db = store(); val token = tokens(); DemoAuthRepository(db, token).login("user@benobat.ir", "Demo1234!").getOrThrow()
        val repo = DemoBookingRepository(db, token)
        val a = db.read { s -> s.appointments.first { it.customerId == "user_customer_1" && it.status == "Completed" && s.reviews.none { r -> r.appointmentId == it.id } } }
        val review = repo.createReview(a.id, ReviewInput(5, "تجربه خوب")).getOrThrow()
        assertFalse(repo.createReview(a.id, ReviewInput(4)).isSuccess)
        assertFalse(repo.getAppointment(a.id).getOrThrow().canReview)
        assertEquals(review.id, repo.getAppointment(a.id).getOrThrow().reviewId)
        val public = DemoBusinessRepository(db)
        assertFalse(public.getBusiness(a.businessId).getOrThrow().reviews.any { it.id == review.id })
        DemoAuthRepository(db, token).login("admin@benobat.ir", "Demo1234!").getOrThrow()
        DemoManagementRepository(db, token).moderateReview(review.id, ManagementModerationInput("Approved")).getOrThrow()
        assertTrue(public.getBusiness(a.businessId).getOrThrow().reviews.any { it.id == review.id })
    }

    @Test fun `email password and favorites persist per account`() = runBlocking {
        val db = store(); val token = tokens(); val auth = DemoAuthRepository(db, token)
        auth.login("user@benobat.ir", "Demo1234!").getOrThrow()
        val favorites = DemoFavoriteRepository(db, token)
        favorites.addFavorite("biz_carplus").getOrThrow()
        auth.changeEmail(EmailInput("changed@example.test", "Demo1234!")).getOrThrow()
        auth.changePassword(PasswordInput("Demo1234!", "Changed123!")).getOrThrow()
        auth.logout().getOrThrow()
        val reopened = DemoAuthRepository(store(), token)
        assertTrue(reopened.login("user@benobat.ir", "Demo1234!").isFailure)
        reopened.login("changed@example.test", "Changed123!").getOrThrow()
        assertTrue(favorites.listFavorites().getOrThrow().items.any { it.id == "biz_carplus" })
        reopened.login("mehdi@gmail.com", "Demo1234!").getOrThrow()
        assertFalse(favorites.listFavorites().getOrThrow().items.any { it.id == "biz_carplus" })
    }

    @Test fun `failed persistence does not publish an in memory change`() = runBlocking {
        val db = store(); val count = db.read { it.categories.size }
        val tmp = File(temp.root, "demo_database.tmp"); tmp.mkdir()
        assertTrue(runCatching { db.update { it.copy(categories = emptyList()) to Unit } }.isFailure)
        assertEquals(count, db.read { it.categories.size })
        assertEquals(count, store().read { it.categories.size })
    }

    @Test fun `availability and branch prices affect booking`() = runBlocking {
        val db = store(); val token = tokens()
        DemoAuthRepository(db, token).login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val management = DemoManagementRepository(db, token)
        val date = demoFormat(System.currentTimeMillis() + 3 * 86400000L).take(10)
        management.upsertBranchService("br_diplomat_1", "srv_dip_1", ManagementBranchServiceInput(77.0)).getOrThrow()
        val rule = management.createAvailability(ManagementAvailabilityInput("biz_diplomat", date, "00:00:00", "23:59:59", "br_diplomat_1", "res_dip_1", false)).getOrThrow()
        assertTrue(db.read { it.slots("biz_diplomat", "br_diplomat_1", listOf("srv_dip_1"), date, "res_dip_1").slots.isEmpty() })
        management.deleteAvailability(rule.id).getOrThrow()
        val service = management.getService("srv_dip_1").getOrThrow()
        management.updateService(service.id, ManagementServiceInput(service.businessId, service.name, durationMinutes = 60, price = 99.0)).getOrThrow()
        val slots = db.read { it.slots("biz_diplomat", "br_diplomat_1", listOf("srv_dip_1"), date, "res_dip_1") }
        assertEquals(77.0, slots.totalPrice, 0.0)
        assertEquals(60, slots.totalDurationMinutes)
        assertTrue(slots.slots.isNotEmpty())
    }
    @Test fun `manager edits preserve other businesses and staff cannot edit services`() = runBlocking {
        val db = store(); val token = tokens(); val auth = DemoAuthRepository(db, token)
        auth.login("manager@benobat.ir", "Demo1234!").getOrThrow()
        val management = DemoManagementRepository(db, token)
        assertEquals(listOf("biz_diplomat"), management.listBusinesses().getOrThrow().items.map { it.id })
        val before = db.read { it.appointments }
        val branch = management.createBranch(ManagementBranchInput("biz_diplomat", "شعبه جدید")).getOrThrow()
        assertTrue(management.listBranches().getOrThrow().items.any { it.id == branch.id })
        management.deleteBranch(branch.id).getOrThrow()
        assertEquals(before.toSet(), db.read { it.appointments.toSet() })
        assertTrue(management.getBusiness("biz_labkhand").isFailure)
        auth.login("staff@benobat.ir", "Demo1234!").getOrThrow()
        assertTrue(management.listAppointments().getOrThrow().items.all { it.resourceId == "res_dip_1" })
        assertTrue(management.createService(ManagementServiceInput("biz_diplomat", "غیرمجاز")).isFailure)
        assertTrue(DemoPlatformRepository(db, token).listUsers().isFailure)
    }

    @Test fun `createBranch with blank businessId auto-resolves to actor business`() = runBlocking {
        val db = store(); val token = tokens()
        DemoAuthRepository(db, token).login("manager@benobat.ir", "Demo1234!").getOrThrow()
        val management = DemoManagementRepository(db, token)
        val branch = management.createBranch(ManagementBranchInput(businessId = "", name = "شعبه خودکار", address = "خیابان تست")).getOrThrow()
        assertEquals("biz_diplomat", branch.businessId)
        assertTrue(management.listBranches(businessId = "biz_diplomat").getOrThrow().items.any { it.id == branch.id })
    }

    @Test fun `createService with blank businessId auto-resolves and links to active branches and is bookable`() = runBlocking {
        val db = store(); val token = tokens()
        DemoAuthRepository(db, token).login("manager@benobat.ir", "Demo1234!").getOrThrow()
        val management = DemoManagementRepository(db, token)
        val service = management.createService(ManagementServiceInput(businessId = "", name = "خدمت تست ویژه", durationMinutes = 30, price = 50.0)).getOrThrow()
        assertEquals("biz_diplomat", service.businessId)
        assertTrue(service.branchIds.isNotEmpty())
        assertTrue(service.resourceIds.isNotEmpty())

        DemoAuthRepository(db, token).login("user@benobat.ir", "Demo1234!").getOrThrow()
        val booking = DemoBookingRepository(db, token)
        val date = demoFormat(System.currentTimeMillis() + 4 * 86400000L).take(10)
        val slots = booking.getSlots("biz_diplomat", service.branchIds.first(), service.id, date, service.resourceIds.first()).getOrThrow()
        assertTrue(slots.slots.isNotEmpty())
        val slot = slots.slots.first()
        val appt = booking.createAppointment(BookingInput("biz_diplomat", service.branchIds.first(), listOf(service.id), service.resourceIds.first(), slot.startsAt, termsAccepted = true)).getOrThrow()
        assertTrue(appt.serviceNames.contains("خدمت تست ویژه"))
    }

    @Test fun `moderateReview accepts Published status and updates public business`() = runBlocking {
        val db = store(); val token = tokens()
        DemoAuthRepository(db, token).login("user@benobat.ir", "Demo1234!").getOrThrow()
        val booking = DemoBookingRepository(db, token)
        val completedAppt = db.read { s -> s.appointments.first { it.customerId == "user_customer_1" && it.status == "Completed" && s.reviews.none { r -> r.appointmentId == it.id } } }
        val review = booking.createReview(completedAppt.id, ReviewInput(5, "عالی بود")).getOrThrow()

        DemoAuthRepository(db, token).login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val management = DemoManagementRepository(db, token)
        val moderated = management.moderateReview(review.id, ManagementModerationInput(status = "Published")).getOrThrow()
        assertTrue(moderated.status in listOf("Approved", "Published"))

        val public = DemoBusinessRepository(db)
        assertTrue(public.getBusiness(completedAppt.businessId).getOrThrow().reviews.any { it.id == review.id })
    }

    @Test fun `customer upcoming appointments tab filters for Pending and Confirmed only`() = runBlocking {
        val db = store(); val token = tokens()
        DemoAuthRepository(db, token).login("user@benobat.ir", "Demo1234!").getOrThrow()
        val booking = DemoBookingRepository(db, token)
        val upcoming = booking.listAppointments(status = null).getOrThrow().items
        assertTrue(upcoming.all { it.status in listOf("Pending", "Confirmed") })
    }

    @Test fun `platform createUser stores temporary password and allows login`() = runBlocking {
        val db = store(); val token = tokens()
        DemoAuthRepository(db, token).login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val platform = DemoPlatformRepository(db, token)
        val created = platform.createUser(ManagementUserInput(displayName = "کاربر تستی جدید", email = "newuser@test.ir", phoneNumber = "09120000000", role = "Customer")).getOrThrow()

        val auth = DemoAuthRepository(db, token)
        auth.login("newuser@test.ir", created.temporaryPassword).getOrThrow()
        assertEquals(created.id, auth.cachedSession?.user?.id)
    }

    @Test fun `seed availabilities and customer reviews are retained`() {
        val s = DemoSeedData.createInitialState()
        assertTrue("Availabilities should be populated", s.availabilities.isNotEmpty())
        assertTrue("Customer reviews should be populated", s.customerReviews.isNotEmpty())
    }
}
