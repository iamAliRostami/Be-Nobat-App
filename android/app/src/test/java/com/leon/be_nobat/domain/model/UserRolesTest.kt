package com.leon.be_nobat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRolesTest {
    @Test fun `empty roles identifies as customer`() {
        val user = User(id = "u", displayName = "Test", roles = emptyList())
        assertTrue(user.isCustomer)
        assertFalse(user.isStaff)
        assertFalse(user.canManageBusiness)
        assertFalse(user.isPlatformAdmin)
    }

    @Test fun `Customer role identifies as customer`() {
        val user = User(id = "u", displayName = "Test", roles = listOf(User.ROLE_CUSTOMER))
        assertTrue(user.isCustomer)
        assertFalse(user.isStaff)
        assertFalse(user.canManageBusiness)
    }

    @Test fun `Staff role is staff but cannot manage business`() {
        val user = User(id = "u", displayName = "Test", roles = listOf(User.ROLE_STAFF))
        assertTrue(user.isStaff)
        assertFalse(user.canManageBusiness)
        assertFalse(user.isPlatformAdmin)
        assertFalse(user.isCustomer)
    }

    @Test fun `Manager role can manage business`() {
        val user = User(id = "u", displayName = "Test", roles = listOf(User.ROLE_MANAGER))
        assertTrue(user.isStaff)
        assertTrue(user.canManageBusiness)
    }

    @Test fun `Owner role can manage business`() {
        val user = User(id = "u", displayName = "Test", roles = listOf(User.ROLE_OWNER))
        assertTrue(user.isStaff)
        assertTrue(user.canManageBusiness)
    }

    @Test fun `PlatformAdmin is platform admin`() {
        val user = User(id = "u", displayName = "Test", roles = listOf(User.ROLE_PLATFORM_ADMIN))
        assertTrue(user.isPlatformAdmin)
        assertTrue(user.canManageBusiness)
        assertTrue(user.isStaff)
    }

    @Test fun `User with both Customer and PlatformAdmin roles is platform admin`() {
        val user = User(id = "u", displayName = "Test", roles = listOf(User.ROLE_CUSTOMER, User.ROLE_PLATFORM_ADMIN))
        assertTrue(user.isPlatformAdmin)
        assertTrue(user.isCustomer)
    }

    @Test fun `avatarUrl is null by default`() {
        val user = User(id = "u", displayName = "Test")
        assertEquals(null, user.avatarUrl)
    }

    @Test fun `user with Customer and Staff roles counts as staff`() {
        val user = User(id = "u", displayName = "Test", roles = listOf(User.ROLE_CUSTOMER, User.ROLE_STAFF))
        // The "isStaff" check looks for any staff-or-higher role.
        assertTrue(user.isStaff)
        // "isCustomer" returns true if any role is Customer OR roles is empty.
        assertTrue(user.isCustomer)
    }

    @Test fun `user initials returns first letters of first two words`() {
        val user = User(id = "u", displayName = "علی رستمی")
        assertEquals("عر", user.initials())
    }

    @Test fun `user initials handles single word`() {
        val user = User(id = "u", displayName = "Ali")
        assertEquals("Al", user.initials())
    }

    @Test fun `user initials handles blank name`() {
        val user = User(id = "u", displayName = "   ")
        assertEquals("?", user.initials())
    }
}
