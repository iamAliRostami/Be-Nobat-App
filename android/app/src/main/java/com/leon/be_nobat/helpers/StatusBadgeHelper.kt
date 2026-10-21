package com.leon.be_nobat.helpers

import android.content.Context
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.leon.be_nobat.R

/**
 * Centralized helper for styling status badges across appointment lists, admin views,
 * and review items. Dynamically applies appropriate pill shapes, pastel backgrounds,
 * and high-contrast semantic colors based on state.
 */
object StatusBadgeHelper {

    enum class BadgeStyle(
        @DrawableRes val backgroundRes: Int,
        @ColorRes val textColorRes: Int,
    ) {
        PENDING(R.drawable.bg_status_badge_pending, R.color.status_pending_text),
        CONFIRMED(R.drawable.bg_status_badge_confirmed, R.color.status_confirmed_text),
        COMPLETED(R.drawable.bg_status_badge_completed, R.color.status_completed_text),
        CANCELLED(R.drawable.bg_status_badge_cancelled, R.color.status_cancelled_text),
        NEUTRAL(R.drawable.bg_status_badge_noshow, R.color.status_noshow_text);
    }

    fun resolveAppointmentStyle(status: String): BadgeStyle = when (status) {
        "Pending" -> BadgeStyle.PENDING
        "Confirmed" -> BadgeStyle.CONFIRMED
        "Completed" -> BadgeStyle.COMPLETED
        "Cancelled" -> BadgeStyle.CANCELLED
        "NoShow" -> BadgeStyle.NEUTRAL
        else -> BadgeStyle.NEUTRAL
    }

    /**
     * Resolves localized label and visual badge style for an appointment status.
     */
    fun resolveAppointmentStatus(
        context: Context,
        status: String,
    ): Pair<String, BadgeStyle> {
        val label = when (status) {
            "Pending" -> context.getString(R.string.appointment_status_pending)
            "Confirmed" -> context.getString(R.string.appointment_status_confirmed)
            "Completed" -> context.getString(R.string.appointment_status_completed)
            "Cancelled" -> context.getString(R.string.appointment_status_cancelled)
            "NoShow" -> context.getString(R.string.appointment_status_no_show)
            else -> status
        }
        return label to resolveAppointmentStyle(status)
    }

    /**
     * Applies localized label, pill background, and semantic text color to [textView]
     * according to the appointment [status].
     */
    fun applyAppointmentStatus(textView: TextView, status: String) {
        val (label, style) = resolveAppointmentStatus(textView.context, status)
        textView.text = label
        applyBadgeStyle(textView, style)
    }

    /**
     * Applies styling for active/inactive entities (branches, services, users, etc.).
     */
    fun applyActiveStatus(textView: TextView, isActive: Boolean) {
        val context = textView.context
        val label = if (isActive) context.getString(R.string.status_active) else context.getString(R.string.status_inactive)
        val style = if (isActive) BadgeStyle.COMPLETED else BadgeStyle.NEUTRAL
        textView.text = label
        applyBadgeStyle(textView, style)
    }

    /**
     * Applies the colored status indicator strip to [view] for active/inactive entities.
     */
    fun applyActiveStatusIndicator(view: android.view.View, isActive: Boolean) {
        val colorRes = if (isActive) R.color.status_indicator_confirmed else R.color.status_indicator_noshow
        applyIndicatorStrip(view, colorRes)
    }

    /**
     * Applies styling for review status (Pending, Published, Rejected).
     */
    fun applyReviewStatus(textView: TextView, status: String) {
        val context = textView.context
        val (label, style) = when (status) {
            "Pending" -> context.getString(R.string.review_status_pending) to BadgeStyle.PENDING
            "Published" -> context.getString(R.string.review_status_published) to BadgeStyle.COMPLETED
            "Rejected" -> context.getString(R.string.review_status_rejected) to BadgeStyle.CANCELLED
            else -> status to BadgeStyle.NEUTRAL
        }
        textView.text = label
        applyBadgeStyle(textView, style)
    }

    fun applyBadgeStyle(textView: TextView, style: BadgeStyle) {
        textView.setBackgroundResource(style.backgroundRes)
        textView.setTextColor(ContextCompat.getColor(textView.context, style.textColorRes))
    }

    /**
     * Applies the colored status indicator strip to [view] for an appointment status.
     * Matches the card border colors in the Web application's calendar.
     */
    fun applyAppointmentStatusIndicator(view: android.view.View, status: String) {
        val colorRes = when (status) {
            "Confirmed" -> R.color.status_indicator_confirmed
            "Pending" -> R.color.status_indicator_pending
            "Completed" -> R.color.status_indicator_completed
            "Cancelled" -> R.color.status_indicator_cancelled
            "NoShow" -> R.color.status_indicator_noshow
            else -> R.color.status_indicator_default
        }
        applyIndicatorStrip(view, colorRes)
    }

    /**
     * Applies the colored status indicator strip to [view] for a review/evaluation status.
     * Matches the review card border colors in the Web application's reviews panel.
     */
    fun applyReviewStatusIndicator(view: android.view.View, status: String) {
        val colorRes = when (status) {
            "Published" -> R.color.status_indicator_confirmed
            "Pending" -> R.color.status_indicator_pending
            "Rejected" -> R.color.status_indicator_cancelled
            else -> R.color.status_indicator_default
        }
        applyIndicatorStrip(view, colorRes)
    }

    private fun applyIndicatorStrip(view: android.view.View, @ColorRes colorRes: Int) {
        view.visibility = android.view.View.VISIBLE
        val color = ContextCompat.getColor(view.context, colorRes)
        val cornerRadius = view.resources.displayMetrics.density * 16f
        val isRtl = view.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        val radii = if (isRtl) {
            floatArrayOf(
                0f, 0f,
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                0f, 0f
            )
        } else {
            floatArrayOf(
                cornerRadius, cornerRadius,
                0f, 0f,
                0f, 0f,
                cornerRadius, cornerRadius
            )
        }
        val shape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(color)
        }
        view.background = shape
    }
}

