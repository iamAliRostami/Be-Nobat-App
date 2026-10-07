package com.leon.be_nobat.domain

import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate
import java.time.Instant

/** Rules shared by the native form and local unit tests. The server remains authoritative. */
object BookingRules {
    fun validEmail(value: String) = value.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
    fun validPhone(value: String): Boolean {
        var normalized = value.trim().map { char -> when (char) {
            in '۰'..'۹' -> '0' + (char - '۰'); in '٠'..'٩' -> '0' + (char - '٠'); else -> char
        } }.joinToString("").replace(" ", "").replace("-", "")
        if (normalized.startsWith("+98")) normalized = "0" + normalized.drop(3)
        else if (normalized.startsWith("0098")) normalized = "0" + normalized.drop(4)
        return normalized.matches(Regex("09[0-9]{9}"))
    }
    fun validPassword(value: String) = value.length in 6..256 && value.any { it in 'a'..'z' } && value.any { it in '0'..'9' }
    fun total(prices: List<BigDecimal>): BigDecimal = prices.fold(BigDecimal.ZERO, BigDecimal::add)
    fun duration(minutes: List<Int>): Int = minutes.sum()
    fun validDate(value: String, today: LocalDate = LocalDate.now()): Boolean =
        runCatching { LocalDate.parse(value) in today..today.plusDays(90) }.getOrDefault(false)
    fun statusActions(status: String, startsAt: String, now: Instant = Instant.now()): List<String> {
        val started = runCatching { !Instant.parse(startsAt).isAfter(now) }.getOrDefault(false)
        return when (status) {
            "Pending" -> listOf("Confirmed", "Cancelled") + if (started) listOf("Completed", "NoShow") else emptyList()
            "Confirmed" -> listOf("Cancelled") + if (started) listOf("Completed", "NoShow") else emptyList()
            else -> emptyList()
        }
    }
    fun serverUrl(value: String, debug: Boolean): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
        require(uri != null && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.scheme == "https" || (debug && uri.scheme == "http" && uri.host in setOf("10.0.2.2", "localhost", "127.0.0.1")))
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        return value.trim().trimEnd('/')
    }
}
