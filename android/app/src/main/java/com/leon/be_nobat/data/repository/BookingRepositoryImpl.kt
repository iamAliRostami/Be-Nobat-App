package com.leon.be_nobat.data.repository

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.model.BookingInput
import com.leon.be_nobat.domain.model.ReviewCreated
import com.leon.be_nobat.domain.model.ReviewInput
import com.leon.be_nobat.domain.model.SlotsResponse
import com.leon.be_nobat.domain.repository.BookingRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Implements customer booking/appointment endpoints against the .NET mobile API. */
class BookingRepositoryImpl(
    private val api: BeNobatApiClient,
) : BookingRepository {

    override suspend fun getSlots(
        businessId: String,
        branchId: String,
        serviceIds: String,
        date: String,
        resourceId: String?,
    ): Result<SlotsResponse> =
        api.getSlots(businessId, branchId, serviceIds, date, resourceId)

    override suspend fun createAppointment(input: BookingInput): Result<Appointment> = runCatching {
        val body = encodeBooking(input)
        api.createAppointment<Appointment>(body).getOrThrow()
    }

    override suspend fun listAppointments(status: String?, page: Int, pageSize: Int): Result<ApiPage<Appointment>> =
        api.listAppointments(status, page, pageSize)

    override suspend fun getAppointment(id: String): Result<Appointment> = api.getAppointment(id)

    override suspend fun cancelAppointment(id: String): Result<Appointment> =
        api.cancelAppointment(id)

    override suspend fun createReview(id: String, input: ReviewInput): Result<ReviewCreated> = runCatching {
        val body = buildJsonObject {
            put("rating", input.rating)
            put("comment", input.comment)
        }
        api.createAppointmentReview<ReviewCreated>(id, body).getOrThrow()
    }

    private fun encodeBooking(input: BookingInput): JsonObject = buildJsonObject {
        put("businessId", input.businessId)
        put("branchId", input.branchId)
        // serviceIds is a List<String> — emit as a JSON array.
        putJsonArray("serviceIds") {
            input.serviceIds.forEach { add(it) }
        }
        input.resourceId?.let { put("resourceId", it) }
        put("startsAt", input.startsAt)
        input.phoneNumber?.let { put("phoneNumber", it) }
        input.customerNote?.let { put("customerNote", it) }
        put("termsAccepted", input.termsAccepted)
        input.expectedPrice?.let { put("expectedPrice", it) }
        input.expectedDurationMinutes?.let { put("expectedDurationMinutes", it) }
    }
}
