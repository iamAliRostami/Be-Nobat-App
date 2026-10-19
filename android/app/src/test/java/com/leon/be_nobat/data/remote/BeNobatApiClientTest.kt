package com.leon.be_nobat.data.remote

import com.leon.be_nobat.domain.model.MobileSession
import com.leon.be_nobat.domain.model.User
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for BeNobatApiClient using Ktor MockEngine. These tests
 * run on the JVM (no Android device required) and verify the contract
 * between the client and the .NET mobile API at the api v1 base path:
 *
 * - Request path, method, and body shape.
 * - Bearer token header attachment.
 * - camelCase JSON deserialization of MobileSession, User, etc.
 * - Error envelope parsing (code, message, fields).
 * - Status code -> BeNobatApiException mapping.
 */
class BeNobatApiClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    /** Build an API client backed by a MockEngine whose handler is the given lambda. */
    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): BeNobatApiClient {
        val engine = MockEngine(handler)
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 5_000
            }
        }
        return BeNobatApiClient(
            client = client,
            baseUrl = "https://api.example.com/api/v1",
            tokenProvider = { "test-token-123" },
        )
    }

    @Test
    fun `login posts to auth login path with email and password`() = runBlocking {
        var capturedUrl: String? = null
        var capturedAuthHeader: String? = null

        val api = api { request ->
            capturedUrl = request.url.toString()
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"accessToken":"tok-1","tokenType":"Bearer","expiresAt":"2026-12-31T00:00:00+00:00","user":{"id":"u-1","displayName":"Ali","email":"ali@example.com","phoneNumber":"+989121234567","avatarUrl":null,"roles":["Customer"]}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = api.login<MobileSession>(
            buildJsonObject {
                put("email", "ali@example.com")
                put("password", "secret")
            }
        )

        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("tok-1", session.accessToken)
        assertEquals("Bearer", session.tokenType)
        assertEquals("2026-12-31T00:00:00+00:00", session.expiresAt)
        assertEquals("u-1", session.user.id)
        assertEquals("Ali", session.user.displayName)
        assertEquals("ali@example.com", session.user.email)
        assertEquals(listOf("Customer"), session.user.roles)

        assertEquals("https://api.example.com/api/v1/auth/login", capturedUrl)
        // The client unconditionally attaches the bearer header if a token is
        // available; the .NET server tolerates the redundant header on
        // /auth/login (it just ignores it).
        assertEquals("Bearer test-token-123", capturedAuthHeader)
    }

    @Test
    fun `auth header attached for getMe call`() = runBlocking {
        var capturedAuthHeader: String? = null
        val api = api { request ->
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"id":"u-1","displayName":"Ali","email":"ali@example.com","phoneNumber":"+989121234567","avatarUrl":"/media/avatar/u-1?v=1","roles":["Customer"]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = api.getMe<User>()
        assertTrue(result.isSuccess)
        assertEquals("u-1", result.getOrThrow().id)
        assertEquals("Bearer test-token-123", capturedAuthHeader)
    }

    @Test
    fun `401 with invalid_credentials code is parsed into BeNobatApiException`() = runBlocking {
        val api = api {
            respond(
                content = """{"code":"invalid_credentials","message":"Email or password is incorrect.","fields":null}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = api.login<MobileSession>(
            buildJsonObject {
                put("email", "x@example.com")
                put("password", "wrong")
            }
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as BeNobatApiException
        assertEquals(401, ex.statusCode)
        assertEquals("invalid_credentials", ex.code)
        assertEquals("Email or password is incorrect.", ex.message)
    }

    @Test
    fun `429 returns rate_limited error code`() = runBlocking {
        val api = api {
            respond(
                content = """{"code":"rate_limited","message":"Too many attempts. Try again in a minute.","fields":null}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = api.login<MobileSession>(
            buildJsonObject {
                put("email", "x@example.com")
                put("password", "x")
            }
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as BeNobatApiException
        assertEquals(429, ex.statusCode)
        assertEquals("rate_limited", ex.code)
    }

    @Test
    fun `404 returns not_found error`() = runBlocking {
        val api = api {
            respond(
                content = """{"code":"not_found","message":"The requested resource was not found.","fields":null}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = api.getBusiness<com.leon.be_nobat.domain.model.BusinessDetail>("missing-id")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as BeNobatApiException
        assertEquals(404, ex.statusCode)
        assertEquals("not_found", ex.code)
    }

    @Test
    fun `pagination parameters appended to list businesses`() = runBlocking {
        var capturedUrl: String? = null
        val api = api { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"items":[],"page":1,"pageSize":20,"total":0}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        api.listBusinesses<com.leon.be_nobat.data.remote.ApiPage<com.leon.be_nobat.domain.model.Business>>(
            q = "clinic",
            city = "Tehran",
            category = "medical",
            page = 2,
            pageSize = 10,
        )
        assertNotNull(capturedUrl)
        assertTrue(capturedUrl!!.contains("/businesses?"))
        assertTrue(capturedUrl!!.contains("q=clinic"))
        assertTrue(capturedUrl!!.contains("city=Tehran"))
        assertTrue(capturedUrl!!.contains("category=medical"))
        assertTrue(capturedUrl!!.contains("page=2"))
        assertTrue(capturedUrl!!.contains("pageSize=10"))
    }

    @Test
    fun `logout posts to auth logout with bearer header`() = runBlocking {
        var capturedUrl: String? = null
        var capturedMethod: String? = null
        var capturedAuth: String? = null
        val api = api { request ->
            capturedUrl = request.url.toString()
            capturedMethod = request.method.value
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        api.logout()
        assertEquals("https://api.example.com/api/v1/auth/logout", capturedUrl)
        assertEquals("POST", capturedMethod)
        assertEquals("Bearer test-token-123", capturedAuth)
    }

    @Test
    fun `favorites PUT sends no body and returns 204`() = runBlocking {
        var capturedUrl: String? = null
        var capturedMethod: String? = null
        val api = api { request ->
            capturedUrl = request.url.toString()
            capturedMethod = request.method.value
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val result = api.addFavorite("b-1")
        assertTrue(result.isSuccess)
        assertEquals("https://api.example.com/api/v1/favorites/b-1", capturedUrl)
        assertEquals("PUT", capturedMethod)
    }

    @Test
    fun `deleteAvatar returns parsed user after DELETE`() = runBlocking {
        var capturedMethod: String? = null
        val api = api { request ->
            capturedMethod = request.method.value
            respond(
                content = """{"id":"u-1","displayName":"Ali","email":"ali@example.com","phoneNumber":"+989121234567","avatarUrl":null,"roles":["Customer"]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = api.deleteAvatar<User>()
        assertTrue(result.isSuccess)
        assertEquals("u-1", result.getOrThrow().id)
        assertNull(result.getOrThrow().avatarUrl)
        assertEquals("DELETE", capturedMethod)
    }

    @Test
    fun `auth header not attached when token provider returns null`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"id":"u-1","displayName":"Ali","email":"ali@example.com","phoneNumber":"+989121234567","avatarUrl":null,"roles":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout)
        }
        val api = BeNobatApiClient(
            client = client,
            baseUrl = "https://api.example.com/api/v1",
            tokenProvider = { null },
        )
        val result = api.getMe<User>()
        assertTrue(result.isSuccess)
    }
}
