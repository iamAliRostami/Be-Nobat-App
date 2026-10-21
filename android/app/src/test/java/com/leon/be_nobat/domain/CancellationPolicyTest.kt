package com.leon.be_nobat.domain

import com.leon.be_nobat.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class CancellationPolicyTest {
    private val now = requireNotNull(CancellationPolicy.instant("2030-01-01T08:00:00Z"))
    @Test fun `default exact deadline and fractional times agree with server`() {
        assertTrue(CancellationPolicy.canCancel("Pending", "2030-01-01T10:00:00.100Z", 120, now + 50))
        assertFalse(CancellationPolicy.canCancel("Pending", "2030-01-01T10:00:00.100Z", 120, now + 101))
        assertEquals(120, ManagementBusiness().cancellationNoticeMinutes)
        assertTrue(CancellationPolicy.canCancel("Pending", "2030-01-01T10:00:00.0000000+00:00", 120, now))
        assertFalse(CancellationPolicy.canCancel("Confirmed", "2030-01-01T09:59:59Z", 120, now))
        assertTrue(CancellationPolicy.canCancel("Confirmed", "2030-01-01T13:30:00+03:30", 120, now))
    }
    @Test fun `specific overrides include zero and null inherits`() {
        assertEquals(360, CancellationPolicy.resolve(360))
        assertEquals(240, CancellationPolicy.resolve(360, 240))
        assertEquals(180, CancellationPolicy.resolve(360, 240, 180))
        assertEquals(30, CancellationPolicy.resolve(360, 240, 180, 30))
        assertEquals(0, CancellationPolicy.resolve(360, 240, 180, 0))
    }
    @Test fun `zero does not allow cancellation after appointment starts and final states stay final`() {
        assertTrue(CancellationPolicy.canCancel("Pending", "2030-01-01T08:00:01Z", 0, now))
        assertFalse(CancellationPolicy.canCancel("Pending", "2030-01-01T08:00:00Z", 0, now))
        listOf("Completed", "Cancelled", "NoShow").forEach {
            assertFalse(CancellationPolicy.canCancel(it, "2030-01-01T12:00:00Z", 0, now))
        }
        assertFalse(CancellationPolicy.canCancel("Pending", "bad-time", 120, now))
    }
    @Test fun `serialized legacy data inherits while explicit override survives reopening`() {
        val json = Json { encodeDefaults = true }
        assertEquals(120, json.decodeFromString<ManagementBusiness>("{}").cancellationNoticeMinutes)
        assertNull(json.decodeFromString<ManagementBranch>("{}").cancellationNoticeMinutes)
        val original = ManagementBranchService(cancellationNoticeMinutes = 30)
        assertEquals(30, json.decodeFromString<ManagementBranchService>(json.encodeToString(ManagementBranchService.serializer(), original)).cancellationNoticeMinutes)
        assertTrue(CancellationPolicy.valid(null)); assertTrue(CancellationPolicy.valid(0))
        assertFalse(CancellationPolicy.valid(-1)); assertFalse(CancellationPolicy.valid(129601))
    }
}
