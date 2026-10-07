package com.leon.be_nobat.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BeNobatApiTest {
    @Test fun nativeClientUsesBearerAndEncodesSearchWithoutChangingRoute() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"items\":[{\"id\":\"one\",\"name\":\"Clinic\"}],\"total\":1}"))
            val api = BeNobatApi({ server.url("/").toString() }, { "test-session" })
            val result = api.request("businesses", query = mapOf("q" to "massage & clinic", "page" to "1"))
            assertEquals("one", result.objects().single().text("id"))
            val request = server.takeRequest()
            assertEquals("/api/v1/businesses", request.requestUrl!!.encodedPath)
            assertEquals("massage & clinic", request.requestUrl!!.queryParameter("q"))
            assertEquals("Bearer test-session", request.getHeader("Authorization"))
        }
    }
    @Test fun bookingBodyPreservesMultipleServicesAndSelectedProvider() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody("{\"trackingCode\":\"ABC12345\"}"))
            val api = BeNobatApi({ server.url("/").toString() }, { "token" })
            val body = JSONObject("{\"branchId\":\"branch\",\"serviceIds\":[\"first\",\"second\"],\"resourceId\":\"staff\",\"termsAccepted\":true}")
            assertEquals("ABC12345", api.request("appointments", "POST", body).text("trackingCode"))
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            val sent = JSONObject(request.body.readUtf8())
            assertEquals(listOf("first", "second"), sent.strings("serviceIds"))
            assertTrue(sent.getBoolean("termsAccepted"))
        }
    }
    @Test fun conflictCodeRemainsAvailableForLocalizedRetryMessage() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(409).setBody("{\"code\":\"terms_changed\",\"message\":\"Do not display raw server text\"}"))
            val error = runCatching { BeNobatApi({ server.url("/").toString() }, { null }).request("appointments", "POST") }.exceptionOrNull()
            assertTrue(error is ApiFailure)
            assertEquals("terms_changed", (error as ApiFailure).code)
            assertEquals(409, error.status)
        }
    }
    @Test fun redirectsDoNotForwardBearerToAnotherHost() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://elsewhere.invalid"))
            val error = runCatching { BeNobatApi({ server.url("/").toString() }, { "token" }).request("me") }.exceptionOrNull()
            assertEquals(302, (error as ApiFailure).status)
            assertEquals(1, server.requestCount)
        }
    }
}
