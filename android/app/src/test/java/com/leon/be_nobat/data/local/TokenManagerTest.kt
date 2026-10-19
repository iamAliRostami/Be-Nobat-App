package com.leon.be_nobat.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.leon.be_nobat.domain.interfaces.ICryptoManager
import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.model.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TokenManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `save persists session and deleteToken clears it`() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(temporaryFolder.root, "session.preferences_pb") },
        )
        val manager = TokenManager(dataStore, Json, FakeCryptoManager)
        val user = User(
            id = "u-1",
            displayName = "Test User",
            email = "user@example.com",
            phoneNumber = "+989121234567",
            avatarUrl = "/media/avatar/u-1?v=1",
            roles = listOf("Customer"),
        )
        val session = MobileSession(
            accessToken = "access-token",
            tokenType = "Bearer",
            expiresAt = "2026-12-31T00:00:00+00:00",
            user = user,
        )

        manager.save(session)

        assertEquals("access-token", manager.userToken.first())
        assertEquals(user, manager.user.first())
        assertEquals("2026-12-31T00:00:00+00:00", manager.expiresAt.first())

        // currentSession() returns the assembled envelope
        val assembled = manager.currentSession()
        assertEquals(session, assembled)

        manager.clear()

        assertNull(manager.userToken.first())
        assertNull(manager.user.first())
        assertNull(manager.expiresAt.first())
        assertNull(manager.currentSession())
    }

    @Test
    fun `saveUser updates the user without touching the token`() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(temporaryFolder.root, "session.preferences_pb") },
        )
        val manager = TokenManager(dataStore, Json, FakeCryptoManager)
        val original = MobileSession(
            accessToken = "tok-1",
            user = User(id = "u-1", displayName = "Old Name"),
        )
        manager.save(original)

        val updated = User(id = "u-1", displayName = "New Name")
        manager.saveUser(updated)

        assertEquals("tok-1", manager.userToken.first())
        assertEquals(updated, manager.user.first())
    }

    @Test
    fun `replaceSession overwrites token user and expiresAt`() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(temporaryFolder.root, "session.preferences_pb") },
        )
        val manager = TokenManager(dataStore, Json, FakeCryptoManager)
        val first = MobileSession(accessToken = "tok-1", user = User(id = "u-1", displayName = "First"))
        manager.save(first)

        val replacement = MobileSession(
            accessToken = "tok-2",
            expiresAt = "2027-01-01T00:00:00+00:00",
            user = User(id = "u-1", displayName = "Second"),
        )
        manager.replaceSession(replacement)

        assertEquals("tok-2", manager.userToken.first())
        assertEquals("Second", manager.user.first()?.displayName)
        assertEquals("2027-01-01T00:00:00+00:00", manager.expiresAt.first())
    }

    private object FakeCryptoManager : ICryptoManager {
        override fun encrypt(threshold: String): String = "encrypted:$threshold"
        override fun decrypt(encryptedData: String): String = encryptedData.removePrefix("encrypted:")
    }
}
