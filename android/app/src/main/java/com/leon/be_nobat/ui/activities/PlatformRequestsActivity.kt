package com.leon.be_nobat.ui.activities

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.leon.be_nobat.helpers.StatusBadgeHelper
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.view_models.platform.PlatformRequestsUiState
import com.leon.be_nobat.ui.view_models.platform.PlatformRequestsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

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
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.platform_request_approve)
            .setMessage(getString(R.string.platform_request_confirm_approve, req.businessName, req.userDisplayName ?: req.userId))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.platform_request_approve) { _, _ ->
                viewModel.approve(req.id)
            }
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
                    item.contactPhone.takeIf { it.isNotBlank() }?.let { "☎ $it" }
                ).filterNotNull().joinToString("   •   ")
                tvSubtitle.isVisible = categoryCityPhone.isNotBlank()
                tvSubtitle.text = categoryCityPhone

                val bodyLines = mutableListOf<String>()
                val applicant = listOfNotNull(item.userDisplayName, item.userPhoneNumber).joinToString(" - ")
                if (applicant.isNotBlank()) {
                    bodyLines.add(root.context.getString(R.string.platform_request_applicant_label, applicant))
                }
                bodyLines.add(root.context.getString(R.string.platform_request_branch_label, item.firstBranchName, item.firstBranchAddress))
                if (item.description.isNotBlank()) {
                    bodyLines.add(root.context.getString(R.string.platform_request_desc_label, item.description))
                }
                if (!item.reviewNote.isNullOrBlank()) {
                    bodyLines.add(root.context.getString(R.string.platform_request_rejection_label, item.reviewNote))
                }
                tvBody.isVisible = true
                tvBody.text = bodyLines.joinToString("\n")

                if (isPending) {
                    btnAction1.isVisible = true
                    btnAction1.text = root.context.getString(R.string.platform_request_approve)
                    btnAction1.setOnClickListener { onApprove(item) }

                    btnAction2.isVisible = true
                    btnAction2.text = root.context.getString(R.string.platform_request_reject)
                    btnAction2.setOnClickListener { onReject(item) }
                } else {
                    btnAction1.isVisible = false
                    btnAction2.isVisible = false
                }
            }
        }
    }
}
