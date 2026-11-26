package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.leon.be_nobat.R
import com.leon.be_nobat.data.remote.BeNobatApiConfig
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.helpers.SocialChannelHelper
import com.leon.be_nobat.ui.adapters.GalleryAdapter
import com.leon.be_nobat.ui.view_models.BusinessDetailUiState
import com.leon.be_nobat.ui.view_models.BusinessDetailViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

class BusinessDetailActivity : BaseActivity() {

    override val layoutResourceId: Int = R.layout.activity_business_detail

    private val viewModel: BusinessDetailViewModel by viewModel()
    private var hasScrolledToReviews = false

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val galleryAdapter = GalleryAdapter { item ->
        val full = BeNobatApiConfig.resolveMediaUrl(item.imageUrl) ?: item.imageUrl
        ImageViewerActivity.open(this, full, "تصویر گالری", item.caption)
        /*
            .setTitle(item.caption ?: "تصویر گالری")
            .setView(ImageView(this).apply {
                load(full) { crossfade(true) }
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(24, 24, 24, 24)
            })
            .setPositiveButton("بستن", null)
            .create()
        dialog.show() */
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.business_details)) {
            handleBackPress()
        }
        setMenuVisibility(false)

        onBackPressedDispatcher.addCallback(this) {
            handleBackPress()
        }

        findViewById<View?>(R.id.btnShareQr)?.setOnClickListener {
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

        findViewById<RecyclerView>(R.id.rvGallery)?.apply {
            layoutManager = LinearLayoutManager(this@BusinessDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = galleryAdapter
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
        findViewById<TextView>(R.id.errorText).apply {
            isVisible = state.error != null
            text = state.error?.let { com.leon.be_nobat.helpers.ErrorFormatter.format(this@BusinessDetailActivity, it) }
        }

        val b = state.business ?: return

        findViewById<TextView>(R.id.tvHeaderTitle)?.text = b.name.ifBlank { getString(R.string.business_details) }
        findViewById<TextView>(R.id.tvName).text = b.name
        findViewById<TextView>(R.id.tvCategoryCity).text = "${b.category} • ${b.city}"
        findViewById<TextView>(R.id.tvDescription).text = b.description

        // Format rating to exactly 1 decimal place
        val formattedRating = b.rating?.let {
            val rounded = String.format(Locale.US, "%.1f", it)
            if (getCurrentLanguage() == "en") rounded else PersianUtils.toPersianDigits(rounded)
        }

        val effectiveBranchCount = if (b.branches.isNotEmpty()) b.branches.size else b.branchCount
        val branchStr = if (effectiveBranchCount == 1) getString(R.string.branch_count_single) else getString(R.string.branch_count, PersianUtils.toPersianDigits(effectiveBranchCount))
        val serviceStr = if (b.serviceCount == 1) getString(R.string.service_count_single) else getString(R.string.service_count, PersianUtils.toPersianDigits(b.serviceCount))
        val reviewStr = getString(R.string.review_count, PersianUtils.toPersianDigits(b.reviewCount))
        val ratingStr = formattedRating?.let { "★ $it" } ?: "—"
        findViewById<TextView>(R.id.tvStats)?.text = "$branchStr • $serviceStr • $ratingStr ($reviewStr)"

        // Header Badges Row
        val layoutDetailRating = findViewById<View>(R.id.layoutDetailRating)
        val tvDetailRating = findViewById<TextView>(R.id.tvDetailRating)
        val tvDetailReviewCount = findViewById<TextView>(R.id.tvDetailReviewCount)
        if (formattedRating != null) {
            layoutDetailRating?.isVisible = true
            tvDetailRating?.text = "★ $formattedRating"
            if (b.reviewCount > 0) {
                tvDetailReviewCount?.isVisible = true
                tvDetailReviewCount?.text = "($reviewStr)"
            } else {
                tvDetailReviewCount?.isVisible = false
            }
        } else {
            layoutDetailRating?.isVisible = false
        }

        findViewById<TextView>(R.id.tvDetailBranchCount)?.text = branchStr
        findViewById<TextView>(R.id.tvDetailServiceCount)?.text = serviceStr

        // Header Banner
        val ivHeaderBanner = findViewById<ImageView>(R.id.ivHeaderBanner)
        val resolvedHeader = BeNobatApiConfig.resolveMediaUrl(b.headerUrl) ?: b.headerUrl
        if (!resolvedHeader.isNullOrBlank()) {
            ivHeaderBanner?.isVisible = true
            ivHeaderBanner?.setOnClickListener { ImageViewerActivity.open(this, resolvedHeader, b.name) }
            ivHeaderBanner?.load(resolvedHeader) {
                crossfade(true)
                placeholder(R.drawable.badge_booked_background)
                error(R.drawable.badge_booked_background)
            }
        } else {
            ivHeaderBanner?.isVisible = false
        }

        // Avatar
        val ivAvatar = findViewById<ImageView>(R.id.ivAvatar)
        val tvAvatarFallback = findViewById<TextView>(R.id.tvAvatarFallback)
        val resolvedAvatar = BeNobatApiConfig.resolveMediaUrl(b.avatarUrl) ?: b.avatarUrl
        if (!resolvedAvatar.isNullOrBlank()) {
            ivAvatar?.isVisible = true
            ivAvatar?.setOnClickListener { ImageViewerActivity.open(this, resolvedAvatar, b.name) }
            tvAvatarFallback?.isVisible = false
            ivAvatar?.load(resolvedAvatar) {
                crossfade(true)
                placeholder(R.drawable.badge_booked_background)
                error(R.drawable.badge_booked_background)
            }
        } else {
            ivAvatar?.isVisible = false
            tvAvatarFallback?.isVisible = true
            tvAvatarFallback?.text = b.name.take(1).ifBlank { "ب" }
        }

        // Gallery
        val tvGalleryTitle = findViewById<TextView>(R.id.tvGalleryTitle)
        val rvGallery = findViewById<RecyclerView>(R.id.rvGallery)
        val tvEmptyGallery = findViewById<TextView>(R.id.tvEmptyGallery)
        if (b.gallery.isNotEmpty()) {
            tvGalleryTitle?.isVisible = true
            rvGallery?.isVisible = true
            tvEmptyGallery?.isVisible = false
            galleryAdapter.submitList(b.gallery)
        } else {
            tvGalleryTitle?.isVisible = false
            rvGallery?.isVisible = false
            tvEmptyGallery?.isVisible = false
        }

        val selectedBranch = if (!state.selectedBranchId.isNullOrBlank()) {
            b.branches.firstOrNull { it.id == state.selectedBranchId }
        } else null

        val cardSelectedBranchBanner = findViewById<View>(R.id.cardSelectedBranchBanner)
        val tvSelectedBranchTitle = findViewById<TextView>(R.id.tvSelectedBranchTitle)
        val btnBook = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBook)

        if (selectedBranch != null) {
            cardSelectedBranchBanner?.isVisible = true
            tvSelectedBranchTitle?.text = getString(R.string.selected_branch_banner_title, selectedBranch.name)
            btnBook.text = getString(R.string.book_at_branch, selectedBranch.name)
        } else {
            cardSelectedBranchBanner?.isVisible = false
            btnBook.text = getString(R.string.book_now)
        }

        // Social & Communication channels
        val cardSocialSection = findViewById<View>(R.id.cardSocialSection)
        val containerSocialChannels = findViewById<LinearLayout>(R.id.containerSocialChannels)
        val tvSocialTitle = findViewById<TextView>(R.id.tvSocialTitle)
        val channels = SocialChannelHelper.resolveEffectiveChannels(b, selectedBranch)
        if (channels.isNotEmpty()) {
            cardSocialSection?.isVisible = true
            if (selectedBranch != null) {
                tvSocialTitle?.text = "راه‌های ارتباطی شعبه ${selectedBranch.name}"
            } else {
                tvSocialTitle?.text = "راه‌های ارتباطی و شبکه‌های اجتماعی"
            }
            if (containerSocialChannels != null) {
                SocialChannelHelper.bindSocialChannels(this, containerSocialChannels, channels)
            }
        } else {
            cardSocialSection?.isVisible = false
        }

        // Section Badges
        val actualBranchCount = b.branches.size
        val actualServiceCount = b.services.size
        findViewById<TextView>(R.id.tvBranchesBadge)?.text =
            if (actualBranchCount == 1) getString(R.string.branch_count_single) else getString(R.string.branch_count, PersianUtils.toPersianDigits(actualBranchCount))

        findViewById<TextView>(R.id.tvServicesBadge)?.text =
            if (actualServiceCount == 1) getString(R.string.service_count_single) else getString(R.string.service_count, PersianUtils.toPersianDigits(actualServiceCount))

        findViewById<TextView>(R.id.tvReviewsBadge)?.apply {
            if (formattedRating != null) {
                isVisible = true
                text = "★ $formattedRating (${PersianUtils.toPersianDigits(b.reviews.size)} نظر)"
            } else {
                isVisible = false
            }
        }

        // Populate Branches List
        val layoutBranchesList = findViewById<LinearLayout>(R.id.layoutBranchesList)
        layoutBranchesList?.removeAllViews()
        val inflater = LayoutInflater.from(this)
        b.branches.forEach { br ->
            val branchView = inflater.inflate(R.layout.item_detail_branch, layoutBranchesList, false)
            val isCurrent = br.id == state.selectedBranchId
            branchView.findViewById<TextView>(R.id.tvBranchName)?.text = br.name
            branchView.findViewById<TextView>(R.id.tvBranchAddress)?.text = br.address
            branchView.findViewById<TextView>(R.id.tvBranchSelectedBadge)?.isVisible = isCurrent

            val layoutHours = branchView.findViewById<View>(R.id.layoutBranchHours)
            val tvHours = branchView.findViewById<TextView>(R.id.tvBranchHours)
            if (br.openHour > 0 || br.closeHour > 0) {
                layoutHours?.isVisible = true
                val openH = String.format(Locale.US, "%02d:00", br.openHour)
                val closeH = String.format(Locale.US, "%02d:00", br.closeHour)
                tvHours?.text = "ساعت کاری: ${PersianUtils.toPersianDigits(openH)} الی ${PersianUtils.toPersianDigits(closeH)}"
            } else {
                layoutHours?.isVisible = false
            }

            val layoutPhone = branchView.findViewById<View>(R.id.layoutBranchPhone)
            val tvPhone = branchView.findViewById<TextView>(R.id.tvBranchPhone)
            val phone = br.phone?.trim()?.takeIf { it.isNotBlank() }
            if (!phone.isNullOrBlank()) {
                layoutPhone?.isVisible = true
                tvPhone?.text = "تلفن تماس: ${PersianUtils.toPersianDigits(phone)}"
            } else {
                layoutPhone?.isVisible = false
            }

            branchView.setOnClickListener {
                viewModel.selectBranch(br.id)
                val intent = BranchDetailActivity.createIntent(this, branchId = br.id, businessId = b.id)
                startActivity(intent)
            }
            layoutBranchesList?.addView(branchView)
        }

        // Populate Services List
        val layoutServicesList = findViewById<LinearLayout>(R.id.layoutServicesList)
        layoutServicesList?.removeAllViews()
        b.services.forEach { srv ->
            val srvView = inflater.inflate(R.layout.item_detail_service, layoutServicesList, false)
            srvView.findViewById<TextView>(R.id.tvServiceName)?.text = srv.name
            srvView.findViewById<TextView>(R.id.tvServicePrice)?.text = PersianUtils.formatPrice(srv.price, srv.currency)
            val tvDesc = srvView.findViewById<TextView>(R.id.tvServiceDescription)
            if (!srv.description.isNullOrBlank()) {
                tvDesc?.isVisible = true
                tvDesc?.text = srv.description
            } else {
                tvDesc?.isVisible = false
            }
            srvView.findViewById<TextView>(R.id.tvServiceDuration)?.text = "⏱ ${PersianUtils.formatDuration(srv.durationMinutes)}"

            srvView.setOnClickListener {
                val targetBranchId = viewModel.state.value.selectedBranchId ?: intent.getStringExtra("branchId")
                val bookingIntent = Intent(this, BookingActivity::class.java).apply {
                    putExtra("businessId", b.id)
                    if (!targetBranchId.isNullOrBlank()) {
                        putExtra("branchId", targetBranchId)
                    }
                    putExtra("serviceId", srv.id)
                }
                startActivity(bookingIntent)
            }
            layoutServicesList?.addView(srvView)
        }

        // Populate Reviews List
        val layoutReviewsList = findViewById<LinearLayout>(R.id.layoutReviewsList)
        layoutReviewsList?.removeAllViews()
        val cardEmptyReviews = findViewById<View>(R.id.cardEmptyReviews)
        if (b.reviews.isNotEmpty()) {
            cardEmptyReviews?.isVisible = false
            layoutReviewsList?.isVisible = true
            b.reviews.forEach { rev ->
                val revView = inflater.inflate(R.layout.item_detail_review, layoutReviewsList, false)
                revView.findViewById<TextView>(R.id.tvReviewAuthor)?.text = rev.customerName.ifBlank { "کاربر به‌نوبت" }
                revView.findViewById<TextView>(R.id.tvReviewRating)?.text = "★ ${PersianUtils.toPersianDigits(rev.rating)}"
                revView.findViewById<TextView>(R.id.tvReviewComment)?.text = rev.comment

                val tvDate = revView.findViewById<TextView>(R.id.tvReviewDate)
                if (rev.createdAt.isNotBlank()) {
                    tvDate?.isVisible = true
                    tvDate?.text = com.leon.be_nobat.helpers.AppDateFormatter.formatDateTime(rev.createdAt, getCurrentLanguage())
                } else {
                    tvDate?.isVisible = false
                }

                val cardManager = revView.findViewById<View>(R.id.cardManagerReply)
                val tvManager = revView.findViewById<TextView>(R.id.tvManagerReply)
                if (!rev.managerReply.isNullOrBlank()) {
                    cardManager?.isVisible = true
                    tvManager?.text = rev.managerReply
                } else {
                    cardManager?.isVisible = false
                }
                layoutReviewsList?.addView(revView)
            }
        } else {
            cardEmptyReviews?.isVisible = true
            layoutReviewsList?.isVisible = false
        }

        // Favorite button
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

        // Smooth scroll to reviews if requested
        if (!hasScrolledToReviews && intent.getBooleanExtra("scrollToReviews", false)) {
            hasScrolledToReviews = true
            val scroll = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollDetail)
            val target = findViewById<View>(R.id.layoutReviewsHeader) ?: findViewById<View>(R.id.cardEmptyReviews)
            target?.let { v ->
                scroll?.postDelayed({
                    scroll.smoothScrollTo(0, v.top)
                }, 200)
            }
        }
    }
}
