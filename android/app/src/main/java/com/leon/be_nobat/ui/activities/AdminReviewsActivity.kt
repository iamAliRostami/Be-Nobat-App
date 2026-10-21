package com.leon.be_nobat.ui.activities

import android.os.Bundle
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
import com.leon.be_nobat.domain.model.ManagementReview
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.JalaliDateHelper
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.view_models.admin.AdminReviewsUiState
import com.leon.be_nobat.ui.view_models.admin.AdminReviewsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminReviewsActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: AdminReviewsViewModel by viewModel()
    private lateinit var adapter: ReviewsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireBusinessManagementPermission()
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.admin_reviews), currentMenuItemId = R.id.drawer_reviews)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = ReviewsAdapter(
            onPublish = { review -> viewModel.publish(review.id) },
            onReject = { review -> viewModel.reject(review.id, null) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@AdminReviewsActivity)
            this.adapter = this@AdminReviewsActivity.adapter
        }

        val tabs = findViewById<TabLayout>(R.id.statusTabs)
        listOf(
            "" to getString(R.string.tab_all),
            "Pending" to getString(R.string.review_status_pending),
            "Published" to getString(R.string.review_status_published),
            "Rejected" to getString(R.string.review_status_rejected),
        ).forEach { (key, label) ->
            tabs.addTab(tabs.newTab().setText(label).also { it.tag = key })
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setStatusFilter((tab.tag as? String)?.ifBlank { null })
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AdminReviewsUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        val emptyStateText = findViewById<android.widget.TextView>(R.id.emptyStateText)
        val isEmpty = !state.loading && state.items.isEmpty()
        emptyStateText.isVisible = isEmpty
        if (isEmpty) {
            emptyStateText.text = when (state.statusFilter) {
                "Pending" -> getString(R.string.no_reviews_admin_pending)
                "Published" -> getString(R.string.no_reviews_admin_published)
                "Rejected" -> getString(R.string.no_reviews_admin_rejected)
                else -> getString(R.string.no_reviews_admin_all)
            }
        }
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)
        state.error?.let { Snackbar.make(swipeRefresh, com.leon.be_nobat.helpers.ErrorFormatter.format(this, it), Snackbar.LENGTH_SHORT).show() }
    }

    private class ReviewsAdapter(
        private val onPublish: (ManagementReview) -> Unit,
        private val onReject: (ManagementReview) -> Unit,
    ) : BaseAdminAdapter<ManagementReview>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementReview) {
            holder.binding.apply {
                tvTitle.text = "${item.customerName} • ★ ${PersianUtils.toPersianDigits(item.rating)}"
                val jalaliDate = item.createdAt.takeIf { it.isNotBlank() }?.let { JalaliDateHelper.formatIsoToJalali(it) }
                if (!jalaliDate.isNullOrBlank()) {
                    tvSubtitle.isVisible = true
                    tvSubtitle.text = jalaliDate
                } else {
                    tvSubtitle.isVisible = false
                }
                tvBody.text = item.comment
                com.leon.be_nobat.helpers.StatusBadgeHelper.applyReviewStatus(tvStatus, item.status)
                com.leon.be_nobat.helpers.StatusBadgeHelper.applyReviewStatusIndicator(viewStatusIndicator, item.status)
                btnAction1.text = root.context.getString(R.string.publish_review)
                btnAction2.text = root.context.getString(R.string.reject_review)
                btnAction1.isVisible = item.status == "Pending"
                btnAction2.isVisible = item.status == "Pending"
                btnAction1.setOnClickListener { onPublish(item) }
                btnAction2.setOnClickListener { onReject(item) }
            }
        }
    }
}
