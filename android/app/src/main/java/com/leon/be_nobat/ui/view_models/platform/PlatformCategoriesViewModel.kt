package com.leon.be_nobat.ui.view_models.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementCategory
import com.leon.be_nobat.domain.usecase.PlatformCreateCategoryUseCase
import com.leon.be_nobat.domain.usecase.PlatformDeleteCategoryUseCase
import com.leon.be_nobat.domain.usecase.PlatformListCategoriesUseCase
import com.leon.be_nobat.domain.usecase.PlatformUpdateCategoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlatformCategoriesUiState(
    val loading: Boolean = false,
    val items: List<ManagementCategory> = emptyList(),
    val filteredItems: List<ManagementCategory> = emptyList(),
    val total: Int = 0,
    val selectedTab: String = "",
    val searchQuery: String = "",
    val error: String? = null,
    val message: String? = null,
)

class PlatformCategoriesViewModel(
    private val list: PlatformListCategoriesUseCase,
    private val createUseCase: PlatformCreateCategoryUseCase,
    private val updateUseCase: PlatformUpdateCategoryUseCase,
    private val deleteUseCase: PlatformDeleteCategoryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlatformCategoriesUiState(loading = true))
    val state: StateFlow<PlatformCategoriesUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        list(includeInactive = true)
            .onSuccess { page: ApiPage<ManagementCategory> ->
                val currentTab = _state.value.selectedTab
                val currentQuery = _state.value.searchQuery
                val filtered = filterCategories(page.items, currentTab, currentQuery)
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
        val filtered = filterCategories(currentItems, tab, currentQuery)
        _state.value = _state.value.copy(
            selectedTab = tab,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    fun setQuery(q: String) {
        val currentItems = _state.value.items
        val currentTab = _state.value.selectedTab
        val filtered = filterCategories(currentItems, currentTab, q)
        _state.value = _state.value.copy(
            searchQuery = q,
            filteredItems = filtered,
            total = filtered.size,
        )
    }

    private fun filterCategories(
        items: List<ManagementCategory>,
        tab: String,
        query: String,
    ): List<ManagementCategory> {
        val trimmed = query.trim()
        return items.filter { cat ->
            val matchesTab = when (tab) {
                "" -> true
                "Business" -> cat.kind.equals("Business", ignoreCase = true)
                "Service" -> cat.kind.equals("Service", ignoreCase = true)
                "Active" -> cat.active
                "Inactive" -> !cat.active
                else -> true
            }

            val matchesQuery = if (trimmed.isEmpty()) {
                true
            } else {
                cat.name.contains(trimmed, ignoreCase = true) ||
                    cat.kind.contains(trimmed, ignoreCase = true) ||
                    (if (cat.kind.equals("Business", ignoreCase = true)) "کسب‌وکار".contains(trimmed) else "خدمت".contains(trimmed))
            }

            matchesTab && matchesQuery
        }
    }

    fun toggleActive(category: ManagementCategory) = viewModelScope.launch {
        val newActive = !category.active
        updateUseCase(
            category.id,
            com.leon.be_nobat.domain.model.ManagementCategoryInput(
                name = category.name,
                kind = category.kind,
                sortOrder = category.sortOrder,
                isActive = newActive,
            )
        ).onSuccess { updated ->
            val items = _state.value.items.map { if (it.id == category.id) updated else it }
            val filtered = filterCategories(items, _state.value.selectedTab, _state.value.searchQuery)
            _state.value = _state.value.copy(
                items = items,
                filteredItems = filtered,
                total = filtered.size,
                message = "updated",
            )
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun delete(id: String) = viewModelScope.launch {
        deleteUseCase(id).onSuccess {
            val items = _state.value.items.filterNot { it.id == id }
            val filtered = filterCategories(items, _state.value.selectedTab, _state.value.searchQuery)
            _state.value = _state.value.copy(
                items = items,
                filteredItems = filtered,
                total = filtered.size,
                message = "deleted",
            )
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun create(input: com.leon.be_nobat.domain.model.ManagementCategoryInput) = viewModelScope.launch {
        createUseCase(input)
            .onSuccess { created ->
                val items = listOf(created) + _state.value.items
                val filtered = filterCategories(items, _state.value.selectedTab, _state.value.searchQuery)
                _state.value = _state.value.copy(
                    items = items,
                    filteredItems = filtered,
                    total = filtered.size,
                    message = "created",
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun update(id: String, input: com.leon.be_nobat.domain.model.ManagementCategoryInput) = viewModelScope.launch {
        updateUseCase(id, input)
            .onSuccess { updated ->
                val items = _state.value.items.map { if (it.id == id) updated else it }
                val filtered = filterCategories(items, _state.value.selectedTab, _state.value.searchQuery)
                _state.value = _state.value.copy(
                    items = items,
                    filteredItems = filtered,
                    total = filtered.size,
                    message = "updated",
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
}
