package com.leon.be_nobat.helpers

import android.content.Context
import com.leon.be_nobat.R
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
    val isConnectionError: Boolean = true
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
                    description = context.getString(R.string.error_unauthorized),
                    isConnectionError = false
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
            else -> {}
        }

        // Check if string contains standard server errors
        if (!message.isNullOrBlank()) {
            if (message == "terms_required") {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.error_terms_required),
                    isConnectionError = false
                )
            }
            val lower = message.lowercase()
            if (lower.contains("503") || lower.contains("service unavailable")) {
                return FormattedError(
                    title = context.getString(R.string.error_service_unavailable_title),
                    description = context.getString(R.string.error_service_unavailable),
                    isConnectionError = false
                )
            }
            if (lower.contains("500") || lower.contains("internal server error")) {
                return FormattedError(
                    title = context.getString(R.string.error_generic),
                    description = context.getString(R.string.unexpected_error),
                    isConnectionError = false
                )
            }
            // If the message is a user-friendly message without stack trace / url / technical code
            if (!message.contains("Exception") && !message.contains("/") && !message.contains("HTTP ")) {
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
