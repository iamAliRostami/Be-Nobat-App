package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.domain.model.User
import com.leon.be_nobat.domain.usecase.ChangeEmailUseCase
import com.leon.be_nobat.domain.usecase.ChangePasswordUseCase
import com.leon.be_nobat.domain.usecase.DeleteAvatarUseCase
import com.leon.be_nobat.domain.usecase.GetMeUseCase
import com.leon.be_nobat.domain.usecase.LogoutUseCase
import com.leon.be_nobat.domain.usecase.UpdateProfileUseCase
import com.leon.be_nobat.domain.usecase.UploadAvatarUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.leon.be_nobat.domain.model.BusinessRequest
import com.leon.be_nobat.domain.model.BusinessRequestInput
import com.leon.be_nobat.domain.model.PlatformPricing
import com.leon.be_nobat.domain.model.ReceiptUploadInput
import com.leon.be_nobat.domain.usecase.GetPlatformPricingUseCase
import com.leon.be_nobat.domain.usecase.UploadReceiptUseCase
import com.leon.be_nobat.domain.usecase.GetCategoriesUseCase
import com.leon.be_nobat.domain.usecase.GetMyBusinessRequestUseCase
import com.leon.be_nobat.domain.usecase.SubmitBusinessRequestUseCase
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = false,
    val user: User? = null,
    val displayName: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val businessRequest: BusinessRequest? = null,
    val loadingBusinessRequest: Boolean = false,
    val submittingBusinessRequest: Boolean = false,
    val uploadingReceipt: Boolean = false,
    val pricing: PlatformPricing = PlatformPricing(),
    val categories: List<String> = emptyList(),
    val loadingCategories: Boolean = false,
    val savingProfile: Boolean = false,
    val changingPassword: Boolean = false,
    val changingEmail: Boolean = false,
    val uploadingAvatar: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class ProfileViewModel(
    private val getMeUseCase: GetMeUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val changeEmailUseCase: ChangeEmailUseCase,
    private val uploadAvatarUseCase: UploadAvatarUseCase,
    private val deleteAvatarUseCase: DeleteAvatarUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val getMyBusinessRequestUseCase: GetMyBusinessRequestUseCase,
    private val submitBusinessRequestUseCase: SubmitBusinessRequestUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getPlatformPricingUseCase: GetPlatformPricingUseCase,
    private val uploadReceiptUseCase: UploadReceiptUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(loading = true))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        getMeUseCase()
            .onSuccess { user ->
                _state.value = _state.value.copy(
                    loading = false, user = user,
                    displayName = user.displayName,
                    phoneNumber = user.phoneNumber ?: "",
                    email = user.email ?: "",
                    avatarUrl = user.avatarUrl,
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }

        loadBusinessRequest()
        loadPricing()
        loadCategories()
    }

    fun loadCategories() = viewModelScope.launch {
        _state.value = _state.value.copy(loadingCategories = true)
        getCategoriesUseCase()
            .onSuccess { apiItems ->
                val businessCategories = apiItems.items
                    .filter { it.kind.equals("Business", ignoreCase = true) }
                    .sortedBy { it.sortOrder }
                    .map { it.name.trim() }
                    .filter { it.isNotBlank() }
                val list = if (businessCategories.isNotEmpty()) {
                    businessCategories
                } else {
                    apiItems.items
                        .map { it.name.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                }
                if (list.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        loadingCategories = false,
                        categories = list
                    )
                } else {
                    _state.value = _state.value.copy(loadingCategories = false)
                }
            }
            .onFailure {
                _state.value = _state.value.copy(loadingCategories = false)
            }
    }

    fun loadBusinessRequest() = viewModelScope.launch {
        _state.value = _state.value.copy(loadingBusinessRequest = true)
        getMyBusinessRequestUseCase()
            .onSuccess { req ->
                _state.value = _state.value.copy(loadingBusinessRequest = false, businessRequest = req)
            }
            .onFailure {
                _state.value = _state.value.copy(loadingBusinessRequest = false)
            }
    }

    fun submitBusinessRequest(input: BusinessRequestInput) = viewModelScope.launch {
        _state.value = _state.value.copy(submittingBusinessRequest = true, error = null, message = null)
        submitBusinessRequestUseCase(input)
            .onSuccess { req ->
                _state.value = _state.value.copy(
                    submittingBusinessRequest = false,
                    businessRequest = req,
                    message = "business_request_submitted"
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(submittingBusinessRequest = false, error = e.message)
            }
    }

    fun setDisplayName(v: String) { _state.value = _state.value.copy(displayName = v) }
    fun setPhoneNumber(v: String) { _state.value = _state.value.copy(phoneNumber = v) }

    fun saveProfile() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(savingProfile = true, error = null, message = null)
        updateProfileUseCase(s.displayName.ifBlank { null }, s.phoneNumber.ifBlank { null })
            .onSuccess { user ->
                _state.value = _state.value.copy(savingProfile = false, user = user, message = "saved")
            }
            .onFailure { e -> _state.value = _state.value.copy(savingProfile = false, error = e.message) }
    }

    fun changePassword(current: String, newPass: String) = viewModelScope.launch {
        _state.value = _state.value.copy(changingPassword = true, error = null, message = null)
        changePasswordUseCase(current, newPass)
            .onSuccess { _state.value = _state.value.copy(changingPassword = false, message = "password_changed") }
            .onFailure { e -> _state.value = _state.value.copy(changingPassword = false, error = e.message) }
    }

    fun changeEmail(newEmail: String, currentPassword: String) = viewModelScope.launch {
        _state.value = _state.value.copy(changingEmail = true, error = null, message = null)
        changeEmailUseCase(newEmail, currentPassword)
            .onSuccess { session ->
                _state.value = _state.value.copy(
                    changingEmail = false,
                    email = newEmail,
                    user = session.user,
                    message = "email_changed",
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(changingEmail = false, error = e.message) }
    }

    fun uploadAvatar(contentType: String, base64Data: String) = viewModelScope.launch {
        _state.value = _state.value.copy(uploadingAvatar = true, error = null, message = null)
        uploadAvatarUseCase(contentType, base64Data)
            .onSuccess { user ->
                _state.value = _state.value.copy(uploadingAvatar = false, user = user, avatarUrl = user.avatarUrl, message = "avatar_updated")
            }
            .onFailure { e -> _state.value = _state.value.copy(uploadingAvatar = false, error = e.message) }
    }

    fun deleteAvatar() = viewModelScope.launch {
        _state.value = _state.value.copy(uploadingAvatar = true, error = null, message = null)
        deleteAvatarUseCase()
            .onSuccess { user ->
                _state.value = _state.value.copy(uploadingAvatar = false, user = user, avatarUrl = null, message = "avatar_removed")
            }
            .onFailure { e -> _state.value = _state.value.copy(uploadingAvatar = false, error = e.message) }
    }

    fun logout() = viewModelScope.launch { logoutUseCase() }

    fun clearMessages() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
