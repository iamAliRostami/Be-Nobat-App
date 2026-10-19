package com.leon.be_nobat.domain.usecase

import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.model.BookingInput
import com.leon.be_nobat.domain.model.ReviewCreated
import com.leon.be_nobat.domain.model.ReviewInput
import com.leon.be_nobat.domain.model.SlotsResponse
import com.leon.be_nobat.domain.repository.BookingRepository

class GetSlotsUseCase(private val repo: BookingRepository) {
    suspend operator fun invoke(
        businessId: String, branchId: String, serviceIds: String, date: String, resourceId: String? = null,
    ): Result<SlotsResponse> = repo.getSlots(businessId, branchId, serviceIds, date, resourceId)
}

class CreateAppointmentUseCase(private val repo: BookingRepository) {
    suspend operator fun invoke(input: BookingInput): Result<Appointment> = repo.createAppointment(input)
}

class ListAppointmentsUseCase(private val repo: BookingRepository) {
    suspend operator fun invoke(status: String? = null, page: Int = 1, pageSize: Int = 20): Result<ApiPage<Appointment>> =
        repo.listAppointments(status, page, pageSize)
}

class GetAppointmentUseCase(private val repo: BookingRepository) {
    suspend operator fun invoke(id: String): Result<Appointment> = repo.getAppointment(id)
}

class CancelAppointmentUseCase(private val repo: BookingRepository) {
    suspend operator fun invoke(id: String): Result<Appointment> = repo.cancelAppointment(id)
}

class CreateReviewUseCase(private val repo: BookingRepository) {
    suspend operator fun invoke(id: String, rating: Int, comment: String?): Result<ReviewCreated> =
        repo.createReview(id, ReviewInput(rating, comment))
}
