package com.leon.be_nobat.ui.view_models.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementCatalog
import com.leon.be_nobat.domain.model.ManagementCatalogInput
import com.leon.be_nobat.domain.usecase.PlatformCreateCatalogUseCase
import com.leon.be_nobat.domain.usecase.PlatformDeleteCatalogUseCase
import com.leon.be_nobat.domain.usecase.PlatformListCatalogUseCase
import com.leon.be_nobat.domain.usecase.PlatformListCategoriesUseCase
import com.leon.be_nobat.domain.usecase.PlatformSetCatalogActiveUseCase
import com.leon.be_nobat.domain.usecase.PlatformUpdateCatalogUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlatformCatalogUiState(
    val loading: Boolean = false,
    val rawItems: List<ManagementCatalog> = emptyList(),
    val items: List<ManagementCatalog> = emptyList(),
    val categories: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: String = "", // "", "published", "hidden", "used", "unused"
    val selectedCategory: String = "",
    val statusFilter: String = "", // "", "published", "hidden"
    val usageFilter: String = "", // "", "used", "unused"
    val total: Int = 0,
    val allCount: Int = 0,
    val publishedCount: Int = 0,
    val hiddenCount: Int = 0,
    val usedCount: Int = 0,
    val unusedCount: Int = 0,
    val error: String? = null,
    val message: String? = null,
) {
    val hasFilters: Boolean
        get() = searchQuery.isNotBlank() || selectedCategory.isNotBlank() ||
                statusFilter.isNotBlank() || usageFilter.isNotBlank() || selectedTab.isNotBlank()

    val availableCategories: List<String>
        get() = (categories + rawItems.map { it.category })
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
}

class PlatformCatalogViewModel(
    private val list: PlatformListCatalogUseCase,
    private val createUseCase: PlatformCreateCatalogUseCase,
    private val updateUseCase: PlatformUpdateCatalogUseCase,
    private val deleteUseCase: PlatformDeleteCatalogUseCase,
    private val setActiveUseCase: PlatformSetCatalogActiveUseCase,
    private val listCategoriesUseCase: PlatformListCategoriesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlatformCatalogUiState(loading = true))
    val state: StateFlow<PlatformCatalogUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        launch {
            listCategoriesUseCase(kind = "Service", page = 1, pageSize = 200)
                .onSuccess { page ->
                    val catNames = page.items.filter { it.active }.map { it.name }.distinct()
                    _state.value = _state.value.copy(categories = catNames)
                }
        }
        list(includeInactive = true, pageSize = 200)
            .onSuccess { page: ApiPage<ManagementCatalog> ->
                _state.value = applyFilter(_state.value.copy(loading = false, rawItems = page.items))
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
    }

    fun setSearchQuery(query: String) {
        _state.value = applyFilter(_state.value.copy(searchQuery = query))
    }

    fun setSelectedTab(tab: String) {
        _state.value = applyFilter(_state.value.copy(selectedTab = tab))
    }

    fun setFilters(category: String, status: String, usage: String) {
        _state.value = applyFilter(_state.value.copy(
            selectedCategory = category,
            statusFilter = status,
            usageFilter = usage
        ))
    }

    fun resetFilters() {
        _state.value = applyFilter(_state.value.copy(
            searchQuery = "",
            selectedTab = "",
            selectedCategory = "",
            statusFilter = "",
            usageFilter = ""
        ))
    }

    fun togglePublished(item: ManagementCatalog) = viewModelScope.launch {
        val targetActive = !item.isPublished
        setActiveUseCase(item.id, targetActive)
            .onSuccess { updated ->
                val newRaw = _state.value.rawItems.map { if (it.id == item.id) updated else it }
                _state.value = applyFilter(_state.value.copy(rawItems = newRaw, message = "updated"))
            }
            .onFailure { e ->
                _state.value = _state.value.copy(error = e.message)
            }
    }

    fun delete(id: String) = viewModelScope.launch {
        deleteUseCase(id).onSuccess {
            val newRaw = _state.value.rawItems.filterNot { it.id == id }
            _state.value = applyFilter(_state.value.copy(rawItems = newRaw, message = "deleted"))
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun create(input: ManagementCatalogInput) = viewModelScope.launch {
        createUseCase(input)
            .onSuccess { created ->
                val newRaw = listOf(created) + _state.value.rawItems
                _state.value = applyFilter(_state.value.copy(rawItems = newRaw, message = "created"))
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun update(id: String, input: ManagementCatalogInput) = viewModelScope.launch {
        updateUseCase(id, input)
            .onSuccess { updated ->
                val newRaw = _state.value.rawItems.map { if (it.id == id) updated else it }
                _state.value = applyFilter(_state.value.copy(rawItems = newRaw, message = "updated"))
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    private fun applyFilter(currentState: PlatformCatalogUiState): PlatformCatalogUiState {
        val query = currentState.searchQuery.trim()
        val filtered = currentState.rawItems.filter { item ->
            // Tab filter
            val tabMatch = when (currentState.selectedTab) {
                "published" -> item.isPublished
                "hidden" -> !item.isPublished
                "used" -> item.usageCount > 0
                "unused" -> item.usageCount == 0
                else -> true
            }
            if (!tabMatch) return@filter false

            // Status filter (from dialog)
            if (currentState.statusFilter == "published" && !item.isPublished) return@filter false
            if (currentState.statusFilter == "hidden" && item.isPublished) return@filter false

            // Usage filter (from dialog)
            if (currentState.usageFilter == "used" && item.usageCount == 0) return@filter false
            if (currentState.usageFilter == "unused" && item.usageCount > 0) return@filter false

            // Category filter
            if (currentState.selectedCategory.isNotBlank() && !item.category.equals(currentState.selectedCategory, ignoreCase = true)) {
                return@filter false
            }

            // Search query (name, category, slug, description)
            if (query.isNotBlank()) {
                val matches = item.name.contains(query, ignoreCase = true) ||
                        item.slug.contains(query, ignoreCase = true) ||
                        item.category.contains(query, ignoreCase = true) ||
                        item.description.contains(query, ignoreCase = true)
                if (!matches) return@filter false
            }

            true
        }

        return currentState.copy(
            items = filtered,
            total = currentState.rawItems.size,
            allCount = currentState.rawItems.size,
            publishedCount = currentState.rawItems.count { it.isPublished },
            hiddenCount = currentState.rawItems.count { !it.isPublished },
            usedCount = currentState.rawItems.count { it.usageCount > 0 },
            unusedCount = currentState.rawItems.count { it.usageCount == 0 },
        )
    }
}
