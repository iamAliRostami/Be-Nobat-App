package com.leon.be_nobat.data.demo

import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.model.BookingInput
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.domain.model.ManagementReview
import com.leon.be_nobat.domain.model.ReviewCreated
import com.leon.be_nobat.domain.model.ReviewInput
import com.leon.be_nobat.domain.model.Slot
import com.leon.be_nobat.domain.model.SlotsResponse
import com.leon.be_nobat.domain.repository.BookingRepository
import java.util.UUID
import kotlin.random.Random

class DemoBookingRepository(
    private val dataStore: DemoDataStore,
    private val tokenManager: TokenManager,
) : BookingRepository {

    override suspend fun getSlots(
        businessId: String,
        branchId: String,
        serviceIds: String,
        date: String,
        resourceId: String?,
    ): Result<SlotsResponse> = runCatching {
        simulateNetworkDelay(180, 320)
        dataStore.read { it.slots(businessId, branchId, serviceIds.split(",").map(String::trim).filter(String::isNotBlank), date, resourceId) }
    }

    override suspend fun createAppointment(input: BookingInput): Result<Appointment> = runCatching {
        simulateNetworkDelay(250, 450)
        val user = requireNotNull(tokenManager.currentSession()) { "ابتدا وارد حساب شوید." }.user
        val customerName = user.displayName
        val customerPhone = input.phoneNumber ?: user.phoneNumber ?: "09120000000"
        val customerId = user.id

        val tracking = "BN-${Random.nextInt(10000, 99999)}"
        val newId = "app_${UUID.randomUUID().toString().take(8)}"

        dataStore.update { state ->
            require(input.termsAccepted)
            val slots = state.slots(
                businessId = input.businessId,
                branchId = input.branchId,
                ids = input.serviceIds,
                date = input.startsAt.take(10),
                resourceId = input.resourceId,
                includePastSlots = true,
            )
            val primarySlot = slots.slots.firstOrNull { it.startsAt == input.startsAt }
                ?: slots.slots.firstOrNull { it.startsAt.take(16) == input.startsAt.take(16) }
            val slot: Slot = primarySlot ?: error("این زمان دیگر قابل رزرو نیست.")
            val biz = state.businesses.firstOrNull { it.id == input.businessId }
            val branch = state.branches.firstOrNull { it.id == input.branchId }
            val services = state.services.filter { input.serviceIds.contains(it.id) }
            val res = state.resources.firstOrNull { it.id == slot.resourceId }
                ?: state.resources.firstOrNull { it.branchId == input.branchId }

            val status = if (biz?.requiresApproval == true) "Pending" else "Confirmed"
            val price = slots.totalPrice
            require(input.expectedPrice == null || kotlin.math.abs(input.expectedPrice - price) < 1.0) { "قیمت خدمات تغییر کرده است." }

            require(input.expectedDurationMinutes == null || input.expectedDurationMinutes == slots.totalDurationMinutes)
            val endsAt = slot.endsAt

            val managementAppt = ManagementAppointment(
                id = newId,
                branchId = input.branchId,
                businessId = input.businessId,
                serviceId = input.serviceIds.firstOrNull().orEmpty(),
                serviceIds = input.serviceIds,
                serviceNames = services.map { it.name },
                resourceId = res?.id,
                resourceName = res?.name,
                customerId = customerId,
                customerName = customerName,
                customerPhoneNumber = customerPhone,
                customerEmail = user.email,
                startsAt = input.startsAt,
                endsAt = endsAt,
                status = status,
                finalPrice = price,
                currency = "IRR",
                customerNote = input.customerNote.orEmpty(),
                branchName = branch?.name.orEmpty(),
                businessName = biz?.name.orEmpty(),
                serviceName = services.firstOrNull()?.name.orEmpty(),
                timeZoneId = "Asia/Tehran",
                trackingCode = tracking,
            )

            val updatedAppointments = listOf(managementAppt) + state.appointments
            state.copy(appointments = updatedAppointments) to managementAppt.toDomainAppointment(state)
        }
    }

    override suspend fun listAppointments(
        status: String?,
        page: Int,
        pageSize: Int,
    ): Result<ApiPage<Appointment>> = runCatching {
        simulateNetworkDelay(150, 280)
        val currentId = requireNotNull(tokenManager.currentSession()).user.id
        dataStore.read { state ->

            val filtered = state.appointments
                .filter { appt ->
                    // Each local account sees its own appointments.
                    appt.customerId == currentId
                }
                .filter { appt ->
                    if (status.isNullOrBlank()) {
                        // "Upcoming" tab: future appointments or pending/confirmed
                        appt.status in listOf("Pending", "Confirmed")
                    } else {
                        appt.status.equals(status, ignoreCase = true)
                    }
                }

            val result = filtered.demoPage(page, pageSize)
            ApiPage(items = result.items.map { it.toDomainAppointment(state, state.reviews.firstOrNull { r -> r.appointmentId == it.id }?.id) }, page = page, pageSize = pageSize, total = result.total)
        }
    }

    override suspend fun getAppointment(id: String): Result<Appointment> = runCatching {
        simulateNetworkDelay(100, 200)
        val currentId = requireNotNull(tokenManager.currentSession()).user.id
        dataStore.read { state ->
            val appt = state.appointments.firstOrNull { it.id == id }
                ?: error("نوبت با شناسه $id یافت نشد.")
            require(appt.customerId == currentId)
            appt.toDomainAppointment(state, state.reviews.firstOrNull { it.appointmentId == id }?.id)
        }
    }

    override suspend fun cancelAppointment(id: String): Result<Appointment> = runCatching {
        simulateNetworkDelay(200, 350)
        val currentId = requireNotNull(tokenManager.currentSession()).user.id
        dataStore.update { state ->
            require(state.appointments.any { it.id == id && it.customerId == currentId && it.status in listOf("Pending", "Confirmed") })
            val target = state.appointments.first { it.id == id && it.customerId == currentId }
            require(com.leon.be_nobat.domain.CancellationPolicy.canCancel(target.status, target.startsAt, target.noticeMinutes(state))) {
                "مهلت لغو این نوبت گذشته است؛ با شعبه تماس بگیرید."
            }
            var managementAppointment: ManagementAppointment? = null
            val list = state.appointments.map { appt ->
                if (appt.id == id) {
                    val mod = appt.copy(status = "Cancelled")
                    managementAppointment = mod
                    mod
                } else appt
            }
            if (managementAppointment == null) error("نوبت با شناسه $id یافت نشد.")
            state.copy(appointments = list) to managementAppointment.toDomainAppointment(state)
        }
    }

    override suspend fun createReview(id: String, input: ReviewInput): Result<ReviewCreated> = runCatching {
        simulateNetworkDelay(220, 380)
        val user = requireNotNull(tokenManager.currentSession()) { "ابتدا وارد حساب شوید." }.user
        val nowIso = demoNow()
        val newRevId = "rev_${UUID.randomUUID().toString().take(8)}"

        dataStore.update { state ->
            val appt = state.appointments.firstOrNull { it.id == id }
                ?: error("نوبت با شناسه $id یافت نشد.")

            require(appt.customerId == user.id && appt.status == "Completed")
            require(input.rating in 1..5)
            require(state.reviews.none { it.appointmentId == id }) { "نظر این نوبت قبلاً ثبت شده است." }
            val newReview = ManagementReview(
                id = newRevId,
                businessId = appt.businessId,
                branchId = appt.branchId,
                customerId = appt.customerId,
                customerName = appt.customerName.ifBlank { user.displayName },
                appointmentId = id,
                rating = input.rating,
                comment = input.comment.orEmpty(),
                status = "Pending",
                active = true,
                createdAt = nowIso,
            )

            val created = ReviewCreated(
                id = newRevId,
                rating = input.rating,
                comment = input.comment.orEmpty(),
                status = "Pending",
                createdAt = nowIso,
            )

            state.copy(
                reviews = listOf(newReview) + state.reviews,
            ) to created
        }
    }

    private fun ManagementAppointment.noticeMinutes(state: DemoDatabaseState): Int {
        val branch = state.branches.firstOrNull { it.id == branchId }
        val business = state.businesses.firstOrNull { it.id == businessId }
        val services = state.services.filter { it.id in serviceIds.ifEmpty { listOf(serviceId) } || (serviceIds.isEmpty() && it.businessId == businessId && it.name in serviceNames) }
        return services.maxOfOrNull { service -> com.leon.be_nobat.domain.CancellationPolicy.resolve(
            business?.cancellationNoticeMinutes ?: 120, branch?.cancellationNoticeMinutes, service.cancellationNoticeMinutes,
            state.branchServices.firstOrNull { it.branchId == branchId && it.serviceId == service.id }?.cancellationNoticeMinutes)
        } ?: com.leon.be_nobat.domain.CancellationPolicy.resolve(business?.cancellationNoticeMinutes ?: 120, branch?.cancellationNoticeMinutes)
    }

    private fun ManagementAppointment.toDomainAppointment(state: DemoDatabaseState, existingReviewId: String? = null): Appointment {
        val notice = noticeMinutes(state)
        val canCancel = com.leon.be_nobat.domain.CancellationPolicy.canCancel(status, startsAt, notice)
        val canReview = status == "Completed" && existingReviewId == null
        return Appointment(
            id = id,
            businessId = businessId,
            businessName = businessName,
            branchId = branchId,
            branchName = branchName,
            timeZoneId = timeZoneId,
            serviceNames = serviceNames.ifEmpty { listOf(serviceName) },
            resourceId = resourceId,
            resourceName = resourceName,
            startsAt = startsAt,
            endsAt = endsAt,
            status = status,
            finalPrice = finalPrice,
            currency = currency,
            customerNote = customerNote,
            trackingCode = trackingCode,
            canCancel = canCancel,
            cancellationNoticeMinutes = notice,
            cancellationDeadline = com.leon.be_nobat.domain.CancellationPolicy.deadline(startsAt, notice),
            canReview = canReview,
            reviewId = existingReviewId,
        )
    }
}