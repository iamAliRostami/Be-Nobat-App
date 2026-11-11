package com.leon.be_nobat.ui.view_models.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.SystemMessageDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class AdminSystemMessagesUiState(
    val loading: Boolean = false,
    val messages: List<SystemMessageDto> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
) {
    val unreadCount: Int get() = messages.count { !it.isRead }
}

class AdminSystemMessagesViewModel(
    private val apiClient: BeNobatApiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminSystemMessagesUiState(loading = true))
    val state: StateFlow<AdminSystemMessagesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        apiClient.adminSystemMessages<List<SystemMessageDto>>()
            .onSuccess { list ->
                _state.value = _state.value.copy(
                    loading = false,
                    messages = list,
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "خطا در دریافت پیام‌ها",
                )
            }
    }

    fun markAsRead(id: String) = viewModelScope.launch {
        apiClient.adminMarkSystemMessageRead<JsonObject>(id)
            .onSuccess {
                val updated = _state.value.messages.map { msg ->
                    if (msg.id == id) msg.copy(isRead = true) else msg
                }
                _state.value = _state.value.copy(messages = updated)
            }
            .onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "خطا در ثبت وضعیت پیام")
            }
    }

    fun markAllAsRead() = viewModelScope.launch {
        apiClient.adminMarkAllSystemMessagesRead<JsonObject>()
            .onSuccess {
                val updated = _state.value.messages.map { it.copy(isRead = true) }
                _state.value = _state.value.copy(
                    messages = updated,
                    successMessage = "تمام پیام‌ها خوانده شدند."
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "خطا در ثبت وضعیت پیام‌ها")
            }
    }
}
