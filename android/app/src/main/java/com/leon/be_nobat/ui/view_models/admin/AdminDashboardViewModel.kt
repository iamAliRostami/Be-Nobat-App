package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.domain.model.ManagementContext
import com.leon.be_nobat.domain.model.ManagementDashboard
import com.leon.be_nobat.domain.usecase.AdminContextUseCase
import com.leon.be_nobat.domain.usecase.AdminDashboardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val loading: Boolean = false,
    val dashboard: ManagementDashboard? = null,
    val context: ManagementContext? = null,
    val error: String? = null,
)

class AdminDashboardViewModel(
    private val dashboardUseCase: AdminDashboardUseCase,
    private val contextUseCase: AdminContextUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminDashboardUiState(loading = true))
    val state: StateFlow<AdminDashboardUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        contextUseCase()
            .onSuccess { ctx -> _state.value = _state.value.copy(context = ctx) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
        dashboardUseCase()
            .onSuccess { dash -> _state.value = _state.value.copy(loading = false, dashboard = dash) }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }
}
