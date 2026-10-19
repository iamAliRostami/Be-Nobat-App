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
import com.leon.be_nobat.domain.model.ManagementService
import com.leon.be_nobat.domain.model.ManagementServiceInput
import com.leon.be_nobat.helpers.AdminFormDialog
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.view_models.admin.AdminServicesUiState
import com.leon.be_nobat.ui.view_models.admin.AdminServicesViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminServicesActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: AdminServicesViewModel by viewModel()
    private lateinit var adapter: ServicesAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val durationOptions = listOf(
        AdminFormDialog.DropdownOption("15", "۱۵ دقیقه"),
        AdminFormDialog.DropdownOption("30", "۳۰ دقیقه"),
        AdminFormDialog.DropdownOption("45", "۴۵ دقیقه"),
        AdminFormDialog.DropdownOption("60", "۱ ساعت"),
        AdminFormDialog.DropdownOption("90", "۱.۵ ساعت"),
        AdminFormDialog.DropdownOption("120", "۲ ساعت"),
        AdminFormDialog.DropdownOption("180", "۳ ساعت"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<TabLayout>(R.id.statusTabs).visibility = View.GONE
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.admin_services), currentMenuItemId = R.id.drawer_services)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = ServicesAdapter(
            onToggleActive = { svc -> viewModel.toggleActive(svc.id, !svc.active) },
            onEdit = { svc -> showEditServiceDialog(svc) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@AdminServicesActivity)
            this.adapter = this@AdminServicesActivity.adapter
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd).apply {
            visibility = View.VISIBLE
            setOnClickListener { showCreateServiceDialog() }
        }
    }

    private fun showCreateServiceDialog() {
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_create_service_title))
            .field(getString(R.string.form_label_name))
            .field(getString(R.string.form_label_description))
            .dropdownField(getString(R.string.form_label_duration_minutes), durationOptions, default = "30")
            .numberField(getString(R.string.form_label_price), "0")
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val description = values.getOrNull(1).orEmpty()
                val duration = values.getOrNull(2)?.toIntOrNull() ?: 30
                val price = values.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                if (name.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.createService(ManagementServiceInput(
                    businessId = "",
                    name = name,
                    description = description,
                    durationMinutes = duration,
                    price = price * 10.0,
                ))
            }
            .show()
    }

    private fun showEditServiceDialog(item: ManagementService) {
        val priceInToman = (item.price / 10.0).toLong().toString()
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_edit_service_title))
            .field(getString(R.string.form_label_name), default = item.name)
            .field(getString(R.string.form_label_description), default = item.description)
            .dropdownField(getString(R.string.form_label_duration_minutes), durationOptions, default = item.durationMinutes.toString())
            .numberField(getString(R.string.form_label_price), default = priceInToman)
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val description = values.getOrNull(1).orEmpty()
                val duration = values.getOrNull(2)?.toIntOrNull() ?: item.durationMinutes
                val price = values.getOrNull(3)?.toDoubleOrNull() ?: (item.price / 10.0)
                if (name.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.updateService(item.id, ManagementServiceInput(
                    businessId = item.businessId,
                    name = name,
                    description = description,
                    durationMinutes = duration,
                    price = price * 10.0,
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

    private fun render(state: AdminServicesUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)
        state.error?.let { Snackbar.make(swipeRefresh, com.leon.be_nobat.helpers.ErrorFormatter.format(this, it), Snackbar.LENGTH_SHORT).show() }
    }

    private class ServicesAdapter(
        private val onToggleActive: (ManagementService) -> Unit,
        private val onEdit: (ManagementService) -> Unit,
    ) : BaseAdminAdapter<ManagementService>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementService) {
            holder.binding.apply {
                tvTitle.text = item.name
                tvSubtitle.text = "${PersianUtils.formatDuration(item.durationMinutes)} • ${PersianUtils.formatPrice(item.price, item.currency)}"
                tvBody.text = item.description
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
