package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.view.View
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.navigation.NavigationView
import com.leon.be_nobat.R
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.view_models.admin.AdminDashboardUiState
import com.leon.be_nobat.ui.view_models.admin.AdminDashboardViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminHomeActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_home

    private val viewModel: AdminDashboardViewModel by viewModel()
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun setupViews() {
        setToolbarTitle(true) // Remove the dull base toolbar

        drawerLayout = findViewById(R.id.drawerLayout)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        setupAdminDrawer(R.id.drawer_dashboard)

        findViewById<View>(R.id.btnThemeToggle)?.setOnClickListener {
            setupThemeToggle()
        }
        findViewById<View>(R.id.btnLanguage)?.setOnClickListener {
            showLanguageMenu(it)
        }
        findViewById<View>(R.id.btnPublicView)?.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finishAffinity()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCalendar).setOnClickListener {
            startActivity(Intent(this, AdminCalendarActivity::class.java))
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnServices)?.setOnClickListener {
            startActivity(Intent(this, AdminServicesActivity::class.java))
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBranches).setOnClickListener {
            startActivity(Intent(this, AdminBranchesActivity::class.java))
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReviews).setOnClickListener {
            startActivity(Intent(this, AdminReviewsActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnViewCalendarHeader)?.setOnClickListener {
            startActivity(AdminCalendarActivity.createIntent(this))
        }
        findViewById<android.view.View>(R.id.cardAppointments)?.setOnClickListener {
            startActivity(AdminCalendarActivity.createIntent(this))
        }
        findViewById<android.view.View>(R.id.cardPending)?.setOnClickListener {
            startActivity(AdminCalendarActivity.createIntent(this, status = "Pending"))
        }
        findViewById<android.view.View>(R.id.cardUpcoming)?.setOnClickListener {
            startActivity(AdminCalendarActivity.createIntent(this, status = "Confirmed"))
        }
        findViewById<android.view.View>(R.id.cardCompleted)?.setOnClickListener {
            startActivity(AdminCalendarActivity.createIntent(this, status = "Completed"))
        }
        findViewById<android.view.View>(R.id.cardPendingReviews)?.setOnClickListener {
            startActivity(Intent(this, AdminReviewsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardBusinesses)?.setOnClickListener {
            lifecycleScope.launch {
                if (isPlatformAdmin()) {
                    startActivity(Intent(this@AdminHomeActivity, PlatformBusinessesActivity::class.java))
                } else {
                    startActivity(Intent(this@AdminHomeActivity, AdminServicesActivity::class.java))
                }
            }
        }
        findViewById<android.view.View>(R.id.cardBranches)?.setOnClickListener {
            startActivity(Intent(this, AdminBranchesActivity::class.java))
        }

        findViewById<android.view.View>(R.id.cardError)?.setOnClickListener {
            viewModel.load()
        }

        setupDoubleBackToExit(
            intercept = {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                } else {
                    false
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AdminDashboardUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.dashboard == null

        val cardError = findViewById<android.view.View>(R.id.cardError)
        val tvErrorTitle = findViewById<android.widget.TextView>(R.id.tvErrorTitle)
        val tvError = findViewById<android.widget.TextView>(R.id.tvError)

        if (state.error != null) {
            val formatted = com.leon.be_nobat.helpers.ErrorFormatter.resolve(this, state.error)
            tvErrorTitle.text = formatted.title
            tvError.text = formatted.description
            cardError.isVisible = true
        } else {
            cardError.isVisible = false
        }

        lifecycleScope.launch {
            val canManage = state.context?.canManageBusiness ?: canManageBusiness()
            val isPlatform = state.context?.isPlatformAdmin ?: isPlatformAdmin()

            val subtitleView = findViewById<android.widget.TextView>(R.id.tvDashboardSubtitle)
            if (subtitleView != null) {
                val apptCount = state.dashboard?.appointments ?: 0
                subtitleView.text = when {
                    isPlatform -> getString(R.string.dashboard_subtitle_platform)
                    canManage -> getString(
                        R.string.dashboard_subtitle_manager,
                        com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(apptCount)
                    )
                    else -> getString(
                        R.string.dashboard_subtitle_staff,
                        com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(apptCount)
                    )
                }
                subtitleView.isVisible = true
            }

            // Role-based visibility
            findViewById<android.view.View>(R.id.rowBusinessesAndBranches)?.isVisible = canManage
            findViewById<android.view.View>(R.id.cardPendingReviews)?.isVisible = canManage
            findViewById<android.view.View>(R.id.btnServices)?.isVisible = canManage
            findViewById<android.view.View>(R.id.btnBranches)?.isVisible = canManage
            findViewById<android.view.View>(R.id.btnReviews)?.isVisible = canManage
        }

        state.dashboard?.let { d ->
            findViewById<android.widget.TextView>(R.id.tvBusinessesValue).text =
                com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(d.businesses)
            findViewById<android.widget.TextView>(R.id.tvBranchesValue).text =
                com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(d.branches)
            findViewById<android.widget.TextView>(R.id.tvAppointmentsValue).text =
                com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(d.appointments)
            findViewById<android.widget.TextView>(R.id.tvPendingValue).text =
                com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(d.pending)
            findViewById<android.widget.TextView>(R.id.tvUpcomingValue).text =
                com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(d.upcoming)
            findViewById<android.widget.TextView>(R.id.tvCompletedValue).text =
                com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(d.completed)
            findViewById<android.widget.TextView>(R.id.tvRevenueValue).text =
                com.leon.be_nobat.helpers.PersianUtils.formatPrice(d.revenue.toLong())
            findViewById<android.widget.TextView>(R.id.tvPendingReviewsValue).text =
                com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(d.pendingReviews)
        }
    }
}
