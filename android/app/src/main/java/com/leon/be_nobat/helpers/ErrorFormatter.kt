package com.leon.be_nobat.helpers

import android.content.Context
import com.leon.be_nobat.R
import com.leon.be_nobat.data.remote.BeNobatApiException
import com.leon.be_nobat.domain.model.AuthException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class FormattedError(
    val title: String,
    val description: String,
    val isConnectionError: Boolean = false,
    val isAuthError: Boolean = false,
)

object ErrorFormatter {

    fun isConnectionIssue(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        if (throwable is ConnectException ||
            throwable is UnknownHostException ||
            throwable is SocketTimeoutException ||
            throwable is SocketException ||
            throwable is HttpRequestTimeoutException ||
            throwable is AuthException.NetworkUnavailable ||
            throwable is AuthException.RequestTimedOut ||
            throwable is IOException
        ) {
            return true
        }
        return isConnectionIssue(throwable.message)
    }

    fun isConnectionIssue(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val lower = message.lowercase()
        return lower.contains("failed to connect") ||
                lower.contains("unable to connect") ||
                lower.contains("connection refused") ||
                lower.contains("connection reset") ||
                lower.contains("connection abort") ||
                lower.contains("unable to resolve host") ||
                lower.contains("network is unreachable") ||
                lower.contains("no route to host") ||
                lower.contains("timed out") ||
                lower.contains("timeout") ||
                lower.contains("connectexception") ||
                lower.contains("socketexception") ||
                lower.contains("unknownhostexception") ||
                lower.contains("econnrefused") ||
                lower.contains("econnreset") ||
                lower.contains("broken pipe") ||
                lower.contains("networkunavailable") ||
                lower.contains("requesttimedout") ||
                lower.contains("127.0.0.1") ||
                lower.contains("localhost")
    }

    fun resolve(context: Context, error: Any?): FormattedError {
        val throwable = error as? Throwable
        val message = when (error) {
            is Throwable -> error.message
            is String -> error
            else -> null
        }

        if ((throwable as? BeNobatApiException)?.code == "cancellation_unavailable" ||
            message?.contains("The cancellation deadline has passed", ignoreCase = true) == true) {
            return FormattedError(context.getString(R.string.error_generic), context.getString(R.string.error_cancellation_deadline))
        }

        if (isConnectionIssue(throwable) || isConnectionIssue(message)) {
            return FormattedError(
                title = context.getString(R.string.error_connection_title),
                description = context.getString(R.string.error_connection_description),
                isConnectionError = true
            )
        }

        when (throwable) {
            is AuthException.ServiceUnavailable -> {
                return FormattedError(
                    title = context.getString(R.string.error_service_unavailable_title),
                    description = context.getString(R.string.error_service_unavailable),
                    isConnectionError = false
                )
            }
            is AuthException.TooManyRequests -> {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.error_too_many_requests),
                    isConnectionError = false
                )
            }
            is AuthException.Unauthorized -> {
                return FormattedError(
                    title = context.getString(R.string.login_required_title),
                    description = context.getString(R.string.error_session_expired),
                    isConnectionError = false,
                    isAuthError = true,
                )
            }
            is AuthException.Forbidden -> {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.error_forbidden),
                    isConnectionError = false
                )
            }
            is AuthException.NotFound -> {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.error_not_found),
                    isConnectionError = false
                )
            }
            is BeNobatApiException -> {
                when {
                    throwable.statusCode == 401 || throwable.code.equals("unauthorized", ignoreCase = true) ||
                            throwable.message.contains("Sign in to continue", ignoreCase = true) -> {
                        return FormattedError(
                            title = context.getString(R.string.login_required_title),
                            description = context.getString(R.string.error_session_expired),
                            isConnectionError = false,
                            isAuthError = true,
                        )
                    }
                    throwable.statusCode == 403 || throwable.code.equals("forbidden", ignoreCase = true) -> {
                        return FormattedError(
                            title = context.getString(R.string.error_generic),
                            description = context.getString(R.string.error_forbidden),
                            isConnectionError = false,
                        )
                    }
                    throwable.statusCode == 404 || throwable.code.equals("not_found", ignoreCase = true) -> {
                        return FormattedError(
                            title = context.getString(R.string.error_generic),
                            description = context.getString(R.string.error_not_found),
                            isConnectionError = false,
                        )
                    }
                    throwable.statusCode == 409 || throwable.code.equals("conflict", ignoreCase = true) -> {
                        return FormattedError(
                            title = context.getString(R.string.error_generic),
                            description = context.getString(R.string.error_conflict),
                            isConnectionError = false,
                        )
                    }
                    throwable.statusCode == 429 || throwable.code.equals("rate_limited", ignoreCase = true) -> {
                        return FormattedError(
                            title = context.getString(R.string.error_generic),
                            description = context.getString(R.string.error_too_many_requests),
                            isConnectionError = false,
                        )
                    }
                    throwable.statusCode == 503 -> {
                        return FormattedError(
                            title = context.getString(R.string.error_service_unavailable_title),
                            description = context.getString(R.string.error_service_unavailable),
                            isConnectionError = false,
                        )
                    }
                    else -> {}
                }
            }
            else -> {}
        }

        // Check if string contains standard server errors or codes
        if (!message.isNullOrBlank()) {
            if (message == "terms_required") {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.error_terms_required),
                    isConnectionError = false
                )
            }
            val lower = message.lowercase()
            if (lower.contains("sign in to continue") ||
                lower.contains("unauthorized") ||
                lower.contains("invalid credential") ||
                lower.contains("expired credential") ||
                lower.contains("revoked credential") ||
                lower == "401" || lower.startsWith("401 ") || lower.contains("http 401")
            ) {
                return FormattedError(
                    title = context.getString(R.string.login_required_title),
                    description = context.getString(R.string.error_session_expired),
                    isConnectionError = false,
                    isAuthError = true,
                )
            }
            if (lower.contains("forbidden") || lower.contains("do not have access") ||
                lower == "403" || lower.startsWith("403 ") || lower.contains("http 403")
            ) {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.error_forbidden),
                    isConnectionError = false,
                )
            }
            if (lower.contains("conflict") || lower.contains("already exists") || lower.contains("record changed") ||
                lower == "409" || lower.startsWith("409 ") || lower.contains("http 409")
            ) {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.error_conflict),
                    isConnectionError = false,
                )
            }
            if (lower.contains("not found") || lower.contains("not_found") ||
                lower == "404" || lower.startsWith("404 ") || lower.contains("http 404")
            ) {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.error_not_found),
                    isConnectionError = false,
                )
            }
            if (lower.contains("rate_limited") || lower.contains("too many attempts") ||
                lower == "429" || lower.startsWith("429 ") || lower.contains("http 429")
            ) {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.error_too_many_requests),
                    isConnectionError = false,
                )
            }
            if (lower.contains("invalid_input") || lower.contains("invalid parameter") || lower.contains("invalid body") ||
                lower == "400" || lower.startsWith("400 ") || lower.contains("http 400")
            ) {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.msg_invalid_input),
                    isConnectionError = false,
                )
            }
            if (lower.contains("503") || lower.contains("service unavailable")) {
                return FormattedError(
                    title = context.getString(R.string.error_service_unavailable_title),
                    description = context.getString(R.string.error_service_unavailable),
                    isConnectionError = false
                )
            }
            if (lower.contains("500") || lower.contains("internal server error") || lower.contains("server_error") || lower.contains("could not be completed")) {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.unexpected_error),
                    isConnectionError = false
                )
            }
            // If the message is already in Persian/Arabic, display directly
            val isPersianOrArabic = message.any { it in '\u0600'..'\u06FF' }
            if (isPersianOrArabic && !message.contains("Exception") && !message.contains("/") && !message.contains("HTTP ")) {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = message,
                    isConnectionError = false
                )
            }
        }

        return FormattedError(
            title = context.getString(R.string.error_generic),
            description = context.getString(R.string.unexpected_error),
            isConnectionError = false
        )
    }

    fun format(context: Context, error: Any?): String {
        return resolve(context, error).description
    }
}
