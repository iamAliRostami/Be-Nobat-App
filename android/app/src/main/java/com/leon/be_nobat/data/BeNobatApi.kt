package com.leon.be_nobat.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiFailure(val code: String, val status: Int = 0) : IOException(code)

class BeNobatApi(private val baseUrl: () -> String, private val token: () -> String?) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, query: Map<String, String> = emptyMap()): JSONObject {
        if (baseUrl().isBlank()) throw ApiFailure("configuration")
        val url = (baseUrl().trimEnd('/') + "/api/v1/" + path.trimStart('/')).toHttpUrl().newBuilder().apply {
            query.filterValues { it.isNotBlank() }.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json").apply {
            token()?.let { header("Authorization", "Bearer $it") }
            method(method, if (method in listOf("GET", "HEAD")) null else (body ?: JSONObject()).toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        }.build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try { response.use {
                        val raw = it.body?.string().orEmpty()
                        val parsed = runCatching {
                            if (raw.trimStart().startsWith("[")) JSONObject().put("items", JSONArray(raw))
                            else if (raw.isBlank()) JSONObject() else JSONObject(raw)
                        }.getOrNull()
                        if (!continuation.isActive) return
                        if (!it.isSuccessful) continuation.resumeWithException(ApiFailure(parsed?.optString("code")?.takeIf(String::isNotBlank) ?: "server_error", it.code))
                        else if (parsed == null) continuation.resumeWithException(ApiFailure("invalid_response"))
                        else continuation.resume(parsed)
                    } } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            })
        }
    }
    suspend fun avatar(path: String): ByteArray {
        val base = (baseUrl().trimEnd('/') + "/").toHttpUrl()
        val url = base.resolve(path) ?: throw ApiFailure("invalid_avatar")
        if (url.host != base.host || url.scheme != base.scheme || url.port != base.port) throw ApiFailure("invalid_avatar")
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (!it.isSuccessful) throw ApiFailure("invalid_avatar")
                            val output = java.io.ByteArrayOutputStream()
                            it.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val read = input.read(buffer); if (read < 0) break
                                    if (output.size() + read > 2 * 1024 * 1024) throw ApiFailure("invalid_avatar")
                                    output.write(buffer, 0, read)
                                }
                            }
                            output.toByteArray()
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            })
        }
    }
    suspend fun all(path: String, query: Map<String, String> = emptyMap()): JSONArray {
        val all = JSONArray()
        var page = 1
        do {
            val result = request(path, query = query + mapOf("page" to page.toString(), "pageSize" to "100"))
            val items = result.objects()
            items.forEach(all::put)
            if (items.isEmpty() || all.length() >= result.optInt("total", all.length())) break
            page++
        } while (page <= 1000)
        return all
    }
}

fun JSONObject.objects(key: String = "items"): List<JSONObject> = optJSONArray(key)?.let { array ->
    (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}.orEmpty()
fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { array ->
    (0 until array.length()).map { array.optString(it) }
}.orEmpty()
fun JSONObject.text(key: String): String = optString(key).takeUnless { it == "null" }.orEmpty()
