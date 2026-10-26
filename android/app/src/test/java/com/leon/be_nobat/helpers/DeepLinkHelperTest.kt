package com.leon.be_nobat.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkHelperTest {

    @Test
    fun createBusinessWebLink_withoutBranch_generatesCorrectUrl() {
        val link = DeepLinkHelper.createBusinessWebLink("biz-100")
        assertEquals("https://benobat.ir/b/biz-100", link)
    }

    @Test
    fun createBusinessWebLink_withBranch_generatesCorrectUrl() {
        val link = DeepLinkHelper.createBusinessWebLink("biz-100", "branch-50")
        assertEquals("https://benobat.ir/b/biz-100?branch=branch-50", link)
    }

    @Test
    fun createBusinessAppLink_withoutBranch_generatesAppScheme() {
        val link = DeepLinkHelper.createBusinessAppLink("biz-100")
        assertEquals("benobat://business/biz-100", link)
    }

    @Test
    fun createBusinessAppLink_withBranch_generatesAppScheme() {
        val link = DeepLinkHelper.createBusinessAppLink("biz-100", "branch-50")
        assertEquals("benobat://business/biz-100?branch=branch-50", link)
    }

    @Test
    fun parseUrl_businessWebShortLink_parsesCorrectly() {
        val parsed = DeepLinkHelper.parseUrl("https://benobat.ir/b/biz-test-123")
        assertNotNull(parsed)
        assertEquals("biz-test-123", parsed?.businessId)
        assertNull(parsed?.branchId)
        assertFalse(parsed!!.isBookingRoute)
    }

    @Test
    fun parseUrl_businessWebLinkWithBranch_parsesBothIds() {
        val parsed = DeepLinkHelper.parseUrl("https://benobat.ir/b/biz-test-123?branch=branch-999")
        assertNotNull(parsed)
        assertEquals("biz-test-123", parsed?.businessId)
        assertEquals("branch-999", parsed?.branchId)
        assertFalse(parsed!!.isBookingRoute)
    }

    @Test
    fun parseUrl_customAppScheme_parsesCorrectly() {
        val parsed = DeepLinkHelper.parseUrl("benobat://business/biz-barber-1?branch=main-branch")
        assertNotNull(parsed)
        assertEquals("biz-barber-1", parsed?.businessId)
        assertEquals("main-branch", parsed?.branchId)
        assertFalse(parsed!!.isBookingRoute)
    }

    @Test
    fun parseUrl_directBookingRoute_marksIsBookingRouteTrue() {
        val parsed = DeepLinkHelper.parseUrl("https://benobat.ir/book/biz-barber-1?branch=main-branch")
        assertNotNull(parsed)
        assertEquals("biz-barber-1", parsed?.businessId)
        assertEquals("main-branch", parsed?.branchId)
        assertTrue(parsed!!.isBookingRoute)
    }

    @Test
    fun parseUrl_standaloneBranchRoute_extractsBranchId() {
        val parsed = DeepLinkHelper.parseUrl("https://benobat.ir/branch/branch-only-88")
        assertNotNull(parsed)
        assertNull(parsed?.businessId)
        assertEquals("branch-only-88", parsed?.branchId)
        assertFalse(parsed!!.isBookingRoute)
    }
}
