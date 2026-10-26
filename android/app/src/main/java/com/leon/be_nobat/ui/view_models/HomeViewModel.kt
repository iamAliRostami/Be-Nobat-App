package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.model.Category
import com.leon.be_nobat.domain.usecase.GetCategoriesUseCase
import com.leon.be_nobat.domain.usecase.ListBusinessesUseCase
import com.leon.be_nobat.domain.usecase.ListFavoritesUseCase
import com.leon.be_nobat.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BusinessSortOption {
    DEFAULT,
    RATING,
    BRANCHES,
    SERVICES,
    NAME
}

data class HomeUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val rawItems: List<Business> = emptyList(),
    val items: List<Business> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val query: String = "",
    val city: String = "",
    val category: String = "",
    val categories: List<Category> = emptyList(),
    val sortOption: BusinessSortOption = BusinessSortOption.DEFAULT,
    val filterInstantOnly: Boolean = false,
    val error: String? = null,
    val favoriteSnapshot: Map<String, Boolean> = emptyMap(),
)

class HomeViewModel(
    private val listBusinesses: ListBusinessesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val listFavorites: ListFavoritesUseCase? = null,
    private val getCategoriesUseCase: GetCategoriesUseCase? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadCategories()
        refresh(initial = true)
    }

    fun loadCategories() {
        val useCase = getCategoriesUseCase ?: return
        viewModelScope.launch {
            useCase.invoke().onSuccess { result ->
                val list = result.items.filter { it.kind.equals("Business", ignoreCase = true) }
                    .sortedBy { it.sortOrder }
                _state.value = _state.value.copy(
                    categories = if (list.isNotEmpty()) list else result.items
                )
            }
        }
    }

    fun refresh(initial: Boolean) {
        loadCategories()
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = initial, refreshing = !initial, error = null)
            val s = _state.value
            var favMap = s.favoriteSnapshot
            listFavorites?.invoke()?.onSuccess { favPage ->
                favMap = favPage.items.associate { it.id to true }
            }
            _state.value = _state.value.copy(favoriteSnapshot = favMap)
            listBusinesses(s.query.ifBlank { null }, s.city.ifBlank { null }, s.category.ifBlank { null }, page = 1)
                .onSuccess { page: ApiPage<Business> ->
                    val filtered = applyFilterAndSort(page.items, s.sortOption, s.filterInstantOnly, favMap)
                    _state.value = _state.value.copy(
                        loading = false, refreshing = false,
                        rawItems = page.items,
                        items = filtered,
                        favoriteSnapshot = favMap,
                        page = 1, total = page.total,
                        hasMore = page.items.size < page.total,
                        error = null,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, refreshing = false, error = e.message)
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || !s.hasMore) return
        viewModelScope.launch {
            _state.value = s.copy(loading = true)
            listBusinesses(s.query.ifBlank { null }, s.city.ifBlank { null }, s.category.ifBlank { null }, page = s.page + 1)
                .onSuccess { page ->
                    val newRaw = s.rawItems + page.items
                    val filtered = applyFilterAndSort(newRaw, s.sortOption, s.filterInstantOnly, s.favoriteSnapshot)
                    _state.value = _state.value.copy(
                        loading = false,
                        rawItems = newRaw,
                        items = filtered,
                        page = s.page + 1,
                        hasMore = newRaw.size < page.total,
                    )
                }
                .onFailure { e ->
                    _state.value = s.copy(loading = false, error = e.message)
                }
        }
    }

    fun updateQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun updateCity(city: String) {
        _state.value = _state.value.copy(city = city)
    }

    fun updateCategory(cat: String) {
        _state.value = _state.value.copy(category = cat)
    }

    fun setSortAndFilter(sortOption: BusinessSortOption, instantOnly: Boolean) {
        val s = _state.value
        val filtered = applyFilterAndSort(s.rawItems, sortOption, instantOnly, s.favoriteSnapshot)
        _state.value = s.copy(sortOption = sortOption, filterInstantOnly = instantOnly, items = filtered)
    }

    private fun applyFilterAndSort(
        source: List<Business>,
        sortOption: BusinessSortOption,
        instantOnly: Boolean,
        favorites: Map<String, Boolean> = _state.value.favoriteSnapshot,
    ): List<Business> {
        var result = if (instantOnly) source.filter { !it.requiresApproval } else source
        result = when (sortOption) {
            BusinessSortOption.DEFAULT -> result
            BusinessSortOption.RATING -> result.sortedByDescending { it.rating ?: 0.0 }
            BusinessSortOption.BRANCHES -> result.sortedByDescending { it.branchCount }
            BusinessSortOption.SERVICES -> result.sortedByDescending { it.serviceCount }
            BusinessSortOption.NAME -> result.sortedBy { it.name }
        }
        if (favorites.isNotEmpty()) {
            result = result.sortedByDescending { favorites[it.id] == true }
        }
        return result
    }

    fun toggleFavoriteFor(businessId: String, isFav: Boolean) {
        viewModelScope.launch {
            toggleFavorite(businessId, isFav).onSuccess {
                val snap = _state.value.favoriteSnapshot.toMutableMap()
                val newFav = !isFav
                if (newFav) snap[businessId] = true else snap.remove(businessId)
                val reordered = applyFilterAndSort(_state.value.rawItems, _state.value.sortOption, _state.value.filterInstantOnly, snap)
                _state.value = _state.value.copy(favoriteSnapshot = snap, items = reordered)
            }
        }
    }

    fun refreshFavoritesOnly() {
        val useCase = listFavorites ?: return
        viewModelScope.launch {
            useCase.invoke().onSuccess { favPage ->
                val favMap = favPage.items.associate { it.id to true }
                if (favMap != _state.value.favoriteSnapshot) {
                    val reordered = applyFilterAndSort(_state.value.rawItems, _state.value.sortOption, _state.value.filterInstantOnly, favMap)
                    _state.value = _state.value.copy(favoriteSnapshot = favMap, items = reordered)
                }
            }
        }
    }
}
