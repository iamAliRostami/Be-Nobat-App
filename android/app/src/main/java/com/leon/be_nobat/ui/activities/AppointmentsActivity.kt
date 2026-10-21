package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.databinding.ItemAppointmentBinding
import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.view_models.AppointmentTab
import com.leon.be_nobat.ui.view_models.AppointmentsUiState
import com.leon.be_nobat.ui.view_models.AppointmentsViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppointmentsActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_appointments
    override val applyBottomInsetsOnRoot: Boolean = false

    private val viewModel: AppointmentsViewModel by viewModel()
    private lateinit var adapter: AppointmentsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val tokenManager: TokenManager by inject()

    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun setupViews() {
        setupMainHeader(getString(R.string.orders_title))

        lifecycleScope.launch {
            if (tokenManager.currentSession() == null) {
                val intent = Intent(this@AppointmentsActivity, LoginActivity::class.java).apply {
                    putExtra(LoginActivity.EXTRA_RETURN_TARGET, AppointmentsActivity::class.java.name)
                }
                startActivity(intent)
                finish()
                return@launch
            }
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                !getSharedPreferences("notification_permission", MODE_PRIVATE).getBoolean("asked", false)) {
                getSharedPreferences("notification_permission", MODE_PRIVATE).edit().putBoolean("asked", true).apply()
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = AppointmentsAdapter(
            onCancel = { appt -> viewModel.cancel(appt.id) },
            onReview = { appt -> viewModel.openReview(appt.id) },
        )
        findViewById<RecyclerView>(R.id.appointmentsList).apply {
            layoutManager = LinearLayoutManager(this@AppointmentsActivity)
            this.adapter = this@AppointmentsActivity.adapter
        }

        val tabs = findViewById<TabLayout>(R.id.tabs)
        AppointmentTab.entries.forEach { tab ->
            tabs.addTab(tabs.newTab().setText(getString(tab.labelRes)))
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(AppointmentTab.entries[tab.position])
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener(BottomNavigationView.OnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); finish(); true }
                R.id.nav_appointments -> { true }
                R.id.nav_favorites -> { startActivity(Intent(this, FavoritesActivity::class.java)); finish(); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); finish(); true }
                else -> false
            }
        })
        bottomNav.selectedItemId = R.id.nav_appointments
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AppointmentsUiState) {
        swipeRefresh.isRefreshing = state.loading && state.items.isNotEmpty()
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        adapter.submitList(state.items)

        state.reviewForAppointmentId?.let { apptId ->
            showReviewDialog(state.reviewRating, state.reviewComment, state.reviewSubmitting) { rating, comment ->
                viewModel.setRating(rating); viewModel.setComment(comment); viewModel.submitReview()
            }
        }

        state.error?.let {
            Snackbar.make(swipeRefresh, com.leon.be_nobat.helpers.ErrorFormatter.format(this, it), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showReviewDialog(rating: Int, comment: String, submitting: Boolean, onSubmit: (Int, String) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_review, null)
        var currentRating = if (rating in 1..5) rating else 5

        val stars = listOf(
            view.findViewById<android.widget.ImageView>(R.id.star1),
            view.findViewById<android.widget.ImageView>(R.id.star2),
            view.findViewById<android.widget.ImageView>(R.id.star3),
            view.findViewById<android.widget.ImageView>(R.id.star4),
            view.findViewById<android.widget.ImageView>(R.id.star5)
        )
        val tvRatingLabel = view.findViewById<android.widget.TextView>(R.id.tvRatingLabel)
        val etComment = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReviewComment)
        etComment.setText(comment)

        fun updateStars(r: Int) {
            currentRating = r
            stars.forEachIndexed { index, starView ->
                if (index < r) {
                    starView.setImageResource(R.drawable.ic_star_filled)
                } else {
                    starView.setImageResource(R.drawable.ic_star_outline)
                }
            }
            val formattedR = com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(r)
            tvRatingLabel.text = when (r) {
                5 -> getString(R.string.rating_excellent, formattedR)
                4 -> getString(R.string.rating_very_good, formattedR)
                3 -> getString(R.string.rating_good, formattedR)
                2 -> getString(R.string.rating_fair, formattedR)
                1 -> getString(R.string.rating_poor, formattedR)
                else -> "$formattedR / 5"
            }
        }

        stars.forEachIndexed { index, starView ->
            starView.setOnClickListener {
                updateStars(index + 1)
            }
        }

        updateStars(currentRating)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_review)
            .setView(view)
            .setPositiveButton(R.string.submit_review) { _, _ ->
                onSubmit(currentRating, etComment.text?.toString() ?: "")
            }
            .setNegativeButton(R.string.action_close) { _, _ -> viewModel.closeReview() }
            .setOnDismissListener { viewModel.closeReview() }
            .show()
    }

    private class AppointmentsAdapter(
        private val onCancel: (Appointment) -> Unit,
        private val onReview: (Appointment) -> Unit,
    ) : ListAdapter<Appointment, AppointmentsAdapter.VH>(
        object : DiffUtil.ItemCallback<Appointment>() {
            override fun areItemsTheSame(a: Appointment, b: Appointment) = a.id == b.id
            override fun areContentsTheSame(a: Appointment, b: Appointment) = a == b
        }
    ) {

        inner class VH(val binding: ItemAppointmentBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Appointment) {
                binding.apply {
                    tvBusiness.text = item.businessName
                    val commaSep = if (com.leon.be_nobat.helpers.PersianUtils.isEnglishLocale()) ", " else "، "
                    tvServices.text = item.serviceNames.joinToString(commaSep)
                    tvProvider.text = item.resourceName ?: ""
                    tvTime.text = com.leon.be_nobat.helpers.JalaliDateHelper.formatIsoDateTime(item.startsAt)
                    tvPrice.text = com.leon.be_nobat.helpers.PersianUtils.formatPrice(item.finalPrice, item.currency)
                    val formattedCode = com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(item.trackingCode)
                    tvTrackingCode.text = root.context.getString(R.string.booking_tracking_code, "\u200E$formattedCode\u200E")
                    val copyTrackingAction = {
                        val clipboard = root.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("tracking_code", item.trackingCode)
                        clipboard?.setPrimaryClip(clip)
                        com.leon.be_nobat.helpers.AppToast.show(root.context, root.context.getString(R.string.tracking_code_copied))
                    }
                    layoutTrackingCode.setOnClickListener { copyTrackingAction() }
                    btnCopyTrackingCode.setOnClickListener { copyTrackingAction() }
                    com.leon.be_nobat.helpers.StatusBadgeHelper.applyAppointmentStatus(tvStatus, item.status)
                    com.leon.be_nobat.helpers.StatusBadgeHelper.applyAppointmentStatusIndicator(viewStatusIndicator, item.status)
                    val deadline = item.cancellationDeadline ?: com.leon.be_nobat.domain.CancellationPolicy.deadline(item.startsAt, item.cancellationNoticeMinutes)
                    tvCancellationDeadline.text = "مهلت لغو: ${deadline?.let { com.leon.be_nobat.notifications.AppointmentMessages.date(it, item.timeZoneId) }.orEmpty()}"
                    tvCancellationDeadline.isVisible = deadline != null
                    btnCancel.isVisible = item.canCancel && com.leon.be_nobat.domain.CancellationPolicy.canCancel(item.status, item.startsAt, item.cancellationNoticeMinutes)
                    btnReview.isVisible = item.canReview
                    btnCancel.setOnClickListener { onCancel(item) }
                    btnReview.setOnClickListener { onReview(item) }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val inflater = LayoutInflater.from(parent.context)
            return VH(ItemAppointmentBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }
    }
}
