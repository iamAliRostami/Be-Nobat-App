package com.leon.be_nobat.ui.view_models.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.ManagementUser
import com.leon.be_nobat.domain.model.PlatformAnnouncementDto
import com.leon.be_nobat.domain.usecase.PlatformListUsersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class PlatformAnnouncementsUiState(
    val loading: Boolean = false,
    val isSending: Boolean = false,
    val items: List<PlatformAnnouncementDto> = emptyList(),
    val users: List<ManagementUser> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
)

class PlatformAnnouncementsViewModel(
    private val apiClient: BeNobatApiClient,
    private val listUsersUseCase: PlatformListUsersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlatformAnnouncementsUiState(loading = true))
    val state: StateFlow<PlatformAnnouncementsUiState> = _state.asStateFlow()

    init {
        load()
        loadUsers()
    }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        apiClient.platformAnnouncements<List<PlatformAnnouncementDto>>()
            .onSuccess { list ->
                _state.value = _state.value.copy(
                    loading = false,
                    items = list,
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "خطا در دریافت اعلانات",
                )
            }
    }

    fun loadUsers() = viewModelScope.launch {
        listUsersUseCase()
            .onSuccess { page ->
                _state.value = _state.value.copy(users = page.items)
            }
    }

    fun sendAnnouncement(title: String, content: String, priority: String, targetUserId: String?) = viewModelScope.launch {
        if (title.isBlank()) {
            _state.value = _state.value.copy(error = "عنوان پیام الزامی است.")
            return@launch
        }
        if (content.isBlank()) {
            _state.value = _state.value.copy(error = "متن پیام الزامی است.")
            return@launch
        }

        _state.value = _state.value.copy(isSending = true, error = null, successMessage = null)
        apiClient.platformCreateAnnouncement<JsonObject>(title, content, priority, targetUserId)
            .onSuccess {
                _state.value = _state.value.copy(
                    isSending = false,
                    successMessage = "پیام با موفقیت ارسال گردید.",
                )
                load()
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    isSending = false,
                    error = e.message ?: "خطا در ارسال پیام",
                )
            }
    }

    fun delete(id: String) = viewModelScope.launch {
        apiClient.platformDeleteAnnouncement(id)
            .onSuccess {
                val updated = _state.value.items.filterNot { it.id == id }
                _state.value = _state.value.copy(
                    items = updated,
                    successMessage = "پیام حذف شد.",
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "خطا در حذف پیام")
            }
    }
}
