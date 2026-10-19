package com.leon.be_nobat.ui.view_models.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.usecase.PlatformCreateBusinessUseCase
import com.leon.be_nobat.domain.usecase.PlatformDeleteBusinessUseCase
import com.leon.be_nobat.domain.usecase.PlatformListBusinessesUseCase
import com.leon.be_nobat.domain.usecase.PlatformToggleBusinessActiveUseCase
import com.leon.be_nobat.domain.usecase.PlatformUpdateBusinessUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlatformBusinessesUiState(
    val loading: Boolean = false,
    val items: List<ManagementBusiness> = emptyList(),
    val total: Int = 0,
    val searchQuery: String = "",
    val error: String? = null,
    val message: String? = null,
)

class PlatformBusinessesViewModel(
    private val list: PlatformListBusinessesUseCase,
    private val createUseCase: PlatformCreateBusinessUseCase,
    private val updateUseCase: PlatformUpdateBusinessUseCase,
    private val toggle: PlatformToggleBusinessActiveUseCase,
    private val deleteUseCase: PlatformDeleteBusinessUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlatformBusinessesUiState(loading = true))
    val state: StateFlow<PlatformBusinessesUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        list(q = s.searchQuery.ifBlank { null })
            .onSuccess { page: ApiPage<ManagementBusiness> ->
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

    fun create(input: com.leon.be_nobat.domain.model.ManagementBusinessInput) = viewModelScope.launch {
        createUseCase(input)
            .onSuccess { created ->
                _state.value = _state.value.copy(items = listOf(created) + _state.value.items, message = "created")
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun update(id: String, input: com.leon.be_nobat.domain.model.ManagementBusinessInput) = viewModelScope.launch {
        updateUseCase(id, input)
            .onSuccess { updated ->
                val items = _state.value.items.map { if (it.id == id) updated else it }
                _state.value = _state.value.copy(items = items, message = "updated")
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun delete(id: String) = viewModelScope.launch {
        deleteUseCase(id).onSuccess {
            _state.value = _state.value.copy(items = _state.value.items.filterNot { it.id == id }, message = "deleted")
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
}
