package com.leon.be_nobat.ui.activities



import android.content.Intent

import android.os.Bundle

import android.view.View

import androidx.activity.addCallback

import androidx.core.view.isVisible

import androidx.lifecycle.Lifecycle

import androidx.lifecycle.lifecycleScope

import androidx.lifecycle.repeatOnLifecycle

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.google.android.material.snackbar.Snackbar

import com.leon.be_nobat.R

import com.leon.be_nobat.helpers.BaseActivity

import com.leon.be_nobat.helpers.PersianUtils

import com.leon.be_nobat.ui.view_models.BusinessDetailUiState

import com.leon.be_nobat.ui.view_models.BusinessDetailViewModel

import kotlinx.coroutines.launch

import org.koin.androidx.viewmodel.ext.android.viewModel



class BusinessDetailActivity : BaseActivity() {

    override val layoutResourceId: Int = R.layout.activity_business_detail



    private val viewModel: BusinessDetailViewModel by viewModel()



    private lateinit var swipeRefresh: SwipeRefreshLayout



    override fun setupViews() {

        setupAdminHeader(getString(R.string.business_details)) {

            handleBackPress()

        }

        setMenuVisibility(false)



        onBackPressedDispatcher.addCallback(this) {

            handleBackPress()

        }



        findViewById<android.view.View?>(R.id.btnShareQr)?.setOnClickListener {

            val biz = viewModel.state.value.business

            if (biz != null) {

                val branch = biz.branches.firstOrNull { it.id == viewModel.state.value.selectedBranchId }

                com.leon.be_nobat.helpers.BusinessShareDialog.show(

                    context = this,

                    businessId = biz.id,

                    businessName = biz.name,

                    branchId = branch?.id,

                    branchName = branch?.name

                )

            }

        }



        swipeRefresh = findViewById(R.id.swipeRefresh)

        swipeRefresh.setOnRefreshListener { viewModel.load() }



        val businessId = intent.getStringExtra("businessId") ?: ""

        val branchId = intent.getStringExtra("branchId")

        viewModel.setBusinessId(businessId, branchId)



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

            val targetBranchId = viewModel.state.value.selectedBranchId ?: intent.getStringExtra("branchId")

            val bookingIntent = Intent(this, BookingActivity::class.java).apply {

                putExtra("businessId", id)

                if (!targetBranchId.isNullOrBlank()) {

                    putExtra("branchId", targetBranchId)

                }

            }

            startActivity(bookingIntent)

        }

    }



    private fun handleBackPress() {

        if (isTaskRoot) {

            startActivity(Intent(this, HomeActivity::class.java).apply {

                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            })

        }

        finish()

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

        val branchStr = if (b.branchCount == 1) getString(R.string.branch_count_single) else getString(R.string.branch_count, PersianUtils.toPersianDigits(b.branchCount))

        val serviceStr = if (b.serviceCount == 1) getString(R.string.service_count_single) else getString(R.string.service_count, PersianUtils.toPersianDigits(b.serviceCount))

        val reviewStr = getString(R.string.review_count, PersianUtils.toPersianDigits(b.reviewCount))

        val ratingStr = b.rating?.let { "★ ${PersianUtils.toPersianDigits(it)}" } ?: "—"

        findViewById<android.widget.TextView>(R.id.tvStats).text = "$branchStr • $serviceStr • $ratingStr ($reviewStr)"



        val selectedBranch = if (!state.selectedBranchId.isNullOrBlank()) {

            b.branches.firstOrNull { it.id == state.selectedBranchId }

        } else null



        val cardSelectedBranchBanner = findViewById<View>(R.id.cardSelectedBranchBanner)

        val tvSelectedBranchTitle = findViewById<android.widget.TextView>(R.id.tvSelectedBranchTitle)

        val btnBook = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBook)



        if (selectedBranch != null) {

            cardSelectedBranchBanner?.isVisible = true

            tvSelectedBranchTitle?.text = getString(R.string.selected_branch_banner_title, selectedBranch.name)

            btnBook.text = getString(R.string.book_at_branch, selectedBranch.name)

        } else {

            cardSelectedBranchBanner?.isVisible = false

            btnBook.text = getString(R.string.book_now)

        }



        findViewById<android.widget.TextView>(R.id.tvBranches).text = b.branches.joinToString("\n\n") { br ->

            val isCurrent = br.id == state.selectedBranchId

            val marker = if (isCurrent) "✓ " else "• "

            val suffix = if (isCurrent) " (${getString(R.string.selected_branch_tag)})" else ""

            "$marker${br.name} — ${br.address}$suffix"

        }



        findViewById<android.widget.TextView>(R.id.tvServices).text = b.services.joinToString("\n\n") {

            "• ${it.name} (${PersianUtils.formatDuration(it.durationMinutes)}) — ${PersianUtils.formatPrice(it.price, it.currency)}"

        }

        val tvReviews = findViewById<android.widget.TextView>(R.id.tvReviews)

        val cardEmptyReviews = findViewById<View>(R.id.cardEmptyReviews)

        if (b.reviews.isNotEmpty()) {

            cardEmptyReviews.isVisible = false

            tvReviews.isVisible = true

            tvReviews.text = b.reviews.joinToString("\n\n") {
                val replyPart = if (!it.managerReply.isNullOrBlank()) "\n${getString(R.string.manager_reply_prefix)} ${it.managerReply}" else ""
                "\u2605 ${PersianUtils.toPersianDigits(it.rating)} \u2014 ${it.customerName}\n${it.comment}$replyPart"

            }

            tvReviews.setTextAppearance(R.style.TextAppearance_BeNobat_Body)

        } else {

            cardEmptyReviews.isVisible = true

            tvReviews.isVisible = false

            findViewById<android.widget.TextView>(R.id.tvEmptyReviewsTitle).text = getString(R.string.no_reviews_user_title)

            findViewById<android.widget.TextView>(R.id.tvEmptyReviewsDesc).text = getString(R.string.no_reviews_user_desc)

        }

        val btnFav = findViewById<android.widget.ImageButton>(R.id.btnFavorite)

        val favColor = androidx.core.content.ContextCompat.getColor(

            this,

            if (state.isFavorite) R.color.favorite_active else R.color.favorite_inactive

        )

        btnFav.setImageResource(if (state.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)

        androidx.core.widget.ImageViewCompat.setImageTintList(

            btnFav,

            android.content.res.ColorStateList.valueOf(favColor)

        )

        btnFav.contentDescription = getString(if (state.isFavorite) R.string.remove_favorite else R.string.add_to_favorites)

    }

}

