package com.leon.be_nobat.data.demo

import com.leon.be_nobat.domain.model.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun demoFormatter() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tehran")
    isLenient = false
}

internal fun demoMillis(value: String): Long {
    val clean = value.trim()
    val full = when {
        clean.length == 10 -> "${clean}T00:00:00"
        clean.length == 16 -> "${clean}:00"
        clean.length >= 19 -> clean.take(19)
        else -> clean
    }
    return requireNotNull(demoFormatter().parse(full)).time
}

internal fun demoFormat(value: Long): String = demoFormatter().format(java.util.Date(value))

private fun timeToMillis(time: String): Long {
    val clean = time.trim()
    val parts = clean.split(":").mapNotNull { it.toIntOrNull() }
    val h = parts.getOrElse(0) { 0 }
    val m = parts.getOrElse(1) { 0 }
    val s = parts.getOrElse(2) { 0 }
    return (h * 3600L + m * 60L + s) * 1000L
}

internal fun DemoDatabaseState.slots(
    businessId: String,
    branchId: String,
    ids: List<String>,
    date: String,
    resourceId: String?,
    includePastSlots: Boolean = false
): SlotsResponse {
    require(businesses.any { it.id == businessId && it.active })
    val branch = branches.first { it.id == branchId && it.businessId == businessId && it.active }
    require(ids.isNotEmpty() && ids.distinct().size == ids.size)
    val selected = ids.map { id -> services.first { it.id == id && it.businessId == businessId && it.active && branchId in it.branchIds } }
    val duration = selected.sumOf { it.durationMinutes }
    require(duration > 0)
    val price = selected.sumOf { service -> branchServices.firstOrNull { it.branchId == branchId && it.serviceId == service.id }?.effectivePrice ?: service.price }
    val midnight = demoMillis("${date}T00:00:00")
    val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone(branch.timeZoneId)).apply { timeInMillis = midnight }
    val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
    val dayOfWeekName = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.SATURDAY -> "Saturday"
        java.util.Calendar.SUNDAY -> "Sunday"
        java.util.Calendar.MONDAY -> "Monday"
        java.util.Calendar.TUESDAY -> "Tuesday"
        java.util.Calendar.WEDNESDAY -> "Wednesday"
        java.util.Calendar.THURSDAY -> "Thursday"
        java.util.Calendar.FRIDAY -> "Friday"
        else -> ""
    }
    val windows = (branch.weeklyHours ?: WeeklyHours.legacy(branch.openHour, branch.closeHour)).filter { it.dayOfWeek == dayOfWeek }
    val open = midnight
    val close = midnight + 86400000L
    val available = resources.filter {
        it.active && it.branchId == branchId &&
        (resourceId.isNullOrBlank() || it.id == resourceId) &&
        (resourceServices[it.id].isNullOrEmpty() || resourceServices[it.id].orEmpty().containsAll(ids))
    }
    val results = available.flatMap { r ->
        val rules = availabilities.filter {
            it.businessId == businessId &&
            (it.branchId == null || it.branchId == branchId) &&
            (it.resourceId == null || it.resourceId == r.id) &&
            (it.effectiveDate == date || (it.effectiveDate.isNullOrBlank() && it.dayOfWeek.equals(dayOfWeekName, ignoreCase = true)))
        }
        val openRules = rules.filter { it.isAvailable }
        val blockedRules = rules.filterNot { it.isAvailable }
        val occupied = appointments.filter { it.resourceId == r.id && it.status in listOf("Pending", "Confirmed", "Completed") && it.startsAt.startsWith(date) }

        generateSequence(open) { it + 15 * 60000L }.takeWhile { it + duration * 60000L <= close }.mapNotNull { start ->
            val end = start + duration * 60000L
            val inStandardHours = windows.any {
                start >= midnight + WeeklyHours.seconds(it.startsAt) * 1000L && end <= midnight + WeeklyHours.seconds(it.endsAt) * 1000L
            }
            val inOpenRule = openRules.any {
                start >= midnight + timeToMillis(it.startsAt) && end <= midnight + timeToMillis(it.endsAt)
            }
            val isOpen = inStandardHours || inOpenRule
            val isBlocked = blockedRules.any {
                start < midnight + timeToMillis(it.endsAt) && end > midnight + timeToMillis(it.startsAt)
            }
            val isOccupied = occupied.any {
                start < demoMillis(it.endsAt) && end > demoMillis(it.startsAt)
            }

            if ((!includePastSlots && start <= System.currentTimeMillis()) || !isOpen || isBlocked || isOccupied) null
            else Slot(resourceId = r.id, resourceName = r.name, startsAt = demoFormat(start), endsAt = demoFormat(end))
        }.toList()
    }.let { list ->
        if (resourceId.isNullOrBlank()) list.distinctBy { it.startsAt } else list
    }.sortedBy { it.startsAt }
    return SlotsResponse(date = date, timeZoneId = branch.timeZoneId, totalDurationMinutes = duration, totalPrice = price, currency = selected.first().currency, slots = results)
}
