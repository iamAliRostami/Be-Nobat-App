package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementReview
import com.leon.be_nobat.domain.usecase.AdminListReviewsUseCase
import com.leon.be_nobat.domain.usecase.AdminModerateReviewUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminReviewsUiState(
    val loading: Boolean = false,
    val items: List<ManagementReview> = emptyList(),
    val total: Int = 0,
    val statusFilter: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class AdminReviewsViewModel(
    private val list: AdminListReviewsUseCase,
    private val moderate: AdminModerateReviewUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminReviewsUiState(loading = true))
    val state: StateFlow<AdminReviewsUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        list(status = s.statusFilter)
            .onSuccess { page: ApiPage<ManagementReview> ->
                _state.value = _state.value.copy(loading = false, items = page.items, total = page.total)
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun setStatusFilter(status: String?) { _state.value = _state.value.copy(statusFilter = status); load() }

    fun publish(id: String) = viewModelScope.launch {
        moderate(id, com.leon.be_nobat.domain.model.ManagementModerationInput(status = "Published"))
            .onSuccess { updated ->
                val items = _state.value.items.map { if (it.id == id) updated else it }
                _state.value = _state.value.copy(items = items, message = "review_published")
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun reject(id: String, reply: String?) = viewModelScope.launch {
        moderate(id, com.leon.be_nobat.domain.model.ManagementModerationInput(status = "Rejected", managerReply = reply))
            .onSuccess { updated ->
                val items = _state.value.items.map { if (it.id == id) updated else it }
                _state.value = _state.value.copy(items = items, message = "review_rejected")
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
}
