package com.leon.be_nobat.ui.activities

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.view_models.admin.AdminBranchesUiState
import com.leon.be_nobat.ui.view_models.admin.AdminBranchesViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

class AdminBranchesActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: AdminBranchesViewModel by viewModel()
    private lateinit var adapter: BranchesAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireBusinessManagementPermission()
        findViewById<TabLayout>(R.id.statusTabs).visibility = View.GONE
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.admin_branches), currentMenuItemId = R.id.drawer_branches)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = BranchesAdapter(
            onToggleActive = { branch -> viewModel.toggleActive(branch.id, !branch.active) },
            onEdit = { branch -> showEditBranchDialog(branch) },
            onDelete = { branch -> viewModel.delete(branch.id) },
            onApprove = { branch -> viewModel.approveBranch(branch.id) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@AdminBranchesActivity)
            this.adapter = this@AdminBranchesActivity.adapter
        }

        lifecycleScope.launch {
            adapter.isPlatformAdmin = isPlatformAdmin()
            adapter.notifyDataSetChanged()
        }

        // Show FAB for branches (Owner/Manager/PlatformAdmin only)
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd).apply {
            visibility = View.VISIBLE
            setOnClickListener { showCreateBranchDialog() }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun showCreateBranchDialog() {
        startActivity(AdminBranchEditActivity.createIntent(this, null))
    }

    private fun showEditBranchDialog(item: ManagementBranch) {
        startActivity(AdminBranchEditActivity.createIntent(this, item.id))
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AdminBranchesUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)

        state.message?.let { msg ->
            if (msg == "branch_approved") {
                Snackbar.make(swipeRefresh, R.string.branch_approved_success, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
        state.error?.let { Snackbar.make(swipeRefresh, com.leon.be_nobat.helpers.ErrorFormatter.format(this, it), Snackbar.LENGTH_SHORT).show() }
    }

    private class BranchesAdapter(
        private val onToggleActive: (ManagementBranch) -> Unit,
        private val onEdit: (ManagementBranch) -> Unit,
        private val onDelete: (ManagementBranch) -> Unit,
        private val onApprove: (ManagementBranch) -> Unit,
    ) : BaseAdminAdapter<ManagementBranch>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        var isPlatformAdmin: Boolean = false

        override fun bindHolder(holder: AdminViewHolder, item: ManagementBranch) {
            holder.binding.apply {
                tvTitle.text = item.name

                val bizName = item.businessName?.takeIf { it.isNotBlank() } ?: "کسب‌وکار"
                val addr = item.address.trim()
                tvSubtitle.isVisible = true
                tvSubtitle.text = if (addr.isNotEmpty()) "$bizName · $addr" else bizName

                val openStr = PersianUtils.toPersianDigits("%02d:00".format(Locale.US, item.openHour))
                val closeStr = PersianUtils.toPersianDigits("%02d:00".format(Locale.US, item.closeHour))
                val hoursText = item.weeklyHours?.let {
                    val daysCount = it.map { range -> range.dayOfWeek }.distinct().size
                    "${PersianUtils.toPersianDigits(daysCount.toString())} روز فعال، ${PersianUtils.toPersianDigits(it.size.toString())} بازه هفتگی"
                } ?: "$openStr تا $closeStr"
                tvBody.isVisible = true
                tvBody.text = root.context.getString(R.string.branch_working_hours, hoursText)

                if (!item.isApproved) {
                    tvStatus.text = root.context.getString(R.string.branch_pending_approval)
                    com.leon.be_nobat.helpers.StatusBadgeHelper.applyBadgeStyle(tvStatus, com.leon.be_nobat.helpers.StatusBadgeHelper.BadgeStyle.PENDING)
                    com.leon.be_nobat.helpers.StatusBadgeHelper.applyAppointmentStatusIndicator(viewStatusIndicator, "Pending")

                    if (isPlatformAdmin) {
                        btnAction1.isVisible = true
                        btnAction1.text = root.context.getString(R.string.branch_approve_action)
                        btnAction1.setOnClickListener { onApprove(item) }
                    } else {
                        btnAction1.isVisible = false
                    }
                    btnAction2.isVisible = true
                    btnAction2.text = root.context.getString(R.string.action_edit)
                    btnAction2.setOnClickListener { onEdit(item) }
                } else {
                    com.leon.be_nobat.helpers.StatusBadgeHelper.applyActiveStatus(tvStatus, item.active)
                    com.leon.be_nobat.helpers.StatusBadgeHelper.applyAppointmentStatusIndicator(viewStatusIndicator, if (item.active) "Confirmed" else "Cancelled")

                    btnAction1.isVisible = true
                    btnAction1.text = if (item.active) root.context.getString(R.string.set_inactive) else root.context.getString(R.string.set_active)
                    btnAction1.setOnClickListener { onToggleActive(item) }
                    btnAction2.isVisible = true
                    btnAction2.text = root.context.getString(R.string.action_edit)
                    btnAction2.setOnClickListener { onEdit(item) }
                }
                root.setOnClickListener { onEdit(item) }
                root.setOnLongClickListener {
                    com.leon.be_nobat.helpers.BusinessShareDialog.show(
                        context = root.context,
                        businessId = item.businessId,
                        businessName = item.businessName?.takeIf { it.isNotBlank() } ?: root.context.getString(R.string.my_business),
                        branchId = item.id,
                        branchName = item.name,
                    )
                    true
                }
            }
        }
    }
}
