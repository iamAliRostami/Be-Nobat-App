package com.leon.be_nobat.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

object DeepLinkHelper {
    const val WEB_BASE_URL = "https://benobat.ir"
    const val APP_SCHEME = "benobat"

    /**
     * Builds the standard public web link for a business or a specific branch.
     * e.g. https://benobat.ir/b/biz-123 or https://benobat.ir/b/biz-123?branch=br-456
     */
    fun createBusinessWebLink(businessId: String, branchId: String? = null): String {
        return if (!branchId.isNullOrBlank()) {
            "$WEB_BASE_URL/b/$businessId?branch=$branchId"
        } else {
            "$WEB_BASE_URL/b/$businessId"
        }
    }

    /**
     * Builds the custom application scheme link.
     * e.g. benobat://business/biz-123 or benobat://business/biz-123?branch=br-456
     */
    fun createBusinessAppLink(businessId: String, branchId: String? = null): String {
        return if (!branchId.isNullOrBlank()) {
            "$APP_SCHEME://business/$businessId?branch=$branchId"
        } else {
            "$APP_SCHEME://business/$businessId"
        }
    }

    fun copyToClipboard(context: Context, link: String, label: String = "BeNobat Link") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, link)
        clipboard.setPrimaryClip(clip)
    }

    fun shareLink(context: Context, title: String, text: String, url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$text\n\n$url")
        }
        context.startActivity(Intent.createChooser(shareIntent, title))
    }

    data class ParsedDeepLink(
        val businessId: String?,
        val branchId: String?,
        val isBookingRoute: Boolean = false,
    )

    fun parse(uri: Uri?): ParsedDeepLink? {
        if (uri == null) return null
        return parseUrl(uri.toString())
    }

    /**
     * Pure Kotlin URL parser for unit testing and deep link handling without mocking android.net.Uri.
     */
    fun parseUrl(url: String): ParsedDeepLink? {
        if (url.isBlank()) return null
        val cleanUrl = url.trim()

        val queryIndex = cleanUrl.indexOf('?')
        val (pathPart, queryPart) = if (queryIndex >= 0) {
            cleanUrl.substring(0, queryIndex) to cleanUrl.substring(queryIndex + 1)
        } else {
            cleanUrl to ""
        }

        var branchParam: String? = null
        if (queryPart.isNotEmpty()) {
            for (param in queryPart.split('&')) {
                val pair = param.split('=', limit = 2)
                if (pair.size == 2) {
                    val key = pair[0].trim().lowercase()
                    if (key == "branch" || key == "branchid") {
                        branchParam = pair[1].trim().takeIf { it.isNotEmpty() }
                    }
                }
            }
        }

        val schemeSplit = pathPart.split("://", limit = 2)
        val afterScheme = if (schemeSplit.size == 2) schemeSplit[1] else pathPart.removePrefix("/")
        val segments = afterScheme.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        val host = segments[0].lowercase()
        val pathSegments = segments.drop(1)

        val isBookingRoute = host == "book" || (pathSegments.isNotEmpty() && pathSegments[0].equals("book", ignoreCase = true))

        if (host == "branch" || (pathSegments.isNotEmpty() && pathSegments[0].equals("branch", ignoreCase = true))) {
            val branchId = if (host == "branch" && pathSegments.isNotEmpty()) {
                pathSegments[0]
            } else if (pathSegments.size >= 2) {
                pathSegments[1]
            } else {
                pathSegments.firstOrNull()
            }
            return if (!branchId.isNullOrBlank()) {
                ParsedDeepLink(businessId = null, branchId = branchId, isBookingRoute = isBookingRoute)
            } else null
        }

        val businessId = when {
            host in listOf("business", "book") && pathSegments.isNotEmpty() -> pathSegments[0]
            pathSegments.size >= 2 && pathSegments[0].lowercase() in listOf("b", "business", "book") -> pathSegments[1]
            pathSegments.size == 1 && host in listOf("benobat.ir", "www.benobat.ir") -> pathSegments[0]
            else -> pathSegments.lastOrNull()
        }

        return if (!businessId.isNullOrBlank()) {
            ParsedDeepLink(
                businessId = businessId,
                branchId = branchParam,
                isBookingRoute = isBookingRoute,
            )
        } else null
    }
}
