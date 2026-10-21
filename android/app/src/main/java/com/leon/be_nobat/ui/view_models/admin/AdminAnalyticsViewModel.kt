package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.domain.model.ManagementAnalyticsSummary
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementResource
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.domain.usecase.AdminAnalyticsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class AdminAnalyticsUiState(
    val loading: Boolean = false,
    val analytics: ManagementAnalyticsSummary? = null,
    val error: String? = null,
    val businesses: List<ManagementBusiness> = emptyList(),
    val branches: List<ManagementBranch> = emptyList(),
    val resources: List<ManagementResource> = emptyList(),
    val selectedBusinessId: String? = null,
    val selectedBranchId: String? = null,
    val selectedResourceId: String? = null,
    val selectedPeriod: String = "30d", // "7d", "30d", "90d", "all"
    val activeTab: String = "businesses", // "businesses", "branches", "staff"
)

class AdminAnalyticsViewModel(
    private val analyticsUseCase: AdminAnalyticsUseCase,
    private val management: ManagementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminAnalyticsUiState(loading = true))
    val state: StateFlow<AdminAnalyticsUiState> = _state.asStateFlow()

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val bizRes = management.listBusinesses(page = 1, pageSize = 100)
            val brRes = management.listBranches(page = 1, pageSize = 100)
            val resRes = management.listResources(page = 1, pageSize = 100)

            _state.update {
                it.copy(
                    businesses = bizRes.getOrNull()?.items.orEmpty(),
                    branches = brRes.getOrNull()?.items.orEmpty(),
                    resources = resRes.getOrNull()?.items.orEmpty(),
                )
            }
            fetchAnalytics()
        }
    }

    fun load() {
        viewModelScope.launch {
            fetchAnalytics()
        }
    }

    private suspend fun fetchAnalytics() {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }

        val (fromDate, toDate) = calculateDateRange(s.selectedPeriod)

        analyticsUseCase(
            businessId = s.selectedBusinessId,
            branchId = s.selectedBranchId,
            resourceId = s.selectedResourceId,
            from = fromDate,
            to = toDate,
        ).onSuccess { summary ->
            _state.update { it.copy(loading = false, analytics = summary, error = null) }
        }.onFailure { err ->
            _state.update { it.copy(loading = false, error = err.message) }
        }
    }

    fun setBusinessFilter(id: String?) {
        _state.update { it.copy(selectedBusinessId = id, selectedBranchId = null, selectedResourceId = null) }
        viewModelScope.launch { fetchAnalytics() }
    }

    fun setBranchFilter(id: String?) {
        _state.update { it.copy(selectedBranchId = id, selectedResourceId = null) }
        viewModelScope.launch { fetchAnalytics() }
    }

    fun setResourceFilter(id: String?) {
        _state.update { it.copy(selectedResourceId = id) }
        viewModelScope.launch { fetchAnalytics() }
    }

    fun setPeriod(period: String) {
        if (_state.value.selectedPeriod == period) return
        _state.update { it.copy(selectedPeriod = period) }
        viewModelScope.launch { fetchAnalytics() }
    }

    fun setActiveTab(tab: String) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun clearFilters() {
        _state.update {
            it.copy(
                selectedBusinessId = null,
                selectedBranchId = null,
                selectedResourceId = null,
                selectedPeriod = "30d",
            )
        }
        viewModelScope.launch { fetchAnalytics() }
    }

    private fun calculateDateRange(period: String): Pair<String?, String?> {
        if (period == "all") return null to null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val toStr = sdf.format(cal.time)
        when (period) {
            "7d" -> cal.add(Calendar.DAY_OF_YEAR, -7)
            "30d" -> cal.add(Calendar.DAY_OF_YEAR, -30)
            "90d" -> cal.add(Calendar.DAY_OF_YEAR, -90)
            else -> cal.add(Calendar.DAY_OF_YEAR, -30)
        }
        val fromStr = sdf.format(cal.time)
        return fromStr to toStr
    }
}
