package com.leon.be_nobat.helpers

import android.app.TimePickerDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.WeeklyHours
import com.leon.be_nobat.domain.model.WeeklyTimeRange
import java.util.Locale

class WeeklyHoursEditor : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, initial: List<WeeklyTimeRange>) : super(context) {
        setWeeklyHours(initial)
    }

    private val ranges = WeeklyHours.defaults().toMutableList()
    val value: List<WeeklyTimeRange> get() = ranges.toList()

    private val days = listOf(
        6 to "شنبه",
        0 to "یکشنبه",
        1 to "دوشنبه",
        2 to "سه‌شنبه",
        3 to "چهارشنبه",
        4 to "پنجشنبه",
        5 to "جمعه",
    )

    private var selectedDayId: Int = 6

    private val layoutDayTabs: LinearLayout
    private val tvSelectedDayTitle: TextView
    private val tvSelectedDayStatus: TextView
    private val switchDayActive: MaterialSwitch
    private val layoutRangesContainer: LinearLayout
    private val layoutDayClosed: LinearLayout
    private val tvDayClosedMessage: TextView
    private val btnEnableDay: MaterialButton
    private val layoutDayActions: LinearLayout
    private val btnAddAnotherRange: MaterialButton
    private val btnCopyDay: MaterialButton

    private var isUpdatingSwitch = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_weekly_hours_editor, this, true)

        layoutDayTabs = findViewById(R.id.layoutDayTabs)
        tvSelectedDayTitle = findViewById(R.id.tvSelectedDayTitle)
        tvSelectedDayStatus = findViewById(R.id.tvSelectedDayStatus)
        switchDayActive = findViewById(R.id.switchDayActive)
        layoutRangesContainer = findViewById(R.id.layoutRangesContainer)
        layoutDayClosed = findViewById(R.id.layoutDayClosed)
        tvDayClosedMessage = findViewById(R.id.tvDayClosedMessage)
        btnEnableDay = findViewById(R.id.btnEnableDay)
        layoutDayActions = findViewById(R.id.layoutDayActions)
        btnAddAnotherRange = findViewById(R.id.btnAddAnotherRange)
        btnCopyDay = findViewById(R.id.btnCopyDay)

        setupListeners()
        render()
    }

    fun setWeeklyHours(newHours: List<WeeklyTimeRange>) {
        ranges.clear()
        ranges.addAll(newHours)
        render()
    }

    private fun setupListeners() {
        switchDayActive.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                if (ranges.none { it.dayOfWeek == selectedDayId }) {
                    ranges.add(WeeklyTimeRange(selectedDayId, "09:00:00", "18:00:00"))
                }
            } else {
                ranges.removeAll { it.dayOfWeek == selectedDayId }
            }
            render()
        }

        btnEnableDay.setOnClickListener {
            ranges.add(WeeklyTimeRange(selectedDayId, "09:00:00", "18:00:00"))
            render()
        }

        btnAddAnotherRange.setOnClickListener {
            val daily = ranges.filter { it.dayOfWeek == selectedDayId }
            val lastEnd = daily.maxOfOrNull { WeeklyHours.seconds(it.endsAt) } ?: (8 * 3600)
            val start = if (lastEnd < 23 * 3600) lastEnd else (8 * 3600)
            val end = minOf(start + 3600, 24 * 3600 - 60)
            ranges.add(WeeklyTimeRange(selectedDayId, formatTime(start), formatTime(end)))
            render()
        }

        btnCopyDay.setOnClickListener {
            val selectedDayName = days.firstOrNull { it.first == selectedDayId }?.second ?: ""
            MaterialAlertDialogBuilder(context)
                .setTitle("کپی برنامه هفتگی")
                .setMessage("آیا مایلید برنامه روز $selectedDayName برای سایر روزهای هفته کپی شود؟")
                .setPositiveButton("کپی برای همه") { _, _ ->
                    val currentDaily = ranges.filter { it.dayOfWeek == selectedDayId }
                    days.forEach { (d, _) ->
                        if (d != selectedDayId) {
                            ranges.removeAll { it.dayOfWeek == d }
                            currentDaily.forEach { r -> ranges.add(r.copy(dayOfWeek = d)) }
                        }
                    }
                    render()
                    Toast.makeText(context, "برنامه روز $selectedDayName برای تمام روزها اعمال شد.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }
    }

    private fun render() {
        renderDayTabs()
        renderSelectedDay()
    }

    private fun renderDayTabs() {
        layoutDayTabs.removeAllViews()
        val inflater = LayoutInflater.from(context)

        days.forEach { (day, label) ->
            val daily = ranges.filter { it.dayOfWeek == day }
            val isDayActive = daily.isNotEmpty()
            val isSelected = (day == selectedDayId)

            val tabView = inflater.inflate(R.layout.item_weekly_day_tab, layoutDayTabs, false)
            val tvDayName = tabView.findViewById<TextView>(R.id.tvDayName)
            val viewDayDot = tabView.findViewById<android.view.View>(R.id.viewDayDot)
            val tvDayBadge = tabView.findViewById<TextView>(R.id.tvDayBadge)

            tvDayName.text = label

            if (isDayActive) {
                viewDayDot.setBackgroundResource(R.drawable.bg_dot_active)
                tvDayBadge.text = if (daily.size == 1) "فعال" else "${PersianUtils.toPersianDigits(daily.size)} بازه"
                tvDayBadge.setTextColor(ContextCompat.getColor(context, R.color.status_completed_text))
            } else {
                viewDayDot.setBackgroundResource(R.drawable.bg_dot_closed)
                tvDayBadge.text = "تعطیل"
                tvDayBadge.setTextColor(ContextCompat.getColor(context, R.color.colorOnSurfaceVariant))
            }

            if (isSelected) {
                tabView.setBackgroundResource(R.drawable.bg_day_tab_selected)
                tvDayName.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
            } else {
                tabView.setBackgroundResource(R.drawable.bg_day_tab_unselected)
                tvDayName.setTextColor(ContextCompat.getColor(context, R.color.colorOnSurface))
            }

            tabView.setOnClickListener {
                selectedDayId = day
                render()
            }

            layoutDayTabs.addView(tabView)
        }
    }

    private fun renderSelectedDay() {
        val selectedDayName = days.firstOrNull { it.first == selectedDayId }?.second ?: ""
        tvSelectedDayTitle.text = "تنظیم برنامه: $selectedDayName"

        val daily = ranges.filter { it.dayOfWeek == selectedDayId }.sortedBy { WeeklyHours.seconds(it.startsAt) }
        val isDayActive = daily.isNotEmpty()

        isUpdatingSwitch = true
        switchDayActive.isChecked = isDayActive
        isUpdatingSwitch = false

        if (isDayActive) {
            tvSelectedDayStatus.text = "روز کاری (${PersianUtils.toPersianDigits(daily.size)} بازه)"
            tvSelectedDayStatus.setTextColor(ContextCompat.getColor(context, R.color.status_completed_text))
            layoutRangesContainer.isVisible = true
            layoutDayActions.isVisible = true
            layoutDayClosed.isVisible = false

            btnAddAnotherRange.text = "+ بازه دیگر برای $selectedDayName"

            layoutRangesContainer.removeAllViews()
            val inflater = LayoutInflater.from(context)

            daily.forEachIndexed { index, range ->
                val row = inflater.inflate(R.layout.item_weekly_range_row, layoutRangesContainer, false)
                val tvRangeIndex = row.findViewById<TextView>(R.id.tvRangeIndex)
                val btnStartTime = row.findViewById<MaterialButton>(R.id.btnStartTime)
                val btnEndTime = row.findViewById<MaterialButton>(R.id.btnEndTime)
                val btnDeleteRange = row.findViewById<ImageButton>(R.id.btnDeleteRange)

                tvRangeIndex.text = "بازه ${PersianUtils.toPersianDigits(index + 1)}"
                btnStartTime.text = "از ${PersianUtils.toPersianDigits(range.startsAt.take(5))}"
                btnEndTime.text = "تا ${PersianUtils.toPersianDigits(range.endsAt.take(5))}"

                btnStartTime.setOnClickListener {
                    val sec = WeeklyHours.seconds(range.startsAt)
                    TimePickerDialog(context, { _, hour, minute ->
                        val newStart = "%02d:%02d:00".format(Locale.US, hour, minute)
                        val idx = ranges.indexOf(range)
                        if (idx >= 0) {
                            ranges[idx] = range.copy(startsAt = newStart)
                            render()
                        }
                    }, sec / 3600, sec / 60 % 60, true).show()
                }

                btnEndTime.setOnClickListener {
                    val sec = WeeklyHours.seconds(range.endsAt)
                    TimePickerDialog(context, { _, hour, minute ->
                        val newEnd = "%02d:%02d:00".format(Locale.US, hour, minute)
                        val idx = ranges.indexOf(range)
                        if (idx >= 0) {
                            ranges[idx] = range.copy(endsAt = newEnd)
                            render()
                        }
                    }, sec / 3600, sec / 60 % 60, true).show()
                }

                btnDeleteRange.setOnClickListener {
                    ranges.remove(range)
                    render()
                }

                layoutRangesContainer.addView(row)
            }
        } else {
            tvSelectedDayStatus.text = "تعطیل"
            tvSelectedDayStatus.setTextColor(ContextCompat.getColor(context, R.color.colorOnSurfaceVariant))
            layoutRangesContainer.isVisible = false
            layoutDayActions.isVisible = false
            layoutDayClosed.isVisible = true
            tvDayClosedMessage.text = "شعبه در روز $selectedDayName تعطیل است"
        }
    }

    private fun formatTime(seconds: Int): String {
        val h = (seconds / 3600).coerceIn(0, 23)
        val m = ((seconds % 3600) / 60).coerceIn(0, 59)
        return "%02d:%02d:00".format(Locale.US, h, m)
    }
}
