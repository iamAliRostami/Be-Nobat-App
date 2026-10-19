package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementService
import com.leon.be_nobat.domain.usecase.AdminCreateServiceUseCase
import com.leon.be_nobat.domain.usecase.AdminListServicesUseCase
import com.leon.be_nobat.domain.usecase.AdminToggleServiceActiveUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateServiceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminServicesUiState(
    val loading: Boolean = false,
    val items: List<ManagementService> = emptyList(),
    val total: Int = 0,
    val searchQuery: String = "",
    val error: String? = null,
    val message: String? = null,
)

class AdminServicesViewModel(
    private val list: AdminListServicesUseCase,
    private val create: AdminCreateServiceUseCase,
    private val update: AdminUpdateServiceUseCase,
    private val toggle: AdminToggleServiceActiveUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminServicesUiState(loading = true))
    val state: StateFlow<AdminServicesUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        list(q = s.searchQuery.ifBlank { null })
            .onSuccess { page: ApiPage<ManagementService> ->
                _state.value = _state.value.copy(loading = false, items = page.items, total = page.total)
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(searchQuery = q) }

    fun toggleActive(id: String, active: Boolean) = viewModelScope.launch {
        toggle(id, active).onSuccess { updated ->
            val items = _state.value.items.map { if (it.id == id) updated else it }
            _state.value = _state.value.copy(items = items, message = "updated")
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun createService(input: com.leon.be_nobat.domain.model.ManagementServiceInput) = viewModelScope.launch {
        create(input)
            .onSuccess { created ->
                _state.value = _state.value.copy(items = listOf(created) + _state.value.items, message = "created")
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun updateService(id: String, input: com.leon.be_nobat.domain.model.ManagementServiceInput) = viewModelScope.launch {
        update(id, input)
            .onSuccess { updated ->
                val items = _state.value.items.map { if (it.id == id) updated else it }
                _state.value = _state.value.copy(items = items, message = "updated")
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
}
