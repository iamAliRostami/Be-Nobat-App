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
import com.google.android.material.tabs.TabLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.databinding.ItemAdminAppointmentBinding
import com.leon.be_nobat.domain.model.ManagementAvailability
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.view_models.admin.AdminAvailabilityUiState
import com.leon.be_nobat.ui.view_models.admin.AdminAvailabilityViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminAvailabilityActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: AdminAvailabilityViewModel by viewModel()
    private lateinit var adapter: AvailabilityAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<TabLayout>(R.id.statusTabs).visibility = View.GONE
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.admin_availability), currentMenuItemId = R.id.drawer_availability)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = AvailabilityAdapter()
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
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)
    }

    private class AvailabilityAdapter :
        BaseAdminAdapter<ManagementAvailability>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementAvailability) {
            holder.binding.apply {
                val dayName = com.leon.be_nobat.helpers.JalaliDateHelper.translateDayOfWeek(item.dayOfWeek)
                tvTitle.text = "$dayName • ${com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(item.startsAt)} - ${com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(item.endsAt)}"
                tvSubtitle.text = item.effectiveDate?.let { com.leon.be_nobat.helpers.JalaliDateHelper.formatIsoToJalali(it) } ?: dayName
                tvBody.text = if (item.isAvailable) root.context.getString(R.string.status_available) else root.context.getString(R.string.status_unavailable)
                tvStatus.text = if (item.isAvailable) root.context.getString(R.string.set_active) else root.context.getString(R.string.set_inactive)
                btnAction1.isVisible = false
                btnAction2.isVisible = false
            }
        }
    }
}
