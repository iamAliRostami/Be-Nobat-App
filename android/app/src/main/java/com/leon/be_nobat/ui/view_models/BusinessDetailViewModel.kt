package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.domain.model.BusinessDetail
import com.leon.be_nobat.domain.repository.BusinessRepository
import com.leon.be_nobat.domain.usecase.GetBusinessUseCase
import com.leon.be_nobat.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BusinessDetailUiState(
    val loading: Boolean = false,
    val business: BusinessDetail? = null,
    val selectedBranchId: String? = null,
    val isFavorite: Boolean = false,
    val error: String? = null,
)

class BusinessDetailViewModel(
    private val getBusiness: GetBusinessUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val businessRepository: BusinessRepository? = null,
) : ViewModel() {

    private var businessId: String = ""
    private var branchId: String? = null

    private val _state = MutableStateFlow(BusinessDetailUiState())
    val state: StateFlow<BusinessDetailUiState> = _state.asStateFlow()

    fun setBusinessId(id: String, initialBranchId: String? = null) {
        branchId = initialBranchId
        if (id.isNotBlank() && id == businessId && _state.value.business != null) {
            _state.value = _state.value.copy(selectedBranchId = initialBranchId)
            return
        }
        businessId = id
        _state.value = _state.value.copy(selectedBranchId = initialBranchId)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, selectedBranchId = branchId)

            var resolvedBusinessId = businessId
            if (resolvedBusinessId.isBlank() && !branchId.isNullOrBlank() && businessRepository != null) {
                resolvedBusinessId = businessRepository.findBusinessIdByBranch(branchId!!) ?: ""
                if (resolvedBusinessId.isNotBlank()) {
                    businessId = resolvedBusinessId
                }
            }

            if (resolvedBusinessId.isEmpty()) {
                _state.value = _state.value.copy(loading = false, error = "business_not_found")
                return@launch
            }

            getBusiness(resolvedBusinessId)
                .onSuccess { detail ->
                    _state.value = _state.value.copy(
                        loading = false,
                        business = detail,
                        selectedBranchId = branchId,
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(loading = false, error = err.message)
                }
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
