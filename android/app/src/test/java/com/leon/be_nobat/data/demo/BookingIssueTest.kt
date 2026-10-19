package com.leon.be_nobat.data.demo

import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.domain.interfaces.ICryptoManager
import com.leon.be_nobat.domain.model.BookingInput
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookingIssueTest {
    @get:Rule val temp = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testBooking() = runBlocking {
        val db = DemoDataStore(temp.root, json)
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
            java.io.File(temp.root, "test.preferences_pb")
        }
        val token = TokenManager(dataStore, json, object : ICryptoManager {
            override fun encrypt(threshold: String) = threshold
            override fun decrypt(encryptedData: String) = encryptedData
        })

        // Login as customer
        DemoAuthRepository(db, token).login("user@benobat.ir", "Demo1234!").getOrThrow()

        val repo = DemoBookingRepository(db, token)
        val state = db.read { it }
        println("All businesses: ${state.businesses.map { "${it.id}: ${it.name}" }}")
        println("All branches: ${state.branches.map { "${it.id}: ${it.name} (biz: ${it.businessId})" }}")

        // Pick first business and branch
        val biz = state.businesses.first()
        val branch = state.branches.first { it.businessId == biz.id }
        val services = state.services.filter { it.businessId == biz.id && branch.id in it.branchIds }
        println("Biz: ${biz.name}, Branch: ${branch.name}, Services: ${services.map { it.name }}")

        val today = com.leon.be_nobat.helpers.JalaliDateHelper.getTodayIso()
        println("Today ISO: $today")

        val slotsToday = repo.getSlots(biz.id, branch.id, services.first().id, today, null).getOrThrow()
        println("Today slots count: ${slotsToday.slots.size}")

        println("All appointments: ${state.appointments.map { "${it.id}: ${it.startsAt}-${it.endsAt} (res: ${it.resourceId}, status: ${it.status})" }}")
        val allSlotsToday = state.slots(biz.id, branch.id, listOf(services.first().id), today, null, includePastSlots = true)
        println("All slots today (includePast=true): ${allSlotsToday.slots.map { it.startsAt }}")

        // Test booking 11:00 AM slot today (which was past System.currentTimeMillis() earlier)
        val slot11Today = BookingInput(
            businessId = biz.id,
            branchId = branch.id,
            serviceIds = listOf(services.first().id),
            resourceId = null,
            startsAt = "${today}T11:00:00",
            phoneNumber = "09134588220",
            customerNote = null,
            termsAccepted = true,
            expectedPrice = null,
            expectedDurationMinutes = null,
        )
        val resToday11 = repo.createAppointment(slot11Today)
        println("Today 11:00 Booking result: $resToday11")
        org.junit.Assert.assertTrue(resToday11.isSuccess)
    }
}
