package com.leon.be_nobat.helpers

import com.leon.be_nobat.domain.model.AuthException
import io.ktor.client.plugins.HttpRequestTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorFormatterTest {

    @Test
    fun `isConnectionIssue returns true for connection exceptions`() {
        assertTrue(ErrorFormatter.isConnectionIssue(ConnectException("Failed to connect to /127.0.0.1:8080")))
        assertTrue(ErrorFormatter.isConnectionIssue(UnknownHostException("Unable to resolve host")))
        assertTrue(ErrorFormatter.isConnectionIssue(SocketTimeoutException("connect timed out")))
        assertTrue(ErrorFormatter.isConnectionIssue(SocketException("Connection refused")))
        assertTrue(ErrorFormatter.isConnectionIssue(HttpRequestTimeoutException("https://api.benobat.ir", 10000L)))
        assertTrue(ErrorFormatter.isConnectionIssue(IOException("Broken pipe")))
        assertTrue(ErrorFormatter.isConnectionIssue(AuthException.NetworkUnavailable))
        assertTrue(ErrorFormatter.isConnectionIssue(AuthException.RequestTimedOut))
    }

    @Test
    fun `isConnectionIssue returns true for raw network error messages`() {
        assertTrue(ErrorFormatter.isConnectionIssue("Failed to connect to /127.0.0.1:8080"))
        assertTrue(ErrorFormatter.isConnectionIssue("java.net.ConnectException: Connection refused"))
        assertTrue(ErrorFormatter.isConnectionIssue("Unable to resolve host \"api.benobat.ir\": No address associated with hostname"))
        assertTrue(ErrorFormatter.isConnectionIssue("Software caused connection abort"))
        assertTrue(ErrorFormatter.isConnectionIssue("Connection timed out"))
        assertTrue(ErrorFormatter.isConnectionIssue("ECONNREFUSED (Connection refused)"))
    }

    @Test
    fun `isConnectionIssue returns false for non-connection errors`() {
        assertFalse(ErrorFormatter.isConnectionIssue(IllegalArgumentException("Invalid input")))
        assertFalse(ErrorFormatter.isConnectionIssue("Email or password is incorrect"))
        assertFalse(ErrorFormatter.isConnectionIssue("Sign in to continue."))
        assertFalse(ErrorFormatter.isConnectionIssue("404 Not Found"))
        assertFalse(ErrorFormatter.isConnectionIssue(null as Throwable?))
        assertFalse(ErrorFormatter.isConnectionIssue(null as String?))
    }
}
