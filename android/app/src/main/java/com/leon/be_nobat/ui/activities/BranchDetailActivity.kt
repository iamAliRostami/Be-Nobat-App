package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import com.google.android.material.button.MaterialButton
import com.leon.be_nobat.R
import com.leon.be_nobat.data.remote.BeNobatApiConfig
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.BusinessShareDialog
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.helpers.SocialChannelHelper
import com.leon.be_nobat.ui.view_models.BranchDetailUiState
import com.leon.be_nobat.ui.view_models.BranchDetailViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

class BranchDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_BRANCH_ID = "branchId"
        const val EXTRA_BUSINESS_ID = "businessId"

        fun createIntent(context: Context, branchId: String, businessId: String? = null): Intent {
            return Intent(context, BranchDetailActivity::class.java).apply {
                putExtra(EXTRA_BRANCH_ID, branchId)
                businessId?.let { putExtra(EXTRA_BUSINESS_ID, it) }
            }
        }
    }

    override val layoutResourceId: Int = R.layout.activity_branch_detail

    private val viewModel: BranchDetailViewModel by viewModel()
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun setupViews() {
        setupAdminHeader(getString(R.string.branch_count_single)) {
            handleBackPress()
        }
        setMenuVisibility(false)

        onBackPressedDispatcher.addCallback(this) {
            handleBackPress()
        }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        val branchId = intent.getStringExtra(EXTRA_BRANCH_ID) ?: ""
        val businessId = intent.getStringExtra(EXTRA_BUSINESS_ID)
        viewModel.setParams(branchId, businessId)

        findViewById<View?>(R.id.btnShareQr)?.setOnClickListener {
            val branch = viewModel.state.value.branch
            val biz = viewModel.state.value.business
            if (branch != null) {
                BusinessShareDialog.showForBranch(
                    context = this,
                    branchId = branch.id,
                    branchName = branch.name,
                    businessName = biz?.name ?: ""
                )
            }
        }

        findViewById<MaterialButton>(R.id.btnBookBranch).setOnClickListener {
            val state = viewModel.state.value
            val bizId = state.business?.id ?: intent.getStringExtra(EXTRA_BUSINESS_ID) ?: return@setOnClickListener
            val brId = state.branch?.id ?: intent.getStringExtra(EXTRA_BRANCH_ID) ?: return@setOnClickListener
            val bookingIntent = Intent(this, BookingActivity::class.java).apply {
                putExtra("businessId", bizId)
                putExtra("branchId", brId)
            }
            startActivity(bookingIntent)
        }

        findViewById<View>(R.id.layoutParentBusiness)?.setOnClickListener {
            val bizId = viewModel.state.value.business?.id ?: intent.getStringExtra(EXTRA_BUSINESS_ID)
            if (!bizId.isNullOrBlank()) {
                val intent = Intent(this, BusinessDetailActivity::class.java).apply {
                    putExtra("businessId", bizId)
                }
                startActivity(intent)
            }
        }
    }

    private fun handleBackPress() {
        if (isTaskRoot) {
            val bizId = viewModel.state.value.business?.id ?: intent.getStringExtra(EXTRA_BUSINESS_ID)
            if (!bizId.isNullOrBlank()) {
                startActivity(Intent(this, BusinessDetailActivity::class.java).apply {
                    putExtra("businessId", bizId)
                })
            } else {
                startActivity(Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
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

    private fun render(state: BranchDetailUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.branch == null
        findViewById<TextView>(R.id.errorText).apply {
            isVisible = state.error != null
            text = state.error?.let { com.leon.be_nobat.helpers.ErrorFormatter.format(this@BranchDetailActivity, it) }
        }

        val branch = state.branch ?: return
        val biz = state.business

        // Header Title
        findViewById<TextView>(R.id.tvHeaderTitle)?.text = branch.name
        findViewById<TextView>(R.id.tvBranchName)?.text = branch.name

        // Parent Business info
        if (biz != null) {
            findViewById<View>(R.id.layoutParentBusiness)?.isVisible = true
            findViewById<TextView>(R.id.tvParentBusinessName)?.text = biz.name
            findViewById<TextView>(R.id.tvBranchCategoryCity)?.text = "${biz.category} • ${biz.city}"
        } else {
            findViewById<View>(R.id.layoutParentBusiness)?.isVisible = false
            findViewById<TextView>(R.id.tvBranchCategoryCity)?.text = branch.address
        }

        // Service Count Badge
        val serviceCount = state.services.size
        val serviceCountStr = if (serviceCount == 1) getString(R.string.service_count_single) else getString(R.string.service_count, PersianUtils.toPersianDigits(serviceCount))
        findViewById<TextView>(R.id.tvDetailServiceCount)?.text = serviceCountStr
        findViewById<TextView>(R.id.tvServicesBadge)?.text = serviceCountStr

        // Header Banner
        val ivBanner = findViewById<ImageView>(R.id.ivBranchHeaderBanner)
        val bannerUrl = (branch.headerUrl ?: biz?.headerUrl)?.let { BeNobatApiConfig.resolveMediaUrl(it) ?: it }
        if (!bannerUrl.isNullOrBlank()) {
            ivBanner?.isVisible = true
            ivBanner?.load(bannerUrl) {
                crossfade(true)
                placeholder(R.drawable.badge_booked_background)
                error(R.drawable.badge_booked_background)
            }
        } else {
            ivBanner?.isVisible = false
        }

        // Avatar
        val ivAvatar = findViewById<ImageView>(R.id.ivBranchAvatar)
        val tvAvatarFallback = findViewById<TextView>(R.id.tvBranchAvatarFallback)
        val avatarUrl = (branch.avatarUrl ?: biz?.avatarUrl)?.let { BeNobatApiConfig.resolveMediaUrl(it) ?: it }
        if (!avatarUrl.isNullOrBlank()) {
            ivAvatar?.isVisible = true
            tvAvatarFallback?.isVisible = false
            ivAvatar?.load(avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.badge_booked_background)
                error(R.drawable.badge_booked_background)
            }
        } else {
            ivAvatar?.isVisible = false
            tvAvatarFallback?.isVisible = true
            tvAvatarFallback?.text = branch.name.take(1).ifBlank { "ش" }
        }

        // Working Hours
        val layoutHours = findViewById<View>(R.id.layoutBranchHours)
        val tvHours = findViewById<TextView>(R.id.tvBranchHours)
        if (branch.openHour > 0 || branch.closeHour > 0) {
            layoutHours?.isVisible = true
            val openH = String.format(Locale.US, "%02d:00", branch.openHour)
            val closeH = String.format(Locale.US, "%02d:00", branch.closeHour)
            tvHours?.text = "ساعت کاری: ${PersianUtils.toPersianDigits(openH)} الی ${PersianUtils.toPersianDigits(closeH)}"
        } else {
            layoutHours?.isVisible = false
        }

        // Direct Phone
        val layoutPhone = findViewById<View>(R.id.layoutBranchPhone)
        val tvPhone = findViewById<TextView>(R.id.tvBranchPhone)
        val btnCall = findViewById<MaterialButton>(R.id.btnCallBranch)
        val phone = branch.phone?.trim()?.takeIf { it.isNotBlank() }
        if (!phone.isNullOrBlank()) {
            layoutPhone?.isVisible = true
            tvPhone?.text = "تلفن تماس: ${PersianUtils.toPersianDigits(phone)}"
            btnCall?.setOnClickListener {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                try {
                    startActivity(dialIntent)
                } catch (_: Exception) {}
            }
        } else {
            layoutPhone?.isVisible = false
        }

        // Address & Navigation
        val layoutAddress = findViewById<View>(R.id.layoutBranchAddress)
        val tvAddress = findViewById<TextView>(R.id.tvBranchAddress)
        val btnNavigate = findViewById<MaterialButton>(R.id.btnNavigateBranch)
        if (branch.address.isNotBlank()) {
            layoutAddress?.isVisible = true
            tvAddress?.text = branch.address
        } else {
            layoutAddress?.isVisible = false
        }

        val hasCoords = (branch.latitude != null && branch.longitude != null && branch.latitude != 0.0 && branch.longitude != 0.0)
        if (hasCoords || branch.address.isNotBlank()) {
            btnNavigate?.isVisible = true
            btnNavigate?.setOnClickListener {
                val uri = if (hasCoords) {
                    Uri.parse("geo:${branch.latitude},${branch.longitude}?q=${branch.latitude},${branch.longitude}(${Uri.encode(branch.name)})")
                } else {
                    Uri.parse("geo:0,0?q=${Uri.encode(branch.address)}")
                }
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                try {
                    startActivity(mapIntent)
                } catch (_: Exception) {
                    val browserUri = if (hasCoords) {
                        Uri.parse("https://maps.google.com/?q=${branch.latitude},${branch.longitude}")
                    } else {
                        Uri.parse("https://maps.google.com/?q=${Uri.encode(branch.address)}")
                    }
                    startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                }
            }
        } else {
            btnNavigate?.isVisible = false
        }

        // Social Channels
        val cardSocial = findViewById<View>(R.id.cardSocialSection)
        val containerSocial = findViewById<LinearLayout>(R.id.containerSocialChannels)
        val tvSocialTitle = findViewById<TextView>(R.id.tvSocialTitle)
        val channels = SocialChannelHelper.resolveEffectiveChannels(biz, branch)
        if (channels.isNotEmpty()) {
            cardSocial?.isVisible = true
            tvSocialTitle?.text = "راه‌های ارتباطی شعبه ${branch.name}"
            if (containerSocial != null) {
                SocialChannelHelper.bindSocialChannels(this, containerSocial, channels)
            }
        } else {
            cardSocial?.isVisible = false
        }

        // Services
        val layoutServices = findViewById<LinearLayout>(R.id.layoutServicesList)
        val cardEmptyServices = findViewById<View>(R.id.cardEmptyServices)
        layoutServices?.removeAllViews()

        if (state.services.isNotEmpty()) {
            cardEmptyServices?.isVisible = false
            layoutServices?.isVisible = true
            val inflater = LayoutInflater.from(this)
            state.services.forEach { srv ->
                val srvView = inflater.inflate(R.layout.item_detail_service, layoutServices, false)
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
                    val bookingIntent = Intent(this, BookingActivity::class.java).apply {
                        biz?.id?.let { putExtra("businessId", it) }
                        putExtra("branchId", branch.id)
                        putExtra("serviceId", srv.id)
                    }
                    startActivity(bookingIntent)
                }
                layoutServices?.addView(srvView)
            }
        } else {
            cardEmptyServices?.isVisible = true
            layoutServices?.isVisible = false
        }
    }
}
