package com.leon.be_nobat.ui.view_models.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementUser
import com.leon.be_nobat.domain.usecase.PlatformCreateUserUseCase
import com.leon.be_nobat.domain.usecase.PlatformListUsersUseCase
import com.leon.be_nobat.domain.usecase.PlatformResetPasswordUseCase
import com.leon.be_nobat.domain.usecase.PlatformSetUserRolesUseCase
import com.leon.be_nobat.domain.usecase.PlatformToggleUserActiveUseCase
import com.leon.be_nobat.domain.usecase.PlatformUpdateUserUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlatformAccessUiState(
    val loading: Boolean = false,
    val items: List<ManagementUser> = emptyList(),
    val total: Int = 0,
    val searchQuery: String = "",
    val error: String? = null,
    val message: String? = null,
    val temporaryPassword: String? = null,
)

class PlatformAccessViewModel(
    private val listUseCase: PlatformListUsersUseCase,
    private val createUseCase: PlatformCreateUserUseCase,
    private val updateUseCase: PlatformUpdateUserUseCase,
    private val setRolesUseCase: PlatformSetUserRolesUseCase,
    private val toggleActiveUseCase: PlatformToggleUserActiveUseCase,
    private val resetPasswordUseCase: PlatformResetPasswordUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlatformAccessUiState(loading = true))
    val state: StateFlow<PlatformAccessUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        listUseCase(q = s.searchQuery.ifBlank { null })
            .onSuccess { page: ApiPage<ManagementUser> ->
                _state.value = _state.value.copy(loading = false, items = page.items, total = page.total)
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(searchQuery = q) }

    fun toggleActive(id: String, active: Boolean) = viewModelScope.launch {
        toggleActiveUseCase(id, active).onSuccess { updated ->
            val items = _state.value.items.map { if (it.id == id) updated else it }
            _state.value = _state.value.copy(items = items, message = "updated")
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun setRole(id: String, role: String) = viewModelScope.launch {
        setRolesUseCase(id, listOf(role)).onSuccess { updated ->
            val items = _state.value.items.map { if (it.id == id) updated else it }
            _state.value = _state.value.copy(items = items, message = "updated")
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun resetPassword(id: String) = viewModelScope.launch {
        resetPasswordUseCase(id).onSuccess { res ->
            _state.value = _state.value.copy(temporaryPassword = res.temporaryPassword, message = "reset_done")
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null, temporaryPassword = null) }

    fun createUser(input: com.leon.be_nobat.domain.model.ManagementUserInput) = viewModelScope.launch {
        createUseCase(input)
            .onSuccess { created ->
                _state.value = _state.value.copy(
                    temporaryPassword = created.temporaryPassword,
                    message = "user_created_temporary_password_shown_once",
                )
                load() // refresh the list
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
}
