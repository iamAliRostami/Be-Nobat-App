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
    @Test fun `business creates editable central branch and persists weekly hours`() = runBlocking {
        val db = store(); val token = tokens()
        DemoAuthRepository(db, token).login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val platform = DemoPlatformRepository(db, token)
        val management = DemoManagementRepository(db, token)
        val business = platform.createBusiness(ManagementBusinessInput(name = "برنامه هفتگی")).getOrThrow()
        val central = management.listBranches(businessId = business.id).getOrThrow().items.single()
        assertEquals("شعبه مرکزی", central.name)
        assertEquals(WeeklyHours.defaults(), central.weeklyHours)
        val custom = listOf(WeeklyTimeRange(6, "08:30:00", "12:00:00"), WeeklyTimeRange(6, "14:00:00", "20:00:00"))
        management.createBranch(ManagementBranchInput(business.id, "شعبه دوم", weeklyHours = emptyList())).getOrThrow()
        platform.updateBusiness(business.id, ManagementBusinessInput(name = business.name, firstBranchName = "مرکزی جدید", firstBranchAddress = "خیابان بهار", firstBranchWeeklyHours = custom)).getOrThrow()
        val saved = store().read { it.branches.first { b -> b.id == central.id } }
        assertEquals("مرکزی جدید", saved.name)
        assertEquals(custom, saved.weeklyHours)
        assertEquals(central.id, platform.listBusinesses(q = business.name).getOrThrow().items.single().firstBranchId)
        assertEquals(emptyList<WeeklyTimeRange>(), management.listBranches(businessId = business.id).getOrThrow().items.first { it.id != central.id }.weeklyHours)
    }

    @Test fun `weekly booking respects days split intervals closed weeks and validation`() = runBlocking {
        val db = store()
        val custom = listOf(WeeklyTimeRange(6, "08:00:00", "12:00:00"), WeeklyTimeRange(6, "14:00:00", "20:00:00"))
        db.update { s -> s.copy(branches = s.branches.map { if (it.id == "br_diplomat_1") it.copy(weeklyHours = custom) else it }, appointments = emptyList(), availabilities = emptyList()) to Unit }
        val saturday = db.read { it.slots("biz_diplomat", "br_diplomat_1", listOf("srv_dip_1"), "2030-01-05", "res_dip_1", includePastSlots = true) }
        assertTrue(saturday.slots.isNotEmpty())
        assertTrue(saturday.slots.all { it.endsAt.substring(11) <= "12:00:00" || it.startsAt.substring(11) >= "14:00:00" })
        assertTrue(db.read { it.slots("biz_diplomat", "br_diplomat_1", listOf("srv_dip_1"), "2030-01-04", "res_dip_1", includePastSlots = true).slots.isEmpty() })
        assertNotNull(WeeklyHours.validate(listOf(WeeklyTimeRange(6, "12:00:00", "08:00:00"))))
        assertNotNull(WeeklyHours.validate(custom + WeeklyTimeRange(6, "11:00:00", "15:00:00")))
        db.update { s -> s.copy(branches = s.branches.map { if (it.id == "br_diplomat_1") it.copy(weeklyHours = emptyList()) else it }) to Unit }
        assertTrue(db.read { it.slots("biz_diplomat", "br_diplomat_1", listOf("srv_dip_1"), "2030-01-05", "res_dip_1", includePastSlots = true).slots.isEmpty() })
    }

    @Test fun `demo datastore recovers gracefully from corrupted json`(): Unit = runBlocking {
        val db = store()
        // Initialize file
        db.read { it.businesses.size }
        val dbFile = File(temp.root, "demo_database.json")
        assertTrue(dbFile.exists())
        // Corrupt the json file
        dbFile.writeText("{ corrupted_json: true, invalid !!!")
        // Create new store instance pointing to same directory
        val recoveredDb = store()
        val count = recoveredDb.read { it.businesses.size }
        assertTrue("Should cleanly recover and populate seed data", count > 0)
    }

    @Test fun `availability CRUD is durable and persists across sessions`(): Unit = runBlocking {
        val db = store(); val token = tokens()
        DemoAuthRepository(db, token).login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val mgmt = DemoManagementRepository(db, token)
        val input = ManagementAvailabilityInput(
            businessId = "biz_diplomat",
            branchId = "br_diplomat_1",
            resourceId = "res_dip_1",
            effectiveDate = "2030-05-01",
            startsAt = "08:00:00",
            endsAt = "18:00:00",
            isAvailable = true
        )
        val created = mgmt.createAvailability(input).getOrThrow()
        assertEquals("2030-05-01", created.effectiveDate)

        // Read in a new store instance (reopened from disk)
        val reopened = store()
        val reopenedMgmt = DemoManagementRepository(reopened, token)
        val listed = reopenedMgmt.listAvailability(branchId = "br_diplomat_1", from = "2030-05-01", to = "2030-05-01").getOrThrow().items
        assertTrue(listed.any { it.id == created.id })

        // Update availability
        val updated = reopenedMgmt.updateAvailability(created.id, input.copy(endsAt = "20:00:00")).getOrThrow()
        assertEquals("20:00:00", updated.endsAt)

        // Delete availability
        reopenedMgmt.deleteAvailability(created.id).getOrThrow()
        val afterDelete = store().read { s -> s.availabilities.none { it.id == created.id } }
        assertTrue("Availability should be deleted from durable storage", afterDelete)
    }

    @Test fun `branch and business inactive filter behaves properly`(): Unit = runBlocking {
        val db = store(); val token = tokens()
        DemoAuthRepository(db, token).login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val mgmt = DemoManagementRepository(db, token)
        val branches = mgmt.listBranches(includeInactive = true).getOrThrow().items
        val targetBranch = branches.first { it.active }

        // Deactivate branch
        mgmt.setBranchActive(targetBranch.id, false).getOrThrow()

        // includeInactive = true includes it
        val withInactive = mgmt.listBranches(includeInactive = true).getOrThrow().items
        assertTrue(withInactive.any { it.id == targetBranch.id && !it.active })

        // includeInactive = false or default hides it
        val withoutInactive = mgmt.listBranches(includeInactive = false).getOrThrow().items
        assertFalse(withoutInactive.any { it.id == targetBranch.id })

        // Re-activate
        mgmt.setBranchActive(targetBranch.id, true).getOrThrow()
    }

    @Test fun `business request submission and platform approval promotes user to owner with business and branch`(): Unit = runBlocking {
        val db = store(); val token = tokens()
        val auth = DemoAuthRepository(db, token)

        // 1. Normal user logs in
        val userSession = auth.login("user@benobat.ir", "Demo1234!").getOrThrow()
        val user = userSession.user
        assertFalse(user.isStaff)

        // 2. Submit business request
        val input = BusinessRequestInput(
            businessName = "کلینیک تخصصی نوین",
            category = "پزشکی و سلامت",
            city = "اصفهان",
            description = "مرکز تخصصی پوست و مو",
            contactPhone = "09130000000",
            firstBranchName = "شعبه اصلی اصفهان",
            firstBranchAddress = "خیابان چهارباغ بالا",
        )
        val submitted = auth.submitBusinessRequest(input).getOrThrow()
        assertEquals("Pending", submitted.status)
        assertEquals("کلینیک تخصصی نوین", submitted.businessName)

        val myReq = auth.getMyBusinessRequest().getOrThrow()
        assertNotNull(myReq)
        assertEquals("Pending", myReq?.status)

        // 3. Admin logs in and reviews requests
        val adminAuth = DemoAuthRepository(db, token)
        adminAuth.login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val platform = DemoPlatformRepository(db, token)

        val pendingRequests = platform.listBusinessRequests(status = "Pending").getOrThrow().items
        val targetReq = pendingRequests.firstOrNull { it.id == submitted.id }
        assertNotNull("Submitted request should be listed under Pending", targetReq)

        // 4. Admin approves request
        val approved = platform.approveBusinessRequest(submitted.id).getOrThrow()
        assertEquals("Approved", approved.status)
        assertNotNull(approved.approvedBusinessId)

        // 5. Verify database state
        val updatedUser = db.read { s -> s.users.first { it.id == user.id } }
        assertTrue("User should now have Owner role", updatedUser.roles.contains("Owner"))

        val createdBiz = db.read { s -> s.businesses.first { it.id == approved.approvedBusinessId } }
        assertEquals("کلینیک تخصصی نوین", createdBiz.name)

        val createdBranch = db.read { s -> s.branches.first { it.businessId == createdBiz.id } }
        assertEquals("شعبه اصلی اصفهان", createdBranch.name)
        assertTrue("First branch from business approval should be approved", createdBranch.isApproved)

        val membership = db.read { s -> s.memberships.first { it.branchId == createdBranch.id && it.userId == user.id } }
        assertEquals("Owner", membership.role)
    }

    @Test fun `branch created by manager requires platform admin approval`(): Unit = runBlocking {
        val db = store(); val token = tokens()
        val auth = DemoAuthRepository(db, token)

        // Manager logs in
        auth.login("manager@benobat.ir", "Demo1234!").getOrThrow()
        val mgmt = DemoManagementRepository(db, token)
        val bizId = "biz_diplomat"

        // Manager creates new branch
        val branchInput = ManagementBranchInput(
            businessId = bizId,
            name = "شعبه دوم دیپلمات",
            address = "ونک، خیابان ملاصدرا",
            timeZoneId = "Asia/Tehran",
            openHour = 9,
            closeHour = 21,
        )
        val createdBranch = mgmt.createBranch(branchInput).getOrThrow()
        assertFalse("Branch created by non-platform admin should have isApproved = false", createdBranch.isApproved)

        // Public discovery should hide unapproved branch
        val publicRepo = DemoBusinessRepository(db)
        val publicBranchesBefore = publicRepo.listBranches(bizId).getOrThrow().items
        assertFalse("Unapproved branch should not be visible to public", publicBranchesBefore.any { it.id == createdBranch.id })

        // Platform admin logs in and approves branch
        auth.login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val platform = DemoPlatformRepository(db, token)
        val approvedBranch = platform.approveBranch(createdBranch.id).getOrThrow()
        assertTrue("Branch should now be approved", approvedBranch.isApproved)

        // Public discovery should now show the branch
        val publicBranchesAfter = publicRepo.listBranches(bizId).getOrThrow().items
        assertTrue("Approved branch should now be visible to public", publicBranchesAfter.any { it.id == createdBranch.id })
    }
    @Test fun `Cancellation defaults block late requests and four independent settings persist`() = runBlocking {
        val db = store(); val token = tokens(); val auth = DemoAuthRepository(db, token)
        auth.login("user@benobat.ir", "Demo1234!").getOrThrow()
        val customerId = requireNotNull(token.currentSession()).user.id
        val target = db.read { state -> state.appointments.first { a -> a.customerId == customerId && state.services.any { it.id == a.serviceId && it.active } } }
        val time = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        db.update { state -> state.copy(appointments = state.appointments.map {
            if (it.id == target.id) it.copy(status = "Confirmed", startsAt = time.format(java.util.Date(System.currentTimeMillis() + 60 * 60_000L)),
                serviceIds = listOf(it.serviceId), serviceNames = emptyList()) else it
        }) to Unit }
        val booking = DemoBookingRepository(db, token)
        assertFalse(booking.getAppointment(target.id).getOrThrow().canCancel)
        assertTrue(booking.cancelAppointment(target.id).isFailure)
        auth.login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val management = DemoManagementRepository(db, token)
        val business = management.getBusiness(target.businessId).getOrThrow()
        DemoPlatformRepository(db, token).updateBusiness(business.id, ManagementBusinessInput(name = business.name, slug = business.slug,
            cancellationNoticeMinutes = 180)).getOrThrow()
        val branch = management.getBranch(target.branchId).getOrThrow()
        management.updateBranch(branch.id, ManagementBranchInput(businessId = branch.businessId, name = branch.name, cancellationNoticeMinutes = 90)).getOrThrow()
        val service = management.getService(target.serviceId).getOrThrow()
        management.updateService(service.id, ManagementServiceInput(businessId = service.businessId, name = service.name,
            price = service.price, cancellationNoticeMinutes = 60)).getOrThrow()
        management.upsertBranchService(branch.id, service.id, ManagementBranchServiceInput(cancellationNoticeMinutes = 30)).getOrThrow()
        assertTrue(management.upsertBranchService(branch.id, service.id, ManagementBranchServiceInput(cancellationNoticeMinutes = -1)).isFailure)
        auth.login("user@benobat.ir", "Demo1234!").getOrThrow()
        val reopened = store()
        val reopenedBooking = DemoBookingRepository(reopened, token)
        val allowed = reopenedBooking.getAppointment(target.id).getOrThrow()
        assertTrue(allowed.canCancel); assertEquals(30, allowed.cancellationNoticeMinutes)
        assertNotNull(allowed.cancellationDeadline)
        assertEquals("Cancelled", reopenedBooking.cancelAppointment(target.id).getOrThrow().status)
        assertTrue(reopenedBooking.cancelAppointment(target.id).isFailure)
        val persisted = store().read { it }
        assertEquals(180, persisted.businesses.first { it.id == business.id }.cancellationNoticeMinutes)
        assertEquals(90, persisted.branches.first { it.id == branch.id }.cancellationNoticeMinutes)
        assertEquals(60, persisted.services.first { it.id == service.id }.cancellationNoticeMinutes)
        assertEquals(30, persisted.branchServices.first { it.branchId == branch.id && it.serviceId == service.id }.cancellationNoticeMinutes)
    }

    @Test fun `Cancellation multi service uses earliest deadline across selected service ids`() = runBlocking {
        val db = store(); val token = tokens(); val auth = DemoAuthRepository(db, token)
        auth.login("user@benobat.ir", "Demo1234!").getOrThrow()
        val customerId = requireNotNull(token.currentSession()).user.id
        val chosen = db.read { state -> state.appointments.first { a -> a.customerId == customerId && state.services.count { it.businessId == a.businessId } > 1 } }
        val selected = db.read { state -> state.services.filter { it.businessId == chosen.businessId }.take(2) }
        val start = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(System.currentTimeMillis() + 200 * 60_000L))
        db.update { state -> state.copy(
            appointments = state.appointments.map { if (it.id == chosen.id) it.copy(serviceId = selected.first().id, serviceIds = selected.map { x -> x.id }, status = "Confirmed", startsAt = start) else it },
            services = state.services.map { if (it.id == selected.first().id) it.copy(cancellationNoticeMinutes = 0) else it },
            branchServices = state.branchServices.filterNot { it.branchId == chosen.branchId && it.serviceId in selected.map { s -> s.id } } +
                ManagementBranchService(id = "cancel-policy-test", branchId = chosen.branchId, serviceId = selected.last().id, cancellationNoticeMinutes = 240)
        ) to Unit }
        val booking = DemoBookingRepository(db, token)
        assertEquals(240, booking.getAppointment(chosen.id).getOrThrow().cancellationNoticeMinutes)
        assertFalse(booking.getAppointment(chosen.id).getOrThrow().canCancel)
        assertTrue(booking.cancelAppointment(chosen.id).isFailure)
    }


    @Test fun `overtime availability rule allows customer to book outside standard branch hours`() = runBlocking {
        val db = store(); val token = tokens(); val auth = DemoAuthRepository(db, token)
        auth.login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val management = DemoManagementRepository(db, token)
        val date = demoFormat(System.currentTimeMillis() + 5 * 86400000L).take(10)
        management.createAvailability(ManagementAvailabilityInput(
            businessId = "biz_diplomat",
            branchId = "br_diplomat_1",
            resourceId = "res_dip_1",
            effectiveDate = date,
            startsAt = "21:00:00",
            endsAt = "23:00:00",
            isAvailable = true,
        )).getOrThrow()

        auth.login("user@benobat.ir", "Demo1234!").getOrThrow()
        val booking = DemoBookingRepository(db, token)
        val slots = booking.getSlots("biz_diplomat", "br_diplomat_1", "srv_dip_1", date, "res_dip_1").getOrThrow()
        val overtimeSlots = slots.slots.filter { it.startsAt >= "${date}T21:00:00" && it.endsAt <= "${date}T23:00:00" }
        assertTrue("Overtime slots should be generated and available", overtimeSlots.isNotEmpty())

        val targetSlot = overtimeSlots.first()
        val appt = booking.createAppointment(BookingInput(
            businessId = "biz_diplomat",
            branchId = "br_diplomat_1",
            serviceIds = listOf("srv_dip_1"),
            resourceId = "res_dip_1",
            startsAt = targetSlot.startsAt,
            termsAccepted = true,
        )).getOrThrow()

        assertEquals(targetSlot.startsAt, appt.startsAt)
    }

    @Test fun `moderate review preserves manager reply and reject updates status`() = runBlocking {
        val db = store(); val token = tokens(); val auth = DemoAuthRepository(db, token)
        auth.login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val management = DemoManagementRepository(db, token)

        val targetReview = db.read { s -> s.reviews.first { it.status == "Pending" } }
        val replied = management.replyReview(targetReview.id, ManagementReplyInput("با تشکر از نظر شما")).getOrThrow()
        assertEquals("با تشکر از نظر شما", replied.managerReply)

        val approved = management.moderateReview(targetReview.id, ManagementModerationInput(status = "Approved")).getOrThrow()
        assertEquals("با تشکر از نظر شما", approved.managerReply)
        assertEquals("Approved", approved.status)

        val rejected = management.moderateReview(targetReview.id, ManagementModerationInput(status = "Rejected")).getOrThrow()
        assertEquals("Rejected", rejected.status)
        assertEquals("با تشکر از نظر شما", rejected.managerReply)
    }

    @Test fun `custom availability preserves standard daytime slots and blocks specific range`() = runBlocking {
        val db = store(); val token = tokens(); val auth = DemoAuthRepository(db, token)
        auth.login("admin@benobat.ir", "Demo1234!").getOrThrow()
        val management = DemoManagementRepository(db, token)
        val date = demoFormat(System.currentTimeMillis() + 6 * 86400000L).take(10)

        // 1. Add overtime range from 20:00 to 22:00
        management.createAvailability(ManagementAvailabilityInput(
            businessId = "biz_diplomat",
            branchId = "br_diplomat_1",
            resourceId = "res_dip_1",
            effectiveDate = date,
            startsAt = "20:00:00",
            endsAt = "22:00:00",
            isAvailable = true,
        )).getOrThrow()

        // 2. Block 12:00 to 14:00 (e.g. lunch/break)
        management.createAvailability(ManagementAvailabilityInput(
            businessId = "biz_diplomat",
            branchId = "br_diplomat_1",
            resourceId = "res_dip_1",
            effectiveDate = date,
            startsAt = "12:00:00",
            endsAt = "14:00:00",
            isAvailable = false,
        )).getOrThrow()

        val booking = DemoBookingRepository(db, token)
        val slots = booking.getSlots("biz_diplomat", "br_diplomat_1", "srv_dip_1", date, "res_dip_1").getOrThrow().slots

        val morningSlots = slots.filter { it.startsAt >= "${date}T09:00:00" && it.endsAt <= "${date}T12:00:00" }
        assertTrue("Morning slots should be available", morningSlots.isNotEmpty())

        val blockedSlots = slots.filter { it.startsAt >= "${date}T12:00:00" && it.endsAt <= "${date}T14:00:00" }
        assertTrue("Blocked slots (12:00-14:00) must not be present", blockedSlots.isEmpty())

        val afternoonSlots = slots.filter { it.startsAt >= "${date}T14:00:00" && it.endsAt <= "${date}T18:00:00" }
        assertTrue("Afternoon slots should be available", afternoonSlots.isNotEmpty())

        val overtimeSlots = slots.filter { it.startsAt >= "${date}T20:00:00" && it.endsAt <= "${date}T22:00:00" }
        assertTrue("Overtime slots (20:00-22:00) should be available", overtimeSlots.isNotEmpty())
    }
}