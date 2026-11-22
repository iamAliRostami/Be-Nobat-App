package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementPerson
import com.leon.be_nobat.domain.model.ManagementTeamMemberInput
import com.leon.be_nobat.domain.usecase.AdminCreateTeamMemberUseCase
import com.leon.be_nobat.domain.usecase.AdminListBranchesUseCase
import com.leon.be_nobat.domain.usecase.AdminListTeamUseCase
import com.leon.be_nobat.domain.usecase.AdminTogglePersonActiveUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateTeamMemberRoleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminTeamUiState(
    val loading: Boolean = false,
    val items: List<ManagementPerson> = emptyList(),
    val filteredItems: List<ManagementPerson> = emptyList(),
    val branches: List<ManagementBranch> = emptyList(),
    val total: Int = 0,
    val searchQuery: String = "",
    val statusFilter: String = "all", // "all", "active", "disabled"
    val selectedBusinessId: String? = null,
    val selectedBranchId: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val hasFilters: Boolean
        get() = !selectedBusinessId.isNullOrBlank() || !selectedBranchId.isNullOrBlank()
}

class AdminTeamViewModel(
    private val list: AdminListTeamUseCase,
    private val create: AdminCreateTeamMemberUseCase,
    private val toggleActiveUseCase: AdminTogglePersonActiveUseCase,
    private val listBranchesUseCase: AdminListBranchesUseCase,
    private val updateRoleUseCase: AdminUpdateTeamMemberRoleUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminTeamUiState(loading = true))
    val state: StateFlow<AdminTeamUiState> = _state.asStateFlow()

    init {
        load()
        loadBranches()
    }

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        list(q = s.searchQuery.ifBlank { null }, pageSize = 100)
            .onSuccess { page: ApiPage<ManagementPerson> ->
                val filtered = applyFilters(page.items, _state.value.searchQuery, _state.value.statusFilter, _state.value.selectedBusinessId, _state.value.selectedBranchId)
                _state.value = _state.value.copy(
                    loading = false,
                    items = page.items,
                    filteredItems = filtered,
                    total = filtered.size,
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    private fun loadBranches() = viewModelScope.launch {
        listBranchesUseCase(includeInactive = false, pageSize = 100)
            .onSuccess { page ->
                _state.value = _state.value.copy(branches = page.items)
            }
    }

    fun setQuery(q: String) {
        val filtered = applyFilters(_state.value.items, q, _state.value.statusFilter, _state.value.selectedBusinessId, _state.value.selectedBranchId)
        _state.value = _state.value.copy(
            searchQuery = q,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    fun setStatusFilter(status: String) {
        val filtered = applyFilters(_state.value.items, _state.value.searchQuery, status, _state.value.selectedBusinessId, _state.value.selectedBranchId)
        _state.value = _state.value.copy(
            statusFilter = status,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    fun setBusinessFilter(businessId: String?) {
        val filtered = applyFilters(_state.value.items, _state.value.searchQuery, _state.value.statusFilter, businessId, null)
        _state.value = _state.value.copy(
            selectedBusinessId = businessId,
            selectedBranchId = null,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    fun setBranchFilter(branchId: String?) {
        val branch = _state.value.branches.firstOrNull { it.id == branchId }
        val bizId = branch?.businessId ?: _state.value.selectedBusinessId
        val filtered = applyFilters(_state.value.items, _state.value.searchQuery, _state.value.statusFilter, bizId, branchId)
        _state.value = _state.value.copy(
            selectedBusinessId = bizId,
            selectedBranchId = branchId,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    fun clearFilters() {
        val filtered = applyFilters(_state.value.items, _state.value.searchQuery, _state.value.statusFilter, null, null)
        _state.value = _state.value.copy(
            selectedBusinessId = null,
            selectedBranchId = null,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    fun createTeamMember(name: String, email: String?, phone: String?, branchId: String, role: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        create(ManagementTeamMemberInput(name = name.trim(), email = email?.trim()?.ifBlank { null }, phone = phone?.trim(), branchId = branchId, role = role))
            .onSuccess { created ->
                val newItems = listOf(created) + _state.value.items.filterNot { it.id == created.id }
                val filtered = applyFilters(newItems, _state.value.searchQuery, _state.value.statusFilter, _state.value.selectedBusinessId, _state.value.selectedBranchId)
                _state.value = _state.value.copy(
                    loading = false,
                    items = newItems,
                    filteredItems = filtered,
                    total = filtered.size,
                    message = "team_member_created",
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun updateMemberRole(userId: String, branchId: String, newRole: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        updateRoleUseCase(userId, branchId, newRole)
            .onSuccess { updated ->
                val newItems = _state.value.items.map {
                    if (it.id == userId) {
                        it.copy(role = newRole, branchName = updated.branchName ?: it.branchName)
                    } else it
                }
                val filtered = applyFilters(newItems, _state.value.searchQuery, _state.value.statusFilter, _state.value.selectedBusinessId, _state.value.selectedBranchId)
                _state.value = _state.value.copy(
                    loading = false,
                    items = newItems,
                    filteredItems = filtered,
                    total = filtered.size,
                    message = "member_role_updated",
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
    }

    fun toggleActive(id: String, active: Boolean) = viewModelScope.launch {
        toggleActiveUseCase(id, active)
            .onSuccess { updated ->
                val newItems = _state.value.items.map { if (it.id == id) it.copy(active = active) else it }
                val filtered = applyFilters(newItems, _state.value.searchQuery, _state.value.statusFilter, _state.value.selectedBusinessId, _state.value.selectedBranchId)
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

    private fun applyFilters(
        items: List<ManagementPerson>,
        query: String,
        status: String,
        businessId: String? = null,
        branchId: String? = null,
    ): List<ManagementPerson> {
        val q = query.trim().lowercase()
        return items.filter { person ->
            val matchesQuery = q.isBlank() ||
                person.displayName.lowercase().contains(q) ||
                (person.email?.lowercase()?.contains(q) == true) ||
                (person.phoneNumber?.lowercase()?.contains(q) == true) ||
                (person.role?.lowercase()?.contains(q) == true) ||
                (person.branchName?.lowercase()?.contains(q) == true) ||
                (person.businessName?.lowercase()?.contains(q) == true)

            val matchesStatus = when (status) {
                "active" -> person.active
                "disabled" -> !person.active
                else -> true
            }

            val matchesBusiness = businessId.isNullOrBlank() || person.businessId == businessId
            val matchesBranch = branchId.isNullOrBlank() || person.branchId == branchId

            matchesQuery && matchesStatus && matchesBusiness && matchesBranch
        }
    }
}
