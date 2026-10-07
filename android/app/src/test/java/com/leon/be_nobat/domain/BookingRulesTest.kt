package com.leon.be_nobat.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BookingRulesTest {
    @Test fun bookingTotalPreservesFractionalPrices() {
        assertEquals(BigDecimal("100.30"), BookingRules.total(listOf(BigDecimal("80.10"), BigDecimal("20.20"))))
        assertEquals(75, BookingRules.duration(listOf(30, 45)))
    }
    @Test fun bookingDateRejectsPastAndMalformedDates() {
        val today = LocalDate.of(2026, 10, 7)
        assertTrue(BookingRules.validDate("2026-10-07", today))
        assertTrue(BookingRules.validDate("2026-10-08", today))
        assertFalse(BookingRules.validDate("2026-10-06", today))
        assertFalse(BookingRules.validDate("2026-02-30", today))
        assertFalse(BookingRules.validDate("2027-10-07", today))
    }
    @Test fun terminalAppointmentsAndFutureCompletionAreNotOfferedAsActions() {
        val now = java.time.Instant.parse("2026-10-07T12:00:00Z")
        assertEquals(listOf("Confirmed", "Cancelled"), BookingRules.statusActions("Pending", "2026-10-08T09:00:00Z", now))
        assertEquals(listOf("Cancelled", "Completed", "NoShow"), BookingRules.statusActions("Confirmed", "2026-10-07T09:00:00Z", now))
        assertTrue(BookingRules.statusActions("Completed", "2026-10-07T09:00:00Z", now).isEmpty())
    }
    @Test fun localizedPhoneNumbersAndEmailAreAccepted() {
        assertTrue(BookingRules.validPhone("+98 912 345 6789"))
        assertTrue(BookingRules.validPhone("۰۹۱۲۳۴۵۶۷۸۹"))
        assertFalse(BookingRules.validPhone("call me"))
        assertTrue(BookingRules.validEmail("person@example.com"))
        assertFalse(BookingRules.validEmail("person@example"))
    }
    @Test fun passwordsFollowTheExistingServerIdentityPolicy() {
        assertTrue(BookingRules.validPassword("pass12"))
        assertFalse(BookingRules.validPassword("abc12"))
        assertFalse(BookingRules.validPassword("password"))
        assertFalse(BookingRules.validPassword("12345678"))
        assertFalse(BookingRules.validPhone("+1 202 555 0100"))
    }
    @Test fun releaseServerRequiresHttpsWithoutEmbeddedCredentialsOrPaths() {
        assertEquals("https://booking.example.com", BookingRules.serverUrl("https://booking.example.com/", false))
        listOf("http://booking.example.com", "https://user:password@booking.example.com", "https://booking.example.com/api", "https://booking.example.com?q=x").forEach {
            assertTrue("Must reject $it", runCatching { BookingRules.serverUrl(it, false) }.isFailure)
        }
    }
    @Test fun debugHttpOnlyAllowsDocumentedLocalHosts() {
        assertEquals("http://10.0.2.2:8080", BookingRules.serverUrl("http://10.0.2.2:8080", true))
        assertTrue(runCatching { BookingRules.serverUrl("http://public.example.com", true) }.isFailure)
    }
}
