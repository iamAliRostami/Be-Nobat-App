package com.leon.be_nobat.data.demo

import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.domain.model.*
import com.leon.be_nobat.domain.repository.AuthRepository

/** Demo accounts use Demo1234! until their password is changed locally. */
class DemoAuthRepository(private val dataStore: DemoDataStore, private val tokenManager: TokenManager) : AuthRepository {
    override var cachedSession: MobileSession? = null
        private set

    private fun ManagementUser.toUser(avatar: String? = null) = User(
        id = id,
        displayName = displayName,
        email = email,
        phoneNumber = phoneNumber,
        roles = roles,
        avatarUrl = avatar
    )

    private suspend fun save(user: User): MobileSession {
        val session = MobileSession(
            accessToken = "demo_${user.id}",
            tokenType = "Bearer",
            expiresAt = "2099-12-31T23:59:59Z",
            user = user
        )
        tokenManager.save(session)
        cachedSession = session
        return session
    }

    private suspend fun current() = requireNotNull(tokenManager.currentSession()) { "ابتدا وارد حساب شوید." }.user

    override suspend fun login(email: String, password: String): Result<MobileSession> = runCatching {
        simulateNetworkDelay()
        val user = dataStore.read { s ->
            val u = s.users.firstOrNull { it.email.equals(email.trim(), true) || it.phoneNumber == email.trim() }
                ?: throw AuthException.InvalidCredentials
            if (!u.active) throw AuthException.AccountLocked
            if (password != (s.passwords[u.id] ?: "Demo1234!")) throw AuthException.InvalidCredentials
            u.toUser(s.avatars[u.id])
        }
        save(user)
    }

    override suspend fun register(input: RegisterInput): Result<MobileSession> = runCatching {
        simulateNetworkDelay()
        require(!input.displayName.isNullOrBlank() && !input.email.isNullOrBlank() && !input.password.isNullOrBlank())
        val user = dataStore.update { s ->
            if (s.users.any { it.email.equals(input.email, true) }) throw AuthException.DuplicateEmail
            val u = ManagementUser(
                id = demoId(),
                displayName = input.displayName.trim(),
                email = input.email.trim(),
                phoneNumber = input.phoneNumber,
                roles = listOf("Customer"),
                active = true
            )
            s.copy(users = s.users + u, passwords = s.passwords + (u.id to input.password)) to u.toUser()
        }
        save(user)
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        simulateNetworkDelay(minMs = 80L, maxMs = 150L)
        tokenManager.clear()
        cachedSession = null
    }

    override suspend fun getMe(): Result<User> = runCatching {
        simulateNetworkDelay(minMs = 100L, maxMs = 200L)
        val id = current().id
        val user = dataStore.read { s ->
            val u = s.users.firstOrNull { it.id == id } ?: error("کاربر یافت نشد.")
            check(u.active)
            u.toUser(s.avatars[id])
        }
        save(user).user
    }

    override suspend fun updateProfile(input: ProfileInput): Result<User> = runCatching {
        simulateNetworkDelay()
        val id = current().id
        val user = dataStore.update { s ->
            val existing = s.users.firstOrNull { it.id == id } ?: error("کاربر یافت نشد.")
            val u = existing.copy(
                displayName = input.displayName ?: existing.displayName,
                phoneNumber = input.phoneNumber ?: existing.phoneNumber
            )
            s.copy(
                users = s.users.map { if (it.id == id) u else it },
                appointments = s.appointments.map { if (it.customerId == id) it.copy(customerName = u.displayName, customerPhoneNumber = u.phoneNumber) else it },
                memberships = s.memberships.map { if (it.userId == id) it.copy(displayName = u.displayName) else it }
            ) to u.toUser(s.avatars[id])
        }
        save(user).user
    }

    override suspend fun changePassword(input: PasswordInput): Result<MobileSession> = runCatching {
        simulateNetworkDelay()
        val user = current()
        dataStore.update { s ->
            if (input.currentPassword != (s.passwords[user.id] ?: "Demo1234!")) throw AuthException.InvalidCredentials
            require(!input.newPassword.isNullOrBlank())
            s.copy(passwords = s.passwords + (user.id to input.newPassword)) to Unit
        }
        save(user)
    }

    override suspend fun changeEmail(input: EmailInput): Result<MobileSession> = runCatching {
        simulateNetworkDelay()
        val id = current().id
        val user = dataStore.update { s ->
            if (input.currentPassword != (s.passwords[id] ?: "Demo1234!")) throw AuthException.InvalidCredentials
            require(!input.email.isNullOrBlank())
            if (s.users.any { it.id != id && it.email.equals(input.email, true) }) throw AuthException.DuplicateEmail
            val u = (s.users.firstOrNull { it.id == id } ?: error("کاربر یافت نشد.")).copy(email = input.email.trim())
            s.copy(users = s.users.map { if (it.id == id) u else it }) to u.toUser(s.avatars[id])
        }
        save(user)
    }

    override suspend fun uploadAvatar(input: AvatarInput): Result<User> = runCatching {
        simulateNetworkDelay()
        val user = current()
        require(!input.dataBase64.isNullOrBlank())
        val url = "data:${input.contentType ?: "image/jpeg"};base64,${input.dataBase64}"
        dataStore.update { s -> s.copy(avatars = s.avatars + (user.id to url)) to Unit }
        save(user.copy(avatarUrl = url)).user
    }

    override suspend fun deleteAvatar(): Result<User> = runCatching {
        simulateNetworkDelay()
        val user = current()
        dataStore.update { s -> s.copy(avatars = s.avatars - user.id) to Unit }
        save(user.copy(avatarUrl = null)).user
    }

    override suspend fun getMyBusinessRequest(): Result<BusinessRequest?> = runCatching {
        simulateNetworkDelay()
        val actor = current()
        dataStore.read { s ->
            s.businessRequests.filter { it.userId == actor.id }.maxByOrNull { it.createdAt }
        }
    }

    override suspend fun submitBusinessRequest(input: BusinessRequestInput): Result<BusinessRequest> = runCatching {
        simulateNetworkDelay()
        val actor = current()
        require(input.businessName.isNotBlank() && input.city.isNotBlank() && input.contactPhone.isNotBlank())
        dataStore.update { s ->
            val pending = s.businessRequests.any { it.userId == actor.id && it.status == "Pending" }
            require(!pending) { "شما در حال حاضر یک درخواست در حال بررسی دارید." }
            val req = BusinessRequest(
                id = demoId(),
                userId = actor.id,
                businessName = input.businessName.trim(),
                category = input.category.trim().ifBlank { "عمومی" },
                city = input.city.trim().ifBlank { "تهران" },
                description = input.description.trim(),
                contactPhone = input.contactPhone.trim(),
                firstBranchName = input.firstBranchName.trim().ifBlank { "شعبه مرکزی" },
                firstBranchAddress = input.firstBranchAddress.trim(),
                status = "Pending",
                createdAt = demoNow(),
                userDisplayName = actor.displayName,
                userPhoneNumber = actor.phoneNumber ?: input.contactPhone.trim()
            )
            s.copy(businessRequests = listOf(req) + s.businessRequests) to req
        }
    }
}
