package com.leon.be_nobat.ui.activities

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leon.be_nobat.R
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.domain.model.ManagementAnalyticsSummary
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.adapters.AdminAnalyticsBreakdownAdapter
import com.leon.be_nobat.ui.view_models.admin.AdminAnalyticsUiState
import com.leon.be_nobat.ui.view_models.admin.AdminAnalyticsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.NumberFormat
import java.util.Locale

class AdminAnalyticsActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_analytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireBusinessManagementPermission()
    }

    private val viewModel: AdminAnalyticsViewModel by viewModel()

    private lateinit var breakdownAdapter: AdminAnalyticsBreakdownAdapter
    private lateinit var rvBreakdown: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingProgress: ProgressBar
    private lateinit var tvEmptyBreakdown: TextView
    private lateinit var tvActiveFilters: TextView

    private lateinit var tvTotalRevenue: TextView
    private lateinit var tvTotalWorkHours: TextView
    private lateinit var tvCompletedAppts: TextView
    private lateinit var tvTotalApptsSub: TextView
    private lateinit var tvCompletionRate: TextView
    private lateinit var tvCancelledNoShowSub: TextView
    private lateinit var layoutChartBars: LinearLayout

    private lateinit var btnPeriod7d: MaterialButton
    private lateinit var btnPeriod30d: MaterialButton
    private lateinit var btnPeriod90d: MaterialButton
    private lateinit var btnPeriodAll: MaterialButton

    private lateinit var tabBusinesses: MaterialButton
    private lateinit var tabBranches: MaterialButton
    private lateinit var tabStaff: MaterialButton

    override fun setupViews() {
        setupAdminHeader(getString(R.string.admin_analytics_title), currentMenuItemId = R.id.drawer_analytics)

        rvBreakdown = findViewById(R.id.rvBreakdown)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        loadingProgress = findViewById(R.id.loadingProgress)
        tvEmptyBreakdown = findViewById(R.id.tvEmptyBreakdown)
        tvActiveFilters = findViewById(R.id.tvActiveFilters)

        tvTotalRevenue = findViewById(R.id.tvTotalRevenue)
        tvTotalWorkHours = findViewById(R.id.tvTotalWorkHours)
        tvCompletedAppts = findViewById(R.id.tvCompletedAppts)
        tvTotalApptsSub = findViewById(R.id.tvTotalApptsSub)
        tvCompletionRate = findViewById(R.id.tvCompletionRate)
        tvCancelledNoShowSub = findViewById(R.id.tvCancelledNoShowSub)
        layoutChartBars = findViewById(R.id.layoutChartBars)

        btnPeriod7d = findViewById(R.id.btnPeriod7d)
        btnPeriod30d = findViewById(R.id.btnPeriod30d)
        btnPeriod90d = findViewById(R.id.btnPeriod90d)
        btnPeriodAll = findViewById(R.id.btnPeriodAll)

        tabBusinesses = findViewById(R.id.tabBusinesses)
        tabBranches = findViewById(R.id.tabBranches)
        tabStaff = findViewById(R.id.tabStaff)

        // btnBack managed by setupAdminHeader
        findViewById<View>(R.id.btnRefresh).setOnClickListener { viewModel.load() }
        findViewById<View>(R.id.btnFilterAnalytics).setOnClickListener { showFilterDialog() }
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        btnPeriod7d.setOnClickListener { viewModel.setPeriod("7d") }
        btnPeriod30d.setOnClickListener { viewModel.setPeriod("30d") }
        btnPeriod90d.setOnClickListener { viewModel.setPeriod("90d") }
        btnPeriodAll.setOnClickListener { viewModel.setPeriod("all") }

        tabBusinesses.setOnClickListener { viewModel.setActiveTab("businesses") }
        tabBranches.setOnClickListener { viewModel.setActiveTab("branches") }
        tabStaff.setOnClickListener { viewModel.setActiveTab("staff") }

        breakdownAdapter = AdminAnalyticsBreakdownAdapter()
        rvBreakdown.layoutManager = LinearLayoutManager(this)
        rvBreakdown.adapter = breakdownAdapter
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AdminAnalyticsUiState) {
        swipeRefresh.isRefreshing = state.loading && state.analytics != null
        loadingProgress.isVisible = state.loading && state.analytics == null

        renderPeriodButtons(state.selectedPeriod)
        renderTabs(state.activeTab)
        renderActiveFiltersLabel(state)

        val data = state.analytics
        if (data != null) {
            renderMetrics(data)
            renderChart(data)
            renderBreakdown(state, data)
        } else {
            tvEmptyBreakdown.isVisible = !state.loading
        }
    }

    private fun renderPeriodButtons(period: String) {
        val selectedColor = ContextCompat.getColor(this, R.color.colorPrimary)
        val selectedText = ContextCompat.getColor(this, R.color.colorOnPrimary)
        val unselectedColor = ContextCompat.getColor(this, R.color.colorSurface)
        val unselectedText = ContextCompat.getColor(this, R.color.colorOnSurface)

        listOf(
            btnPeriod7d to "7d",
            btnPeriod30d to "30d",
            btnPeriod90d to "90d",
            btnPeriodAll to "all",
        ).forEach { (btn, p) ->
            if (p == period) {
                btn.setBackgroundColor(selectedColor)
                btn.setTextColor(selectedText)
            } else {
                btn.setBackgroundColor(unselectedColor)
                btn.setTextColor(unselectedText)
            }
        }
    }

    private fun renderTabs(activeTab: String) {
        val selectedColor = ContextCompat.getColor(this, R.color.colorPrimary)
        val selectedText = ContextCompat.getColor(this, R.color.colorOnPrimary)
        val unselectedColor = ContextCompat.getColor(this, R.color.colorSurface)
        val unselectedText = ContextCompat.getColor(this, R.color.colorOnSurface)

        listOf(
            tabBusinesses to "businesses",
            tabBranches to "branches",
            tabStaff to "staff",
        ).forEach { (btn, tab) ->
            if (tab == activeTab) {
                btn.setBackgroundColor(selectedColor)
                btn.setTextColor(selectedText)
            } else {
                btn.setBackgroundColor(unselectedColor)
                btn.setTextColor(unselectedText)
            }
        }
    }

    private fun renderActiveFiltersLabel(state: AdminAnalyticsUiState) {
        val parts = mutableListOf<String>()
        state.selectedBusinessId?.let { id ->
            val b = state.businesses.firstOrNull { it.id == id }
            parts.add(b?.name ?: id)
        }
        state.selectedBranchId?.let { id ->
            val br = state.branches.firstOrNull { it.id == id }
            parts.add(br?.name ?: id)
        }
        state.selectedResourceId?.let { id ->
            val r = state.resources.firstOrNull { it.id == id }
            parts.add(r?.name ?: id)
        }

        if (parts.isNotEmpty()) {
            tvActiveFilters.isVisible = true
            tvActiveFilters.text = "${getString(R.string.filter)}: ${parts.joinToString(" · ")}"
        } else {
            tvActiveFilters.isVisible = false
        }
    }

    private fun renderMetrics(data: ManagementAnalyticsSummary) {
        val nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("fa-IR"))
        tvTotalRevenue.text = nf.format(data.totalRevenue)

        val hours = String.format(Locale.forLanguageTag("fa-IR"), "%.1f", data.totalWorkHours)
        tvTotalWorkHours.text = hours

        tvCompletedAppts.text = nf.format(data.completedAppointments)
        tvTotalApptsSub.text = getString(R.string.analytics_total_appts_sub, nf.format(data.totalAppointments))

        val rate = if (data.totalAppointments > 0) {
            (data.completedAppointments * 100) / data.totalAppointments
        } else 0
        tvCompletionRate.text = getString(R.string.analytics_percent_format, PersianUtils.toPersianDigits(rate))
        tvCancelledNoShowSub.text = getString(
            R.string.analytics_cancelled_noshow_sub,
            nf.format(data.cancelledAppointments),
            nf.format(data.noShowAppointments)
        )
    }

    private fun renderChart(data: ManagementAnalyticsSummary) {
        layoutChartBars.removeAllViews()
        val trend = data.trend
        if (trend.isEmpty()) return

        val maxRevenue = maxOf(1L, trend.maxOf { it.revenue })
        val density = resources.displayMetrics.density

        trend.forEach { item ->
            val barLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    (36 * density).toInt(),
                    LinearLayout.LayoutParams.MATCH_PARENT,
                ).apply {
                    setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                }
            }

            val apptCountText = TextView(this).apply {
                text = if (item.completed > 0) item.completed.toString() else ""
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@AdminAnalyticsActivity, R.color.colorPrimary))
            }
            barLayout.addView(apptCountText)

            val heightRatio = if (maxRevenue > 0) (item.revenue.toFloat() / maxRevenue).coerceIn(0.08f, 1.0f) else 0.08f
            val barHeight = ((140 - 45) * density * heightRatio).toInt().coerceAtLeast((10 * density).toInt())

            val barView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (20 * density).toInt(),
                    barHeight,
                )
                val bgDrawable = ContextCompat.getDrawable(this@AdminAnalyticsActivity, R.drawable.bg_stat_badge)?.mutate()
                bgDrawable?.setTint(ContextCompat.getColor(this@AdminAnalyticsActivity, if (item.completed > 0) R.color.colorPrimary else R.color.colorOutline))
                background = bgDrawable
            }
            barLayout.addView(barView)

            val dateText = TextView(this).apply {
                text = item.date.takeLast(5).replace("-", "/")
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@AdminAnalyticsActivity, R.color.colorOnSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = (4 * density).toInt()
                }
            }
            barLayout.addView(dateText)

            layoutChartBars.addView(barLayout)
        }
    }

    private fun renderBreakdown(state: AdminAnalyticsUiState, data: ManagementAnalyticsSummary) {
        val list = when (state.activeTab) {
            "businesses" -> data.byBusiness
            "branches" -> data.byBranch
            "staff" -> data.byStaff
            else -> data.byBusiness
        }

        breakdownAdapter.submitList(list)
        tvEmptyBreakdown.isVisible = list.isEmpty()
        rvBreakdown.isVisible = list.isNotEmpty()
    }

    private fun showFilterDialog() {
        val state = viewModel.state.value

        val options = mutableListOf<String>()
        options.add(getString(R.string.analytics_all_businesses))
        state.businesses.forEach { options.add(it.name) }

        val currentBusinessIdx = if (state.selectedBusinessId == null) 0
        else state.businesses.indexOfFirst { it.id == state.selectedBusinessId } + 1

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.analytics_filter_title)
            .setSingleChoiceItems(options.toTypedArray(), currentBusinessIdx.coerceAtLeast(0)) { dialog, which ->
                if (which == 0) {
                    viewModel.clearFilters()
                } else {
                    val biz = state.businesses.getOrNull(which - 1)
                    viewModel.setBusinessFilter(biz?.id)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
