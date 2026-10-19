package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.usecase.ListFavoritesUseCase
import com.leon.be_nobat.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val loading: Boolean = false,
    val items: List<Business> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
)

class FavoritesViewModel(
    private val listFavorites: ListFavoritesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState(loading = true))
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        listFavorites()
            .onSuccess { page: ApiPage<Business> ->
                _state.value = _state.value.copy(loading = false, items = page.items, total = page.total)
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
    }

    fun remove(businessId: String) = viewModelScope.launch {
        toggleFavorite(businessId, isCurrentlyFavorite = true).onSuccess {
            _state.value = _state.value.copy(items = _state.value.items.filterNot { it.id == businessId })
        }
    }
}
