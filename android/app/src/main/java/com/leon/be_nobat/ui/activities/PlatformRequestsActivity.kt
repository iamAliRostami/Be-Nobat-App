package com.leon.be_nobat.ui.activities

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.BusinessRequest
import com.leon.be_nobat.helpers.AdminFormDialog
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.helpers.StatusBadgeHelper
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.view_models.platform.PlatformRequestsUiState
import com.leon.be_nobat.ui.view_models.platform.PlatformRequestsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.NumberFormat
import java.util.Locale

class PlatformRequestsActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: PlatformRequestsViewModel by viewModel()
    private lateinit var adapter: RequestsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requirePlatformAdminPermission()
    }

    override fun setupViews() {
        setupAdminHeader(
            getString(R.string.platform_requests_title),
            currentMenuItemId = R.id.drawer_platform_requests
        )

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        // Setup Tabs (همه، در انتظار بررسی، تأیید شده، رد شده)
        val tabs = findViewById<TabLayout>(R.id.statusTabs)
        tabs.isVisible = true
        tabs.removeAllTabs()
        val requestTabs = listOf(
            "" to getString(R.string.platform_tab_all),
            "Pending" to getString(R.string.platform_tab_pending),
            "Approved" to getString(R.string.platform_tab_approved),
            "Rejected" to getString(R.string.platform_tab_rejected),
        )
        requestTabs.forEach { (key, label) ->
            tabs.addTab(tabs.newTab().setText(label).also { it.tag = key })
        }
        val currentTab = viewModel.state.value.selectedTab
        val initialTabIndex = requestTabs.indexOfFirst { it.first == currentTab }
        if (initialTabIndex > 0) {
            tabs.getTabAt(initialTabIndex)?.select()
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val key = (tab.tag as? String).orEmpty()
                viewModel.setSelectedTab(key)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Setup Search
        findViewById<TextInputLayout>(R.id.adminSearchLayout)?.apply {
            isVisible = true
            findViewById<TextInputEditText>(R.id.adminSearchInput)?.apply {
                hint = getString(R.string.search)
                setText(viewModel.state.value.searchQuery)
                doAfterTextChanged { text ->
                    viewModel.setQuery(text?.toString().orEmpty())
                    adapter.submitList(viewModel.state.value.filteredItems)
                }
                setOnEditorActionListener { v, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(v.windowToken, 0)
                        true
                    } else false
                }
            }
        }

        // Hide FAB because user submits requests from profile
        findViewById<FloatingActionButton>(R.id.fabAdd)?.isVisible = false

        adapter = RequestsAdapter(
            onApprove = { req -> showApproveConfirmDialog(req) },
            onReject = { req -> showRejectDialog(req) },
            onShowReceipt = { req -> showReceiptDialog(req) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@PlatformRequestsActivity)
            this.adapter = this@PlatformRequestsActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun showApproveConfirmDialog(req: BusinessRequest) {
        val name = req.applicantName
        val phone = req.applicantPhone?.let { PersianUtils.toPersianDigits(it) }
        val applicantDisplay = when {
            name != null && phone != null && !name.contains(phone) -> "$name ($phone)"
            name != null -> name
            phone != null -> phone
            !req.userEmail.isNullOrBlank() -> req.userEmail
            else -> getString(R.string.platform_request_applicant_default)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.platform_request_approve)
            .setMessage(getString(R.string.platform_request_confirm_approve, req.businessName, applicantDisplay))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.platform_request_approve)
            { _, _ -> viewModel.approve(req.id) }
            .show()
    }

    private fun showRejectDialog(req: BusinessRequest) {
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.platform_request_reject_dialog_title))
            .field(getString(R.string.platform_request_reject_note_hint))
            .onSubmit { values ->
                val note = values.getOrNull(0)?.ifBlank { null }
                viewModel.reject(req.id, note)
            }
            .show()
    }

    private fun showReceiptDialog(req: BusinessRequest) {
        val base64 = req.receiptImageBase64
        val iv = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (280 * resources.displayMetrics.density).toInt()
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        if (!base64.isNullOrBlank()) {
            try {
                val clean = if (base64.contains(",")) base64.substringAfter(",") else base64
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                iv.setImageBitmap(bmp)
            } catch (_: Exception) {
                iv.setImageResource(R.drawable.ic_document)
            }
        } else {
            iv.setImageResource(R.drawable.ic_document)
        }

        val trackInfo = if (!req.receiptTrackingCode.isNullOrBlank()) "\nکد پیگیری: ${req.receiptTrackingCode}" else ""
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.business_request_receipt_view_btn)
            .setMessage("فیش واریزی ارسال شده برای کسب‌وکار «${req.businessName}»$trackInfo")
            .setView(iv)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: PlatformRequestsUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible =
            state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible =
            !state.loading && state.filteredItems.isEmpty()

        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.filteredItems)

        state.message?.let { msg ->
            val resId = when (msg) {
                "pricing_updated" -> R.string.platform_pricing_save_success
                "approved" -> R.string.platform_request_approved_success
                "rejected" -> R.string.platform_request_rejected_success
                else -> null
            }
            resId?.let { Snackbar.make(swipeRefresh, getString(it), Snackbar.LENGTH_SHORT).show() }
            viewModel.clearMessage()
        }

        state.error?.let {
            Snackbar.make(
                swipeRefresh,
                com.leon.be_nobat.helpers.ErrorFormatter.format(this, it),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private class RequestsAdapter(
        private val onApprove: (BusinessRequest) -> Unit,
        private val onReject: (BusinessRequest) -> Unit,
        private val onShowReceipt: (BusinessRequest) -> Unit,
    ) : BaseAdminAdapter<BusinessRequest>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {

        override fun bindHolder(holder: AdminViewHolder, item: BusinessRequest) {
            holder.binding.apply {
                val isPending = item.status.equals("Pending", ignoreCase = true)
                val isApproved = item.status.equals("Approved", ignoreCase = true)
                val isRejected = item.status.equals("Rejected", ignoreCase = true)

                when {
                    isPending -> {
                        StatusBadgeHelper.applyAppointmentStatusIndicator(viewStatusIndicator, "Pending")
                        tvStatus.text = root.context.getString(R.string.platform_tab_pending)
                        StatusBadgeHelper.applyBadgeStyle(tvStatus, StatusBadgeHelper.BadgeStyle.PENDING)
                    }
                    isApproved -> {
                        StatusBadgeHelper.applyAppointmentStatusIndicator(viewStatusIndicator, "Confirmed")
                        tvStatus.text = root.context.getString(R.string.platform_tab_approved)
                        StatusBadgeHelper.applyBadgeStyle(tvStatus, StatusBadgeHelper.BadgeStyle.CONFIRMED)
                    }
                    isRejected -> {
                        StatusBadgeHelper.applyAppointmentStatusIndicator(viewStatusIndicator, "Cancelled")
                        tvStatus.text = root.context.getString(R.string.platform_tab_rejected)
                        StatusBadgeHelper.applyBadgeStyle(tvStatus, StatusBadgeHelper.BadgeStyle.CANCELLED)
                    }
                    else -> {
                        StatusBadgeHelper.applyActiveStatusIndicator(viewStatusIndicator, true)
                        tvStatus.text = item.status
                    }
                }

                tvTitle.text = item.businessName

                val categoryCityPhone = listOf(
                    item.category.takeIf { it.isNotBlank() },
                    item.city.takeIf { it.isNotBlank() },
                    item.contactPhone.takeIf { it.isNotBlank() }?.let { "☎ ${PersianUtils.toPersianDigits(it)}" }
                ).filterNotNull().joinToString("   •   ")
                tvSubtitle.isVisible = categoryCityPhone.isNotBlank()
                tvSubtitle.text = categoryCityPhone

                val bodyLines = mutableListOf<String>()
                val applicantPhone = item.applicantPhone?.let { PersianUtils.toPersianDigits(it) }
                val applicant = listOfNotNull(item.applicantName, applicantPhone).distinct().joinToString(" - ")
                if (applicant.isNotBlank()) {
                    bodyLines.add(root.context.getString(R.string.platform_request_applicant_label, applicant))
                }
                bodyLines.add(root.context.getString(R.string.platform_request_branch_label, item.firstBranchName, item.firstBranchAddress))

                if (item.totalBranchesCount > 1 || item.additionalBranches.isNotEmpty()) {
                    bodyLines.add(root.context.getString(R.string.platform_request_total_branches_label, PersianUtils.toPersianDigits(item.totalBranchesCount)))
                }
                if (item.totalFee > 0) {
                    bodyLines.add(root.context.getString(R.string.platform_request_fee_label, PersianUtils.formatPrice(item.totalFee, "تومان")))
                }
                if (item.hasReceipt) {
                    val trackCode = item.receiptTrackingCode?.takeIf { it.isNotBlank() }
                    val receiptLine = if (trackCode != null) {
                        root.context.getString(R.string.platform_request_receipt_uploaded_with_track, PersianUtils.toPersianDigits(trackCode))
                    } else {
                        root.context.getString(R.string.platform_request_receipt_uploaded)
                    }
                    bodyLines.add(receiptLine)
                } else {
                    bodyLines.add(root.context.getString(R.string.platform_request_receipt_not_uploaded))
                }

                if (item.description.isNotBlank()) {
                    bodyLines.add(root.context.getString(R.string.platform_request_desc_label, item.description))
                }
                if (!item.reviewNote.isNullOrBlank()) {
                    bodyLines.add(root.context.getString(R.string.platform_request_rejection_label, item.reviewNote))
                }
                tvBody.isVisible = true
                tvBody.text = bodyLines.joinToString("\n")

                if (item.hasReceipt) {
                    btnAction3.isVisible = true
                    btnAction3.text = root.context.getString(R.string.business_request_receipt_view_btn)
                    btnAction3.setIconResource(R.drawable.ic_document)
                    btnAction3.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                    btnAction3.setOnClickListener { onShowReceipt(item) }
                } else {
                    btnAction3.isVisible = false
                }

                if (isPending) {
                    actionRow.isVisible = true
                    btnAction1.isVisible = true
                    btnAction1.text = root.context.getString(R.string.platform_request_approve_btn)
                    btnAction1.setIconResource(R.drawable.ic_check)
                    btnAction1.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                    val primaryColor = com.google.android.material.color.MaterialColors.getColor(btnAction1, androidx.appcompat.R.attr.colorPrimary)
                    val onPrimaryColor = com.google.android.material.color.MaterialColors.getColor(btnAction1, com.google.android.material.R.attr.colorOnPrimary)
                    btnAction1.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                    btnAction1.setTextColor(onPrimaryColor)
                    btnAction1.iconTint = android.content.res.ColorStateList.valueOf(onPrimaryColor)
                    btnAction1.strokeWidth = 0
                    btnAction1.setOnClickListener { onApprove(item) }

                    btnAction2.isVisible = true
                    btnAction2.text = root.context.getString(R.string.platform_request_reject)
                    btnAction2.setIconResource(R.drawable.ic_close)
                    btnAction2.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                    val errorColor = com.google.android.material.color.MaterialColors.getColor(btnAction2, androidx.appcompat.R.attr.colorError)
                    btnAction2.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                    btnAction2.setTextColor(errorColor)
                    btnAction2.iconTint = android.content.res.ColorStateList.valueOf(errorColor)
                    btnAction2.strokeColor = android.content.res.ColorStateList.valueOf(errorColor)
                    btnAction2.strokeWidth = (1 * root.context.resources.displayMetrics.density).toInt()
                    btnAction2.setOnClickListener { onReject(item) }
                } else {
                    actionRow.isVisible = false
                    btnAction1.isVisible = false
                    btnAction2.isVisible = false
                }
        }
    }
}
}