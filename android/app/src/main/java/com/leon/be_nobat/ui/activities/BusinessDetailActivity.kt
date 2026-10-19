package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.leon.be_nobat.R
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.view_models.BusinessDetailUiState
import com.leon.be_nobat.ui.view_models.BusinessDetailViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class BusinessDetailActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_business_detail

    private val viewModel: BusinessDetailViewModel by viewModel()

    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun setupViews() {
        setupAdminHeader(getString(R.string.business_details))
        setMenuVisibility(false)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        val businessId = intent.getStringExtra("businessId") ?: ""
        viewModel.setBusinessId(businessId)

        val tokenManager = org.koin.java.KoinJavaComponent.get<com.leon.be_nobat.data.local.TokenManager>(com.leon.be_nobat.data.local.TokenManager::class.java)
        findViewById<android.widget.ImageButton>(R.id.btnFavorite).setOnClickListener {
            lifecycleScope.launch {
                if (tokenManager.currentSession() == null) {
                    val intent = Intent(this@BusinessDetailActivity, LoginActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_RETURN_TARGET, BusinessDetailActivity::class.java.name)
                        putExtra(LoginActivity.EXTRA_BUSINESS_ID, businessId)
                    }
                    startActivity(intent)
                } else {
                    viewModel.toggleFavorite()
                }
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBook).setOnClickListener {
            val id = viewModel.state.value.business?.id ?: return@setOnClickListener
            val intent = Intent(this, BookingActivity::class.java).apply {
                putExtra("businessId", id)
            }
            startActivity(intent)
        }
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: BusinessDetailUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.business == null
        findViewById<android.widget.TextView>(R.id.errorText).apply {
            isVisible = state.error != null
            text = state.error?.let { com.leon.be_nobat.helpers.ErrorFormatter.format(this@BusinessDetailActivity, it) }
        }
        val b = state.business ?: return
        findViewById<android.widget.TextView>(R.id.tvHeaderTitle)?.text = b.name.ifBlank { getString(R.string.business_details) }
        findViewById<android.widget.TextView>(R.id.tvName).text = b.name
        findViewById<android.widget.TextView>(R.id.tvCategoryCity).text = "${b.category} • ${b.city}"
        findViewById<android.widget.TextView>(R.id.tvDescription).text = b.description
        val branchStr = if (b.branchCount == 1) getString(R.string.branch_count_single) else getString(R.string.branch_count, com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(b.branchCount))
        val serviceStr = if (b.serviceCount == 1) getString(R.string.service_count_single) else getString(R.string.service_count, com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(b.serviceCount))
        val reviewStr = getString(R.string.review_count, com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(b.reviewCount))
        val ratingStr = b.rating?.let { "★ ${com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(it)}" } ?: "—"
        findViewById<android.widget.TextView>(R.id.tvStats).text = "$branchStr • $serviceStr • $ratingStr ($reviewStr)"
        findViewById<android.widget.TextView>(R.id.tvBranches).text = b.branches.joinToString("\n\n") { "• ${it.name} — ${it.address}" }
        findViewById<android.widget.TextView>(R.id.tvServices).text = b.services.joinToString("\n\n") {
            "• ${it.name} (${com.leon.be_nobat.helpers.PersianUtils.formatDuration(it.durationMinutes)}) — ${com.leon.be_nobat.helpers.PersianUtils.formatPrice(it.price, it.currency)}"
        }
        val tvReviews = findViewById<android.widget.TextView>(R.id.tvReviews)
        val cardEmptyReviews = findViewById<View>(R.id.cardEmptyReviews)
        if (b.reviews.isNotEmpty()) {
            cardEmptyReviews.isVisible = false
            tvReviews.isVisible = true
            tvReviews.text = b.reviews.joinToString("\n\n") {
                "★ ${com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(it.rating)} — ${it.customerName}\n${it.comment}"
            }
            tvReviews.setTextAppearance(R.style.TextAppearance_BeNobat_Body)
        } else {
            cardEmptyReviews.isVisible = true
            tvReviews.isVisible = false
            findViewById<android.widget.TextView>(R.id.tvEmptyReviewsTitle).text = getString(R.string.no_reviews_user_title)
            findViewById<android.widget.TextView>(R.id.tvEmptyReviewsDesc).text = getString(R.string.no_reviews_user_desc)
        }
        findViewById<android.widget.ImageButton>(R.id.btnFavorite).setColorFilter(if (state.isFavorite) getColor(R.color.error_red) else getColor(R.color.brand_accent))
    }
}
