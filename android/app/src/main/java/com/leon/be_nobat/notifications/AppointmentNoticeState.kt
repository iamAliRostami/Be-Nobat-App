package com.leon.be_nobat.notifications

import com.leon.be_nobat.domain.model.Appointment
import kotlinx.serialization.Serializable

@Serializable
data class AppointmentNotice(val id: String, val appointmentId: String, val title: String, val body: String,
    val createdAt: Long, val read: Boolean = false, val posted: Boolean = false)

@Serializable
data class AppointmentNoticeState(val initialized: Boolean = false, val statuses: Map<String, String> = emptyMap(),
    val notices: List<AppointmentNotice> = emptyList()) {
    /** Only complete successful snapshots reach this function; absent rows never imply cancellation. */
    fun update(appointments: List<Appointment>, now: Long): AppointmentNoticeState {
        val changes = if (!initialized) emptyList() else appointments.filter {
            val previous = statuses[it.id]
            (previous != null && previous != it.status) || (previous == null && it.status != "Pending")
        }.map {
            AppointmentNotice(java.util.UUID.randomUUID().toString(), it.id,
                "وضعیت نوبت: ${AppointmentMessages.status(it.status)}", AppointmentMessages.body(it), now)
        }
        return copy(initialized = true, statuses = statuses + appointments.associate { it.id to it.status },
            notices = (changes + notices).take(200))
    }
}
