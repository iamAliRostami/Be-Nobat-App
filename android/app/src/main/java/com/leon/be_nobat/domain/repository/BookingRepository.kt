package com.leon.be_nobat.domain.repository

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.model.BookingInput
import com.leon.be_nobat.domain.model.ReviewCreated
import com.leon.be_nobat.domain.model.ReviewInput
import com.leon.be_nobat.domain.model.SlotsResponse

/** Customer booking + appointment endpoints (`/api/v1/slots`, `/appointments`, `/favorites`). */
interface BookingRepository {
    suspend fun getSlots(
        businessId: String,
        branchId: String,
        serviceIds: String,
        date: String,
        resourceId: String? = null,
    ): Result<SlotsResponse>

    suspend fun createAppointment(input: BookingInput): Result<Appointment>
    suspend fun listAppointments(status: String? = null, page: Int = 1, pageSize: Int = 20): Result<ApiPage<Appointment>>
    suspend fun getAppointment(id: String): Result<Appointment>
    suspend fun cancelAppointment(id: String): Result<Appointment>
    suspend fun createReview(id: String, input: ReviewInput): Result<ReviewCreated>
}
