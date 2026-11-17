package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.JalaliDateHelper
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.helpers.StatusBadgeHelper
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.view_models.admin.AdminCalendarUiState
import com.leon.be_nobat.ui.view_models.admin.AdminCalendarViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminCalendarActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: AdminCalendarViewModel by viewModel()
    private lateinit var adapter: ApptsAdapter
    private lateinit var slotsAdapter: CalendarSlotsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var listView: RecyclerView
    private lateinit var slotsGrid: RecyclerView
    private var isSettingUp = true
    private var hasResumedOnce = false
    private var smsDialogShowing = false
    private val smsPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        viewModel.smsPermissionPending = false
        sendNextSms()
    }

    private fun sendNextSms() {
        val appointment = viewModel.smsQueue.value.firstOrNull() ?: return
        // Acknowledge before handing off to telephony so recreation cannot send the same SMS twice.
        viewModel.acknowledgeSms(appointment.id)
        showFeedback(com.leon.be_nobat.notifications.AppointmentSms.send(this, appointment))
    }

    private fun skipSms() {
        smsDialogShowing = false
        viewModel.smsQueue.value.firstOrNull()?.let { viewModel.acknowledgeSms(it.id) }
        showFeedback("وضعیت ذخیره شد؛ پیامک ارسال نشد.")
    }

    private fun processSmsQueue() {
        if (viewModel.smsQueue.value.isEmpty() || viewModel.smsPermissionPending || smsDialogShowing) return
        if (com.leon.be_nobat.BuildConfig.DEMO_MODE ||
            com.leon.be_nobat.notifications.AppointmentMessages.phone(viewModel.smsQueue.value.first().customerPhoneNumber) == null ||
            !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY) ||
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            sendNextSms()
        } else {
            smsDialogShowing = true
            MaterialAlertDialogBuilder(this)
                .setTitle("پیامک وضعیت نوبت")
                .setMessage("وضعیت نوبت ذخیره شد. برای اطلاع‌رسانی از سیم‌کارت پیش‌فرض پیامک این گوشی به مشتری، مجوز ارسال پیامک لازم است. هزینهٔ پیامک طبق تعرفهٔ سیم‌کارت محاسبه می‌شود.")
                .setPositiveButton("ادامه") { _, _ ->
                    smsDialogShowing = false
                    viewModel.smsPermissionPending = true
                    smsPermission.launch(android.Manifest.permission.SEND_SMS)
                }
                .setNegativeButton("بدون پیامک") { _, _ -> skipSms() }
                .setOnCancelListener { skipSms() }
                .show()
        }
    }

    companion object {
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_CUSTOMER_ID = "extra_customer_id"
        const val EXTRA_CUSTOMER_NAME = "extra_customer_name"

        fun createIntent(
            context: Context,
            status: String? = null,
            date: String? = null,
            customerId: String? = null,
            customerName: String? = null,
        ): Intent {
            return Intent(context, AdminCalendarActivity::class.java).apply {
                status?.let { putExtra(EXTRA_STATUS, it) }
                date?.let { putExtra(EXTRA_DATE, it) }
                customerId?.let { putExtra(EXTRA_CUSTOMER_ID, it) }
                customerName?.let { putExtra(EXTRA_CUSTOMER_NAME, it) }
            }
        }
    }

    override fun setupViews() {
        val initialStatus = intent.getStringExtra(EXTRA_STATUS)
        val initialDate = intent.getStringExtra(EXTRA_DATE)
        val initialCustomerId = intent.getStringExtra(EXTRA_CUSTOMER_ID)
        val initialCustomerName = intent.getStringExtra(EXTRA_CUSTOMER_NAME)

        val headerTitle = if (!initialCustomerName.isNullOrBlank()) {
            "نوبت‌های $initialCustomerName"
        } else {
            getString(R.string.admin_calendar)
        }
        setupAdminHeader(headerTitle, currentMenuItemId = R.id.drawer_calendar)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        // Setup Calendar Date Toolbar
        val layoutCalendarToolbar = findViewById<android.view.View>(R.id.layoutCalendarToolbar)
        layoutCalendarToolbar.isVisible = true

        findViewById<android.view.View>(R.id.btnCalendarToday).setOnClickListener {
            viewModel.changeDay(0)
        }
        findViewById<android.view.View>(R.id.btnCalendarPrev).setOnClickListener {
            viewModel.changeDay(-1)
        }
        findViewById<android.view.View>(R.id.btnCalendarNext).setOnClickListener {
            viewModel.changeDay(1)
        }
        findViewById<android.view.View>(R.id.layoutDateClickable).setOnClickListener {
            showDatePickerDialog()
        }

        val toggleDateMode = findViewById<MaterialButtonToggleGroup>(R.id.toggleDateMode)
        toggleDateMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isSettingUp) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnModeDay -> viewModel.setAllDates(false)
                R.id.btnModeAllDates -> viewModel.setAllDates(true)
            }
        }

        listView = findViewById(R.id.list)
        slotsGrid = findViewById(R.id.rvCalendarSlotsGrid)

        slotsAdapter = CalendarSlotsAdapter { slot -> onSlotClicked(slot) }
        slotsGrid.apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@AdminCalendarActivity, 4)
            adapter = slotsAdapter
        }

        adapter = ApptsAdapter(
            onItemClick = { appt -> showAppointmentDetails(appt) },
            onConfirm = { appt -> openConfirmRescheduleDialog(appt) },
            onComplete = { appt ->
                viewModel.changeStatus(appt.id, "Completed")
            },
            onCancel = { appt -> confirmCancel(appt) },
        )

        listView.apply {
            layoutManager = LinearLayoutManager(this@AdminCalendarActivity)
            this.adapter = this@AdminCalendarActivity.adapter
        }

        val tabs = findViewById<TabLayout>(R.id.statusTabs)
        val statusList = listOf(
            "" to getString(R.string.all),
            "Pending" to getString(R.string.appointment_status_pending),
            "Confirmed" to getString(R.string.appointment_status_confirmed),
            "Completed" to getString(R.string.appointment_status_completed),
            "Cancelled" to getString(R.string.appointment_status_cancelled),
            "NoShow" to getString(R.string.appointment_status_no_show)
        )

        var selectedIndex = 0
        statusList.forEachIndexed { index, (key, label) ->
            val tab = tabs.newTab().setText(label).also { it.tag = key }
            tabs.addTab(tab)
            if (!initialStatus.isNullOrBlank() && key.equals(initialStatus, ignoreCase = true)) {
                selectedIndex = index
            }
        }

        if (selectedIndex > 0) {
            tabs.getTabAt(selectedIndex)?.select()
        }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (isSettingUp) return
                viewModel.setStatusFilter((tab.tag as? String)?.ifBlank { null })
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Initialize ViewModel filters cleanly in one single batch
        viewModel.initFilters(initialStatus, initialDate, initialCustomerId, initialCustomerName)
        isSettingUp = false

        val fabAdd = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd)
        fabAdd.isVisible = true
        fabAdd.contentDescription = "ثبت نوبت حضوری / متفرقه"
        fabAdd.setOnClickListener { openWalkInDialog(null) }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) {
            viewModel.load()
        }
        hasResumedOnce = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newStatus = intent.getStringExtra(EXTRA_STATUS)
        val newDate = intent.getStringExtra(EXTRA_DATE)
        val newCustomerId = intent.getStringExtra(EXTRA_CUSTOMER_ID)
        val newCustomerName = intent.getStringExtra(EXTRA_CUSTOMER_NAME)

        if (!newCustomerName.isNullOrBlank()) {
            setupAdminHeader("نوبت‌های $newCustomerName", currentMenuItemId = R.id.drawer_calendar)
        }

        isSettingUp = true
        val tabs = findViewById<TabLayout>(R.id.statusTabs)
        for (i in 0 until tabs.tabCount) {
            val tag = tabs.getTabAt(i)?.tag as? String
            if ((newStatus.isNullOrBlank() && tag.isNullOrBlank()) || (!newStatus.isNullOrBlank() && tag.equals(newStatus, ignoreCase = true))) {
                tabs.getTabAt(i)?.select()
                break
            }
        }
        isSettingUp = false
        viewModel.initFilters(newStatus, newDate, newCustomerId, newCustomerName)
    }

    private fun showDatePickerDialog() {
        val currentIso = viewModel.state.value.dateFilter.ifBlank { null }
        com.leon.be_nobat.helpers.PersianDatePickerDialog.show(this, initialIsoDate = currentIso) { iso, _ ->
            viewModel.setDateFilter(iso)
        }
    }

    private fun showFeedback(message: String) {
        findViewById<android.view.View>(R.id.list)?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmCancel(appt: ManagementAppointment) {
        val customer = appt.customerName.ifBlank { getString(R.string.role_customer) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cancel_appointment_admin)
            .setMessage(getString(R.string.confirm_cancel_appointment_msg, customer))
            .setPositiveButton(R.string.confirm_cancel_positive) { _, _ ->
                viewModel.changeStatus(appt.id, "Cancelled")
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun showAppointmentDetails(item: ManagementAppointment) {
        val phone = if (!item.customerPhoneNumber.isNullOrBlank()) {
            item.customerPhoneNumber
        } else if (!item.customerEmail.isNullOrBlank() && item.customerEmail.startsWith("customer")) {
            val digits = item.customerEmail.filter { it.isDigit() }
            digits.toIntOrNull()?.let { n -> "0912%03d%04d".format(Locale.US, 100 + n, 2000 + n) }
        } else null

        val details = buildString {
            append("${getString(R.string.label_customer, item.customerName.ifBlank { getString(R.string.role_customer) })}\n")
            if (!phone.isNullOrBlank()) {
                append("${getString(R.string.label_phone, PersianUtils.toPersianDigits(phone))}\n")
            } else {
                append("شماره تماس: ثبت نشده\n")
            }
            append("${getString(R.string.label_business, "${item.businessName} - ${item.branchName}")}\n")
            if (item.serviceNames.isNotEmpty()) {
                append("${getString(R.string.label_services, item.serviceNames.joinToString(", "))}\n")
            }
            if (!item.resourceName.isNullOrBlank()) {
                append("${getString(R.string.label_provider, item.resourceName)}\n")
            }
            append("${getString(R.string.label_time, JalaliDateHelper.formatIsoDateTime(item.startsAt))}\n")
            append("${getString(R.string.label_price, PersianUtils.formatPrice(item.finalPrice, item.currency))}\n")
            append("${getString(R.string.label_tracking_code, "\u200E${PersianUtils.toPersianDigits(item.trackingCode)}\u200E")}\n")
            append(getString(R.string.label_status, resolveStatusLabel(item.status)))
            if (item.customerNote.isNotBlank()) {
                append("\n${getString(R.string.label_customer_note, item.customerNote)}")
            }
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(item.customerName.ifBlank { getString(R.string.admin_calendar) })
            .setMessage(details)
            .setNegativeButton(R.string.action_close, null)

        if (!phone.isNullOrBlank()) {
            builder.setNeutralButton(R.string.action_call_phone) { _, _ ->
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    startActivity(dialIntent)
                } catch (_: Exception) { }
            }
        } else if (item.trackingCode.isNotBlank()) {
            builder.setNeutralButton(R.string.copy_tracking_code) { _, _ ->
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("tracking_code", item.trackingCode)
                clipboard?.setPrimaryClip(clip)
                com.leon.be_nobat.helpers.AppToast.show(this, getString(R.string.tracking_code_copied))
            }
        }

        when (item.status) {
            "Pending" -> {
                builder.setPositiveButton(R.string.confirm_appointment) { _, _ ->
                    openConfirmRescheduleDialog(item)
                }
            }
            "Confirmed" -> {
                builder.setPositiveButton(R.string.complete_appointment) { _, _ ->
                    viewModel.changeStatus(item.id, "Completed")
                }
            }
        }

        builder.show()
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.smsQueue.collect { processSmsQueue() } }
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AdminCalendarUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }

        val isSlotsOverview = state.statusFilter.isNullOrBlank() && !state.allDates
        if (isSlotsOverview) {
            listView.isVisible = false
            slotsGrid.isVisible = true
            val slots = buildDaySlots(state.items)
            slotsAdapter.submitList(slots)
            findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = false
        } else {
            slotsGrid.isVisible = false
            listView.isVisible = true
            adapter.submitList(state.items)
            findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        }

        // Date toolbar rendering
        val tvCalendarDate = findViewById<android.widget.TextView>(R.id.tvCalendarDate)
        val toggleDateMode = findViewById<MaterialButtonToggleGroup>(R.id.toggleDateMode)
        val btnPrev = findViewById<android.view.View>(R.id.btnCalendarPrev)
        val btnNext = findViewById<android.view.View>(R.id.btnCalendarNext)
        val btnToday = findViewById<android.view.View>(R.id.btnCalendarToday)
        val layoutDateClickable = findViewById<android.view.View>(R.id.layoutDateClickable)

        if (state.allDates) {
            tvCalendarDate.text = getString(R.string.calendar_all_dates)
            if (toggleDateMode.checkedButtonId != R.id.btnModeAllDates) {
                toggleDateMode.check(R.id.btnModeAllDates)
            }
            btnPrev.isEnabled = false
            btnNext.isEnabled = false
            btnPrev.alpha = 0.35f
            btnNext.alpha = 0.35f
            layoutDateClickable.isEnabled = false
            layoutDateClickable.alpha = 0.6f
            btnToday.isEnabled = true
            btnToday.alpha = 1.0f
        } else {
            val dateDisplay = JalaliDateHelper.formatIsoToJalaliDisplay(state.dateFilter)
            tvCalendarDate.text = dateDisplay
            if (toggleDateMode.checkedButtonId != R.id.btnModeDay) {
                toggleDateMode.check(R.id.btnModeDay)
            }
            btnPrev.isEnabled = true
            btnNext.isEnabled = true
            btnPrev.alpha = 1.0f
            btnNext.alpha = 1.0f
            layoutDateClickable.isEnabled = true
            layoutDateClickable.alpha = 1.0f
            val isToday = (state.dateFilter == JalaliDateHelper.getTodayIso())
            btnToday.isEnabled = !isToday
            btnToday.alpha = if (isToday) 0.5f else 1.0f
        }
    }

    private fun resolveStatusLabel(status: String): String = when (status) {
        "Pending" -> getString(R.string.appointment_status_pending)
        "Confirmed" -> getString(R.string.appointment_status_confirmed)
        "Completed" -> getString(R.string.appointment_status_completed)
        "Cancelled" -> getString(R.string.appointment_status_cancelled)
        "NoShow" -> getString(R.string.appointment_status_no_show)
        else -> status
    }

    private class ApptsAdapter(
        private val onItemClick: (ManagementAppointment) -> Unit,
        private val onConfirm: (ManagementAppointment) -> Unit,
        private val onComplete: (ManagementAppointment) -> Unit,
        private val onCancel: (ManagementAppointment) -> Unit,
    ) : BaseAdminAdapter<ManagementAppointment>(
        simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })
    ) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementAppointment) {
            holder.binding.apply {
                root.setOnClickListener { onItemClick(item) }
                tvTitle.text = item.customerName.ifBlank { item.businessName }
                val isEn = PersianUtils.isEnglishLocale()
                val providerLabel = if (isEn) "Provider:" else "ارائه‌دهنده:"
                val provider = item.resourceName?.let { " • $providerLabel $it" } ?: ""
                val commaSep = if (isEn) ", " else "، "
                tvSubtitle.text = "${item.businessName} • ${item.branchName} • ${item.serviceNames.joinToString(commaSep)}$provider"
                val dateStr = JalaliDateHelper.formatIsoDateTime(item.startsAt)
                val priceStr = PersianUtils.formatPrice(item.finalPrice, item.currency)
                val codeStr = "\u200E${PersianUtils.toPersianDigits(item.trackingCode)}\u200E"
                val priceLabel = if (isEn) "Price:" else "مبلغ:"
                val codeLabel = if (isEn) "Tracking:" else "کد پیگیری:"
                tvBody.text = "$dateStr • $priceLabel $priceStr • $codeLabel $codeStr"
                val copyTrackingCodeAction = {
                    if (item.trackingCode.isNotBlank()) {
                        val clipboard = root.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("tracking_code", item.trackingCode)
                        clipboard?.setPrimaryClip(clip)
                        com.leon.be_nobat.helpers.AppToast.show(root.context, root.context.getString(R.string.tracking_code_copied))
                    }
                }
                tvBody.setOnClickListener { copyTrackingCodeAction() }
                root.setOnLongClickListener {
                    copyTrackingCodeAction()
                    true
                }

                com.leon.be_nobat.helpers.StatusBadgeHelper.applyAppointmentStatus(tvStatus, item.status)
                com.leon.be_nobat.helpers.StatusBadgeHelper.applyAppointmentStatusIndicator(viewStatusIndicator, item.status)

                btnAction1.isVisible = item.status == "Pending" || item.status == "Confirmed"
                btnAction2.isVisible = item.status == "Pending" || item.status == "Confirmed"

                btnAction1.text = root.context.getString(
                    if (item.status == "Pending") R.string.confirm_appointment else R.string.complete_appointment
                )
                btnAction2.text = root.context.getString(R.string.cancel_appointment_admin)

                btnAction1.setOnClickListener {
                    val next = if (item.status == "Pending") onConfirm else onComplete
                    next(item)
                }
                btnAction2.setOnClickListener { onCancel(item) }
            }
        }
    }

    private fun buildDaySlots(items: List<ManagementAppointment>): List<CalendarSlotItem> {
        var startHour = 10
        var endHour = 18
        var endMinute = 30

        items.forEach { appt ->
            val cal = JalaliDateHelper.parseIsoToCalendar(appt.startsAt)
            if (cal != null) {
                val h = cal.get(Calendar.HOUR_OF_DAY)
                if (h < startHour) startHour = h
                if (h > endHour) {
                    endHour = h
                    endMinute = if (cal.get(Calendar.MINUTE) >= 30) 30 else 0
                }
            }
        }

        val result = mutableListOf<CalendarSlotItem>()
        var h = startHour
        var m = 0
        while (h < endHour || (h == endHour && m <= endMinute)) {
            val timeStr = "%02d:%02d".format(Locale.US, h, m)
            val matching = items.filter { appt ->
                val cal = JalaliDateHelper.parseIsoToCalendar(appt.startsAt)
                if (cal != null) {
                    val apptH = cal.get(Calendar.HOUR_OF_DAY)
                    val apptM = cal.get(Calendar.MINUTE)
                    apptH == h && apptM >= m && apptM < m + 30
                } else false
            }
            result.add(CalendarSlotItem(timeStr, matching))
            m += 30
            if (m >= 60) {
                h++
                m = 0
            }
        }
        return result
    }

    private fun onSlotClicked(slot: CalendarSlotItem) {
        if (slot.appointments.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.label_time, PersianUtils.toPersianDigits(slot.time)))
                .setMessage("در این ساعت نوبتی ثبت نشده است (ساعت آزاد).")
                .setPositiveButton("ثبت نوبت حضوری") { _, _ ->
                    openWalkInDialog(slot.time)
                }
                .setNegativeButton(R.string.action_close, null)
                .show()
        } else if (slot.appointments.size == 1) {
            showAppointmentDetails(slot.appointments.first())
        } else {
            val names = slot.appointments.map { appt ->
                val name = appt.customerName.ifBlank { appt.businessName }
                val status = resolveStatusLabel(appt.status)
                "$name ($status)"
            }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle("نوبت‌های ساعت ${PersianUtils.toPersianDigits(slot.time)}")
                .setItems(names) { _, which ->
                    showAppointmentDetails(slot.appointments[which])
                }
                .setNeutralButton("ثبت نوبت دیگر") { _, _ ->
                    openWalkInDialog(slot.time)
                }
                .setNegativeButton(R.string.action_close, null)
                .show()
        }
    }

    private fun openWalkInDialog(defaultTime: String?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_admin_walk_in, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWalkInCustomerName)
        val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWalkInCustomerPhone)
        val etBranch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWalkInBranch)
        val etService = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWalkInService)
        val etResource = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWalkInResource)
        val etDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWalkInDate)
        val etTime = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWalkInTime)
        val etNote = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWalkInNote)
        val tvError = dialogView.findViewById<android.widget.TextView>(R.id.tvWalkInError)

        listOf(etBranch, etService, etResource, etDate, etTime).forEach { et ->
            et.isFocusable = false
            et.isFocusableInTouchMode = false
            et.isClickable = true
            et.isCursorVisible = false
            et.keyListener = null
            et.isSingleLine = true
            et.maxLines = 1
            et.ellipsize = android.text.TextUtils.TruncateAt.END
        }

        var selectedDateIso = viewModel.state.value.dateFilter.ifBlank { JalaliDateHelper.getTodayIso() }
        var selectedTime = defaultTime ?: "10:00"

        etDate.setText(JalaliDateHelper.formatIsoToJalali(selectedDateIso))
        etTime.setText(PersianUtils.toPersianDigits(selectedTime))

        var branches = emptyList<com.leon.be_nobat.domain.model.ManagementBranch>()
        var services = emptyList<com.leon.be_nobat.domain.model.ManagementService>()
        var resources = emptyList<com.leon.be_nobat.domain.model.ManagementResource>()

        var selectedBranch: com.leon.be_nobat.domain.model.ManagementBranch? = null
        var selectedService: com.leon.be_nobat.domain.model.ManagementService? = null
        var selectedResource: com.leon.be_nobat.domain.model.ManagementResource? = null

        fun updateBranch(b: com.leon.be_nobat.domain.model.ManagementBranch?) {
            selectedBranch = b
            etBranch.setText(b?.let {
                val bName = it.businessName
                val hasMultipleBusinesses = branches.mapNotNull { br -> br.businessName }.distinct().size > 1
                if (hasMultipleBusinesses && !bName.isNullOrBlank() && it.name.isNotBlank() && !it.name.contains(bName)) {
                    "$bName - ${it.name}"
                } else {
                    it.name.ifBlank { bName ?: "" }
                }
            } ?: "")
        }

        fun updateService(s: com.leon.be_nobat.domain.model.ManagementService?) {
            selectedService = s
            etService.setText(s?.let { "${it.name} (${PersianUtils.formatPrice(it.price, it.currency)})" } ?: "")
        }

        fun updateResource(r: com.leon.be_nobat.domain.model.ManagementResource?) {
            selectedResource = r
            etResource.setText(r?.name ?: "بدون کارمند خاص")
        }

        updateResource(null)

        lifecycleScope.launch {
            branches = viewModel.getBranches()
            if (branches.isNotEmpty()) {
                updateBranch(branches.first())
            }
            services = viewModel.getServices()
            if (services.isNotEmpty()) {
                updateService(services.first())
            }
            resources = viewModel.getResources()
        }

        etBranch.setOnClickListener {
            if (branches.isEmpty()) return@setOnClickListener
            val hasMultipleBusinesses = branches.mapNotNull { br -> br.businessName }.distinct().size > 1
            val names = branches.map {
                val bName = it.businessName
                if (hasMultipleBusinesses && !bName.isNullOrBlank() && it.name.isNotBlank() && !it.name.contains(bName)) {
                    "$bName - ${it.name}"
                } else {
                    it.name.ifBlank { bName ?: "" }
                }
            }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle("انتخاب شعبه")
                .setItems(names) { _, which ->
                    updateBranch(branches[which])
                }
                .show()
        }

        etService.setOnClickListener {
            if (services.isEmpty()) return@setOnClickListener
            val names = services.map { "${it.name} (${PersianUtils.formatPrice(it.price, it.currency)})" }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle("انتخاب نوع خدمت")
                .setItems(names) { _, which ->
                    updateService(services[which])
                }
                .show()
        }

        etResource.setOnClickListener {
            val list = mutableListOf("بدون کارمند خاص")
            list.addAll(resources.map { it.name })
            MaterialAlertDialogBuilder(this)
                .setTitle("انتخاب ارائه‌دهنده")
                .setItems(list.toTypedArray()) { _, which ->
                    if (which == 0) {
                        updateResource(null)
                    } else {
                        updateResource(resources[which - 1])
                    }
                }
                .show()
        }

        etDate.setOnClickListener {
            val cal = Calendar.getInstance()
            try {
                val parts = selectedDateIso.split("-").mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) cal.set(parts[0], parts[1] - 1, parts[2])
            } catch (_: Exception) {}
            android.app.DatePickerDialog(
                this,
                R.style.ThemeOverlay_BeNobat_DatePickerDialog,
                { _, y, m, d ->
                    val chosen = Calendar.getInstance().apply { set(y, m, d) }
                    selectedDateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.time)
                    etDate.setText(JalaliDateHelper.formatIsoToJalali(selectedDateIso))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        etTime.setOnClickListener {
            val parts = selectedTime.split(":").mapNotNull { it.toIntOrNull() }
            val h = if (parts.isNotEmpty()) parts[0] else 10
            val m = if (parts.size > 1) parts[1] else 0
            android.app.TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedTime = "%02d:%02d".format(Locale.US, hourOfDay, minute)
                    etTime.setText(PersianUtils.toPersianDigits(selectedTime))
                },
                h, m, true
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("ثبت و تأیید نوبت", null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            val btnSubmit = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            btnSubmit.setOnClickListener {
                val branch = selectedBranch
                val service = selectedService
                if (branch == null || service == null) {
                    tvError.text = "شعبه و نوع خدمت الزامی هستند."
                    tvError.isVisible = true
                    return@setOnClickListener
                }
                val name = etName.text?.toString()?.trim()
                val phone = etPhone.text?.toString()?.trim()
                val note = etNote.text?.toString()?.trim()
                val startsAtIso = JalaliDateHelper.parseTimeToIso(selectedDateIso, selectedTime)

                btnSubmit.isEnabled = false
                btnSubmit.text = "در حال ثبت..."
                tvError.isVisible = false

                viewModel.createWalkInAppointment(
                    branchId = branch.id,
                    serviceId = service.id,
                    resourceId = selectedResource?.id,
                    startsAt = startsAtIso,
                    customerName = name,
                    phoneNumber = phone,
                    note = note,
                ) { success, errorMsg ->
                    if (success) {
                        dialog.dismiss()
                        com.leon.be_nobat.helpers.AppToast.show(this@AdminCalendarActivity, "نوبت با موفقیت ثبت و تأیید شد.")
                    } else {
                        btnSubmit.isEnabled = true
                        btnSubmit.text = "ثبت و تأیید نوبت"
                        tvError.text = errorMsg ?: "خطا در ثبت نوبت"
                        tvError.isVisible = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun openConfirmRescheduleDialog(appt: ManagementAppointment) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.dialog_confirm_reschedule, null)
        dialog.setContentView(sheetView)

        val btnClose = sheetView.findViewById<View>(R.id.btnCloseDialog)
        val tvMeta = sheetView.findViewById<TextView>(R.id.tvAppointmentMeta)
        val tvReqTime = sheetView.findViewById<TextView>(R.id.tvRequestedTime)
        val btnPrev = sheetView.findViewById<View>(R.id.btnDayPrev)
        val btnNext = sheetView.findViewById<View>(R.id.btnDayNext)
        val tvDateDisplay = sheetView.findViewById<TextView>(R.id.tvSelectedDateDisplay)
        val progress = sheetView.findViewById<View>(R.id.progressSlots)
        val tvEmpty = sheetView.findViewById<View>(R.id.tvEmptySlots)
        val rvSlots = sheetView.findViewById<RecyclerView>(R.id.rvRescheduleSlots)
        val btnClear = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClearReschedule)
        val btnConfirm = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmAction)

        btnClose.setOnClickListener { dialog.dismiss() }

        val customer = appt.customerName.ifBlank { getString(R.string.role_customer) }
        val services = appt.serviceNames.joinToString("، ")
        val provider = if (!appt.resourceName.isNullOrBlank()) " · ${appt.resourceName}" else ""
        tvMeta.text = "$customer · $services$provider"
        tvReqTime.text = "زمان درخواستی مشتری: ${JalaliDateHelper.formatIsoDateTime(appt.startsAt)}"

        val apptCal = JalaliDateHelper.parseIsoToCalendar(appt.startsAt) ?: Calendar.getInstance()
        val currentDate = Calendar.getInstance().apply { time = apptCal.time }
        var selectedSlot: String? = null

        fun updateSelectionUI() {
            if (selectedSlot != null) {
                btnConfirm.text = "تأیید و انتقال به ساعت ${PersianUtils.toPersianDigits(selectedSlot)}"
                btnClear.isVisible = true
            } else {
                btnConfirm.text = "تأیید در زمان درخواستی"
                btnClear.isVisible = false
            }
        }

        class RescheduleSlotAdapter(
            private var slots: List<String>,
        ) : RecyclerView.Adapter<RescheduleSlotAdapter.VH>() {
            fun setSlots(newSlots: List<String>) {
                slots = newSlots
                notifyDataSetChanged()
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
                val v = layoutInflater.inflate(R.layout.item_booking_slot, parent, false)
                return VH(v)
            }

            override fun onBindViewHolder(holder: VH, position: Int) {
                val slot = slots[position]
                holder.bind(slot, slot == selectedSlot) {
                    selectedSlot = if (selectedSlot == slot) null else slot
                    updateSelectionUI()
                    notifyDataSetChanged()
                }
            }

            override fun getItemCount() = slots.size

            inner class VH(view: View) : RecyclerView.ViewHolder(view) {
                private val card = view.findViewById<MaterialCardView>(R.id.cardSlot)
                private val tv = view.findViewById<TextView>(R.id.tvTime)
                private val badge = view.findViewById<View>(R.id.tvBookedBadge)

                fun bind(slot: String, isSelected: Boolean, onClick: () -> Unit) {
                    card.setOnClickListener { onClick() }
                    badge.isVisible = false
                    tv.text = PersianUtils.toPersianDigits(slot)
                    if (isSelected) {
                        card.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary))
                        card.strokeColor = com.google.android.material.color.MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary)
                        tv.setTextColor(com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimary))
                    } else {
                        card.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurface))
                        card.strokeColor = androidx.core.content.ContextCompat.getColor(itemView.context, R.color.colorOutline)
                        tv.setTextColor(com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface))
                    }
                }
            }
        }

        val slotAdapter = RescheduleSlotAdapter(emptyList())
        rvSlots.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)
        rvSlots.adapter = slotAdapter

        fun loadSlotsForCurrentDate() {
            val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(currentDate.time)
            tvDateDisplay.text = JalaliDateHelper.formatIsoToJalaliDisplay(iso)
            progress.isVisible = true
            tvEmpty.isVisible = false
            rvSlots.isVisible = false
            lifecycleScope.launch {
                val slots = viewModel.loadRescheduleSlots(appt, iso)
                progress.isVisible = false
                if (slots.isEmpty()) {
                    tvEmpty.isVisible = true
                    rvSlots.isVisible = false
                } else {
                    tvEmpty.isVisible = false
                    rvSlots.isVisible = true
                    slotAdapter.setSlots(slots)
                }
            }
        }

        btnPrev.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, -1)
            selectedSlot = null
            updateSelectionUI()
            loadSlotsForCurrentDate()
        }

        btnNext.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, 1)
            selectedSlot = null
            updateSelectionUI()
            loadSlotsForCurrentDate()
        }

        btnClear.setOnClickListener {
            selectedSlot = null
            updateSelectionUI()
            slotAdapter.notifyDataSetChanged()
        }

        btnConfirm.setOnClickListener {
            val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(currentDate.time)
            val newStartsAt = if (selectedSlot != null) {
                JalaliDateHelper.parseTimeToIso(isoDate, selectedSlot!!)
            } else null

            btnConfirm.isEnabled = false
            viewModel.confirmAppointment(appt.id, newStartsAt) { success, err ->
                dialog.dismiss()
                if (success) {
                    showFeedback(if (newStartsAt != null) "نوبت تأیید و منتقل شد." else "نوبت تأیید شد.")
                } else {
                    showFeedback(err ?: "تأیید نوبت انجام نشد.")
                }
            }
        }

        loadSlotsForCurrentDate()
        updateSelectionUI()
        dialog.show()
    }

    data class CalendarSlotItem(
        val time: String,
        val appointments: List<ManagementAppointment> = emptyList(),
    )

    private class CalendarSlotsAdapter(
        private val onSlotClick: (CalendarSlotItem) -> Unit,
    ) : RecyclerView.Adapter<CalendarSlotsAdapter.SlotViewHolder>() {
        private var items: List<CalendarSlotItem> = emptyList()

        fun submitList(list: List<CalendarSlotItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_calendar_slot, parent, false)
            return SlotViewHolder(view)
        }

        override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
            holder.bind(items[position], onSlotClick)
        }

        override fun getItemCount(): Int = items.size

        class SlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cardSlot: MaterialCardView = itemView.findViewById(R.id.cardSlot)
            private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            private val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)

            fun bind(item: CalendarSlotItem, onClick: (CalendarSlotItem) -> Unit) {
                cardSlot.setOnClickListener { onClick(item) }
                tvTime.text = PersianUtils.toPersianDigits(item.time)
                val appts = item.appointments
                if (appts.isEmpty()) {
                    tvStatusBadge.isVisible = false
                    cardSlot.setStrokeColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.colorOutline))
                    cardSlot.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurface))
                } else {
                    tvStatusBadge.isVisible = true
                    val primary = appts.first()
                    if (appts.size > 1) {
                        tvStatusBadge.text = "${PersianUtils.toPersianDigits(appts.size)} نوبت"
                    } else {
                        tvStatusBadge.text = when (primary.status) {
                            "Pending" -> itemView.context.getString(R.string.appointment_status_pending)
                            "Confirmed" -> itemView.context.getString(R.string.appointment_status_confirmed)
                            "Completed" -> itemView.context.getString(R.string.appointment_status_completed)
                            "Cancelled" -> itemView.context.getString(R.string.appointment_status_cancelled)
                            "NoShow" -> itemView.context.getString(R.string.appointment_status_no_show)
                            else -> primary.status
                        }
                    }
                    StatusBadgeHelper.applyAppointmentStatus(tvStatusBadge, primary.status)
                    val strokeColor = when (primary.status) {
                        "Pending" -> R.color.status_indicator_pending
                        "Confirmed" -> R.color.status_indicator_confirmed
                        "Completed" -> R.color.status_indicator_completed
                        "Cancelled" -> R.color.status_indicator_cancelled
                        else -> R.color.colorOutline
                    }
                    cardSlot.setStrokeColor(androidx.core.content.ContextCompat.getColor(itemView.context, strokeColor))
                }
            }
        }
    }
}

