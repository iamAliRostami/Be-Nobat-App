package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.domain.usecase.AdminListAppointmentsUseCase
import com.leon.be_nobat.domain.usecase.AdminSetAppointmentStatusUseCase
import com.leon.be_nobat.helpers.JalaliDateHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AdminCalendarUiState(
    val loading: Boolean = false,
    val items: List<ManagementAppointment> = emptyList(),
    val total: Int = 0,
    val statusFilter: String? = null,
    val dateFilter: String = JalaliDateHelper.getTodayIso(),
    val allDates: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null,
)

class AdminCalendarViewModel(
    private val listAppointments: AdminListAppointmentsUseCase,
    private val setStatus: AdminSetAppointmentStatusUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminCalendarUiState(loading = true))
    val state: StateFlow<AdminCalendarUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var isInitialized = false

    fun initFilters(initialStatus: String?, initialDate: String?) {
        val resolvedDate = if (!initialDate.isNullOrBlank()) initialDate else JalaliDateHelper.getTodayIso()
        val resolvedStatus = initialStatus?.ifBlank { null }
        _state.value = _state.value.copy(
            statusFilter = resolvedStatus,
            dateFilter = resolvedDate,
            allDates = false
        )
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(loading = true, error = null)
            val queryDate = if (s.allDates) null else s.dateFilter.ifBlank { null }
            listAppointments(
                date = queryDate,
                status = s.statusFilter,
            ).onSuccess { page: ApiPage<ManagementAppointment> ->
                _state.value = _state.value.copy(loading = false, items = page.items, total = page.total, error = null)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException || e.message?.contains("cancel", ignoreCase = true) == true) {
                    return@launch
                }
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun setStatusFilter(status: String?) {
        if (_state.value.statusFilter == status) return
        _state.value = _state.value.copy(statusFilter = status)
        load()
    }

    fun setDateFilter(date: String) {
        _state.value = _state.value.copy(dateFilter = date, allDates = false)
        load()
    }

    fun setAllDates(all: Boolean) {
        if (_state.value.allDates == all) return
        _state.value = _state.value.copy(allDates = all)
        load()
    }

    fun changeDay(offsetDays: Int) {
        if (offsetDays == 0) {
            _state.value = _state.value.copy(dateFilter = JalaliDateHelper.getTodayIso(), allDates = false)
        } else {
            val currentIso = _state.value.dateFilter.ifBlank { JalaliDateHelper.getTodayIso() }
            val cal = Calendar.getInstance()
            try {
                val parts = currentIso.split("-").mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) {
                    cal.set(parts[0], parts[1] - 1, parts[2])
                }
            } catch (_: Exception) {}
            cal.add(Calendar.DAY_OF_YEAR, offsetDays)
            val newIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            _state.value = _state.value.copy(dateFilter = newIso, allDates = false)
        }
        load()
    }

    fun setSearchQuery(q: String) { _state.value = _state.value.copy(searchQuery = q) }

    fun changeStatus(id: String, newStatus: String) = viewModelScope.launch {
        setStatus(id, newStatus).onSuccess { updated ->
            val currentFilter = _state.value.statusFilter
            if (!currentFilter.isNullOrBlank() && !currentFilter.equals(newStatus, ignoreCase = true)) {
                _state.value = _state.value.copy(items = _state.value.items.filterNot { it.id == id })
            }
            load()
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
}
