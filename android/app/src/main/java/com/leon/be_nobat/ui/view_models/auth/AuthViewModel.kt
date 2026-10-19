package com.leon.be_nobat.ui.view_models.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.domain.model.AuthException
import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.usecase.LoginUseCase
import com.leon.be_nobat.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val session: MobileSession) : LoginUiState
    data class Error(val error: Throwable) : LoginUiState
}

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    fun login(identity: String, password: String) {
        if (_loginState.value is LoginUiState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            loginUseCase(identity, password)
                .onSuccess { session -> _loginState.value = LoginUiState.Success(session) }
                .onFailure { err -> _loginState.value = LoginUiState.Error(err) }
        }
    }

    fun resetState() {
        _loginState.value = LoginUiState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _loggedOut.value = true
        }
    }

    /** Convenience: derive a localized error key from the current error state. */
    fun errorKey(error: Throwable?): String? = when (error as? AuthException) {
        AuthException.InvalidIdentifier -> "error_invalid_identifier"
        AuthException.EmptyPassword -> "error_empty_password"
        AuthException.InvalidCredentials -> "error_invalid_credentials"
        AuthException.AccountLocked -> "error_account_locked"
        AuthException.DuplicateEmail -> "error_duplicate_email"
        AuthException.PasswordMismatch -> "error_password_mismatch"
        AuthException.WeakPassword -> "error_weak_password"
        AuthException.TooManyRequests -> "error_too_many_requests"
        AuthException.ServiceUnavailable -> "error_service_unavailable"
        AuthException.NetworkUnavailable -> "error_network_unavailable"
        AuthException.RequestTimedOut -> "error_request_timed_out"
        AuthException.Unauthorized -> "error_unauthorized"
        AuthException.Forbidden -> "error_forbidden"
        AuthException.NotFound -> "error_not_found"
        AuthException.Conflict -> "error_conflict"
        AuthException.Unexpected, null -> "unexpected_error"
    }
}
