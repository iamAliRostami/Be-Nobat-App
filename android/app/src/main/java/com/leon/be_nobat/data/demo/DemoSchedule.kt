package com.leon.be_nobat.data.demo

import com.leon.be_nobat.domain.model.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun demoFormatter() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tehran")
    isLenient = false
}
internal fun demoMillis(value: String): Long = requireNotNull(demoFormatter().parse(value.take(19))).time
internal fun demoFormat(value: Long): String = demoFormatter().format(java.util.Date(value))

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
    val dayOfWeek = java.util.Calendar.getInstance(TimeZone.getTimeZone(branch.timeZoneId)).apply { timeInMillis = midnight }.get(java.util.Calendar.DAY_OF_WEEK) - 1
    val windows = (branch.weeklyHours ?: WeeklyHours.legacy(branch.openHour, branch.closeHour)).filter { it.dayOfWeek == dayOfWeek }
    val open = midnight
    val close = midnight + 86400000L
    val available = resources.filter {
        it.active && it.branchId == branchId &&
        (resourceId.isNullOrBlank() || it.id == resourceId) &&
        (resourceServices[it.id].isNullOrEmpty() || resourceServices[it.id].orEmpty().containsAll(ids))
    }
    val results = available.flatMap { r ->
        val rules = availabilities.filter { it.businessId == businessId && (it.branchId == null || it.branchId == branchId) && (it.resourceId == null || it.resourceId == r.id) && it.effectiveDate == date }
        val occupied = appointments.filter { it.resourceId == r.id && it.status in listOf("Pending", "Confirmed", "Completed") && it.startsAt.startsWith(date) }
        generateSequence(open) { it + 15 * 60000L }.takeWhile { it + duration * 60000L <= close }.mapNotNull { start ->
            val end = start + duration * 60000L
            val overtimeRule = rules.any { it.isAvailable && it.effectiveDate == date && start >= demoMillis("${date}T${it.startsAt}") && end <= demoMillis("${date}T${it.endsAt}") }
            val inStandardHours = windows.any { start >= midnight + WeeklyHours.seconds(it.startsAt) * 1000L && end <= midnight + WeeklyHours.seconds(it.endsAt) * 1000L }
            val branchOpen = inStandardHours || overtimeRule
            val within = rules.filter { it.isAvailable }.let { windows -> windows.isEmpty() || windows.any { start >= demoMillis("${date}T${it.startsAt}") && end <= demoMillis("${date}T${it.endsAt}") } }
            val blocked = rules.filterNot { it.isAvailable }.any { start < demoMillis("${date}T${it.endsAt}") && end > demoMillis("${date}T${it.startsAt}") }
            if ((!includePastSlots && start <= System.currentTimeMillis()) || !branchOpen || !within || blocked || occupied.any { start < demoMillis(it.endsAt) && end > demoMillis(it.startsAt) }) null
            else Slot(resourceId = r.id, resourceName = r.name, startsAt = demoFormat(start), endsAt = demoFormat(end))
        }.toList()
    }.let { list ->
        if (resourceId.isNullOrBlank()) list.distinctBy { it.startsAt } else list
    }.sortedBy { it.startsAt }
    return SlotsResponse(date = date, timeZoneId = branch.timeZoneId, totalDurationMinutes = duration, totalPrice = price, currency = selected.first().currency, slots = results)
}
