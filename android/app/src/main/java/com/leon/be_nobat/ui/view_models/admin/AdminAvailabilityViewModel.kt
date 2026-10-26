package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementAvailability
import com.leon.be_nobat.domain.model.ManagementAvailabilityInput
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementResource
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.domain.usecase.AdminCreateAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminDeleteAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminListAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateAvailabilityUseCase
import com.leon.be_nobat.helpers.JalaliDateHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminAvailabilityUiState(
    val loading: Boolean = false,
    val items: List<ManagementAvailability> = emptyList(),
    val filteredItems: List<ManagementAvailability> = emptyList(),
    val total: Int = 0,
    val activeTab: String = "active", // "active" or "past"
    val searchQuery: String = "",
    val businesses: List<ManagementBusiness> = emptyList(),
    val branches: List<ManagementBranch> = emptyList(),
    val resources: List<ManagementResource> = emptyList(),
    val selectedBusinessId: String? = null,
    val selectedBranchId: String? = null,
    val selectedResourceId: String? = null,
    val selectedBusinessName: String? = null,
    val selectedBranchName: String? = null,
    val selectedResourceName: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val hasFilters: Boolean
        get() = !selectedBusinessId.isNullOrBlank() || !selectedBranchId.isNullOrBlank() || !selectedResourceId.isNullOrBlank()

    val filterBranches: List<ManagementBranch>
        get() = if (selectedBusinessId.isNullOrBlank()) branches else branches.filter { it.businessId == selectedBusinessId }

    val filterResources: List<ManagementResource>
        get() = resources.filter { res ->
            (selectedBranchId.isNullOrBlank() || res.branchId == selectedBranchId) &&
            (selectedBusinessId.isNullOrBlank() || filterBranches.any { b -> b.id == res.branchId })
        }
}

class AdminAvailabilityViewModel(
    private val list: AdminListAvailabilityUseCase,
    private val create: AdminCreateAvailabilityUseCase,
    private val update: AdminUpdateAvailabilityUseCase,
    private val delete: AdminDeleteAvailabilityUseCase,
    private val management: ManagementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminAvailabilityUiState(loading = true))
    val state: StateFlow<AdminAvailabilityUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }

        val bizDeferred = async { management.listBusinesses(pageSize = 100) }
        val branchDeferred = async { management.listBranches(pageSize = 100) }
        val resDeferred = async { management.listResources(pageSize = 100) }
        val listDeferred = async { list(pageSize = 100) }

        val bizResult = bizDeferred.await()
        val branchResult = branchDeferred.await()
        val resResult = resDeferred.await()
        val listResult = listDeferred.await()

        _state.update { current ->
            val activeBiz = bizResult.getOrNull()?.items?.filter { it.active } ?: current.businesses
            val activeBranches = branchResult.getOrNull()?.items?.filter { it.active } ?: current.branches
            val resources = resResult.getOrNull()?.items ?: current.resources
            val items = listResult.getOrNull()?.items ?: current.items

            val currentBiz = activeBiz.firstOrNull { it.id == current.selectedBusinessId }
            val currentBranch = activeBranches.firstOrNull { it.id == current.selectedBranchId }
            val currentRes = resources.firstOrNull { it.id == current.selectedResourceId }

            val filtered = applyFilters(
                items,
                current.activeTab,
                current.searchQuery,
                current.selectedBusinessId,
                current.selectedBranchId,
                current.selectedResourceId
            )

            current.copy(
                loading = false,
                businesses = activeBiz,
                branches = activeBranches,
                resources = resources,
                selectedBusinessName = currentBiz?.name,
                selectedBranchName = currentBranch?.name,
                selectedResourceName = currentRes?.name,
                items = items,
                filteredItems = filtered,
                total = filtered.size,
                error = listResult.exceptionOrNull()?.message
            )
        }
    }

    fun setBusinessFilter(businessId: String?) {
        _state.update { s ->
            val resolvedId = businessId?.ifBlank { null }
            val bizName = s.businesses.firstOrNull { it.id == resolvedId }?.name
            // Changing business resets branch and resource (matching Web AdminAvailability.razor)
            val filtered = applyFilters(s.items, s.activeTab, s.searchQuery, resolvedId, null, null)
            s.copy(
                selectedBusinessId = resolvedId,
                selectedBusinessName = bizName,
                selectedBranchId = null,
                selectedBranchName = null,
                selectedResourceId = null,
                selectedResourceName = null,
                filteredItems = filtered,
                total = filtered.size
            )
        }
    }

    fun setBranchFilter(branchId: String?) {
        _state.update { s ->
            val resolvedId = branchId?.ifBlank { null }
            val branchName = s.branches.firstOrNull { it.id == resolvedId }?.name
            // Changing branch resets resource (matching Web AdminAvailability.razor)
            val filtered = applyFilters(s.items, s.activeTab, s.searchQuery, s.selectedBusinessId, resolvedId, null)
            s.copy(
                selectedBranchId = resolvedId,
                selectedBranchName = branchName,
                selectedResourceId = null,
                selectedResourceName = null,
                filteredItems = filtered,
                total = filtered.size
            )
        }
    }

    fun setResourceFilter(resourceId: String?) {
        _state.update { s ->
            val resolvedId = resourceId?.ifBlank { null }
            val resName = s.resources.firstOrNull { it.id == resolvedId }?.name
            val filtered = applyFilters(s.items, s.activeTab, s.searchQuery, s.selectedBusinessId, s.selectedBranchId, resolvedId)
            s.copy(
                selectedResourceId = resolvedId,
                selectedResourceName = resName,
                filteredItems = filtered,
                total = filtered.size
            )
        }
    }

    fun setFilters(businessId: String?, branchId: String?, resourceId: String?) {
        _state.update { s ->
            val resBizId = businessId?.ifBlank { null }
            val resBranchId = branchId?.ifBlank { null }
            val resResourceId = resourceId?.ifBlank { null }
            val bizName = s.businesses.firstOrNull { it.id == resBizId }?.name
            val branchName = s.branches.firstOrNull { it.id == resBranchId }?.name
            val resName = s.resources.firstOrNull { it.id == resResourceId }?.name
            val filtered = applyFilters(s.items, s.activeTab, s.searchQuery, resBizId, resBranchId, resResourceId)
            s.copy(
                selectedBusinessId = resBizId,
                selectedBusinessName = bizName,
                selectedBranchId = resBranchId,
                selectedBranchName = branchName,
                selectedResourceId = resResourceId,
                selectedResourceName = resName,
                filteredItems = filtered,
                total = filtered.size
            )
        }
    }

    fun clearFilters() {
        _state.update { s ->
            val filtered = applyFilters(s.items, s.activeTab, s.searchQuery, null, null, null)
            s.copy(
                selectedBusinessId = null,
                selectedBusinessName = null,
                selectedBranchId = null,
                selectedBranchName = null,
                selectedResourceId = null,
                selectedResourceName = null,
                filteredItems = filtered,
                total = filtered.size
            )
        }
    }

    fun setTab(tab: String) {
        _state.update { s ->
            val filtered = applyFilters(s.items, tab, s.searchQuery, s.selectedBusinessId, s.selectedBranchId, s.selectedResourceId)
            s.copy(
                activeTab = tab,
                filteredItems = filtered,
                total = filtered.size
            )
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { s ->
            val filtered = applyFilters(s.items, s.activeTab, query, s.selectedBusinessId, s.selectedBranchId, s.selectedResourceId)
            s.copy(
                searchQuery = query,
                filteredItems = filtered,
                total = filtered.size
            )
        }
    }

    fun create(input: ManagementAvailabilityInput) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        create.invoke(input)
            .onSuccess {
                _state.value = _state.value.copy(message = "availability_created")
                load()
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun update(id: String, input: ManagementAvailabilityInput) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        update.invoke(id, input)
            .onSuccess {
                _state.value = _state.value.copy(message = "availability_updated")
                load()
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun delete(id: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        delete.invoke(id)
            .onSuccess {
                _state.value = _state.value.copy(message = "availability_deleted")
                load()
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun applyFilters(
        items: List<ManagementAvailability>,
        tab: String,
        query: String,
        businessId: String? = null,
        branchId: String? = null,
        resourceId: String? = null,
    ): List<ManagementAvailability> {
        val todayIso = JalaliDateHelper.getTodayIso()
        var result = if (tab == "past") {
            items.filter { it.effectiveDate != null && it.effectiveDate < todayIso }
        } else {
            items.filter { it.effectiveDate == null || it.effectiveDate >= todayIso }
        }

        if (!businessId.isNullOrBlank()) {
            result = result.filter { it.businessId == businessId }
        }
        if (!branchId.isNullOrBlank()) {
            result = result.filter { it.branchId == branchId }
        }
        if (!resourceId.isNullOrBlank()) {
            result = result.filter { it.resourceId == resourceId }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter { item ->
                item.businessName?.lowercase()?.contains(q) == true ||
                item.branchName?.lowercase()?.contains(q) == true ||
                item.resourceName?.lowercase()?.contains(q) == true ||
                JalaliDateHelper.translateDayOfWeek(item.dayOfWeek).contains(q) ||
                item.effectiveDate?.contains(q) == true ||
                item.startsAt.contains(q) ||
                item.endsAt.contains(q)
            }
        }
        return result
    }
}
