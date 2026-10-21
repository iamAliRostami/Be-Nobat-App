package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementAvailability
import com.leon.be_nobat.domain.model.ManagementAvailabilityInput
import com.leon.be_nobat.domain.usecase.AdminCreateAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminDeleteAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminListAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateAvailabilityUseCase
import com.leon.be_nobat.helpers.JalaliDateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminAvailabilityUiState(
    val loading: Boolean = false,
    val items: List<ManagementAvailability> = emptyList(),
    val filteredItems: List<ManagementAvailability> = emptyList(),
    val total: Int = 0,
    val activeTab: String = "active", // "active" or "past"
    val searchQuery: String = "",
    val error: String? = null,
    val message: String? = null,
)

class AdminAvailabilityViewModel(
    private val list: AdminListAvailabilityUseCase,
    private val create: AdminCreateAvailabilityUseCase,
    private val update: AdminUpdateAvailabilityUseCase,
    private val delete: AdminDeleteAvailabilityUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminAvailabilityUiState(loading = true))
    val state: StateFlow<AdminAvailabilityUiState> = _state.asStateFlow()

    init { load() }

    fun load(businessId: String? = null, branchId: String? = null, resourceId: String? = null) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        list(businessId = businessId, branchId = branchId, resourceId = resourceId, pageSize = 100)
            .onSuccess { page: ApiPage<ManagementAvailability> ->
                val currentTab = _state.value.activeTab
                val currentQuery = _state.value.searchQuery
                val filtered = applyFilters(page.items, currentTab, currentQuery)
                _state.value = _state.value.copy(
                    loading = false,
                    items = page.items,
                    filteredItems = filtered,
                    total = filtered.size
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun setTab(tab: String) {
        val currentItems = _state.value.items
        val currentQuery = _state.value.searchQuery
        val filtered = applyFilters(currentItems, tab, currentQuery)
        _state.value = _state.value.copy(
            activeTab = tab,
            filteredItems = filtered,
            total = filtered.size
        )
    }

    fun setSearchQuery(query: String) {
        val currentItems = _state.value.items
        val currentTab = _state.value.activeTab
        val filtered = applyFilters(currentItems, currentTab, query)
        _state.value = _state.value.copy(
            searchQuery = query,
            filteredItems = filtered,
            total = filtered.size
        )
    }

    fun create(input: ManagementAvailabilityInput) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        create.invoke(input)
            .onSuccess {
                _state.value = _state.value.copy(message = "بازه زمانی با موفقیت ثبت شد.")
                load()
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun update(id: String, input: ManagementAvailabilityInput) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        update.invoke(id, input)
            .onSuccess {
                _state.value = _state.value.copy(message = "بازه زمانی با موفقیت ویرایش شد.")
                load()
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun delete(id: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        delete.invoke(id)
            .onSuccess {
                _state.value = _state.value.copy(message = "بازه زمانی با موفقیت حذف شد.")
                load()
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    private fun applyFilters(
        items: List<ManagementAvailability>,
        tab: String,
        query: String
    ): List<ManagementAvailability> {
        val todayIso = JalaliDateHelper.getTodayIso()
        val byTab = if (tab == "past") {
            items.filter { it.effectiveDate != null && it.effectiveDate < todayIso }
        } else {
            items.filter { it.effectiveDate == null || it.effectiveDate >= todayIso }
        }

        if (query.isBlank()) return byTab

        val q = query.trim().lowercase()
        return byTab.filter { item ->
            item.businessName?.lowercase()?.contains(q) == true ||
            item.branchName?.lowercase()?.contains(q) == true ||
            item.resourceName?.lowercase()?.contains(q) == true ||
            JalaliDateHelper.translateDayOfWeek(item.dayOfWeek).contains(q) ||
            item.effectiveDate?.contains(q) == true ||
            item.startsAt.contains(q) ||
            item.endsAt.contains(q)
        }
    }
}
