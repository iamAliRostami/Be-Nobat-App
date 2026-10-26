package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementCustomerInput
import com.leon.be_nobat.domain.model.ManagementPerson
import com.leon.be_nobat.domain.usecase.AdminCreateCustomerUseCase
import com.leon.be_nobat.domain.usecase.AdminListCustomersUseCase
import com.leon.be_nobat.domain.usecase.AdminTogglePersonActiveUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminCustomersUiState(
    val loading: Boolean = false,
    val items: List<ManagementPerson> = emptyList(),
    val filteredItems: List<ManagementPerson> = emptyList(),
    val total: Int = 0,
    val searchQuery: String = "",
    val statusFilter: String = "all", // "all", "active", "disabled"
    val error: String? = null,
    val message: String? = null,
)

class AdminCustomersViewModel(
    private val list: AdminListCustomersUseCase,
    private val create: AdminCreateCustomerUseCase,
    private val toggleActiveUseCase: AdminTogglePersonActiveUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminCustomersUiState(loading = true))
    val state: StateFlow<AdminCustomersUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        list(q = s.searchQuery.ifBlank { null }, pageSize = 100)
            .onSuccess { page: ApiPage<ManagementPerson> ->
                val filtered = applyFilters(page.items, _state.value.searchQuery, _state.value.statusFilter)
                _state.value = _state.value.copy(
                    loading = false,
                    items = page.items,
                    filteredItems = filtered,
                    total = filtered.size,
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun setQuery(q: String) {
        val filtered = applyFilters(_state.value.items, q, _state.value.statusFilter)
        _state.value = _state.value.copy(
            searchQuery = q,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    fun setStatusFilter(status: String) {
        val filtered = applyFilters(_state.value.items, _state.value.searchQuery, status)
        _state.value = _state.value.copy(
            statusFilter = status,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    fun createCustomer(name: String, email: String?, phone: String?) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        create(ManagementCustomerInput(name = name.trim(), email = email?.trim()?.ifBlank { null }, phone = phone?.trim()))
            .onSuccess { created ->
                val newItems = listOf(created) + _state.value.items.filterNot { it.id == created.id }
                val filtered = applyFilters(newItems, _state.value.searchQuery, _state.value.statusFilter)
                _state.value = _state.value.copy(
                    loading = false,
                    items = newItems,
                    filteredItems = filtered,
                    total = filtered.size,
                    message = "customer_created",
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun toggleActive(id: String, active: Boolean) = viewModelScope.launch {
        toggleActiveUseCase(id, active)
            .onSuccess { updated ->
                val newItems = _state.value.items.map { if (it.id == id) it.copy(active = active) else it }
                val filtered = applyFilters(newItems, _state.value.searchQuery, _state.value.statusFilter)
                _state.value = _state.value.copy(
                    items = newItems,
                    filteredItems = filtered,
                    total = filtered.size,
                    message = if (active) "person_activated" else "person_disabled",
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun applyFilters(items: List<ManagementPerson>, query: String, status: String): List<ManagementPerson> {
        val q = query.trim().lowercase()
        return items.filter { person ->
            val matchesQuery = q.isBlank() ||
                person.displayName.lowercase().contains(q) ||
                (person.email?.lowercase()?.contains(q) == true) ||
                (person.phoneNumber?.lowercase()?.contains(q) == true)

            val matchesStatus = when (status) {
                "active" -> person.active
                "disabled" -> !person.active
                else -> true
            }

            matchesQuery && matchesStatus
        }
    }
}
