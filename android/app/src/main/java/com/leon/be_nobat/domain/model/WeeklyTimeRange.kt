package com.leon.be_nobat.domain.model

import kotlinx.serialization.Serializable

/** Sunday = 0, Saturday = 6, matching the API's DayOfWeek values. */
@Serializable
data class WeeklyTimeRange(val dayOfWeek: Int, val startsAt: String = "08:00:00", val endsAt: String = "20:00:00")

object WeeklyHours {
    fun defaults() = listOf(6, 0, 1, 2, 3, 4).map { WeeklyTimeRange(it) }
    fun legacy(openHour: Int, closeHour: Int) = (0..6).map {
        WeeklyTimeRange(it, "%02d:00:00".format(java.util.Locale.US, openHour), if (closeHour == 24) "23:59:59" else "%02d:00:00".format(java.util.Locale.US, closeHour))
    }
    fun seconds(time: String): Int {
        require(Regex("\\d{2}:\\d{2}(:\\d{2})?").matches(time)) { "زمان نامعتبر است." }
        val parts = time.split(":").map { it.toInt() }
        require(parts[0] in 0..23 && parts[1] in 0..59 && parts.getOrElse(2) { 0 } in 0..59)
        return parts[0] * 3600 + parts[1] * 60 + parts.getOrElse(2) { 0 }
    }
    fun validate(hours: List<WeeklyTimeRange>): String? = runCatching {
        require(hours.size <= 56) { "حداکثر ۵۶ بازه در هفته مجاز است." }
        hours.forEach { require(it.dayOfWeek in 0..6 && seconds(it.startsAt) < seconds(it.endsAt)) { "پایان بازه باید بعد از شروع باشد." } }
        hours.groupBy { it.dayOfWeek }.values.forEach { day ->
            day.sortedBy { seconds(it.startsAt) }.zipWithNext().forEach { (a, b) ->
                require(seconds(a.endsAt) <= seconds(b.startsAt)) { "بازه‌های یک روز نباید هم‌پوشانی داشته باشند." }
            }
        }
    }.exceptionOrNull()?.message
}
