package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.usecase.AdminCreateBranchUseCase
import com.leon.be_nobat.domain.usecase.AdminDeleteBranchUseCase
import com.leon.be_nobat.domain.usecase.AdminListBranchesUseCase
import com.leon.be_nobat.domain.usecase.AdminToggleBranchActiveUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateBranchUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminBranchesUiState(
    val loading: Boolean = false,
    val items: List<ManagementBranch> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
    val message: String? = null,
)

class AdminBranchesViewModel(
    private val listUseCase: AdminListBranchesUseCase,
    private val createUseCase: AdminCreateBranchUseCase,
    private val updateUseCase: AdminUpdateBranchUseCase,
    private val toggleUseCase: AdminToggleBranchActiveUseCase,
    private val deleteUseCase: AdminDeleteBranchUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminBranchesUiState(loading = true))
    val state: StateFlow<AdminBranchesUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        listUseCase()
            .onSuccess { page: ApiPage<ManagementBranch> ->
                _state.value = _state.value.copy(loading = false, items = page.items, total = page.total)
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun toggleActive(id: String, active: Boolean) = viewModelScope.launch {
        toggleUseCase(id, active).onSuccess { updated ->
            val items = _state.value.items.map { if (it.id == id) updated else it }
            _state.value = _state.value.copy(items = items, message = "updated")
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun create(input: com.leon.be_nobat.domain.model.ManagementBranchInput) = viewModelScope.launch {
        createUseCase(input)
            .onSuccess { created ->
                _state.value = _state.value.copy(items = listOf(created) + _state.value.items, message = "created")
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun update(id: String, input: com.leon.be_nobat.domain.model.ManagementBranchInput) = viewModelScope.launch {
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
