package com.leon.be_nobat.domain.usecase

import com.leon.be_nobat.domain.model.AuthException
import com.leon.be_nobat.domain.model.LoginIdentifier
import com.leon.be_nobat.domain.model.User
import com.leon.be_nobat.domain.repository.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(identity: String, password: String): Result<User> {
        val parsedIdentity = LoginIdentifier.parse(identity).getOrElse {
            return Result.failure(it)
        }
        if (password.isBlank()) return Result.failure(AuthException.EmptyPassword)
        return repository.login(parsedIdentity.value, password)
    }
}
