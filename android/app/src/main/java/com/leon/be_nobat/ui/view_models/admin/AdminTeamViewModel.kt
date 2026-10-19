package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementPerson
import com.leon.be_nobat.domain.usecase.AdminListTeamUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminTeamUiState(
    val loading: Boolean = false,
    val items: List<ManagementPerson> = emptyList(),
    val total: Int = 0,
    val searchQuery: String = "",
    val error: String? = null,
)

class AdminTeamViewModel(
    private val list: AdminListTeamUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminTeamUiState(loading = true))
    val state: StateFlow<AdminTeamUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        list(q = s.searchQuery.ifBlank { null })
            .onSuccess { page: ApiPage<ManagementPerson> ->
                _state.value = _state.value.copy(loading = false, items = page.items, total = page.total)
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(searchQuery = q) }
}
