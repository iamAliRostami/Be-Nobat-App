package com.leon.be_nobat.domain

import com.leon.be_nobat.domain.model.ManagementAppointment
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object CancellationPolicy {
    const val DEFAULT_MINUTES = 120
    const val MAXIMUM_MINUTES = 129600
    fun valid(minutes: Int?) = minutes == null || minutes in 0..MAXIMUM_MINUTES
    fun resolve(business: Int = DEFAULT_MINUTES, branch: Int? = null, service: Int? = null, branchService: Int? = null): Int =
        branchService ?: service ?: branch ?: business
    fun instant(iso: String): Long? = try {
        if (iso.contains("Z") || Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(iso)) {
            val fraction = Regex("\\.(\\d+)(?=Z|[+-]\\d{2}:?\\d{2}$)")
            val hasFraction = fraction.containsMatchIn(iso)
            val normalized = fraction.replace(iso) { "." + it.groupValues[1].padEnd(3, '0').take(3) }
            val format = if (hasFraction) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
            SimpleDateFormat(format, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }.parse(normalized)?.time
        } else {
            val fraction = Regex("\\.(\\d+)")
            val hasFraction = fraction.containsMatchIn(iso)
            val normalized = fraction.replace(iso) { "." + it.groupValues[1].padEnd(3, '0').take(3) }
            val format = if (hasFraction) "yyyy-MM-dd'T'HH:mm:ss.SSS" else "yyyy-MM-dd'T'HH:mm:ss"
            SimpleDateFormat(format, Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Tehran"); isLenient = false }.parse(normalized.take(if (hasFraction) 23 else 19))?.time
        }
    } catch (_: Exception) { null }
    fun deadline(startsAt: String, minutes: Int): String? = instant(startsAt)?.let {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(it - minutes * 60_000L))
    }
    fun canCancel(status: String, startsAt: String, minutes: Int, now: Long = System.currentTimeMillis()): Boolean {
        val start = instant(startsAt) ?: return false
        return status in listOf("Pending", "Confirmed") && start > now && start - now >= minutes * 60_000L
    }
}
