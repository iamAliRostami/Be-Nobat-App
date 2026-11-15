package com.leon.be_nobat.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leon.be_nobat.R
import java.util.Calendar

object PersianDatePickerDialog {

    data class DayCell(
        val dayNumber: Int?, // null if blank
        val jalaliDate: JalaliDate?,
        val isSelected: Boolean = false,
        val isToday: Boolean = false,
        val isEnabled: Boolean = true,
    )

    fun show(
        context: Context,
        initialIsoDate: String? = null,
        minIsoDate: String? = null,
        onDateSelected: (isoDate: String, jalaliDate: JalaliDate) -> Unit,
    ) {
        val today = JalaliDateHelper.getTodayJalali()
        val initialJalali = if (!initialIsoDate.isNullOrBlank()) {
            val parts = initialIsoDate.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size == 3) {
                JalaliDateHelper.gregorianToJalali(parts[0], parts[1], parts[2])
            } else today
        } else today

        var viewingYear = initialJalali.year
        var viewingMonth = initialJalali.month
        var selectedDate = initialJalali

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_persian_date_picker, null)
        val tvSelectedPersianDate = dialogView.findViewById<TextView>(R.id.tvSelectedPersianDate)
        val tvMonthYear = dialogView.findViewById<TextView>(R.id.tvMonthYear)
        val btnPrevMonth = dialogView.findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNextMonth = dialogView.findViewById<ImageButton>(R.id.btnNextMonth)
        val rvCalendarDays = dialogView.findViewById<RecyclerView>(R.id.rvCalendarDays)
        val btnToday = dialogView.findViewById<MaterialButton>(R.id.btnToday)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirm)

        rvCalendarDays.layoutManager = GridLayoutManager(context, 7)

        var dialog: AlertDialog? = null

        fun updateSelectedHeader() {
            val (gy, gm, gd) = JalaliDateHelper.jalaliToGregorian(selectedDate.year, selectedDate.month, selectedDate.day)
            val cal = Calendar.getInstance().apply { set(gy, gm - 1, gd) }
            val weekdayIdx = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> 0
                Calendar.SUNDAY -> 1
                Calendar.MONDAY -> 2
                Calendar.TUESDAY -> 3
                Calendar.WEDNESDAY -> 4
                Calendar.THURSDAY -> 5
                Calendar.FRIDAY -> 6
                else -> 0
            }
            val weekdayName = JalaliDateHelper.getWeekdayName(weekdayIdx)
            tvSelectedPersianDate.text = "$weekdayName، ${selectedDate.toDisplayString()}"
        }

        fun getDaysInMonth(year: Int, month: Int): Int {
            if (month in 1..6) return 31
            if (month in 7..11) return 30
            val (gy, gm, gd) = JalaliDateHelper.jalaliToGregorian(year, 12, 30)
            val back = JalaliDateHelper.gregorianToJalali(gy, gm, gd)
            return if (back.year == year && back.month == 12 && back.day == 30) 30 else 29
        }

        fun getFirstWeekdayOffset(year: Int, month: Int): Int {
            val (gy, gm, gd) = JalaliDateHelper.jalaliToGregorian(year, month, 1)
            val cal = Calendar.getInstance().apply { set(gy, gm - 1, gd) }
            return when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> 0
                Calendar.SUNDAY -> 1
                Calendar.MONDAY -> 2
                Calendar.TUESDAY -> 3
                Calendar.WEDNESDAY -> 4
                Calendar.THURSDAY -> 5
                Calendar.FRIDAY -> 6
                else -> 0
            }
        }

        fun buildCells(): List<DayCell> {
            val daysCount = getDaysInMonth(viewingYear, viewingMonth)
            val offset = getFirstWeekdayOffset(viewingYear, viewingMonth)
            val cells = ArrayList<DayCell>(42)

            for (i in 0 until offset) {
                cells.add(DayCell(null, null))
            }

            for (d in 1..daysCount) {
                val jd = JalaliDate(viewingYear, viewingMonth, d)
                val isSelected = (jd.year == selectedDate.year && jd.month == selectedDate.month && jd.day == selectedDate.day)
                val isToday = (jd.year == today.year && jd.month == today.month && jd.day == today.day)

                var isEnabled = true
                if (!minIsoDate.isNullOrBlank()) {
                    val cellIso = jd.toIsoDate()
                    if (cellIso < minIsoDate) {
                        isEnabled = false
                    }
                }

                cells.add(DayCell(d, jd, isSelected, isToday, isEnabled))
            }

            // Fill trailing cells to complete the last row
            while (cells.size % 7 != 0) {
                cells.add(DayCell(null, null))
            }

            return cells
        }

        lateinit var adapter: DaysAdapter

        fun refreshGrid() {
            val monthName = JalaliDateHelper.PERSIAN_MONTHS.getOrElse(viewingMonth - 1) { "" }
            tvMonthYear.text = "$monthName ${PersianUtils.toPersianDigits(viewingYear)}"
            updateSelectedHeader()
            adapter.submit(buildCells())
        }

        adapter = DaysAdapter { cell ->
            if (cell.jalaliDate != null && cell.isEnabled) {
                selectedDate = cell.jalaliDate
                refreshGrid()
            }
        }
        rvCalendarDays.adapter = adapter

        // Previous month (in RTL right chevron means earlier)
        btnPrevMonth.setOnClickListener {
            if (viewingMonth == 1) {
                viewingMonth = 12
                viewingYear--
            } else {
                viewingMonth--
            }
            refreshGrid()
        }

        // Next month (in RTL left chevron means later)
        btnNextMonth.setOnClickListener {
            if (viewingMonth == 12) {
                viewingMonth = 1
                viewingYear++
            } else {
                viewingMonth++
            }
            refreshGrid()
        }

        btnToday.setOnClickListener {
            viewingYear = today.year
            viewingMonth = today.month
            selectedDate = today
            refreshGrid()
        }

        btnCancel.setOnClickListener {
            dialog?.dismiss()
        }

        btnConfirm.setOnClickListener {
            val iso = selectedDate.toIsoDate()
            onDateSelected(iso, selectedDate)
            dialog?.dismiss()
        }

        refreshGrid()

        dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()
        dialog.show()
    }

    private class DaysAdapter(
        private val onCellClicked: (DayCell) -> Unit,
    ) : RecyclerView.Adapter<DaysAdapter.VH>() {

        private val items = ArrayList<DayCell>()

        fun submit(newItems: List<DayCell>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_persian_calendar_day, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cell = items[position]
            holder.bind(cell, onCellClicked)
        }

        override fun getItemCount(): Int = items.size

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvDayNumber: TextView = itemView.findViewById(R.id.tvDayNumber)

            fun bind(cell: DayCell, onClick: (DayCell) -> Unit) {
                if (cell.dayNumber == null) {
                    tvDayNumber.text = ""
                    tvDayNumber.background = null
                    itemView.setOnClickListener(null)
                    itemView.isClickable = false
                    return
                }

                tvDayNumber.text = PersianUtils.toPersianDigits(cell.dayNumber)
                itemView.isClickable = cell.isEnabled

                when {
                    cell.isSelected -> {
                        tvDayNumber.setBackgroundResource(R.drawable.bg_persian_date_selected)
                        tvDayNumber.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorOnPrimary))
                    }
                    cell.isToday -> {
                        tvDayNumber.setBackgroundResource(R.drawable.bg_persian_date_today)
                        tvDayNumber.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorPrimary))
                    }
                    !cell.isEnabled -> {
                        tvDayNumber.background = null
                        tvDayNumber.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorOutline))
                    }
                    else -> {
                        tvDayNumber.background = null
                        tvDayNumber.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorOnSurface))
                    }
                }

                if (cell.isEnabled) {
                    itemView.setOnClickListener { onClick(cell) }
                } else {
                    itemView.setOnClickListener(null)
                }
            }
        }
    }
}
