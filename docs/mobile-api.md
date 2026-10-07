# Native API contract

The ASP.NET application serves JSON at `/api/v1`. Native clients use this API and the existing PostgreSQL/ASP.NET Identity accounts. Web authentication continues to use its application cookie.

Send `Content-Type: application/json` on JSON writes and `Authorization: Bearer <accessToken>` on protected requests. DTO properties use camel case; IDs are UUID strings; instants use ISO 8601 UTC; branch-local dates use Gregorian `YYYY-MM-DD`. The client may display those dates in Persian, Arabic or English without changing their wire representation. Prices are decimal values in the accompanying currency; do not convert or silently relabel stored values.

Successful list responses use `{items, page, pageSize, total}` unless the list is a small options collection, which uses `{items}`. Customer pagination defaults to page 1 and 20 items, with a maximum size of 100. Management pagination defaults to page 1 and 25 items and rejects invalid limits. Lists order consistently before pagination.

Errors use `{code, message, fields?}`. Handle the stable code in client translations; the English message is a diagnostic fallback. Common HTTP statuses are 400 for invalid input, 401 for missing/expired/revoked sign-in, 403 for denied operations, 404 for unavailable or out-of-scope records, 409 for conflicting state and 429 for rate-limited sign-in/registration. Unsupported JSON content types return 415 and oversized request bodies return 413. Do not show a success state when an API call fails.

## Accounts

| Method | Route | Body / result |
| --- | --- | --- |
| POST | `/auth/register` | `{displayName,email,phoneNumber,password}` → session, HTTP 201 |
| POST | `/auth/login` | `{email,password}` → session |
| POST | `/auth/logout` | Revokes the current session; HTTP 204 |
| GET | `/me` | Current user |
| PATCH | `/me` | `{displayName,phoneNumber}` → current user |
| POST | `/me/email` | `{email,currentPassword}` → replacement session |
| POST | `/me/password` | `{currentPassword,newPassword}` → replacement session |
| POST | `/me/avatar` | `{contentType,dataBase64}` → current user |
| DELETE | `/me/avatar` | Removes the image → current user |

A session is `{accessToken,tokenType:"Bearer",expiresAt,user}`. A user is `{id,displayName,email,phoneNumber,avatarUrl,roles}`. Roles are `Customer`, `Staff`, `Manager`, `Owner`, and `PlatformAdmin`. The relative avatar URL is resolved against the application origin. Images must be PNG, JPEG or WebP, with matching file signatures, and at most 2 MB.

Passwords follow the existing Identity policy: at least six characters, a lowercase Latin letter, and a digit; uppercase letters and punctuation are optional. API passwords are capped at 256 characters. Names are required and capped at 100 characters, emails at 256, and booking notes at 500. Iranian mobile numbers normalize Persian/Arabic digits and `+98`/`0098` prefixes into the 11-digit `09...` form.

Sessions expire after seven days. Each request validates the Identity security stamp, current account lockout, current session entry, and fresh roles. Logout revokes that token. Password/email changes return a new session and revoke the previous stamp's credentials: save the replacement access token immediately. Role/membership changes can invalidate an existing session; handle 401 by returning to sign-in. Login and registration share a per-IP limit of eight attempts per minute; honor `Retry-After` after HTTP 429. Login does not issue a web cookie.

## Discovery and booking

| Method | Route | Result / query |
| --- | --- | --- |
| GET | `/categories` | `{items:[{id,name,kind,sortOrder}]}`; kind is `Business` or `Service` |
| GET | `/catalog` | Published standard services; paginated |
| GET | `/businesses` | Business cards; `q`, `city`, `category`, `page`, `pageSize` |
| GET | `/businesses/{businessId}` | Business card fields plus `branches`, `services`, latest `reviews` |
| GET | `/businesses/{businessId}/branches` | Branch options |
| GET | `/businesses/{businessId}/reviews` | Published reviews; paginated |
| GET | `/businesses/{businessId}/branches/{branchId}/services` | Branch services with effective branch prices |
| GET | `/businesses/{businessId}/branches/{branchId}/providers` | Staff qualified for every comma-separated `serviceIds` |
| GET | `/slots` | `businessId`, `branchId`, comma-separated `serviceIds`, optional `resourceId`, branch-local `date` |
| GET | `/favorites` | Current customer's business cards; paginated |
| PUT / DELETE | `/favorites/{businessId}` | Add/remove current customer's favorite; HTTP 204 |
| POST | `/appointments` | Creates an appointment; HTTP 201 |
| GET | `/appointments` | Own history; optional status, paginated |
| GET | `/appointments/{id}` | Own appointment |
| POST | `/appointments/{id}/cancel` | Conditional customer cancellation → appointment |
| POST | `/appointments/{id}/review` | `{rating,comment}` → pending review, HTTP 201 |

A business card is `{id,name,slug,category,city,description,requiresApproval,branchCount,serviceCount,rating,reviewCount}`. Unrated businesses have `rating:null`. A branch is `{id,businessId,name,address,timeZoneId,openHour,closeHour}`. A service is `{id,businessId,name,description,durationMinutes,price,currency}`. A provider is `{id,branchId,name,kind,userId}`. A public review is `{id,rating,comment,managerReply,customerName,createdAt}`.

Slots return `{date,timeZoneId,totalDurationMinutes,totalPrice,currency,slots:[{resourceId,resourceName,startsAt,endsAt}]}`. A null provider means the customer accepts any qualified provider; submit `resourceId:null` to preserve that choice. Availability and creation use the same shared server booking rules.

Submit `{businessId,branchId,serviceIds:[...],resourceId,startsAt,phoneNumber,customerNote,termsAccepted:true,expectedPrice?,expectedDurationMinutes?}` to book. Submit the last two values from the shown slot summary to detect changed terms. The server validates active parent records, branch/service/provider assignments, lead time, the 90-day horizon, opening rules, provider conflicts, customer overlap, and the active-appointment cap. Concurrent bookings are serialized through PostgreSQL locks. The server fixes the final duration and price; client-calculated values cannot override them.

Booking error codes include `terms_required`, `invalid_phone`, `business_unavailable`, `branch_unavailable`, `service_unavailable`, `no_provider`, `outside_horizon`, `too_soon`, `terms_changed`, `slot_unavailable`, `customer_overlap`, and `active_limit`. Refresh slots after a conflict or changed terms.

An appointment is `{id,businessId,businessName,branchId,branchName,timeZoneId,serviceNames,resourceId,resourceName,startsAt,endsAt,status,finalPrice,currency,customerNote,trackingCode,canCancel,canReview,reviewId}`. Status is `Pending`, `Confirmed`, `Completed`, `Cancelled`, or `NoShow`. History remains readable after a referenced business/branch/service is disabled. Cancellation requires an active status and at least two hours' notice. Reviews require eligible attended appointments; one review per appointment is enforced in PostgreSQL. Reviews enter moderation as `Pending` and do not appear publicly before publication.

## Management

All `/admin` routes require a mobile session. `/admin/context` supplies branch memberships and capability flags. Current membership role in each branch governs every read and write; a manager role elsewhere grants no access. Staff can see only appointments assigned to their linked resource. Managers/owners can manage their branch; owners and platform administrators can assign owners. `/platform` routes additionally require the current persisted `PlatformAdmin` role.

Management collections are `/admin/businesses`, `/admin/branches`, `/admin/resources`, `/admin/memberships`, `/admin/team`, `/admin/customers`, `/admin/services`, `/admin/availability`, `/admin/appointments` (also `/admin/calendar`), `/admin/reviews`, and `/admin/customer-reviews`. CRUD writes use POST collection, PUT `/{id}`, and DELETE `/{id}` where supported. Records cannot be moved across tenant boundaries. Availability writes accept dated branch/business/provider intervals with explicit branch-local times; reads also expose legacy recurring rules.

Current authorized branch members can read inherited business-wide availability.
Filtering an authorized branch includes its own intervals and its business-wide
intervals. Business-wide rows without a branch are read-only for non-platform
actors; their creation, update and deletion remain platform operations.

Service assignments use PUT/DELETE `/admin/branches/{branchId}/services/{serviceId}` with `{price:null|decimal}` and PUT `/admin/resources/{resourceId}/services` with `{serviceIds:[...]}`. Update appointment status with PUT `/admin/appointments/{id}/status` and `{status}`. Moderate a review with PUT `/admin/reviews/{id}/moderation` and `{status,managerReply?}`, and update its reply with PUT `/admin/reviews/{id}/reply` and `{managerReply}`. Customer evaluations use `{appointmentId,rating,comment}` on `/admin/customer-reviews`; they are never public.

Platform collections are `/platform/businesses`, `/platform/catalog`, `/platform/categories`, and `/platform/users`. Create a user with `{displayName,email,phoneNumber,role}`; the response includes a generated `temporaryPassword` to show once to the administrator. The only global grants are `Customer` and `PlatformAdmin`; `Owner`, `Manager`, and `Staff` derive from branch memberships. User access uses PUT `/platform/users/{id}/roles` with `{roles:[...]}` for global grants while preserving derived roles, PUT `/platform/users/{id}/active` with `{active}`, and POST `/platform/users/{id}/reset-password` with no body, returning a generated `temporaryPassword`. Disabling users revokes their credentials. Reactivation requires a new sign-in. Serializable updates preserve the last owner and the last active platform administrator when concurrent edits compete.

The route handlers and their `Management*Input`/`Management*Dto` records in `Api/ManagementEndpoints.cs` are the canonical management field definitions; inspect the development OpenAPI document at `/openapi/v1.json` when adapting a client. No production credentials or fallback demo state are embedded in the native app.
