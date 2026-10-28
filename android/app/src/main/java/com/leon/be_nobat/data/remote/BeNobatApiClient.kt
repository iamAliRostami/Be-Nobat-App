package com.leon.be_nobat.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Server-side error envelope. See `Api/ApiContracts.cs` on the backend.
 *
 * Example: `{"code":"invalid_credentials","message":"Email or password is incorrect.","fields":null}`
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val fields: JsonElement? = null,
)

/** Generic page envelope: `{items, page, pageSize, total}`. */
@Serializable
data class ApiPage<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
)

/** Simple `{items:[...]}` envelope used by `categories`, `branches`, `services`, `providers`. */
@Serializable
data class ApiItems<T>(
    val items: List<T> = emptyList(),
)

/**
 * Raised on non-2xx responses. The body is parsed into an [ApiError] when
 * possible so callers can map known error codes (e.g. `invalid_credentials`)
 * to user-facing messages.
 */
class BeNobatApiException(
    val statusCode: Int,
    val error: ApiError?,
    message: String,
) : Exception(message) {
    val code: String? get() = error?.code
    override val message: String = message
}

/**
 * HTTP gateway to the BeNobat .NET REST API at the api v1 base path.
 *
 * Wire conventions (see `docs/mobile-api.md`):
 * - JSON is camelCase with string enums.
 * - Auth is `Authorization: Bearer <accessToken>` (opaque token, NOT JWT).
 * - Errors return `{code, message, fields?}` and stable HTTP statuses.
 *
 * All public methods return `Result<T>` so callers can use `getOrElse` /
 * `getOrThrow` and map errors uniformly. Methods are split per concern
 * (auth, public discovery, customer, admin, platform) for readability.
 */
class BeNobatApiClient(
    val client: HttpClient,
    val baseUrl: String,
    private val tokenProvider: suspend () -> String? = { null },
) {
    suspend fun currentToken(): String? = tokenProvider()

    fun HttpRequestBuilder.bearer(token: String?) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    suspend fun HttpResponse.ensureSuccess(): HttpResponse {
        if (!status.isSuccess()) {
            val raw = runCatching { body<String>() }.getOrDefault("")
            val parsed = runCatching {
                JsonLenient.decodeFromString(ApiError.serializer(), raw)
            }.getOrNull()
            throw BeNobatApiException(
                statusCode = status.value,
                error = parsed,
                message = parsed?.message?.ifBlank { raw } ?: raw.ifBlank { "HTTP ${status.value}" },
            )
        }
        return this
    }

    // ====================  AUTH  ====================

    /**
     * POST /auth/login
     * Returns the new session `{accessToken, tokenType, expiresAt, user}`.
     */
    suspend inline fun <reified T> login(body: JsonObject): Result<T> = post("/auth/login", body)

    /**
     * POST /auth/register — same shape as login; 201 Created on success.
     */
    suspend inline fun <reified T> register(body: JsonObject): Result<T> = post("/auth/register", body)

    /** POST /auth/logout — returns 204 No Content; consumes the current token. */
    suspend fun logout(): Result<Unit> = postEmpty("/auth/logout")

    // ====================  ME  ====================

    /** GET /me — current user DTO. */
    suspend inline fun <reified T> getMe(): Result<T> = get("/me")

    /** PATCH /me — `{displayName, phoneNumber}` → updated user DTO. */
    suspend inline fun <reified T> updateProfile(body: JsonObject): Result<T> = patch("/me", body)

    /** POST /me/password — `{currentPassword, newPassword}` → new session. */
    suspend inline fun <reified T> changePassword(body: JsonObject): Result<T> = post("/me/password", body)

    /** POST /me/email — `{email, currentPassword}` → new session. */
    suspend inline fun <reified T> changeEmail(body: JsonObject): Result<T> = post("/me/email", body)

    /** POST /me/avatar — `{contentType, dataBase64}` → updated user. */
    suspend inline fun <reified T> uploadAvatar(body: JsonObject): Result<T> = post("/me/avatar", body)

    /** DELETE /me/avatar — returns updated user with `avatarUrl:null`. */
    suspend inline fun <reified T> deleteAvatar(): Result<T> = deleteReturning("/me/avatar")

    /** GET /business-request — gets user's current business request. */
    suspend inline fun <reified T> getMyBusinessRequest(): Result<T> = get("/business-request")

    /** POST /business-request — submits a business request for Platform Admin approval. */
    suspend inline fun <reified T> submitBusinessRequest(body: JsonObject): Result<T> = post("/business-request", body)

    /** GET /platform-pricing - gets platform pricing and bank payment info. */
    suspend inline fun <reified T> getPlatformPricing(): Result<T> = get("/platform-pricing")

    /** POST /business-request/receipt - uploads payment receipt. */
    suspend inline fun <reified T> uploadBusinessRequestReceipt(body: JsonObject): Result<T> = post("/business-request/receipt", body)

    // ====================  PUBLIC DISCOVERY  ====================

    /** GET /categories — `{items: CategoryDto[]}`. */
    suspend inline fun <reified T> getCategories(): Result<T> = get("/categories")

    /** GET /catalog?page&pageSize — `ApiPage<ServiceCatalogDto>`. */
    suspend inline fun <reified T> getCatalog(page: Int = 1, pageSize: Int = 30): Result<T> =
        get("/catalog") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }

    /** GET /businesses?q&city&category&page&pageSize — `ApiPage<BusinessDto>`. */
    suspend inline fun <reified T> listBusinesses(
        q: String? = null,
        city: String? = null,
        category: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<T> = get("/businesses") {
        q?.let { parameter("q", it) }
        city?.let { parameter("city", it) }
        category?.let { parameter("category", it) }
        parameter("page", page)
        parameter("pageSize", pageSize)
    }

    /** GET /businesses/{id} — `BusinessDetailDto`. */
    suspend inline fun <reified T> getBusiness(id: String): Result<T> {
        val isUuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(id)
        return if (isUuid) get("/businesses/$id") else get("/businesses/by-slug/$id")
    }

    /** GET /businesses/{id}/reviews?page&pageSize — `ApiPage<PublicReviewDto>`. */
    suspend inline fun <reified T> listBusinessReviews(id: String, page: Int = 1, pageSize: Int = 20): Result<T> =
        get("/businesses/$id/reviews") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }

    /** GET /businesses/{id}/branches — `{items:BranchDto[]}`. */
    suspend inline fun <reified T> listBranches(businessId: String): Result<T> =
        get("/businesses/$businessId/branches")

    /** GET /businesses/{id}/branches/{branchId}/services — `{items:ServiceDto[]}`. */
    suspend inline fun <reified T> listBranchServices(businessId: String, branchId: String): Result<T> =
        get("/businesses/$businessId/branches/$branchId/services")

    /** GET /businesses/{id}/branches/{branchId}/providers?serviceIds= — `{items:ProviderDto[]}`. */
    suspend inline fun <reified T> listProviders(
        businessId: String,
        branchId: String,
        serviceIds: String,
    ): Result<T> = get("/businesses/$businessId/branches/$branchId/providers") {
        parameter("serviceIds", serviceIds)
    }

    // ====================  SLOTS + CUSTOMER  ====================

    /**
     * GET /slots?businessId&branchId&serviceIds&date&resourceId?
     * Anonymous endpoint. Returns `SlotsResponse`.
     */
    suspend inline fun <reified T> getSlots(
        businessId: String,
        branchId: String,
        serviceIds: String,
        date: String,
        resourceId: String? = null,
    ): Result<T> = get("/slots") {
        parameter("businessId", businessId)
        parameter("branchId", branchId)
        parameter("serviceIds", serviceIds)
        parameter("date", date)
        resourceId?.let { parameter("resourceId", it) }
    }

    /** GET /favorites?page&pageSize — `ApiPage<BusinessDto>`. */
    suspend inline fun <reified T> listFavorites(page: Int = 1, pageSize: Int = 20): Result<T> =
        get("/favorites") {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }

    /** PUT /favorites/{businessId} — idempotent; 204. */
    suspend fun addFavorite(businessId: String): Result<Unit> = putEmpty("/favorites/$businessId")

    /** DELETE /favorites/{businessId} — 204. */
    suspend fun removeFavorite(businessId: String): Result<Unit> = delete("/favorites/$businessId")

    /** POST /appointments — `BookingInput` → 201 `AppointmentDto`. */
    suspend inline fun <reified T> createAppointment(body: JsonObject): Result<T> =
        post("/appointments", body)

    /** GET /appointments?status&page&pageSize — `ApiPage<AppointmentDto>`. */
    suspend inline fun <reified T> listAppointments(
        status: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<T> = get("/appointments") {
        status?.let { parameter("status", it) }
        parameter("page", page)
        parameter("pageSize", pageSize)
    }

    /** GET /appointments/{id} — `AppointmentDto`. */
    suspend inline fun <reified T> getAppointment(id: String): Result<T> = get("/appointments/$id")

    /** POST /appointments/{id}/cancel — `AppointmentDto`. */
    suspend inline fun <reified T> cancelAppointment(id: String): Result<T> =
        postEmpty("/appointments/$id/cancel")

    /** POST /appointments/{id}/review — `{rating, comment?}` → 201 `ReviewCreatedDto`. */
    suspend inline fun <reified T> createAppointmentReview(id: String, body: JsonObject): Result<T> =
        post("/appointments/$id/review", body)

    // ====================  ADMIN  ====================

    /** GET /admin/dashboard — counts object. */
    suspend inline fun <reified T> adminDashboard(): Result<T> = get("/admin/dashboard")

    /** GET /admin/analytics?businessId&branchId&resourceId&from&to — activity statistics. */
    suspend inline fun <reified T> adminAnalytics(
        businessId: String? = null,
        branchId: String? = null,
        resourceId: String? = null,
        from: String? = null,
        to: String? = null,
    ): Result<T> = get("/admin/analytics") {
        businessId?.let { parameter("businessId", it) }
        branchId?.let { parameter("branchId", it) }
        resourceId?.let { parameter("resourceId", it) }
        from?.let { parameter("from", it) }
        to?.let { parameter("to", it) }
    }

    /** GET /admin/context — current user's permissions/memberships. */
    suspend inline fun <reified T> adminContext(): Result<T> = get("/admin/context")

    /** GET /admin/businesses?q&page&pageSize. */
    suspend inline fun <reified T> adminListBusinesses(
        q: String? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/businesses") {
        q?.let { parameter("q", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** GET /admin/businesses/{id}. */
    suspend inline fun <reified T> adminGetBusiness(id: String): Result<T> = get("/admin/businesses/$id")

    /** PUT /admin/businesses/{id} — `ManagementBusinessInput`. */
    suspend inline fun <reified T> adminUpdateBusiness(id: String, body: JsonObject): Result<T> =
        put("/admin/businesses/$id", body)

    /** GET /admin/branches?businessId&q&includeInactive&page&pageSize. */
    suspend inline fun <reified T> adminListBranches(
        businessId: String? = null, q: String? = null, includeInactive: Boolean? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/branches") {
        businessId?.let { parameter("businessId", it) }
        q?.let { parameter("q", it) }
        includeInactive?.let { parameter("includeInactive", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /admin/branches — `ManagementBranchInput`. */
    suspend inline fun <reified T> adminCreateBranch(body: JsonObject): Result<T> = post("/admin/branches", body)

    /** GET /admin/branches/{id}. */
    suspend inline fun <reified T> adminGetBranch(id: String): Result<T> = get("/admin/branches/$id")

    /** PUT /admin/branches/{id} — `ManagementBranchInput`. */
    suspend inline fun <reified T> adminUpdateBranch(id: String, body: JsonObject): Result<T> =
        put("/admin/branches/$id", body)

    /** PUT /admin/branches/{id}/active — `{active}`. */
    suspend inline fun <reified T> adminSetBranchActive(id: String, body: JsonObject): Result<T> =
        put("/admin/branches/$id/active", body)

    /** DELETE /admin/branches/{id}. */
    suspend fun adminDeleteBranch(id: String): Result<Unit> = delete("/admin/branches/$id")

    /** POST /platform/branches/{id}/approve. */
    suspend inline fun <reified T> platformApproveBranch(id: String): Result<T> =
        postEmpty("/platform/branches/$id/approve")

    /** GET /admin/resources?branchId&businessId&includeInactive&page&pageSize. */
    suspend inline fun <reified T> adminListResources(
        branchId: String? = null, businessId: String? = null, includeInactive: Boolean? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/resources") {
        branchId?.let { parameter("branchId", it) }
        businessId?.let { parameter("businessId", it) }
        includeInactive?.let { parameter("includeInactive", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /admin/resources — `ManagementResourceInput`. */
    suspend inline fun <reified T> adminCreateResource(body: JsonObject): Result<T> = post("/admin/resources", body)

    /** PUT /admin/resources/{id}. */
    suspend inline fun <reified T> adminUpdateResource(id: String, body: JsonObject): Result<T> =
        put("/admin/resources/$id", body)

    /** PUT /admin/resources/{id}/active. */
    suspend inline fun <reified T> adminSetResourceActive(id: String, body: JsonObject): Result<T> =
        put("/admin/resources/$id/active", body)

    /** DELETE /admin/resources/{id}. */
    suspend fun adminDeleteResource(id: String): Result<Unit> = delete("/admin/resources/$id")

    /** GET /admin/memberships?branchId&userId&page&pageSize. */
    suspend inline fun <reified T> adminListMemberships(
        branchId: String? = null, userId: String? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/memberships") {
        branchId?.let { parameter("branchId", it) }
        userId?.let { parameter("userId", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /admin/memberships. */
    suspend inline fun <reified T> adminCreateMembership(body: JsonObject): Result<T> = post("/admin/memberships", body)

    /** PUT /admin/memberships/{id}. */
    suspend inline fun <reified T> adminUpdateMembership(id: String, body: JsonObject): Result<T> =
        put("/admin/memberships/$id", body)

    /** DELETE /admin/memberships/{id}. */
    suspend fun adminDeleteMembership(id: String): Result<Unit> = delete("/admin/memberships/$id")

    /** GET /admin/team?q&branchId&page&pageSize. */
    suspend inline fun <reified T> adminListTeam(
        q: String? = null, branchId: String? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/team") {
        q?.let { parameter("q", it) }
        branchId?.let { parameter("branchId", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /admin/team — `ManagementTeamMemberInput`. */
    suspend inline fun <reified T> adminCreateTeamMember(body: JsonObject): Result<T> = post("/admin/team", body)

    /** GET /admin/customers?q&branchId&page&pageSize. */
    suspend inline fun <reified T> adminListCustomers(
        q: String? = null, branchId: String? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/customers") {
        q?.let { parameter("q", it) }
        branchId?.let { parameter("branchId", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /admin/customers — `ManagementCustomerInput`. */
    suspend inline fun <reified T> adminCreateCustomer(body: JsonObject): Result<T> = post("/admin/customers", body)

    /** PUT /admin/people/{id}/active — `{active}`. */
    suspend inline fun <reified T> adminSetPersonActive(id: String, body: JsonObject): Result<T> =
        put("/admin/people/$id/active", body)

    /** GET /admin/services?businessId&branchId&q&includeInactive&page&pageSize. */
    suspend inline fun <reified T> adminListServices(
        businessId: String? = null, branchId: String? = null, q: String? = null,
        includeInactive: Boolean? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/services") {
        businessId?.let { parameter("businessId", it) }
        branchId?.let { parameter("branchId", it) }
        q?.let { parameter("q", it) }
        includeInactive?.let { parameter("includeInactive", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** GET /admin/services/{id}. */
    suspend inline fun <reified T> adminGetService(id: String): Result<T> = get("/admin/services/$id")

    /** POST /admin/services. */
    suspend inline fun <reified T> adminCreateService(body: JsonObject): Result<T> = post("/admin/services", body)

    /** PUT /admin/services/{id}. */
    suspend inline fun <reified T> adminUpdateService(id: String, body: JsonObject): Result<T> =
        put("/admin/services/$id", body)

    /** PUT /admin/services/{id}/active. */
    suspend inline fun <reified T> adminSetServiceActive(id: String, body: JsonObject): Result<T> =
        put("/admin/services/$id/active", body)

    /** DELETE /admin/services/{id}. */
    suspend fun adminDeleteService(id: String): Result<Unit> = delete("/admin/services/$id")

    /** GET /admin/branches/{branchId}/services. */
    suspend inline fun <reified T> adminListBranchServices(branchId: String, page: Int = 1, pageSize: Int = 25): Result<T> =
        get("/admin/branches/$branchId/services") {
            parameter("page", page); parameter("pageSize", pageSize)
        }

    /** PUT /admin/branches/{branchId}/services/{serviceId} — `{price?}`. */
    suspend inline fun <reified T> adminUpsertBranchService(
        branchId: String, serviceId: String, body: JsonObject,
    ): Result<T> = put("/admin/branches/$branchId/services/$serviceId", body)

    /** DELETE /admin/branches/{branchId}/services/{serviceId}. */
    suspend fun adminDeleteBranchService(branchId: String, serviceId: String): Result<Unit> =
        delete("/admin/branches/$branchId/services/$serviceId")

    /** GET /admin/resources/{resourceId}/services — `ManagementResourceServicesInput`. */
    suspend inline fun <reified T> adminListResourceServices(resourceId: String): Result<T> =
        get("/admin/resources/$resourceId/services")

    /** PUT /admin/resources/{resourceId}/services — `{serviceIds:[...]}`. */
    suspend inline fun <reified T> adminSetResourceServices(
        resourceId: String, body: JsonObject,
    ): Result<T> = put("/admin/resources/$resourceId/services", body)

    /** GET /admin/availability?businessId&branchId&resourceId&from&to&page&pageSize. */
    suspend inline fun <reified T> adminListAvailability(
        businessId: String? = null, branchId: String? = null, resourceId: String? = null,
        from: String? = null, to: String? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/availability") {
        businessId?.let { parameter("businessId", it) }
        branchId?.let { parameter("branchId", it) }
        resourceId?.let { parameter("resourceId", it) }
        from?.let { parameter("from", it) }
        to?.let { parameter("to", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /admin/availability. */
    suspend inline fun <reified T> adminCreateAvailability(body: JsonObject): Result<T> = post("/admin/availability", body)

    /** PUT /admin/availability/{id}. */
    suspend inline fun <reified T> adminUpdateAvailability(id: String, body: JsonObject): Result<T> =
        put("/admin/availability/$id", body)

    /** DELETE /admin/availability/{id}. */
    suspend fun adminDeleteAvailability(id: String): Result<Unit> = delete("/admin/availability/$id")

    /** GET /admin/appointments — alias `/admin/calendar`. */
    suspend inline fun <reified T> adminListAppointments(
        businessId: String? = null, branchId: String? = null, resourceId: String? = null,
        customerId: String? = null, date: String? = null, status: String? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/appointments") {
        businessId?.let { parameter("businessId", it) }
        branchId?.let { parameter("branchId", it) }
        resourceId?.let { parameter("resourceId", it) }
        customerId?.let { parameter("customerId", it) }
        date?.let { parameter("date", it) }
        status?.let { parameter("status", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** GET /admin/appointments/{id}. */
    suspend inline fun <reified T> adminGetAppointment(id: String): Result<T> = get("/admin/appointments/$id")

    /** PUT /admin/appointments/{id}/status — `{status}`. */
    suspend inline fun <reified T> adminSetAppointmentStatus(id: String, body: JsonObject): Result<T> =
        put("/admin/appointments/$id/status", body)

    /** GET /admin/reviews. */
    suspend inline fun <reified T> adminListReviews(
        businessId: String? = null, branchId: String? = null, customerId: String? = null,
        status: String? = null, rating: Int? = null, includeInactive: Boolean? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/reviews") {
        businessId?.let { parameter("businessId", it) }
        branchId?.let { parameter("branchId", it) }
        customerId?.let { parameter("customerId", it) }
        status?.let { parameter("status", it) }
        rating?.let { parameter("rating", it) }
        includeInactive?.let { parameter("includeInactive", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** PUT /admin/reviews/{id}/moderation — `{status, managerReply?}`. */
    suspend inline fun <reified T> adminModerateReview(id: String, body: JsonObject): Result<T> =
        put("/admin/reviews/$id/moderation", body)

    /** PUT /admin/reviews/{id}/reply — `{managerReply}`. */
    suspend inline fun <reified T> adminReplyReview(id: String, body: JsonObject): Result<T> =
        put("/admin/reviews/$id/reply", body)

    /** PUT /admin/reviews/{id}/active — `{active}`. */
    suspend inline fun <reified T> adminSetReviewActive(id: String, body: JsonObject): Result<T> =
        put("/admin/reviews/$id/active", body)

    /** DELETE /admin/reviews/{id}. */
    suspend fun adminDeleteReview(id: String): Result<Unit> = delete("/admin/reviews/$id")

    /** GET /admin/customer-reviews. */
    suspend inline fun <reified T> adminListCustomerReviews(
        businessId: String? = null, branchId: String? = null, customerId: String? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/admin/customer-reviews") {
        businessId?.let { parameter("businessId", it) }
        branchId?.let { parameter("branchId", it) }
        customerId?.let { parameter("customerId", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** GET /admin/customer-reviews/pending. */
    suspend inline fun <reified T> adminListPendingCustomerReviews(page: Int = 1, pageSize: Int = 25): Result<T> =
        get("/admin/customer-reviews/pending") {
            parameter("page", page); parameter("pageSize", pageSize)
        }

    /** POST /admin/customer-reviews — `{appointmentId, rating, comment?}`. */
    suspend inline fun <reified T> adminCreateCustomerReview(body: JsonObject): Result<T> =
        post("/admin/customer-reviews", body)

    /** PUT /admin/customer-reviews/{id}. */
    suspend inline fun <reified T> adminUpdateCustomerReview(id: String, body: JsonObject): Result<T> =
        put("/admin/customer-reviews/$id", body)

    /** DELETE /admin/customer-reviews/{id}. */
    suspend fun adminDeleteCustomerReview(id: String): Result<Unit> = delete("/admin/customer-reviews/$id")

    // ====================  PLATFORM  ====================

    /** GET /platform/businesses. */
    suspend inline fun <reified T> platformListBusinesses(
        q: String? = null, includeInactive: Boolean? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/platform/businesses") {
        q?.let { parameter("q", it) }
        includeInactive?.let { parameter("includeInactive", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /platform/businesses. */
    suspend inline fun <reified T> platformCreateBusiness(body: JsonObject): Result<T> = post("/platform/businesses", body)

    /** PUT /platform/businesses/{id}. */
    suspend inline fun <reified T> platformUpdateBusiness(id: String, body: JsonObject): Result<T> =
        put("/platform/businesses/$id", body)

    /** PUT /platform/businesses/{id}/active. */
    suspend inline fun <reified T> platformSetBusinessActive(id: String, body: JsonObject): Result<T> =
        put("/platform/businesses/$id/active", body)

    /** DELETE /platform/businesses/{id}. */
    suspend fun platformDeleteBusiness(id: String): Result<Unit> = delete("/platform/businesses/$id")

    /** GET /platform/catalog. */
    suspend inline fun <reified T> platformListCatalog(
        q: String? = null, category: String? = null, includeInactive: Boolean? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/platform/catalog") {
        q?.let { parameter("q", it) }
        category?.let { parameter("category", it) }
        includeInactive?.let { parameter("includeInactive", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /platform/catalog. */
    suspend inline fun <reified T> platformCreateCatalogItem(body: JsonObject): Result<T> = post("/platform/catalog", body)

    /** PUT /platform/catalog/{id}. */
    suspend inline fun <reified T> platformUpdateCatalogItem(id: String, body: JsonObject): Result<T> =
        put("/platform/catalog/$id", body)

    /** DELETE /platform/catalog/{id}. */
    suspend fun platformDeleteCatalogItem(id: String): Result<Unit> = delete("/platform/catalog/$id")

    /** GET /platform/categories. */
    suspend inline fun <reified T> platformListCategories(
        kind: String? = null, includeInactive: Boolean? = null, page: Int = 1, pageSize: Int = 100,
    ): Result<T> = get("/platform/categories") {
        kind?.let { parameter("kind", it) }
        includeInactive?.let { parameter("includeInactive", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /platform/categories. */
    suspend inline fun <reified T> platformCreateCategory(body: JsonObject): Result<T> = post("/platform/categories", body)

    /** PUT /platform/categories/{id}. */
    suspend inline fun <reified T> platformUpdateCategory(id: String, body: JsonObject): Result<T> =
        put("/platform/categories/$id", body)

    /** DELETE /platform/categories/{id}. */
    suspend fun platformDeleteCategory(id: String): Result<Unit> = delete("/platform/categories/$id")

    /** GET /platform/users. */
    suspend inline fun <reified T> platformListUsers(
        q: String? = null, role: String? = null, active: Boolean? = null,
        page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/platform/users") {
        q?.let { parameter("q", it) }
        role?.let { parameter("role", it) }
        active?.let { parameter("active", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** GET /platform/users/{id}. */
    suspend inline fun <reified T> platformGetUser(id: String): Result<T> = get("/platform/users/$id")

    /** POST /platform/users — `{displayName, email, phoneNumber, role}`. */
    suspend inline fun <reified T> platformCreateUser(body: JsonObject): Result<T> = post("/platform/users", body)

    /** PUT /platform/users/{id}. */
    suspend inline fun <reified T> platformUpdateUser(id: String, body: JsonObject): Result<T> =
        put("/platform/users/$id", body)

    /** PUT /platform/users/{id}/roles — `{roles:[...]}`. */
    suspend inline fun <reified T> platformSetUserRoles(id: String, body: JsonObject): Result<T> =
        put("/platform/users/$id/roles", body)

    /** PUT /platform/users/{id}/active — `{active}`. */
    suspend inline fun <reified T> platformSetUserActive(id: String, body: JsonObject): Result<T> =
        put("/platform/users/$id/active", body)

    /** POST /platform/users/{id}/reset-password → `{userId, temporaryPassword}`. */
    suspend inline fun <reified T> platformResetUserPassword(id: String): Result<T> =
        postEmpty("/platform/users/$id/reset-password")

    /** GET /platform/pricing. */
    suspend inline fun <reified T> getPlatformPricingAdmin(): Result<T> = get("/platform/pricing")

    /** POST /platform/pricing. */
    suspend inline fun <reified T> updatePlatformPricingAdmin(body: JsonObject): Result<T> = post("/platform/pricing", body)

    /** GET /platform/business-requests. */
    suspend inline fun <reified T> platformListBusinessRequests(
        status: String? = null, q: String? = null, page: Int = 1, pageSize: Int = 25,
    ): Result<T> = get("/platform/business-requests") {
        status?.let { parameter("status", it) }
        q?.let { parameter("q", it) }
        parameter("page", page); parameter("pageSize", pageSize)
    }

    /** POST /platform/business-requests/{id}/approve. */
    suspend inline fun <reified T> platformApproveBusinessRequest(id: String): Result<T> =
        postEmpty("/platform/business-requests/$id/approve")

    /** POST /platform/business-requests/{id}/reject. */
    suspend inline fun <reified T> platformRejectBusinessRequest(id: String, body: JsonObject): Result<T> =
        post("/platform/business-requests/$id/reject", body)

    // ====================  INTERNAL HELPERS  ====================

    public suspend inline fun <reified T> get(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = currentToken()
                client.get("$baseUrl$path") { block(); bearer(token) }.ensureSuccess().body<T>()
            }
        }

    public suspend inline fun <reified T> post(path: String, body: JsonObject, crossinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = currentToken()
                client.post("$baseUrl$path") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                    block(); bearer(token)
                }.ensureSuccess().body<T>()
            }
        }

    /** POST without a request body (e.g. /auth/logout, /appointments/{id}/cancel). */
    public suspend inline fun <reified T> postEmpty(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = currentToken()
                client.post("$baseUrl$path") { block(); bearer(token) }.ensureSuccess().body<T>()
            }
        }

    public suspend inline fun <reified T> put(path: String, body: JsonObject, crossinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = currentToken()
                client.put("$baseUrl$path") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                    block(); bearer(token)
                }.ensureSuccess().body<T>()
            }
        }

    /** PUT without a request body â€” used for idempotent toggles (favorites, active flags). */
    public suspend fun putEmpty(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = currentToken()
                client.put("$baseUrl$path") { bearer(token) }.ensureSuccess().let { }
            }
        }

    public suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = currentToken()
                client.delete("$baseUrl$path") { bearer(token) }.ensureSuccess().let { }
            }
        }

    /** DELETE that returns a deserialized body (e.g. `/me/avatar` returns the updated user). */
    public suspend inline fun <reified T> deleteReturning(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = currentToken()
                client.delete("$baseUrl$path") { block(); bearer(token) }.ensureSuccess().body<T>()
            }
        }

    public suspend inline fun <reified T> patch(path: String, body: JsonObject, crossinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = currentToken()
                client.patch("$baseUrl$path") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                    block(); bearer(token)
                }.ensureSuccess().body<T>()
            }
        }
}

/** Lazy access to the JSON instance used by the error parser. */
private val JsonLenient by lazy {
    kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        coerceInputValues = true
    }
}