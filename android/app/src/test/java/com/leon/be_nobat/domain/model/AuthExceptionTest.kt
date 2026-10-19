package com.leon.be_nobat.domain.model

import com.leon.be_nobat.data.remote.ApiError
import com.leon.be_nobat.data.remote.BeNobatApiException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.UnknownHostException

class AuthExceptionTest {

    @Test fun `fromApi passes through existing AuthException`() {
        assertSame(AuthException.InvalidCredentials, AuthException.fromApi(AuthException.InvalidCredentials))
        assertSame(AuthException.TooManyRequests, AuthException.fromApi(AuthException.TooManyRequests))
    }

    @Test fun `fromApi maps HttpRequestTimeoutException to RequestTimedOut`() {
        val ex = HttpRequestTimeoutException("https://example.com", 15_000L)
        assertSame(AuthException.RequestTimedOut, AuthException.fromApi(ex))
    }

    @Test fun `fromApi maps SocketTimeoutException to RequestTimedOut`() {
        assertSame(AuthException.RequestTimedOut, AuthException.fromApi(SocketTimeoutException("timeout")))
    }

    @Test fun `fromApi maps ConnectTimeoutException to RequestTimedOut`() {
        assertSame(AuthException.RequestTimedOut, AuthException.fromApi(ConnectTimeoutException("connect")))
    }

    @Test fun `fromApi maps UnknownHostException to NetworkUnavailable`() {
        assertSame(AuthException.NetworkUnavailable, AuthException.fromApi(UnknownHostException("host")))
    }

    @Test fun `fromApi maps IOException to NetworkUnavailable`() {
        assertSame(AuthException.NetworkUnavailable, AuthException.fromApi(IOException("io")))
    }

    @Test fun `fromApi maps 401 with invalid_credentials code to InvalidCredentials`() {
        val ex = beNobatApiException(401, "invalid_credentials")
        assertSame(AuthException.InvalidCredentials, AuthException.fromApi(ex))
    }

    @Test fun `fromApi maps 401 with account_locked code to AccountLocked`() {
        val ex = beNobatApiException(401, "account_locked")
        assertSame(AuthException.AccountLocked, AuthException.fromApi(ex))
    }

    @Test fun `fromApi maps 403 to Forbidden`() {
        val ex = beNobatApiException(403, "forbidden")
        assertSame(AuthException.Forbidden, AuthException.fromApi(ex))
    }

    @Test fun `fromApi maps 404 to NotFound`() {
        val ex = beNobatApiException(404, "not_found")
        assertSame(AuthException.NotFound, AuthException.fromApi(ex))
    }

    @Test fun `fromApi maps 409 with duplicate_email to DuplicateEmail`() {
        val ex = beNobatApiException(409, "duplicate_email")
        assertSame(AuthException.DuplicateEmail, AuthException.fromApi(ex))
    }

    @Test fun `fromApi maps 409 with password_mismatch to PasswordMismatch`() {
        val ex = beNobatApiException(409, "password_mismatch")
        assertSame(AuthException.PasswordMismatch, AuthException.fromApi(ex))
    }

    @Test fun `fromApi maps 409 with review_exists to Conflict`() {
        val ex = beNobatApiException(409, "review_exists")
        assertSame(AuthException.Conflict, AuthException.fromApi(ex))
    }

    @Test fun `fromApi maps 429 to TooManyRequests`() {
        val ex = beNobatApiException(429, "rate_limited")
        assertSame(AuthException.TooManyRequests, AuthException.fromApi(ex))
    }

    @Test fun `fromApi maps 5xx to ServiceUnavailable`() {
        assertSame(AuthException.ServiceUnavailable, AuthException.fromApi(beNobatApiException(500, "server_error")))
        assertSame(AuthException.ServiceUnavailable, AuthException.fromApi(beNobatApiException(502, "bad_gateway")))
        assertSame(AuthException.ServiceUnavailable, AuthException.fromApi(beNobatApiException(503, "unavailable")))
        assertSame(AuthException.ServiceUnavailable, AuthException.fromApi(beNobatApiException(599, "unknown")))
    }

    @Test fun `fromApi maps known codes for unmatched status codes`() {
        assertSame(AuthException.WeakPassword, AuthException.fromApi(beNobatApiException(400, "weak_password")))
        assertSame(AuthException.InvalidCredentials, AuthException.fromApi(beNobatApiException(400, "invalid_credentials")))
        assertSame(AuthException.DuplicateEmail, AuthException.fromApi(beNobatApiException(400, "duplicate_email")))
        assertSame(AuthException.Unauthorized, AuthException.fromApi(beNobatApiException(418, "unauthorized")))
        assertSame(AuthException.Forbidden, AuthException.fromApi(beNobatApiException(418, "forbidden")))
    }

    @Test fun `fromApi always treats 5xx as ServiceUnavailable regardless of code`() {
        assertSame(AuthException.ServiceUnavailable, AuthException.fromApi(beNobatApiException(500, "weak_password")))
        assertSame(AuthException.ServiceUnavailable, AuthException.fromApi(beNobatApiException(503, "invalid_credentials")))
    }

    @Test fun `fromApi maps unknown errors to Unexpected`() {
        assertSame(AuthException.Unexpected, AuthException.fromApi(RuntimeException("oops")))
        assertSame(AuthException.Unexpected, AuthException.fromApi(beNobatApiException(418, "im_a_teapot")))
    }

    @Test fun `ApiException exposes code and message`() {
        val ex = beNobatApiException(429, "rate_limited", "Too many attempts")
        assertEquals("rate_limited", ex.code)
        assertEquals("Too many attempts", ex.message)
    }

    private fun beNobatApiException(status: Int, code: String, message: String = "test"): BeNobatApiException =
        BeNobatApiException(status, ApiError(code, message, null), message)
}
