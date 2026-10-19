package com.leon.be_nobat.data.demo

import com.leon.be_nobat.data.remote.ApiPage
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun demoId() = UUID.randomUUID().toString()
internal fun demoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }.format(Date())
internal fun <T> List<T>.demoPage(page: Int, pageSize: Int): ApiPage<T> {
    require(page >= 1 && pageSize in 1..1000) { "صفحه نامعتبر است." }
    val offset = ((page.toLong() - 1) * pageSize).coerceAtMost(size.toLong()).toInt()
    return ApiPage(items = drop(offset).take(pageSize), page = page, pageSize = pageSize, total = size)
}
internal fun String.matchesQuery(q: String?) = q.isNullOrBlank() || contains(q.trim(), ignoreCase = true)

@Volatile
internal var demoNetworkDelayEnabled: Boolean = true

internal suspend fun simulateNetworkDelay(minMs: Long = 180L, maxMs: Long = 320L) {
    if (demoNetworkDelayEnabled) {
        val delayTime = if (maxMs > minMs) kotlin.random.Random.nextLong(minMs, maxMs) else minMs
        kotlinx.coroutines.delay(delayTime)
    }
}

internal fun reviewStatusMatches(reviewStatus: String, queryStatus: String?): Boolean {
    if (queryStatus.isNullOrBlank()) return true
    val q = queryStatus.trim()
    if (q.equals("Published", ignoreCase = true) || q.equals("Approved", ignoreCase = true)) {
        return reviewStatus.equals("Published", ignoreCase = true) || reviewStatus.equals("Approved", ignoreCase = true)
    }
    return reviewStatus.equals(q, ignoreCase = true)
}

