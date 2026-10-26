package com.leon.be_nobat.data.repository

import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.AuthException
import com.leon.be_nobat.domain.model.AvatarInput
import com.leon.be_nobat.domain.model.EmailInput
import com.leon.be_nobat.domain.model.LoginInput
import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.model.PasswordInput
import com.leon.be_nobat.domain.model.ProfileInput
import com.leon.be_nobat.domain.model.RegisterInput
import com.leon.be_nobat.domain.model.User
import com.leon.be_nobat.domain.repository.AuthRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Authentication + account repository backed by [BeNobatApiClient] (the
 * new .NET `api v1` mobile API).
 *
 * Each method maps transport-level failures into a typed [AuthException] via
 * [AuthException.fromApi] so the UI can show a localized message per case.
 */
class AuthRepositoryImpl(
    private val api: BeNobatApiClient,
    private val tokenManager: TokenManager,
) : AuthRepository {

    override val cachedSession: MobileSession? = null

    override suspend fun login(email: String, password: String): Result<MobileSession> = runCatching {
        val body = encodeJsonObject {
            put("identifier", email)
            put("email", email)
            put("password", password)
        }
        val session = api.login<MobileSession>(body).getOrThrow()
        tokenManager.save(session)
        session
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun register(input: RegisterInput): Result<MobileSession> = runCatching {
        val body = encodeJsonObject {
            put("displayName", input.displayName)
            put("email", input.email)
            put("phoneNumber", input.phoneNumber)
            put("password", input.password)
        }
        val session = api.register<MobileSession>(body).getOrThrow()
        tokenManager.save(session)
        session
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun logout(): Result<Unit> = runCatching {
        // Best-effort: call /auth/logout, then clear local session even on failure.
        api.logout().onFailure { /* swallow — clear locally regardless */ }
        tokenManager.clear()
        Unit
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun getMe(): Result<User> = runCatching {
        val user = api.getMe<User>().getOrThrow()
        tokenManager.saveUser(user)
        user
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun updateProfile(input: ProfileInput): Result<User> = runCatching {
        val body = encodeJsonObject {
            put("displayName", input.displayName)
            put("phoneNumber", input.phoneNumber)
        }
        val user = api.updateProfile<User>(body).getOrThrow()
        tokenManager.saveUser(user)
        user
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun changePassword(input: PasswordInput): Result<MobileSession> = runCatching {
        val body = encodeJsonObject {
            put("currentPassword", input.currentPassword)
            put("newPassword", input.newPassword)
        }
        val session = api.changePassword<MobileSession>(body).getOrThrow()
        tokenManager.replaceSession(session)
        session
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun changeEmail(input: EmailInput): Result<MobileSession> = runCatching {
        val body = encodeJsonObject {
            put("email", input.email)
            put("currentPassword", input.currentPassword)
        }
        val session = api.changeEmail<MobileSession>(body).getOrThrow()
        tokenManager.replaceSession(session)
        session
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun uploadAvatar(input: AvatarInput): Result<User> = runCatching {
        val body = encodeJsonObject {
            put("contentType", input.contentType)
            put("dataBase64", input.dataBase64)
        }
        val user = api.uploadAvatar<User>(body).getOrThrow()
        tokenManager.saveUser(user)
        user
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun deleteAvatar(): Result<User> = runCatching {
        val user = api.deleteAvatar<User>().getOrThrow()
        tokenManager.saveUser(user)
        user
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun getMyBusinessRequest(): Result<com.leon.be_nobat.domain.model.BusinessRequest?> = runCatching {
        api.getMyBusinessRequest<com.leon.be_nobat.domain.model.BusinessRequest?>().getOrThrow()
    }.recoverCatching { throw AuthException.fromApi(it) }

    override suspend fun submitBusinessRequest(input: com.leon.be_nobat.domain.model.BusinessRequestInput): Result<com.leon.be_nobat.domain.model.BusinessRequest> = runCatching {
        val body = encodeJsonObject {
            put("businessName", input.businessName)
            put("category", input.category)
            put("city", input.city)
            put("description", input.description)
            put("contactPhone", input.contactPhone)
            put("firstBranchName", input.firstBranchName)
            put("firstBranchAddress", input.firstBranchAddress)
        }
        api.submitBusinessRequest<com.leon.be_nobat.domain.model.BusinessRequest>(body).getOrThrow()
    }.recoverCatching { throw AuthException.fromApi(it) }

    private fun encodeJsonObject(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject(block)
}
