package com.leon.be_nobat.ui.view_models.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.BusinessRequest
import com.leon.be_nobat.domain.usecase.PlatformApproveBusinessRequestUseCase
import com.leon.be_nobat.domain.usecase.PlatformListBusinessRequestsUseCase
import com.leon.be_nobat.domain.usecase.PlatformRejectBusinessRequestUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlatformRequestsUiState(
    val loading: Boolean = false,
    val items: List<BusinessRequest> = emptyList(),
    val total: Int = 0,
    val selectedTab: String = "",
    val searchQuery: String = "",
    val error: String? = null,
    val message: String? = null,
) {
    val filteredItems: List<BusinessRequest>
        get() {
            var list = items
            if (selectedTab.isNotBlank()) {
                list = list.filter { it.status.equals(selectedTab, ignoreCase = true) }
            }
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                list = list.filter {
                    it.businessName.lowercase().contains(q) ||
                            it.city.lowercase().contains(q) ||
                            it.category.lowercase().contains(q) ||
                            it.contactPhone.lowercase().contains(q) ||
                            (it.userDisplayName?.lowercase()?.contains(q) == true) ||
                            (it.userPhoneNumber?.lowercase()?.contains(q) == true)
                }
            }
            return list
        }
}

class PlatformRequestsViewModel(
    private val listUseCase: PlatformListBusinessRequestsUseCase,
    private val approveUseCase: PlatformApproveBusinessRequestUseCase,
    private val rejectUseCase: PlatformRejectBusinessRequestUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlatformRequestsUiState(loading = true))
    val state: StateFlow<PlatformRequestsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        listUseCase(status = s.selectedTab.ifBlank { null }, q = s.searchQuery.ifBlank { null })
            .onSuccess { page: ApiPage<BusinessRequest> ->
                _state.value = _state.value.copy(
                    loading = false,
                    items = page.items,
                    total = page.total,
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
    }

    fun setSelectedTab(status: String) {
        _state.value = _state.value.copy(selectedTab = status)
        load()
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(searchQuery = q)
    }

    fun approve(id: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null, message = null)
        approveUseCase(id)
            .onSuccess {
                load()
                _state.value = _state.value.copy(message = "approved")
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
    }

    fun reject(id: String, note: String?) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null, message = null)
        rejectUseCase(id, note)
            .onSuccess {
                load()
                _state.value = _state.value.copy(message = "rejected")
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
