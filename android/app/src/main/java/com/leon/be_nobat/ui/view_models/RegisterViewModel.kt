package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.domain.model.AuthException
import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.model.RegisterInput
import com.leon.be_nobat.domain.usecase.RegisterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RegisterUiState {
    data object Idle : RegisterUiState
    data object Loading : RegisterUiState
    data class Success(val session: MobileSession) : RegisterUiState
    data class Error(val error: Throwable) : RegisterUiState
}

class RegisterViewModel(
    private val registerUseCase: RegisterUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun register(displayName: String, email: String, phoneNumber: String, password: String) {
        if (_state.value is RegisterUiState.Loading) return
        viewModelScope.launch {
            _state.value = RegisterUiState.Loading
            registerUseCase(
                RegisterInput(
                    displayName = displayName.ifBlank { null },
                    email = email.ifBlank { null },
                    phoneNumber = phoneNumber.ifBlank { null },
                    password = password.ifBlank { null },
                )
            ).onSuccess { session -> _state.value = RegisterUiState.Success(session) }
                .onFailure { err -> _state.value = RegisterUiState.Error(err) }
        }
    }

    fun reset() { _state.value = RegisterUiState.Idle }
}
