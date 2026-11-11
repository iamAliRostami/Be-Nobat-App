package com.leon.be_nobat.ui.view_models.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.ContactMessageDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class PlatformMessagesUiState(
    val loading: Boolean = false,
    val items: List<ContactMessageDto> = emptyList(),
    val selectedTab: String = "", // "" = all, "unread", "read"
    val searchQuery: String = "",
    val error: String? = null,
    val successMessage: String? = null,
) {
    val allCount: Int get() = items.size
    val unreadCount: Int get() = items.count { !it.isRead }
    val readCount: Int get() = items.count { it.isRead }

    val filteredItems: List<ContactMessageDto>
        get() {
            var list = items
            if (selectedTab.isNotBlank()) {
                list = if (selectedTab.equals("unread", ignoreCase = true)) {
                    list.filter { !it.isRead }
                } else {
                    list.filter { it.isRead }
                }
            }
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                list = list.filter {
                    it.name.lowercase().contains(q) ||
                            it.contactInfo.lowercase().contains(q) ||
                            it.subject.lowercase().contains(q) ||
                            it.message.lowercase().contains(q)
                }
            }
            return list
        }
}

class PlatformMessagesViewModel(
    private val apiClient: BeNobatApiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(PlatformMessagesUiState(loading = true))
    val state: StateFlow<PlatformMessagesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        apiClient.platformContactMessages<List<ContactMessageDto>>()
            .onSuccess { list ->
                _state.value = _state.value.copy(
                    loading = false,
                    items = list,
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "خطا در دریافت پیام‌ها",
                )
            }
    }

    fun setSelectedTab(status: String) {
        _state.value = _state.value.copy(selectedTab = status)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun markAsRead(id: String, isRead: Boolean) = viewModelScope.launch {
        apiClient.platformMarkContactMessageRead<JsonObject>(id, isRead)
            .onSuccess {
                val updated = _state.value.items.map {
                    if (it.id == id) it.copy(isRead = isRead) else it
                }
                _state.value = _state.value.copy(items = updated)
            }
            .onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "خطا در تغییر وضعیت")
            }
    }

    fun markAllAsRead() = viewModelScope.launch {
        apiClient.platformMarkAllContactMessagesRead<JsonObject>()
            .onSuccess {
                val updated = _state.value.items.map { it.copy(isRead = true) }
                _state.value = _state.value.copy(
                    items = updated,
                    successMessage = "تمام پیام‌ها به عنوان بررسی‌شده علامت‌گذاری شدند."
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "خطا در بررسی همه پیام‌ها")
            }
    }

    fun reply(id: String, replyText: String) = viewModelScope.launch {
        apiClient.platformReplyContactMessage<JsonObject>(id, replyText)
            .onSuccess {
                _state.value = _state.value.copy(successMessage = "پاسخ با موفقیت ثبت شد.")
                load()
            }
            .onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "خطا در ثبت پاسخ")
            }
    }

    fun delete(id: String) = viewModelScope.launch {
        apiClient.platformDeleteContactMessage(id)
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
