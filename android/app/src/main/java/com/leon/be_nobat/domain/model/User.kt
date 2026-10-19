package com.leon.be_nobat.domain.model

import kotlinx.serialization.Serializable

/**
 * The user object as returned by the BeNobat .NET mobile API.
 *
 * Wire shape (camelCase):
 * ```
 * { id, displayName, email?, phoneNumber?, avatarUrl?, roles:[] }
 * ```
 * See `Api/ApiContracts.cs` `UserDto`.
 */
@Serializable
data class User(
    val id: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val avatarUrl: String? = null,
    val roles: List<String> = emptyList(),
) {
    /** True if any role is one of the management roles (Owner/Manager/Staff/PlatformAdmin). */
    val isStaff: Boolean get() = roles.any { it in staffRoles }

    /** True if this user can use the customer surface (Customer role OR no role at all). */
    val isCustomer: Boolean get() = roles.isEmpty() || roles.any { it == ROLE_CUSTOMER }

    /** True for Owner / Manager / PlatformAdmin (no Staff — staff only manage appointments). */
    val canManageBusiness: Boolean get() = roles.any { it in businessManagerRoles }

    val isPlatformAdmin: Boolean get() = roles.any { it == ROLE_PLATFORM_ADMIN }

    companion object {
        const val ROLE_CUSTOMER = "Customer"
        const val ROLE_STAFF = "Staff"
        const val ROLE_MANAGER = "Manager"
        const val ROLE_OWNER = "Owner"
        const val ROLE_PLATFORM_ADMIN = "PlatformAdmin"

        val staffRoles = setOf(ROLE_STAFF, ROLE_MANAGER, ROLE_OWNER, ROLE_PLATFORM_ADMIN)
        val businessManagerRoles = setOf(ROLE_MANAGER, ROLE_OWNER, ROLE_PLATFORM_ADMIN)
    }
}

/**
 * Mobile session returned by `/auth/login`, `/auth/register`, `/me/password`, `/me/email`.
 *
 * `accessToken` is an opaque DataProtection-protected token (NOT a JWT). Its
 * SHA-256 hash is stored server-side and validated on every authenticated
 * request, so a stamp change (logout / password change / role removal)
 * invalidates the token immediately.
 */
@Serializable
data class MobileSession(
    val accessToken: String = "",
    val tokenType: String = "Bearer",
    val expiresAt: String = "",
    val user: User = User(),
)
