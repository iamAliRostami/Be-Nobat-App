package com.leon.be_nobat.helpers

import com.leon.be_nobat.R
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBadgeHelperTest {

    @Test
    fun `BadgeStyle maps to correct drawables and colors`() {
        assertEquals(R.drawable.bg_status_badge_pending, StatusBadgeHelper.BadgeStyle.PENDING.backgroundRes)
        assertEquals(R.color.status_pending_text, StatusBadgeHelper.BadgeStyle.PENDING.textColorRes)

        assertEquals(R.drawable.bg_status_badge_confirmed, StatusBadgeHelper.BadgeStyle.CONFIRMED.backgroundRes)
        assertEquals(R.color.status_confirmed_text, StatusBadgeHelper.BadgeStyle.CONFIRMED.textColorRes)

        assertEquals(R.drawable.bg_status_badge_completed, StatusBadgeHelper.BadgeStyle.COMPLETED.backgroundRes)
        assertEquals(R.color.status_completed_text, StatusBadgeHelper.BadgeStyle.COMPLETED.textColorRes)

        assertEquals(R.drawable.bg_status_badge_cancelled, StatusBadgeHelper.BadgeStyle.CANCELLED.backgroundRes)
        assertEquals(R.color.status_cancelled_text, StatusBadgeHelper.BadgeStyle.CANCELLED.textColorRes)

        assertEquals(R.drawable.bg_status_badge_noshow, StatusBadgeHelper.BadgeStyle.NEUTRAL.backgroundRes)
        assertEquals(R.color.status_noshow_text, StatusBadgeHelper.BadgeStyle.NEUTRAL.textColorRes)
    }

    @Test
    fun `resolveAppointmentStyle maps status string to correct style`() {
        assertEquals(StatusBadgeHelper.BadgeStyle.PENDING, StatusBadgeHelper.resolveAppointmentStyle("Pending"))
        assertEquals(StatusBadgeHelper.BadgeStyle.CONFIRMED, StatusBadgeHelper.resolveAppointmentStyle("Confirmed"))
        assertEquals(StatusBadgeHelper.BadgeStyle.COMPLETED, StatusBadgeHelper.resolveAppointmentStyle("Completed"))
        assertEquals(StatusBadgeHelper.BadgeStyle.CANCELLED, StatusBadgeHelper.resolveAppointmentStyle("Cancelled"))
        assertEquals(StatusBadgeHelper.BadgeStyle.NEUTRAL, StatusBadgeHelper.resolveAppointmentStyle("NoShow"))
        assertEquals(StatusBadgeHelper.BadgeStyle.NEUTRAL, StatusBadgeHelper.resolveAppointmentStyle("UnknownStatus"))
    }
}
