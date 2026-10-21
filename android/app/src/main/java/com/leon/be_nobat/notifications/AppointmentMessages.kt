package com.leon.be_nobat.notifications

import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.helpers.JalaliDateHelper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object AppointmentMessages {
    fun status(status: String): String = when (status) {
        "Pending" -> "در انتظار تأیید"
        "Confirmed" -> "تأیید شده"
        "Completed" -> "انجام شده"
        "Cancelled" -> "لغو شده"
        "NoShow" -> "عدم حضور"
        else -> status
    }

    // Format using the appointment's branch timezone, independent of the manager's device.
    fun date(iso: String, zone: String): String = try {
        val normalized = iso.replace(Regex("\\.\\d+(?=Z|[+-]\\d{2}:?\\d{2}$)"), "")
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val value = requireNotNull(parser.parse(normalized))
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { time = value }
        val j = JalaliDateHelper.gregorianToJalali(cal.get(1), cal.get(2) + 1, cal.get(5))
        "%04d/%02d/%02d ساعت %02d:%02d".format(Locale.US, j.year, j.month, j.day, cal.get(11), cal.get(12))
    } catch (_: Exception) { iso }

    fun body(status: String, business: String, branch: String, services: List<String>, startsAt: String,
             timeZoneId: String, trackingCode: String): String = buildString {
        append("به‌نوبت\nوضعیت نوبت شما: ${status(status)}\n")
        append(listOf(business, branch).filter { it.isNotBlank() }.joinToString(" - "))
        if (services.isNotEmpty()) append("\nخدمات: ${services.joinToString("، ")}")
        append("\nزمان: ${date(startsAt, timeZoneId)}")
        if (trackingCode.isNotBlank()) append("\nکد پیگیری: $trackingCode")
    }

    fun body(a: Appointment) = body(a.status, a.businessName, a.branchName, a.serviceNames, a.startsAt, a.timeZoneId, a.trackingCode)
    fun body(a: ManagementAppointment) = body(a.status, a.businessName, a.branchName,
        a.serviceNames.ifEmpty { listOf(a.serviceName).filter { it.isNotBlank() } }, a.startsAt, a.timeZoneId, a.trackingCode)

    fun phone(value: String?): String? {
        val text = value.orEmpty().map { c -> when (c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            else -> c
        } }.joinToString("").replace(Regex("[\\s()\\-]"), "")
        return text.takeIf { Regex("\\+?[0-9]{7,15}").matches(it) }
    }
}
