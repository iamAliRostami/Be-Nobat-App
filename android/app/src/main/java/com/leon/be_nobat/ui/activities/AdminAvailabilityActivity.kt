package com.leon.be_nobat.ui.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.*
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.helpers.*
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.view_models.admin.AdminAvailabilityUiState
import com.leon.be_nobat.ui.view_models.admin.AdminAvailabilityViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminAvailabilityActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: AdminAvailabilityViewModel by viewModel()
    private val management: ManagementRepository by inject()

    private lateinit var adapter: AvailabilityAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusTabs: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireBusinessManagementPermission()
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.admin_availability), currentMenuItemId = R.id.drawer_availability)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        // Setup Tabs (Current & Future / Past)
        statusTabs = findViewById(R.id.statusTabs)
        statusTabs.isVisible = true
        statusTabs.removeAllTabs()
        statusTabs.addTab(statusTabs.newTab().setText(R.string.availability_tab_current_future))
        statusTabs.addTab(statusTabs.newTab().setText(R.string.availability_tab_past))
        statusTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val tabKey = if (tab?.position == 1) "past" else "active"
                viewModel.setTab(tabKey)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Setup Search
        findViewById<TextInputLayout>(R.id.adminSearchLayout)?.apply {
            isVisible = true
            findViewById<TextInputEditText>(R.id.adminSearchInput)?.apply {
                hint = getString(R.string.home_search_hint)
                doAfterTextChanged { text -> viewModel.setSearchQuery(text?.toString().orEmpty()) }
            }
        }

        // Setup FAB for adding new availability
        findViewById<FloatingActionButton>(R.id.fabAdd)?.apply {
            isVisible = true
            contentDescription = getString(R.string.form_create_availability_title)
            setOnClickListener { showCreateAvailabilityDialog() }
        }

        adapter = AvailabilityAdapter(
            onEdit = { item -> showEditAvailabilityDialog(item) },
            onDelete = { item -> confirmDelete(item) }
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@AdminAvailabilityActivity)
            this.adapter = this@AdminAvailabilityActivity.adapter
        }
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AdminAvailabilityUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.filteredItems.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.filteredItems.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.filteredItems)

        state.message?.let {
            Snackbar.make(swipeRefresh, it, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(item: ManagementAvailability) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.availability_delete_confirm_title)
            .setMessage(R.string.availability_delete_confirm_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(item.id) }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun showCreateAvailabilityDialog() {
        loadDataAndShowDialog(null)
    }

    private fun showEditAvailabilityDialog(item: ManagementAvailability) {
        loadDataAndShowDialog(item)
    }

    private fun loadDataAndShowDialog(item: ManagementAvailability?) {
        lifecycleScope.launch {
            val bizResult = management.listBusinesses(pageSize = 100)
            val branchResult = management.listBranches(pageSize = 100)
            val resourceResult = management.listResources(pageSize = 100)

            val businesses = bizResult.getOrNull()?.items?.filter { it.active }.orEmpty()
            if (businesses.isEmpty()) {
                Snackbar.make(swipeRefresh, "ابتدا یک کسب‌وکار فعال ایجاد کنید.", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val allBranches = branchResult.getOrNull()?.items?.filter { it.active }.orEmpty()
            val allResources = resourceResult.getOrNull()?.items.orEmpty()

            showAvailabilityForm(item, businesses, allBranches, allResources)
        }
    }

    private fun showAvailabilityForm(
        item: ManagementAvailability?,
        businesses: List<ManagementBusiness>,
        allBranches: List<ManagementBranch>,
        allResources: List<ManagementResource>,
    ) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_availability, null)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val etBusiness = view.findViewById<TextInputEditText>(R.id.etBusiness)
        val etBranch = view.findViewById<TextInputEditText>(R.id.etBranch)
        val etResource = view.findViewById<TextInputEditText>(R.id.etResource)
        val etDate = view.findViewById<TextInputEditText>(R.id.etDate)
        val etStartTime = view.findViewById<TextInputEditText>(R.id.etStartTime)
        val etEndTime = view.findViewById<TextInputEditText>(R.id.etEndTime)
        val cbIsAvailable = view.findViewById<MaterialCheckBox>(R.id.cbIsAvailable)
        val tvError = view.findViewById<TextView>(R.id.tvValidationError)

        tvTitle.setText(if (item == null) R.string.form_create_availability_title else R.string.form_edit_availability_title)

        // State variables
        var selectedBusinessId = item?.businessId ?: businesses.first().id
        var selectedBranchId: String? = item?.branchId
        var selectedResourceId: String? = item?.resourceId
        var selectedIsoDate = item?.effectiveDate ?: JalaliDateHelper.getTodayIso()
        var selectedStartTime = item?.startsAt?.take(5) ?: "09:00"
        var selectedEndTime = item?.endsAt?.take(5) ?: "18:00"
        cbIsAvailable.isChecked = item?.isAvailable ?: true

        // Helpers to refresh labels
        fun updateBusinessField() {
            val biz = businesses.firstOrNull { it.id == selectedBusinessId }
            etBusiness.setText(biz?.name ?: "")
        }

        fun updateBranchField() {
            val branch = allBranches.firstOrNull { it.id == selectedBranchId }
            etBranch.setText(branch?.name ?: getString(R.string.availability_all_branches))
        }

        fun updateResourceField() {
            val res = allResources.firstOrNull { it.id == selectedResourceId }
            etResource.setText(res?.name ?: getString(R.string.availability_all_staff))
        }

        fun updateDateField() {
            val jDate = JalaliDateHelper.formatIsoToJalali(selectedIsoDate)
            etDate.setText(PersianUtils.toPersianDigits(jDate))
        }

        fun updateTimeFields() {
            etStartTime.setText(PersianUtils.toPersianDigits(selectedStartTime))
            etEndTime.setText(PersianUtils.toPersianDigits(selectedEndTime))
        }

        updateBusinessField()
        updateBranchField()
        updateResourceField()
        updateDateField()
        updateTimeFields()

        // Disable moving to another scope on edit (backend restriction)
        if (item != null) {
            etBusiness.isEnabled = false
            etBranch.isEnabled = false
            etResource.isEnabled = false
        } else {
            // Business Picker
            etBusiness.setOnClickListener {
                val names = businesses.map { it.name }.toTypedArray()
                val currentIdx = businesses.indexOfFirst { it.id == selectedBusinessId }.coerceAtLeast(0)
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.category_kind_business)
                    .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                        selectedBusinessId = businesses[which].id
                        selectedBranchId = null
                        selectedResourceId = null
                        updateBusinessField()
                        updateBranchField()
                        updateResourceField()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.action_close, null)
                    .show()
            }

            // Branch Picker
            etBranch.setOnClickListener {
                val branchesForBiz = allBranches.filter { it.businessId == selectedBusinessId }
                val options = listOf(null to getString(R.string.availability_all_branches)) +
                        branchesForBiz.map { it.id to it.name }
                val labels = options.map { it.second }.toTypedArray()
                val currentIdx = options.indexOfFirst { it.first == selectedBranchId }.coerceAtLeast(0)
                MaterialAlertDialogBuilder(this)
                    .setTitle("شعبه")
                    .setSingleChoiceItems(labels, currentIdx) { dialog, which ->
                        selectedBranchId = options[which].first
                        selectedResourceId = null
                        updateBranchField()
                        updateResourceField()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.action_close, null)
                    .show()
            }

            // Resource Picker
            etResource.setOnClickListener {
                val resourcesForBranch = if (selectedBranchId == null) {
                    allResources.filter { res -> allBranches.any { b -> b.id == res.branchId && b.businessId == selectedBusinessId } }
                } else {
                    allResources.filter { it.branchId == selectedBranchId }
                }
                val options = listOf(null to getString(R.string.availability_all_staff)) +
                        resourcesForBranch.map { it.id to it.name }
                val labels = options.map { it.second }.toTypedArray()
                val currentIdx = options.indexOfFirst { it.first == selectedResourceId }.coerceAtLeast(0)
                MaterialAlertDialogBuilder(this)
                    .setTitle("ارائه‌دهنده")
                    .setSingleChoiceItems(labels, currentIdx) { dialog, which ->
                        selectedResourceId = options[which].first
                        updateResourceField()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.action_close, null)
                    .show()
            }
        }

        // Date Picker
        etDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val parts = selectedIsoDate.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size == 3) {
                cal.set(parts[0], parts[1] - 1, parts[2])
            }
            val datePicker = DatePickerDialog(
                this,
                R.style.ThemeOverlay_BeNobat_DatePickerDialog,
                { _, y, m, d ->
                    val chosen = Calendar.getInstance().apply { set(y, m, d) }
                    selectedIsoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.time)
                    updateDateField()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            if (item == null) {
                datePicker.datePicker.minDate = System.currentTimeMillis() - 1000
            }
            datePicker.show()
        }

        // Time Pickers
        etStartTime.setOnClickListener {
            val parts = selectedStartTime.split(":").mapNotNull { it.toIntOrNull() }
            val h = if (parts.size >= 2) parts[0] else 9
            val m = if (parts.size >= 2) parts[1] else 0
            TimePickerDialog(this, { _, hour, minute ->
                selectedStartTime = "%02d:%02d".format(Locale.US, hour, minute)
                updateTimeFields()
            }, h, m, true).show()
        }

        etEndTime.setOnClickListener {
            val parts = selectedEndTime.split(":").mapNotNull { it.toIntOrNull() }
            val h = if (parts.size >= 2) parts[0] else 18
            val m = if (parts.size >= 2) parts[1] else 0
            TimePickerDialog(this, { _, hour, minute ->
                selectedEndTime = "%02d:%02d".format(Locale.US, hour, minute)
                updateTimeFields()
            }, h, m, true).show()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.action_close, null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val startFull = if (selectedStartTime.length == 5) "$selectedStartTime:00" else selectedStartTime
                val endFull = if (selectedEndTime.length == 5) "$selectedEndTime:00" else selectedEndTime

                if (startFull >= endFull) {
                    tvError.text = "ساعت پایان باید بعد از ساعت شروع باشد."
                    tvError.isVisible = true
                    return@setOnClickListener
                }

                val input = ManagementAvailabilityInput(
                    businessId = selectedBusinessId,
                    branchId = selectedBranchId,
                    resourceId = selectedResourceId,
                    effectiveDate = selectedIsoDate,
                    startsAt = startFull,
                    endsAt = endFull,
                    isAvailable = cbIsAvailable.isChecked
                )

                if (item == null) {
                    viewModel.create(input)
                } else {
                    viewModel.update(item.id, input)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private class AvailabilityAdapter(
        private val onEdit: (ManagementAvailability) -> Unit,
        private val onDelete: (ManagementAvailability) -> Unit,
    ) : BaseAdminAdapter<ManagementAvailability>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {

        override fun bindHolder(holder: AdminViewHolder, item: ManagementAvailability) {
            holder.binding.apply {
                val context = root.context
                val dayName = JalaliDateHelper.translateDayOfWeek(item.dayOfWeek)
                val jalaliDate = item.effectiveDate?.let { JalaliDateHelper.formatIsoToJalali(it) }

                // Title: "شنبه • ۱۴۰۵/۰۷/۱۸" or "شنبه (هفتگی)"
                tvTitle.text = if (jalaliDate.isNullOrBlank()) {
                    "$dayName (هفتگی)"
                } else {
                    "$dayName • $jalaliDate"
                }

                // Subtitle: "ساعت ۰۹:۰۰ تا ۱۸:۰۰"
                val startClean = PersianUtils.toPersianDigits(item.startsAt.take(5))
                val endClean = PersianUtils.toPersianDigits(item.endsAt.take(5))
                tvSubtitle.text = "ساعت $startClean تا $endClean"

                // Body: Scope "کسب‌وکار | شعبه | کارمند"
                val bName = item.businessName?.ifBlank { null } ?: "کسب‌وکار"
                val brName = item.branchName?.ifBlank { null } ?: context.getString(R.string.availability_all_branches)
                val rName = item.resourceName?.ifBlank { null } ?: context.getString(R.string.availability_all_staff)
                tvBody.text = "$bName  |  $brName  |  $rName"

                // Status Badge: "قابل رزرو" or "مسدود"
                if (item.isAvailable) {
                    tvStatus.text = context.getString(R.string.availability_available)
                    StatusBadgeHelper.applyActiveStatus(tvStatus, true)
                } else {
                    tvStatus.text = context.getString(R.string.availability_blocked)
                    StatusBadgeHelper.applyActiveStatus(tvStatus, false)
                }

                // Actions: Hide on past items (matching web rule)
                val todayIso = JalaliDateHelper.getTodayIso()
                val isPast = item.effectiveDate != null && item.effectiveDate < todayIso

                if (isPast) {
                    actionRow.isVisible = false
                } else {
                    actionRow.isVisible = true

                    btnAction1.apply {
                        isVisible = true
                        text = context.getString(R.string.action_delete)
                        val primaryColor = com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
                        setTextColor(primaryColor)
                        strokeColor = ColorStateList.valueOf(primaryColor)
                        setOnClickListener { onDelete(item) }
                    }

                    btnAction2.apply {
                        isVisible = true
                        text = context.getString(R.string.action_edit)
                        setOnClickListener { onEdit(item) }
                    }
                }

                root.setOnClickListener {
                    if (!isPast) onEdit(item)
                }
            }
        }
    }
}
