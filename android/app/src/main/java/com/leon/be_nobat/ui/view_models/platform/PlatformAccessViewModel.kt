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
    val filteredItems: List<ManagementUser> = emptyList(),
    val total: Int = 0,
    val selectedTab: String = "",
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

    private var resettingPassword = false
    private val _passwordSms = MutableStateFlow<com.leon.be_nobat.notifications.PasswordResetSms?>(null)
    val passwordSms = _passwordSms.asStateFlow()
    var smsPermissionPending = false

    fun takePasswordSms(): com.leon.be_nobat.notifications.PasswordResetSms? {
        val pending = _passwordSms.value ?: return null
        _passwordSms.value = null
        if (_state.value.message == "reset_done") clearMessage()
        return pending
    }

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        listUseCase(page = 1, pageSize = 100)
            .onSuccess { page: ApiPage<ManagementUser> ->
                val currentTab = _state.value.selectedTab
                val currentQuery = _state.value.searchQuery
                val filtered = filterUsers(page.items, currentTab, currentQuery)
                _state.value = _state.value.copy(
                    loading = false,
                    items = page.items,
                    filteredItems = filtered,
                    total = filtered.size,
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun setSelectedTab(tab: String) {
        if (_state.value.selectedTab == tab) return
        val currentItems = _state.value.items
        val currentQuery = _state.value.searchQuery
        val filtered = filterUsers(currentItems, tab, currentQuery)
        _state.value = _state.value.copy(
            selectedTab = tab,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    fun setQuery(q: String) {
        val currentItems = _state.value.items
        val currentTab = _state.value.selectedTab
        val filtered = filterUsers(currentItems, currentTab, q)
        _state.value = _state.value.copy(
            searchQuery = q,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    private fun filterUsers(items: List<ManagementUser>, tab: String, query: String): List<ManagementUser> {
        val trimmed = query.trim()
        return items.filter { user ->
            val matchesTab = when (tab) {
                "" -> true
                "Customer" -> (user.roles.isEmpty() || user.roles.contains("Customer")) &&
                    !user.roles.any { it in listOf("PlatformAdmin", "Owner", "Manager", "Staff") }
                "Manager" -> user.roles.any { it.equals("Owner", ignoreCase = true) || it.equals("Manager", ignoreCase = true) }
                "Staff" -> user.roles.any { it.equals("Staff", ignoreCase = true) }
                "PlatformAdmin" -> user.roles.any { it.equals("PlatformAdmin", ignoreCase = true) }
                "Inactive" -> !user.active
                else -> true
            }

            val matchesQuery = if (trimmed.isEmpty()) {
                true
            } else {
                user.displayName.contains(trimmed, ignoreCase = true) ||
                    (user.email?.contains(trimmed, ignoreCase = true) == true) ||
                    (user.phoneNumber?.contains(trimmed, ignoreCase = true) == true) ||
                    user.roles.any { it.contains(trimmed, ignoreCase = true) }
            }

            matchesTab && matchesQuery
        }
    }

    fun toggleActive(id: String, active: Boolean) = viewModelScope.launch {
        toggleActiveUseCase(id, active).onSuccess { updated ->
            val items = _state.value.items.map { if (it.id == id) updated else it }
            val filtered = filterUsers(items, _state.value.selectedTab, _state.value.searchQuery)
            _state.value = _state.value.copy(
                items = items,
                filteredItems = filtered,
                total = filtered.size,
                message = "updated",
            )
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun setRole(id: String, role: String) = viewModelScope.launch {
        setRolesUseCase(id, listOf(role)).onSuccess { updated ->
            val items = _state.value.items.map { if (it.id == id) updated else it }
            val filtered = filterUsers(items, _state.value.selectedTab, _state.value.searchQuery)
            _state.value = _state.value.copy(
                items = items,
                filteredItems = filtered,
                total = filtered.size,
                message = "updated",
            )
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun resetPassword(id: String) = viewModelScope.launch {
        if (resettingPassword || _passwordSms.value != null) return@launch
        val user = _state.value.items.firstOrNull { it.id == id } ?: return@launch
        resettingPassword = true
        try {
            resetPasswordUseCase(id).onSuccess { res ->
                if (res.userId != id || res.temporaryPassword.isBlank()) {
                    _state.value = _state.value.copy(error = "invalid_reset_response")
                    return@onSuccess
                }
                _state.value = _state.value.copy(temporaryPassword = res.temporaryPassword, message = "reset_done", error = null)
                _passwordSms.value = com.leon.be_nobat.notifications.PasswordResetSms(
                    user.id, user.displayName, user.phoneNumber, user.email, res.temporaryPassword)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(error = e.message)
            }
        } finally { resettingPassword = false }
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
