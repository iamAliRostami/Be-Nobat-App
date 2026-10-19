package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementAvailability
import com.leon.be_nobat.domain.usecase.AdminCreateAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminListAvailabilityUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminAvailabilityUiState(
    val loading: Boolean = false,
    val items: List<ManagementAvailability> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
    val message: String? = null,
)

class AdminAvailabilityViewModel(
    private val list: AdminListAvailabilityUseCase,
    private val create: AdminCreateAvailabilityUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminAvailabilityUiState(loading = true))
    val state: StateFlow<AdminAvailabilityUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        list()
            .onSuccess { page: ApiPage<ManagementAvailability> ->
                _state.value = _state.value.copy(loading = false, items = page.items, total = page.total)
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }
}
