package com.leon.be_nobat.domain.usecase

import com.leon.be_nobat.domain.model.AuthException
import com.leon.be_nobat.domain.model.LoginIdentifier
import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.model.RegisterInput
import com.leon.be_nobat.domain.repository.AuthRepository

/**
 * Logs the user in via `/api/v1/auth/login`.
 *
 * The .NET backend currently accepts email + password (the `LoginInput` wire
 * shape names the field `email`). If the user typed a mobile number we
 * surface [AuthException.InvalidIdentifier] so they can correct it — the
 * server would otherwise reject with `invalid_credentials`.
 */
class LoginUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(identity: String, password: String): Result<MobileSession> {
        if (password.isBlank()) return Result.failure(AuthException.EmptyPassword)
        val parsed = LoginIdentifier.parse(identity).getOrElse {
            return Result.failure(AuthException.InvalidIdentifier)
        }
        // The current backend contract accepts only email for login.
        if (parsed is LoginIdentifier.Mobile) {
            return Result.failure(AuthException.InvalidIdentifier)
        }
        return repository.login(parsed.value, password)
    }
}

class RegisterUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(input: RegisterInput): Result<MobileSession> {
        if (input.displayName.isNullOrBlank()) return Result.failure(AuthException.InvalidIdentifier)
        if (input.email.isNullOrBlank()) return Result.failure(AuthException.InvalidIdentifier)
        if (input.password.isNullOrBlank()) return Result.failure(AuthException.EmptyPassword)
        if (input.phoneNumber.isNullOrBlank()) return Result.failure(AuthException.InvalidIdentifier)
        return repository.register(input)
    }
}

class LogoutUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.logout()
}

class GetMeUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() = repository.getMe()
}

class UpdateProfileUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(displayName: String?, phoneNumber: String?) =
        repository.updateProfile(com.leon.be_nobat.domain.model.ProfileInput(displayName, phoneNumber))
}

class ChangePasswordUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(current: String, newPass: String) =
        repository.changePassword(
            com.leon.be_nobat.domain.model.PasswordInput(current, newPass)
        )
}

class ChangeEmailUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(newEmail: String, currentPassword: String) =
        repository.changeEmail(
            com.leon.be_nobat.domain.model.EmailInput(newEmail, currentPassword)
        )
}

class UploadAvatarUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(contentType: String, base64Data: String) =
        repository.uploadAvatar(
            com.leon.be_nobat.domain.model.AvatarInput(contentType, base64Data)
        )
}

class DeleteAvatarUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() = repository.deleteAvatar()
}
