package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.domain.model.Branch
import com.leon.be_nobat.domain.model.BusinessDetail
import com.leon.be_nobat.domain.model.Service
import com.leon.be_nobat.domain.repository.BusinessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BranchDetailUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val business: BusinessDetail? = null,
    val branch: Branch? = null,
    val services: List<Service> = emptyList(),
)

class BranchDetailViewModel(
    private val businessRepository: BusinessRepository,
) : ViewModel() {

    private var branchId: String = ""
    private var businessId: String? = null

    private val _state = MutableStateFlow(BranchDetailUiState())
    val state: StateFlow<BranchDetailUiState> = _state.asStateFlow()

    fun setParams(targetBranchId: String, targetBusinessId: String? = null) {
        branchId = targetBranchId
        businessId = targetBusinessId?.takeIf { it.isNotBlank() }
        load()
    }

    fun load() {
        if (branchId.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            var resolvedBusinessId = businessId
            if (resolvedBusinessId.isNullOrBlank()) {
                resolvedBusinessId = businessRepository.findBusinessIdByBranch(branchId)
                if (!resolvedBusinessId.isNullOrBlank()) {
                    businessId = resolvedBusinessId
                }
            }

            if (resolvedBusinessId.isNullOrBlank()) {
                _state.value = _state.value.copy(loading = false, error = "branch_not_found")
                return@launch
            }

            businessRepository.getBusiness(resolvedBusinessId)
                .onSuccess { biz ->
                    val branch = biz.branches.firstOrNull { it.id == branchId }
                    if (branch == null) {
                        _state.value = _state.value.copy(loading = false, error = "branch_not_found")
                        return@launch
                    }

                    val branchServicesRes = businessRepository.listBranchServices(resolvedBusinessId, branchId)
                    val effectiveServices = branchServicesRes.getOrNull()?.items?.takeIf { it.isNotEmpty() }
                        ?: biz.services

                    _state.value = BranchDetailUiState(
                        loading = false,
                        business = biz,
                        branch = branch,
                        services = effectiveServices,
                        error = null
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(loading = false, error = err.message)
                }
        }
    }
}
