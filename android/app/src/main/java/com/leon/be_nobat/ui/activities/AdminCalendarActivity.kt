package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.JalaliDateHelper
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.view_models.admin.AdminCalendarUiState
import com.leon.be_nobat.ui.view_models.admin.AdminCalendarViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminCalendarActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: AdminCalendarViewModel by viewModel()
    private lateinit var adapter: ApptsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
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

        adapter = ApptsAdapter(
            onItemClick = { appt -> showAppointmentDetails(appt) },
            onConfirm = { appt ->
                viewModel.changeStatus(appt.id, "Confirmed")
            },
            onComplete = { appt ->
                viewModel.changeStatus(appt.id, "Completed")
            },
            onCancel = { appt -> confirmCancel(appt) },
        )

        findViewById<RecyclerView>(R.id.list).apply {
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
        val cal = java.util.Calendar.getInstance()
        val currentIso = viewModel.state.value.dateFilter
        if (currentIso.isNotBlank()) {
            val parts = currentIso.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size == 3) {
                cal.set(parts[0], parts[1] - 1, parts[2])
            }
        }
        android.app.DatePickerDialog(
            this,
            R.style.ThemeOverlay_BeNobat_DatePickerDialog,
            { _, y, m, d ->
                val chosen = java.util.Calendar.getInstance().apply { set(y, m, d) }
                val iso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(chosen.time)
                viewModel.setDateFilter(iso)
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
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
        val details = buildString {
            append("${getString(R.string.label_customer, item.customerName.ifBlank { getString(R.string.role_customer) })}\n")
            if (!item.customerPhoneNumber.isNullOrBlank()) {
                append("${getString(R.string.label_phone, PersianUtils.toPersianDigits(item.customerPhoneNumber))}\n")
            }
            if (!item.customerEmail.isNullOrBlank()) {
                append("${getString(R.string.label_email, item.customerEmail)}\n")
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

        if (!item.customerPhoneNumber.isNullOrBlank()) {
            builder.setNeutralButton(R.string.action_call_phone) { _, _ ->
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.customerPhoneNumber}"))
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
                    viewModel.changeStatus(item.id, "Confirmed")
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
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)

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
}

