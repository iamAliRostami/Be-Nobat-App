package com.leon.be_nobat.notifications

import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.model.ManagementAppointment
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class AppointmentNotificationsTest {
    private val appointment = Appointment(id = "a1", status = "Pending", businessName = "درمانگاه مهر",
        branchName = "شعبه مرکزی", serviceNames = listOf("مشاوره"), startsAt = "2026-10-21T06:30:00.0000000+00:00",
        timeZoneId = "Asia/Tehran", trackingCode = "BN123")

    @Test fun `initial snapshot is a silent baseline`() {
        val state = AppointmentNoticeState().update(listOf(appointment.copy(status = "Completed")), 1)
        assertTrue(state.initialized); assertTrue(state.notices.isEmpty())
    }
    @Test fun `every status change creates one durable notice and identical polls create none`() {
        var state = AppointmentNoticeState().update(listOf(appointment), 1)
        for (status in listOf("Confirmed", "Completed", "Cancelled", "NoShow", "Pending")) {
            state = state.update(listOf(appointment.copy(status = status)), 2)
        }
        assertEquals(5, state.notices.size)
        assertTrue(state.notices.first().body.contains("BN123"))
        val restored = Json.decodeFromString<AppointmentNoticeState>(Json.encodeToString(AppointmentNoticeState.serializer(), state))
        assertEquals(state, restored.update(listOf(appointment), 3))
    }
    @Test fun `missing rows never imply cancellation and known rows still detect changes after absence`() {
        val state = AppointmentNoticeState().update(listOf(appointment), 1).update(emptyList(), 2)
        assertTrue(state.notices.isEmpty())
        assertEquals(1, state.update(listOf(appointment.copy(status = "Cancelled")), 3).notices.size)
    }
    @Test fun `new confirmed appointment is notified after baseline and inbox is bounded`() {
        var state = AppointmentNoticeState().update(emptyList(), 1)
        state = state.update((1..230).map { appointment.copy(id = "a$it", status = "Confirmed") }, 2)
        assertEquals(200, state.notices.size)
        assertEquals(230, state.statuses.size)
    }
    @Test fun `phone accepts Persian Arabic and international digits and rejects malformed recipients`() {
        assertEquals("09121234567", AppointmentMessages.phone("۰۹۱۲ ۱۲۳-۴۵۶۷"))
        assertEquals("+989121234567", AppointmentMessages.phone("+٩٨٩١٢١٢٣٤٥٦٧"))
        listOf(null, "", "0912;123", "++989123456789", "123", "phone").forEach { assertNull(AppointmentMessages.phone(it)) }
    }
    @Test fun `customer message includes new status branch services tracking code and branch local time`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val body = AppointmentMessages.body(appointment.copy(status = "Confirmed"))
            listOf("تأیید شده", "درمانگاه مهر", "شعبه مرکزی", "مشاوره", "BN123", "10:00").forEach { assertTrue(body, body.contains(it)) }
        } finally { TimeZone.setDefault(original) }
        assertTrue(AppointmentMessages.body(ManagementAppointment(status = "NoShow", serviceName = "ویزیت")).contains("عدم حضور"))
    }
}
