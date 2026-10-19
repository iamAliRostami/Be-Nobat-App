package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.model.BookingInput
import com.leon.be_nobat.domain.model.Branch
import com.leon.be_nobat.domain.model.Provider
import com.leon.be_nobat.domain.model.Service
import com.leon.be_nobat.domain.model.Slot
import com.leon.be_nobat.domain.model.SlotsResponse
import com.leon.be_nobat.domain.usecase.CreateAppointmentUseCase
import com.leon.be_nobat.domain.usecase.GetSlotsUseCase
import com.leon.be_nobat.domain.usecase.ListBranchesUseCase
import com.leon.be_nobat.domain.usecase.ListBranchServicesUseCase
import com.leon.be_nobat.domain.usecase.ListProvidersUseCase
import com.leon.be_nobat.helpers.JalaliDateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BookingUiState(
    val currentStep: Int = 1,
    val businessId: String = "",
    val branchId: String = "",
    val branches: List<Branch> = emptyList(),
    val services: List<Service> = emptyList(),
    val selectedServices: List<Service> = emptyList(),
    val providers: List<Provider> = emptyList(),
    val selectedProviderId: String? = null,
    val date: String = "",
    val slots: SlotsResponse? = null,
    val selectedSlot: Slot? = null,
    val phoneNumber: String = "",
    val note: String = "",
    val termsAccepted: Boolean = false,
    val loadingBranches: Boolean = false,
    val loadingServices: Boolean = false,
    val loadingProviders: Boolean = false,
    val loadingSlots: Boolean = false,
    val booking: Boolean = false,
    val bookedTrackingCode: String? = null,
    val bookedAppointment: Appointment? = null,
    val error: String? = null,
) {
    val selectedServiceIds: List<String> get() = selectedServices.map { it.id }
    val totalPrice: Double get() = slots?.totalPrice ?: selectedServices.sumOf { it.price }
    val totalDuration: Int get() = slots?.totalDurationMinutes ?: selectedServices.sumOf { it.durationMinutes }
}

class BookingViewModel(
    private val listBranches: ListBranchesUseCase,
    private val listBranchServices: ListBranchServicesUseCase,
    private val listProviders: ListProvidersUseCase,
    private val getSlots: GetSlotsUseCase,
    private val createAppointment: CreateAppointmentUseCase,
) : ViewModel() {

    private var businessId: String = ""

    private val _state = MutableStateFlow(BookingUiState(date = JalaliDateHelper.getTodayIso()))
    val state: StateFlow<BookingUiState> = _state.asStateFlow()

    fun setBusinessId(id: String) {
        if (id == businessId && _state.value.branches.isNotEmpty()) return
        businessId = id
        _state.value = _state.value.copy(businessId = id, date = JalaliDateHelper.getTodayIso())
        loadBranches()
    }

    private fun loadBranches() = viewModelScope.launch {
        _state.value = _state.value.copy(loadingBranches = true)
        listBranches(businessId)
            .onSuccess { res ->
                val list = res.items
                val first = list.firstOrNull()
                _state.value = _state.value.copy(branches = list, branchId = first?.id ?: "", loadingBranches = false)
                if (first != null) loadServices(first.id)
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loadingBranches = false, error = e.message)
            }
    }

    fun selectBranch(id: String) {
        if (id == _state.value.branchId && _state.value.services.isNotEmpty()) return
        _state.value = _state.value.copy(
            branchId = id,
            selectedServices = emptyList(),
            providers = emptyList(),
            selectedProviderId = null,
            slots = null,
            selectedSlot = null
        )
        loadServices(id)
    }

    private fun loadServices(branchId: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loadingServices = true)
        listBranchServices(businessId, branchId)
            .onSuccess { res ->
                val services = res.items
                val first = services.firstOrNull()
                val selected = if (first != null && _state.value.selectedServices.isEmpty()) listOf(first) else _state.value.selectedServices
                _state.value = _state.value.copy(
                    services = services,
                    selectedServices = selected,
                    loadingServices = false
                )
                if (selected.isNotEmpty()) {
                    loadProviders()
                }
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loadingServices = false, error = e.message)
            }
    }

    fun toggleService(service: Service) {
        val current = _state.value.selectedServices.toMutableList()
        if (current.any { it.id == service.id }) current.removeAll { it.id == service.id }
        else current.add(service)
        _state.value = _state.value.copy(
            selectedServices = current,
            providers = emptyList(),
            selectedProviderId = null,
            slots = null,
            selectedSlot = null,
        )
        if (current.isNotEmpty()) {
            loadProviders()
        }
    }

    private fun loadProviders() = viewModelScope.launch {
        val s = _state.value
        if (s.selectedServiceIds.isEmpty()) return@launch
        _state.value = s.copy(loadingProviders = true)
        listProviders(businessId, s.branchId, s.selectedServiceIds.joinToString(","))
            .onSuccess { res ->
                val providers = res.items
                val autoSelectedProvider = if (providers.size == 1) providers[0].id
                else if (providers.any { it.id == s.selectedProviderId }) s.selectedProviderId
                else null
                _state.value = _state.value.copy(
                    providers = providers,
                    selectedProviderId = autoSelectedProvider,
                    loadingProviders = false
                )
                if (_state.value.date.isNotBlank()) {
                    loadSlots()
                }
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loadingProviders = false, error = e.message)
            }
    }

    fun selectProvider(id: String?) {
        _state.value = _state.value.copy(selectedProviderId = id, slots = null, selectedSlot = null)
        if (_state.value.date.isNotBlank() && _state.value.selectedServices.isNotEmpty()) {
            loadSlots()
        }
    }

    fun setDate(date: String) {
        _state.value = _state.value.copy(date = date, slots = null, selectedSlot = null)
        if (_state.value.selectedServices.isNotEmpty()) {
            loadSlots()
        }
    }

    fun loadSlots() = viewModelScope.launch {
        val s = _state.value
        if (s.date.isBlank() || s.selectedServiceIds.isEmpty()) return@launch
        _state.value = s.copy(loadingSlots = true)
        getSlots(s.businessId, s.branchId, s.selectedServiceIds.joinToString(","), s.date, s.selectedProviderId)
            .onSuccess { resp ->
                _state.value = _state.value.copy(slots = resp, loadingSlots = false)
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loadingSlots = false, error = e.message)
            }
    }

    fun selectSlot(slot: Slot) {
        _state.value = _state.value.copy(selectedSlot = slot)
    }

    fun setStep(step: Int) {
        _state.value = _state.value.copy(currentStep = step)
    }

    fun setPhone(v: String) { _state.value = _state.value.copy(phoneNumber = v) }
    fun setNote(v: String) { _state.value = _state.value.copy(note = v) }
    fun setTerms(v: Boolean) { _state.value = _state.value.copy(termsAccepted = v) }

    fun book() = viewModelScope.launch {
        val s = _state.value
        val slot = s.selectedSlot ?: return@launch
        if (!s.termsAccepted) {
            _state.value = s.copy(error = "terms_required")
            return@launch
        }
        _state.value = s.copy(booking = true, error = null)
        val input = BookingInput(
            businessId = s.businessId,
            branchId = s.branchId,
            serviceIds = s.selectedServiceIds,
            resourceId = s.selectedProviderId ?: slot.resourceId,
            startsAt = slot.startsAt,
            phoneNumber = s.phoneNumber.ifBlank { null },
            customerNote = s.note.ifBlank { null },
            termsAccepted = s.termsAccepted,
            expectedPrice = s.slots?.totalPrice,
            expectedDurationMinutes = s.slots?.totalDurationMinutes,
        )
        createAppointment(input)
            .onSuccess { appt ->
                _state.value = _state.value.copy(
                    booking = false,
                    bookedTrackingCode = appt.trackingCode,
                    bookedAppointment = appt
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(booking = false, error = e.message) }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
