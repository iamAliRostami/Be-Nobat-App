package com.leon.be_nobat.domain.usecase

import com.leon.be_nobat.domain.model.AuthException
import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.model.RegisterInput
import com.leon.be_nobat.domain.model.User
import com.leon.be_nobat.domain.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUseCaseTest {
    private val repository = RecordingAuthRepository()
    private val useCase = LoginUseCase(repository)

    @Test fun `valid email is normalized and passed to repository`() = runBlocking {
        val result = useCase("  User@Example.COM ", "secret")
        assertTrue(result.isSuccess)
        assertEquals("user@example.com", repository.lastEmail)
    }

    @Test fun `iranian mobile is rejected because the backend accepts email only`() = runBlocking {
        val result = useCase("09121234567", "secret")
        assertSame(AuthException.InvalidIdentifier, result.exceptionOrNull())
        assertEquals(0, repository.calls)
    }

    @Test fun `mobile with international prefix is also rejected for now`() = runBlocking {
        val result = useCase("00989121234567", "secret")
        assertSame(AuthException.InvalidIdentifier, result.exceptionOrNull())
        assertEquals(0, repository.calls)
    }

    @Test fun `invalid identity is rejected without calling repository`() = runBlocking {
        val result = useCase("invalid-identity", "secret")
        assertSame(AuthException.InvalidIdentifier, result.exceptionOrNull())
        assertEquals(0, repository.calls)
    }

    @Test fun `blank password is rejected without calling repository`() = runBlocking {
        val result = useCase("user@example.com", "  ")
        assertSame(AuthException.EmptyPassword, result.exceptionOrNull())
        assertEquals(0, repository.calls)
    }

    private class RecordingAuthRepository : AuthRepository {
        var calls = 0
        var lastEmail: String? = null

        override suspend fun login(email: String, password: String): Result<MobileSession> {
            calls++
            lastEmail = email
            return Result.success(MobileSession(accessToken = "tok", user = User(id = "u-1", displayName = "Test User")))
        }

        override suspend fun register(input: RegisterInput): Result<MobileSession> {
            calls++
            return Result.success(MobileSession(accessToken = "tok", user = User(id = "u-1", displayName = "Test User")))
        }

        override suspend fun logout(): Result<Unit> { calls++; return Result.success(Unit) }
        override suspend fun getMe(): Result<User> { calls++; return Result.success(User(id = "u-1", displayName = "Me")) }
        override suspend fun updateProfile(input: com.leon.be_nobat.domain.model.ProfileInput): Result<User> = Result.success(User(id = "u-1", displayName = "Updated"))
        override suspend fun changePassword(input: com.leon.be_nobat.domain.model.PasswordInput): Result<MobileSession> = Result.success(MobileSession(accessToken = "tok2", user = User(id = "u-1")))
        override suspend fun changeEmail(input: com.leon.be_nobat.domain.model.EmailInput): Result<MobileSession> = Result.success(MobileSession(accessToken = "tok2", user = User(id = "u-1")))
        override suspend fun uploadAvatar(input: com.leon.be_nobat.domain.model.AvatarInput): Result<User> = Result.success(User(id = "u-1"))
        override suspend fun deleteAvatar(): Result<User> = Result.success(User(id = "u-1"))
        override val cachedSession: MobileSession? = null
    }
}

class RegisterUseCaseTest {
    private val repository = RecordingAuthRepository()
    private val useCase = RegisterUseCase(repository)

    @Test fun `missing display name is rejected`() = runBlocking {
        val result = useCase(RegisterInput(displayName = "", email = "u@e.com", phoneNumber = "09123456789", password = "pass"))
        assertSame(AuthException.InvalidIdentifier, result.exceptionOrNull())
        assertEquals(0, repository.calls)
    }

    @Test fun `missing email is rejected`() = runBlocking {
        val result = useCase(RegisterInput(displayName = "User", email = "", phoneNumber = "09123456789", password = "pass"))
        assertSame(AuthException.InvalidIdentifier, result.exceptionOrNull())
    }

    @Test fun `missing phone is rejected`() = runBlocking {
        val result = useCase(RegisterInput(displayName = "User", email = "u@e.com", phoneNumber = "", password = "pass"))
        assertSame(AuthException.InvalidIdentifier, result.exceptionOrNull())
    }

    @Test fun `blank password is rejected`() = runBlocking {
        val result = useCase(RegisterInput(displayName = "User", email = "u@e.com", phoneNumber = "09123456789", password = ""))
        assertSame(AuthException.EmptyPassword, result.exceptionOrNull())
    }

    @Test fun `valid register delegates to repository`() = runBlocking {
        val result = useCase(RegisterInput(displayName = "User", email = "u@e.com", phoneNumber = "09123456789", password = "pass"))
        assertTrue(result.isSuccess)
        assertEquals(1, repository.calls)
    }

    private class RecordingAuthRepository : AuthRepository {
        var calls = 0

        override suspend fun login(email: String, password: String) =
            Result.success<MobileSession>(MobileSession(accessToken = "tok", user = User(id = "u-1")))
        override suspend fun register(input: RegisterInput): Result<MobileSession> {
            calls++
            return Result.success(MobileSession(accessToken = "tok", user = User(id = "u-1", displayName = input.displayName ?: "")))
        }
        override suspend fun logout() = Result.success(Unit)
        override suspend fun getMe() = Result.success(User(id = "u-1"))
        override suspend fun updateProfile(input: com.leon.be_nobat.domain.model.ProfileInput) = Result.success(User(id = "u-1"))
        override suspend fun changePassword(input: com.leon.be_nobat.domain.model.PasswordInput) =
            Result.success(MobileSession(accessToken = "tok2", user = User(id = "u-1")))
        override suspend fun changeEmail(input: com.leon.be_nobat.domain.model.EmailInput) =
            Result.success(MobileSession(accessToken = "tok2", user = User(id = "u-1")))
        override suspend fun uploadAvatar(input: com.leon.be_nobat.domain.model.AvatarInput) = Result.success(User(id = "u-1"))
        override suspend fun deleteAvatar() = Result.success(User(id = "u-1"))
        override val cachedSession: MobileSession? = null
    }
}
