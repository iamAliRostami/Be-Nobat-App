package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.usecase.CancelAppointmentUseCase
import com.leon.be_nobat.domain.usecase.CreateReviewUseCase
import com.leon.be_nobat.domain.usecase.ListAppointmentsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppointmentsUiState(
    val loading: Boolean = false,
    val items: List<Appointment> = emptyList(),
    val total: Int = 0,
    val tab: AppointmentTab = AppointmentTab.All,
    val error: String? = null,
    val reviewForAppointmentId: String? = null,
    val reviewRating: Int = 5,
    val reviewComment: String = "",
    val reviewSubmitting: Boolean = false,
    val reviewSuccess: Boolean = false,
)

enum class AppointmentTab(val apiStatus: String?, @androidx.annotation.StringRes val labelRes: Int) {
    All(null, com.leon.be_nobat.R.string.tab_all_appointments),
    Pending("Pending", com.leon.be_nobat.R.string.tab_pending),
    Confirmed("Confirmed", com.leon.be_nobat.R.string.tab_confirmed),
    Completed("Completed", com.leon.be_nobat.R.string.tab_completed),
    Cancelled("Cancelled", com.leon.be_nobat.R.string.tab_cancelled),
}

class AppointmentsViewModel(
    private val listAppointments: ListAppointmentsUseCase,
    private val cancelAppointment: CancelAppointmentUseCase,
    private val createReviewUseCase: CreateReviewUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AppointmentsUiState(loading = true))
    val state: StateFlow<AppointmentsUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        listAppointments(s.tab.apiStatus)
            .onSuccess { page: ApiPage<Appointment> ->
                _state.value = _state.value.copy(loading = false, items = page.items, total = page.total, error = null)
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun selectTab(tab: AppointmentTab) {
        _state.value = _state.value.copy(tab = tab)
        load()
    }

    fun cancel(id: String) = viewModelScope.launch {
        cancelAppointment(id).onSuccess { updated ->
            val items = _state.value.items.map { if (it.id == id) updated else it }
            _state.value = _state.value.copy(items = items)
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun openReview(id: String) {
        _state.value = _state.value.copy(
            reviewForAppointmentId = id,
            reviewRating = 5,
            reviewComment = "",
            reviewSubmitting = false,
            reviewSuccess = false
        )
    }

    fun closeReview() {
        _state.value = _state.value.copy(reviewForAppointmentId = null, reviewSubmitting = false)
    }

    fun setRating(rating: Int) {
        _state.value = _state.value.copy(reviewRating = rating.coerceIn(1, 5))
    }

    fun setComment(text: String) {
        _state.value = _state.value.copy(reviewComment = text)
    }

    fun consumeReviewSuccess() {
        _state.value = _state.value.copy(reviewSuccess = false)
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }

    fun submitReview(rating: Int? = null, comment: String? = null) = viewModelScope.launch {
        val s = _state.value
        val id = s.reviewForAppointmentId ?: return@launch
        val finalRating = rating?.coerceIn(1, 5) ?: s.reviewRating
        val finalComment = comment ?: s.reviewComment
        _state.value = s.copy(
            reviewSubmitting = true,
            reviewRating = finalRating,
            reviewComment = finalComment
        )
        createReviewUseCase(id, finalRating, finalComment.ifBlank { null })
            .onSuccess {
                _state.value = _state.value.copy(
                    reviewSubmitting = false,
                    reviewForAppointmentId = null,
                    reviewSuccess = true
                )
                load()
            }
            .onFailure { e ->
                _state.value = _state.value.copy(reviewSubmitting = false, error = e.message)
            }
    }
}
