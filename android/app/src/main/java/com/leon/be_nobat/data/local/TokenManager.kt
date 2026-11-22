package com.leon.be_nobat.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.leon.be_nobat.domain.interfaces.ICryptoManager
import com.leon.be_nobat.domain.model.ManagementContext
import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.model.SavedAccount
import com.leon.be_nobat.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Encrypted persistence for mobile sessions supporting multi-account switching (Telegram style).
 *
 * Stores the list of all saved accounts in [KEY_ACCOUNTS] while keeping the active
 * account's credentials in [KEY_TOKEN], [KEY_USER], [KEY_EXPIRES_AT], and [KEY_MANAGEMENT_CONTEXT]
 * for instant backwards compatibility with existing flows and interceptors.
 */
class TokenManager(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val cryptoManager: ICryptoManager,
) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("encrypted_token")
        private val KEY_USER = stringPreferencesKey("encrypted_user")
        private val KEY_EXPIRES_AT = stringPreferencesKey("encrypted_expires_at")
        private val KEY_MANAGEMENT_CONTEXT = stringPreferencesKey("encrypted_management_context")
        private val KEY_ACCOUNTS = stringPreferencesKey("encrypted_accounts")
    }

    /** Plain bearer token of the active account (decrypted). */
    val userToken: Flow<String?> = dataStore.data.map { it.decrypt(KEY_TOKEN) }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** The active persisted user (decoded from JSON). */
    val user: Flow<User?> = dataStore.data.map { prefs ->
        prefs.decrypt(KEY_USER)?.let { serialized ->
            runCatching { json.decodeFromString(User.serializer(), serialized) }.getOrNull()
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** The active persisted management context (decoded from JSON). */
    val managementContext: Flow<ManagementContext?> = dataStore.data.map { prefs ->
        prefs.decrypt(KEY_MANAGEMENT_CONTEXT)?.let { serialized ->
            runCatching { json.decodeFromString(ManagementContext.serializer(), serialized) }.getOrNull()
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** ISO-8601 expiry timestamp of the current active session. */
    val expiresAt: Flow<String?> = dataStore.data.map { it.decrypt(KEY_EXPIRES_AT) }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** All saved accounts list (decrypted & decoded), with auto-migration from legacy single session. */
    val accounts: Flow<List<SavedAccount>> = dataStore.data.map { prefs ->
        val serializedAccounts = prefs.decrypt(KEY_ACCOUNTS)
        if (!serializedAccounts.isNullOrBlank()) {
            runCatching {
                json.decodeFromString(ListSerializer(SavedAccount.serializer()), serializedAccounts)
            }.getOrDefault(emptyList())
        } else {
            // Check for legacy single-account data
            val token = prefs.decrypt(KEY_TOKEN)
            val userStr = prefs.decrypt(KEY_USER)
            if (!token.isNullOrBlank() && !userStr.isNullOrBlank()) {
                val legacyUser = runCatching { json.decodeFromString(User.serializer(), userStr) }.getOrNull()
                if (legacyUser != null) {
                    val exp = prefs.decrypt(KEY_EXPIRES_AT) ?: ""
                    val mgmtStr = prefs.decrypt(KEY_MANAGEMENT_CONTEXT)
                    val mgmt = mgmtStr?.let { runCatching { json.decodeFromString(ManagementContext.serializer(), it) }.getOrNull() }
                    listOf(
                        SavedAccount(
                            session = MobileSession(
                                accessToken = token,
                                tokenType = "Bearer",
                                expiresAt = exp,
                                user = legacyUser
                            ),
                            managementContext = mgmt,
                            lastActiveAt = System.currentTimeMillis()
                        )
                    )
                } else emptyList()
            } else emptyList()
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** Convenience accessor used by `StartActivity` to decide the launcher target. */
    suspend fun currentSession(): MobileSession? = withContext(Dispatchers.IO) {
        val token = userToken.first() ?: return@withContext null
        val userVal = user.first() ?: return@withContext null
        val exp = expiresAt.first()
        MobileSession(
            accessToken = token,
            tokenType = "Bearer",
            expiresAt = exp ?: "",
            user = userVal,
        )
    }

    /** Retrieve list of all saved accounts. */
    suspend fun getSavedAccounts(): List<SavedAccount> = withContext(Dispatchers.IO) {
        accounts.first()
    }

    /**
     * Persist the session and upsert it into the saved accounts list.
     * Sets this session as the active account.
     */
    suspend fun save(session: MobileSession) = withContext(Dispatchers.IO) {
        val currentAccounts = accounts.first().toMutableList()
        val existingIndex = currentAccounts.indexOfFirst { it.session.user.id == session.user.id }
        val updatedAccount = if (existingIndex >= 0) {
            currentAccounts[existingIndex].copy(
                session = session,
                lastActiveAt = System.currentTimeMillis()
            )
        } else {
            SavedAccount(
                session = session,
                managementContext = null,
                lastActiveAt = System.currentTimeMillis()
            )
        }

        if (existingIndex >= 0) {
            currentAccounts[existingIndex] = updatedAccount
        } else {
            currentAccounts.add(updatedAccount)
        }

        val encToken = cryptoManager.encrypt(session.accessToken)
        val encUser = cryptoManager.encrypt(json.encodeToString(User.serializer(), session.user))
        val encExp = session.expiresAt.takeIf { it.isNotBlank() }?.let { cryptoManager.encrypt(it) }
        val encAccounts = cryptoManager.encrypt(json.encodeToString(ListSerializer(SavedAccount.serializer()), currentAccounts))

        val currentMgmt = updatedAccount.managementContext
        val encMgmt = currentMgmt?.let {
            cryptoManager.encrypt(json.encodeToString(ManagementContext.serializer(), it))
        }

        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = encToken
            prefs[KEY_USER] = encUser
            if (encExp != null) prefs[KEY_EXPIRES_AT] = encExp else prefs.remove(KEY_EXPIRES_AT)
            if (encMgmt != null) prefs[KEY_MANAGEMENT_CONTEXT] = encMgmt else prefs.remove(KEY_MANAGEMENT_CONTEXT)
            prefs[KEY_ACCOUNTS] = encAccounts
        }
    }

    /** Update only the persisted user (used after `PATCH /me` or avatar uploads). */
    suspend fun saveUser(user: User) = withContext(Dispatchers.IO) {
        val currentAccounts = accounts.first().toMutableList()
        val index = currentAccounts.indexOfFirst { it.session.user.id == user.id }
        if (index >= 0) {
            val updated = currentAccounts[index].copy(
                session = currentAccounts[index].session.copy(user = user)
            )
            currentAccounts[index] = updated
        }

        val encUser = cryptoManager.encrypt(json.encodeToString(User.serializer(), user))
        val encAccounts = cryptoManager.encrypt(json.encodeToString(ListSerializer(SavedAccount.serializer()), currentAccounts))

        dataStore.edit { prefs ->
            prefs[KEY_USER] = encUser
            prefs[KEY_ACCOUNTS] = encAccounts
        }
    }

    /** Replace the whole session (used after `/me/password` or `/me/email` reissue). */
    suspend fun replaceSession(session: MobileSession) = save(session)

    suspend fun currentManagementContext(): ManagementContext? = withContext(Dispatchers.IO) { managementContext.first() }

    suspend fun saveManagementContext(context: ManagementContext) = withContext(Dispatchers.IO) {
        val activeUser = user.first()
        val currentAccounts = accounts.first().toMutableList()
        if (activeUser != null) {
            val index = currentAccounts.indexOfFirst { it.session.user.id == activeUser.id }
            if (index >= 0) {
                currentAccounts[index] = currentAccounts[index].copy(managementContext = context)
            }
        }

        val encContext = cryptoManager.encrypt(json.encodeToString(ManagementContext.serializer(), context))
        val encAccounts = cryptoManager.encrypt(json.encodeToString(ListSerializer(SavedAccount.serializer()), currentAccounts))

        dataStore.edit { prefs ->
            prefs[KEY_MANAGEMENT_CONTEXT] = encContext
            prefs[KEY_ACCOUNTS] = encAccounts
        }
    }

    /**
     * Switch active account to the specified [userId].
     * Returns true if found and switched successfully, false otherwise.
     */
    suspend fun switchAccount(userId: String): Boolean = withContext(Dispatchers.IO) {
        val currentAccounts = accounts.first().toMutableList()
        val targetIndex = currentAccounts.indexOfFirst { it.session.user.id == userId }
        if (targetIndex < 0) return@withContext false

        val target = currentAccounts[targetIndex].copy(lastActiveAt = System.currentTimeMillis())
        currentAccounts[targetIndex] = target

        val encToken = cryptoManager.encrypt(target.session.accessToken)
        val encUser = cryptoManager.encrypt(json.encodeToString(User.serializer(), target.session.user))
        val encExp = target.session.expiresAt.takeIf { it.isNotBlank() }?.let { cryptoManager.encrypt(it) }
        val encAccounts = cryptoManager.encrypt(json.encodeToString(ListSerializer(SavedAccount.serializer()), currentAccounts))

        val encMgmt = target.managementContext?.let {
            cryptoManager.encrypt(json.encodeToString(ManagementContext.serializer(), it))
        }

        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = encToken
            prefs[KEY_USER] = encUser
            if (encExp != null) prefs[KEY_EXPIRES_AT] = encExp else prefs.remove(KEY_EXPIRES_AT)
            if (encMgmt != null) prefs[KEY_MANAGEMENT_CONTEXT] = encMgmt else prefs.remove(KEY_MANAGEMENT_CONTEXT)
            prefs[KEY_ACCOUNTS] = encAccounts
        }
        true
    }

    /**
     * Remove an account by [userId].
     * If the removed account was active:
     * - Switches to the next most recently active account if available, returning its session.
     * - Or clears everything if no other accounts remain, returning null.
     */
    suspend fun removeAccount(userId: String): MobileSession? = withContext(Dispatchers.IO) {
        val currentAccounts = accounts.first().toMutableList()
        val removed = currentAccounts.removeAll { it.session.user.id == userId }
        if (!removed) return@withContext currentSession()

        val activeUser = user.first()
        val wasActive = activeUser == null || activeUser.id == userId

        if (currentAccounts.isEmpty()) {
            clear()
            return@withContext null
        }

        if (wasActive) {
            val nextActive = currentAccounts.maxByOrNull { it.lastActiveAt } ?: currentAccounts.first()
            val encToken = cryptoManager.encrypt(nextActive.session.accessToken)
            val encUser = cryptoManager.encrypt(json.encodeToString(User.serializer(), nextActive.session.user))
            val encExp = nextActive.session.expiresAt.takeIf { it.isNotBlank() }?.let { cryptoManager.encrypt(it) }
            val encAccounts = cryptoManager.encrypt(json.encodeToString(ListSerializer(SavedAccount.serializer()), currentAccounts))
            val encMgmt = nextActive.managementContext?.let {
                cryptoManager.encrypt(json.encodeToString(ManagementContext.serializer(), it))
            }

            dataStore.edit { prefs ->
                prefs[KEY_TOKEN] = encToken
                prefs[KEY_USER] = encUser
                if (encExp != null) prefs[KEY_EXPIRES_AT] = encExp else prefs.remove(KEY_EXPIRES_AT)
                if (encMgmt != null) prefs[KEY_MANAGEMENT_CONTEXT] = encMgmt else prefs.remove(KEY_MANAGEMENT_CONTEXT)
                prefs[KEY_ACCOUNTS] = encAccounts
            }
            return@withContext nextActive.session
        } else {
            val encAccounts = cryptoManager.encrypt(json.encodeToString(ListSerializer(SavedAccount.serializer()), currentAccounts))
            dataStore.edit { prefs ->
                prefs[KEY_ACCOUNTS] = encAccounts
            }
            return@withContext currentSession()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_USER)
            prefs.remove(KEY_EXPIRES_AT)
            prefs.remove(KEY_MANAGEMENT_CONTEXT)
            prefs.remove(KEY_ACCOUNTS)
        }
    }

    private fun Preferences.decrypt(key: Preferences.Key<String>): String? =
        get(key)?.let { encrypted -> runCatching { cryptoManager.decrypt(encrypted) }.getOrNull() }
}
