package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.domain.model.BusinessDetail
import com.leon.be_nobat.domain.usecase.GetBusinessUseCase
import com.leon.be_nobat.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BusinessDetailUiState(
    val loading: Boolean = false,
    val business: BusinessDetail? = null,
    val isFavorite: Boolean = false,
    val error: String? = null,
)

class BusinessDetailViewModel(
    private val getBusiness: GetBusinessUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private var businessId: String = ""

    private val _state = MutableStateFlow(BusinessDetailUiState())
    val state: StateFlow<BusinessDetailUiState> = _state.asStateFlow()

    fun setBusinessId(id: String) {
        if (id == businessId && _state.value.business != null) return
        businessId = id
        load()
    }

    fun load() {
        if (businessId.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            getBusiness(businessId)
                .onSuccess { detail -> _state.value = _state.value.copy(loading = false, business = detail) }
                .onFailure { err -> _state.value = _state.value.copy(loading = false, error = err.message) }
        }
    }

    fun toggleFavorite() {
        val current = _state.value.isFavorite
        if (businessId.isEmpty()) return
        viewModelScope.launch {
            toggleFavorite(businessId, current).onSuccess {
                _state.value = _state.value.copy(isFavorite = !current)
            }
        }
    }
}
