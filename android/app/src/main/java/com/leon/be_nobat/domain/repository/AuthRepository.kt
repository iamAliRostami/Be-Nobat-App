package com.leon.be_nobat.domain.repository

import com.leon.be_nobat.domain.model.AvatarInput
import com.leon.be_nobat.domain.model.EmailInput
import com.leon.be_nobat.domain.model.LoginInput
import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.model.PasswordInput
import com.leon.be_nobat.domain.model.ProfileInput
import com.leon.be_nobat.domain.model.RegisterInput
import com.leon.be_nobat.domain.model.User
import kotlinx.serialization.json.JsonObject

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<MobileSession>
    suspend fun register(input: RegisterInput): Result<MobileSession>
    suspend fun logout(): Result<Unit>
    suspend fun getMe(): Result<User>
    suspend fun updateProfile(input: ProfileInput): Result<User>
    suspend fun changePassword(input: PasswordInput): Result<MobileSession>
    suspend fun changeEmail(input: EmailInput): Result<MobileSession>
    suspend fun uploadAvatar(input: AvatarInput): Result<User>
    suspend fun deleteAvatar(): Result<User>
    suspend fun getMyBusinessRequest(): Result<com.leon.be_nobat.domain.model.BusinessRequest?>
    suspend fun submitBusinessRequest(input: com.leon.be_nobat.domain.model.BusinessRequestInput): Result<com.leon.be_nobat.domain.model.BusinessRequest>
    /** Persisted current session; used by StartActivity to decide the launcher target. */
    val cachedSession: MobileSession?
}
