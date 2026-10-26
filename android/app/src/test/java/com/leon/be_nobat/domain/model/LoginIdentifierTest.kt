package com.leon.be_nobat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginIdentifierTest {
    @Test fun `accepts common valid email formats`() {
        assertTrue(LoginIdentifier.parse("user.name+tag@example.co.uk").isSuccess)
    }

    @Test fun `rejects malformed iranian mobile`() {
        assertTrue(LoginIdentifier.parse("091234567").isFailure)
    }

    @Test fun `accepts mobile without zero prefix`() {
        val identifier = LoginIdentifier.parse("9121234567").getOrThrow()
        assertEquals("09121234567", identifier.value)
    }

    @Test fun `normalizes 0098 prefix to international format`() {
        val identifier = LoginIdentifier.parse("00989121234567").getOrThrow()
        assertEquals("09121234567", identifier.value)
    }

    @Test fun `normalizes +98 prefix to international format`() {
        val identifier = LoginIdentifier.parse("+989121234567").getOrThrow()
        assertEquals("09121234567", identifier.value)
    }

    @Test fun `normalizeIranianMobileForRegister returns 09 prefix`() {
        assertEquals("09121234567", LoginIdentifier.normalizeIranianMobileForRegister("+989121234567"))
        assertEquals("09121234567", LoginIdentifier.normalizeIranianMobileForRegister("00989121234567"))
        assertEquals("09121234567", LoginIdentifier.normalizeIranianMobileForRegister("09121234567"))
        assertEquals("09121234567", LoginIdentifier.normalizeIranianMobileForRegister("9121234567"))
    }

    @Test fun `parses lowercase email`() {
        val identifier = LoginIdentifier.parse("USER@EXAMPLE.COM").getOrThrow() as LoginIdentifier.Email
        assertEquals("user@example.com", identifier.value)
    }

    @Test fun `parses mobile into Mobile variant`() {
        val identifier = LoginIdentifier.parse("09121234567").getOrThrow()
        assertTrue(identifier is LoginIdentifier.Mobile)
    }

    @Test fun `parses email into Email variant`() {
        val identifier = LoginIdentifier.parse("user@example.com").getOrThrow()
        assertTrue(identifier is LoginIdentifier.Email)
    }

    @Test fun `empty string is rejected`() {
        assertTrue(LoginIdentifier.parse("").isFailure)
    }

    @Test fun `whitespace string is rejected`() {
        assertTrue(LoginIdentifier.parse("    ").isFailure)
    }

    @Test fun `email missing TLD is rejected`() {
        assertTrue(LoginIdentifier.parse("user@example").isFailure)
    }
}
