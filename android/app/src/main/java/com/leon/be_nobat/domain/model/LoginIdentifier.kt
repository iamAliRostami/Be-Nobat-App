package com.leon.be_nobat.domain.model

import com.leon.be_nobat.data.remote.BeNobatApiException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import java.net.UnknownHostException

/**
 * Identifier accepted by the customer login form. The new .NET backend
 * `/auth/login` endpoint expects an email address; if the user types a
 * mobile number we surface a clear validation error so they can correct it.
 *
 * The parser is kept around for the register flow (where `phoneNumber` is a
 * separate field) and for future migrations.
 */
sealed interface LoginIdentifier {
    val value: String

    data class Email(override val value: String) : LoginIdentifier
    data class Mobile(override val value: String) : LoginIdentifier

    companion object {
        private val emailPattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
        private val iranianMobilePattern = Regex("^(?:\\+98|0098|98|0)?9\\d{9}$")

        fun parse(rawValue: String): Result<LoginIdentifier> {
            val normalized = rawValue.trim()
            return when {
                emailPattern.matches(normalized) -> Result.success(Email(normalized.lowercase()))
                iranianMobilePattern.matches(normalized) -> Result.success(
                    Mobile(normalizeIranianMobile(normalized))
                )
                else -> Result.failure(AuthException.InvalidIdentifier)
            }
        }

        /** Iranian mobile canonical form: `+989XXXXXXXXX`. */
        fun normalizeIranianMobile(value: String): String {
            val nationalNumber = when {
                value.startsWith("0098") -> value.removePrefix("0098")
                value.startsWith("+98") -> value.removePrefix("+98")
                value.startsWith("98") -> value.removePrefix("98")
                value.startsWith("0") -> value.removePrefix("0")
                else -> value
            }
            return "+98$nationalNumber"
        }

        /** Iranian mobile as required by `/auth/register`: `09XXXXXXXXX`. */
        fun normalizeIranianMobileForRegister(value: String): String {
            val national = when {
                value.startsWith("0098") -> value.removePrefix("0098")
                value.startsWith("+98") -> value.removePrefix("+98")
                value.startsWith("98") -> value.removePrefix("98")
                value.startsWith("0") -> value.removePrefix("0")
                else -> value
            }
            return "0$national"
        }
    }
}

/**
 * Domain-level auth/account errors. The `fromApi` companion maps
 * [BeNobatApiException] (HTTP-level) into one of these typed errors so the
 * UI can show a localized message per case.
 */
sealed class AuthException : Exception() {
    data object InvalidIdentifier : AuthException()
    data object EmptyPassword : AuthException()
    data object WeakPassword : AuthException()
    data object InvalidCredentials : AuthException()
    data object AccountLocked : AuthException()
    data object DuplicateEmail : AuthException()
    data object PasswordMismatch : AuthException()
    data object Unauthorized : AuthException()
    data object Forbidden : AuthException()
    data object NotFound : AuthException()
    data object Conflict : AuthException()
    data object TooManyRequests : AuthException()
    data object ServiceUnavailable : AuthException()
    data object NetworkUnavailable : AuthException()
    data object RequestTimedOut : AuthException()
    data object Unexpected : AuthException()

    companion object {
        /**
         * Map a [BeNobatApiException] (or any Throwable) into a typed
         * [AuthException]. Falls back to [Unexpected] for unknown errors.
         */
        fun fromApi(throwable: Throwable): AuthException = when (throwable) {
            is AuthException -> throwable
            is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException -> RequestTimedOut
            is UnknownHostException, is IOException -> NetworkUnavailable
            is BeNobatApiException -> {
                when (throwable.statusCode) {
                    401 -> when (throwable.code) {
                        "account_locked" -> AccountLocked
                        else -> InvalidCredentials
                    }
                    403 -> Forbidden
                    404 -> NotFound
                    409 -> when (throwable.code) {
                        "duplicate_email" -> DuplicateEmail
                        "password_mismatch" -> PasswordMismatch
                        "review_exists" -> Conflict
                        else -> Conflict
                    }
                    429 -> TooManyRequests
                    in 500..599 -> ServiceUnavailable
                    else -> when (throwable.code) {
                        "invalid_credentials" -> InvalidCredentials
                        "account_locked" -> AccountLocked
                        "duplicate_email" -> DuplicateEmail
                        "password_mismatch" -> PasswordMismatch
                        "weak_password" -> WeakPassword
                        "rate_limited" -> TooManyRequests
                        "unauthorized" -> Unauthorized
                        "forbidden" -> Forbidden
                        "not_found" -> NotFound
                        else -> Unexpected
                    }
                }
            }
            else -> Unexpected
        }
    }
}
