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
import com.leon.be_nobat.domain.model.ManagementBranchInput
import com.leon.be_nobat.helpers.AdminFormDialog
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

    private val openHourOptions by lazy {
        (6..14).map { h ->
            AdminFormDialog.DropdownOption(h.toString(), PersianUtils.toPersianDigits("%02d:00".format(Locale.US, h)))
        }
    }

    private val closeHourOptions by lazy {
        (12..24).map { h ->
            AdminFormDialog.DropdownOption(h.toString(), PersianUtils.toPersianDigits("%02d:00".format(Locale.US, h)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@AdminBranchesActivity)
            this.adapter = this@AdminBranchesActivity.adapter
        }

        // Show FAB for branches (Owner/Manager/PlatformAdmin only)
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd).apply {
            visibility = View.VISIBLE
            setOnClickListener { showCreateBranchDialog() }
        }
    }

    private fun showCreateBranchDialog() {
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_create_branch_title))
            .field(getString(R.string.form_label_name), "شعبه مرکزی")
            .field(getString(R.string.form_label_address), "")
            .dropdownField(getString(R.string.form_label_open_hour), openHourOptions, default = "9")
            .dropdownField(getString(R.string.form_label_close_hour), closeHourOptions, default = "18")
            .onSubmit { values ->
                val businessId = "" // populated by admin context if needed
                val name = values.getOrNull(0).orEmpty()
                val address = values.getOrNull(1).orEmpty()
                val openHour = values.getOrNull(2)?.toIntOrNull() ?: 9
                val closeHour = values.getOrNull(3)?.toIntOrNull() ?: 18
                if (name.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.create(ManagementBranchInput(
                    businessId = businessId,
                    name = name,
                    address = address,
                    openHour = openHour,
                    closeHour = closeHour,
                ))
            }
            .show()
    }

    private fun showEditBranchDialog(item: ManagementBranch) {
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_edit_branch_title))
            .field(getString(R.string.form_label_name), default = item.name)
            .field(getString(R.string.form_label_address), default = item.address)
            .dropdownField(getString(R.string.form_label_open_hour), openHourOptions, default = item.openHour.toString())
            .dropdownField(getString(R.string.form_label_close_hour), closeHourOptions, default = item.closeHour.toString())
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val address = values.getOrNull(1).orEmpty()
                val openHour = values.getOrNull(2)?.toIntOrNull() ?: item.openHour
                val closeHour = values.getOrNull(3)?.toIntOrNull() ?: item.closeHour
                if (name.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.update(item.id, ManagementBranchInput(
                    businessId = item.businessId,
                    name = name,
                    address = address,
                    openHour = openHour,
                    closeHour = closeHour,
                ))
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

    private fun render(state: AdminBranchesUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)
        state.error?.let { Snackbar.make(swipeRefresh, com.leon.be_nobat.helpers.ErrorFormatter.format(this, it), Snackbar.LENGTH_SHORT).show() }
    }

    private class BranchesAdapter(
        private val onToggleActive: (ManagementBranch) -> Unit,
        private val onEdit: (ManagementBranch) -> Unit,
        private val onDelete: (ManagementBranch) -> Unit,
    ) : BaseAdminAdapter<ManagementBranch>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementBranch) {
            holder.binding.apply {
                tvTitle.text = item.name
                val openStr = PersianUtils.toPersianDigits("%02d:00".format(Locale.US, item.openHour))
                val closeStr = PersianUtils.toPersianDigits("%02d:00".format(Locale.US, item.closeHour))
                tvBody.text = "$openStr - $closeStr"
                tvStatus.text = if (item.active) root.context.getString(R.string.set_active) else root.context.getString(R.string.set_inactive)
                btnAction1.text = if (item.active) root.context.getString(R.string.set_inactive) else root.context.getString(R.string.set_active)
                btnAction2.isVisible = true
                btnAction2.text = root.context.getString(R.string.action_edit)
                btnAction1.setOnClickListener { onToggleActive(item) }
                btnAction2.setOnClickListener { onEdit(item) }
                root.setOnClickListener { onEdit(item) }
            }
        }
    }
}
