package com.leon.be_nobat.data.remote

import com.leon.be_nobat.domain.model.Business
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiPageSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Test fun `ApiPage deserializes standard envelope`() {
        val payload = """
        {
            "items": [
                {"id": "b1", "name": "Clinic A", "slug": "clinic-a", "category": "medical", "city": "Tehran",
                 "description": "", "requiresApproval": false, "branchCount": 1, "serviceCount": 3,
                 "rating": 4.5, "reviewCount": 12}
            ],
            "page": 1,
            "pageSize": 20,
            "total": 1
        }
        """.trimIndent()

        val page = json.decodeFromString(ApiPage.serializer(Business.serializer()), payload)
        assertEquals(1, page.items.size)
        assertEquals("Clinic A", page.items[0].name)
        assertEquals(4.5, page.items[0].rating!!, 0.001)
        assertEquals(1, page.page)
        assertEquals(1, page.total)
    }

    @Test fun `ApiPage handles empty items array`() {
        val payload = """{"items":[],"page":2,"pageSize":20,"total":50}"""
        val page = json.decodeFromString(ApiPage.serializer(Business.serializer()), payload)
        assertTrue(page.items.isEmpty())
        assertEquals(2, page.page)
        assertEquals(50, page.total)
    }

    @Test fun `ApiPage defaults when items omitted`() {
        val payload = """{"page":1,"pageSize":10,"total":0}"""
        val page = json.decodeFromString(ApiPage.serializer(Business.serializer()), payload)
        assertTrue(page.items.isEmpty())
    }

    @Test fun `ApiItems deserializes simple envelope`() {
        val payload = """
        {
            "items": [
                {"id": "b1", "name": "Branch 1"}
            ]
        }
        """.trimIndent()
        val items = json.decodeFromString(ApiItems.serializer(Business.serializer()), payload)
        assertEquals(1, items.items.size)
    }

    @Test fun `ApiError parses known envelope`() {
        val payload = """{"code":"invalid_credentials","message":"Email or password is incorrect.","fields":null}"""
        val err = json.decodeFromString(ApiError.serializer(), payload)
        assertEquals("invalid_credentials", err.code)
        assertEquals("Email or password is incorrect.", err.message)
        assertEquals(null, err.fields)
    }

    @Test fun `ApiError parses envelope with fields array`() {
        val payload = """{"code":"identity_error","message":"Identity errors","fields":[{"code":"DuplicateUserName","message":"taken"}]}"""
        val err = json.decodeFromString(ApiError.serializer(), payload)
        assertEquals("identity_error", err.code)
        assertTrue(err.fields != null)
    }

    @Test fun `ApiError tolerates extra fields`() {
        val payload = """{"code":"x","message":"y","fields":null,"trace_id":"abc"}"""
        val err = json.decodeFromString(ApiError.serializer(), payload)
        assertEquals("x", err.code)
    }
}
