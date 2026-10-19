package com.leon.be_nobat.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.leon.be_nobat.domain.interfaces.ICryptoManager
import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Encrypted persistence for the mobile bearer session.
 *
 * The session envelope (`{accessToken, tokenType, expiresAt, user}`) is the
 * thing that survives app restarts. The token is also exposed as a plain
 * `String?` flow so [BeNobatApiClient] can attach it as a `Bearer` header.
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
    }

    /** Plain bearer token (decrypted). */
    val userToken: Flow<String?> = dataStore.data.map { it.decrypt(KEY_TOKEN) }

    /** The persisted user (decoded from JSON). */
    val user: Flow<User?> = dataStore.data.map { prefs ->
        prefs.decrypt(KEY_USER)?.let { serialized ->
            runCatching { json.decodeFromString(User.serializer(), serialized) }.getOrNull()
        }
    }

    /** ISO-8601 expiry timestamp of the current session. */
    val expiresAt: Flow<String?> = dataStore.data.map { it.decrypt(KEY_EXPIRES_AT) }

    /** Convenience accessor used by `StartActivity` to decide the launcher target. */
    suspend fun currentSession(): MobileSession? {
        val token = userToken.first() ?: return null
        val userVal = user.first() ?: return null
        val exp = expiresAt.first()
        return MobileSession(
            accessToken = token,
            tokenType = "Bearer",
            expiresAt = exp ?: "",
            user = userVal,
        )
    }

    /** Persist the full session in one DataStore transaction. */
    suspend fun save(session: MobileSession) {
        val encToken = cryptoManager.encrypt(session.accessToken)
        val encUser = cryptoManager.encrypt(json.encodeToString(User.serializer(), session.user))
        val encExp = session.expiresAt.takeIf { it.isNotBlank() }?.let { cryptoManager.encrypt(it) }
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = encToken
            prefs[KEY_USER] = encUser
            encExp?.let { prefs[KEY_EXPIRES_AT] = it }
        }
    }

    /** Update only the persisted user (used after `PATCH /me` or avatar uploads). */
    suspend fun saveUser(user: User) {
        val encUser = cryptoManager.encrypt(json.encodeToString(User.serializer(), user))
        dataStore.edit { prefs -> prefs[KEY_USER] = encUser }
    }

    /** Replace the whole session (used after `/me/password` or `/me/email` reissue). */
    suspend fun replaceSession(session: MobileSession) = save(session)

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_USER)
            prefs.remove(KEY_EXPIRES_AT)
        }
    }

    private fun Preferences.decrypt(key: Preferences.Key<String>): String? =
        get(key)?.let { encrypted -> runCatching { cryptoManager.decrypt(encrypted) }.getOrNull() }
}
