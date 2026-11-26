package com.leon.be_nobat.notifications

import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.model.ManagementAppointment
import kotlinx.serialization.Serializable

@Serializable
data class AppointmentNotice(val id: String, val appointmentId: String, val title: String, val body: String,
    val createdAt: Long, val read: Boolean = false, val posted: Boolean = false)

@Serializable
data class AppointmentNoticeState(
    val initialized: Boolean = false,
    val statuses: Map<String, String> = emptyMap(),
    val managementInitialized: Boolean = false,
    val managementStatuses: Map<String, String> = emptyMap(),
    val notices: List<AppointmentNotice> = emptyList(),
) {
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

    /** Tracks branch/provider appointments for Owner, Manager, and Staff to notify on new bookings and status changes. */
    fun updateManaged(appointments: List<ManagementAppointment>, now: Long): AppointmentNoticeState {
        val changes = if (!managementInitialized) emptyList() else appointments.mapNotNull { appt ->
            val previous = managementStatuses[appt.id]
            when {
                previous == null -> AppointmentNotice(
                    id = java.util.UUID.randomUUID().toString(),
                    appointmentId = appt.id,
                    title = "نوبت جدید: ${appt.customerName.ifBlank { appt.branchName }}",
                    body = AppointmentMessages.newAppointmentBody(appt),
                    createdAt = now,
                )
                previous != appt.status -> AppointmentNotice(
                    id = java.util.UUID.randomUUID().toString(),
                    appointmentId = appt.id,
                    title = "تغییر وضعیت نوبت: ${AppointmentMessages.status(appt.status)}",
                    body = AppointmentMessages.newAppointmentBody(appt),
                    createdAt = now,
                )
                else -> null
            }
        }
        return copy(
            managementInitialized = true,
            managementStatuses = managementStatuses + appointments.associate { it.id to it.status },
            notices = (changes + notices).take(200),
        )
    }
}
