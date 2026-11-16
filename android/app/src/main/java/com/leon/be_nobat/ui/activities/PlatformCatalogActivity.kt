package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementCatalog
import com.leon.be_nobat.domain.model.ManagementCatalogInput
import com.leon.be_nobat.helpers.AdminFormDialog
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.view_models.platform.PlatformCatalogUiState
import com.leon.be_nobat.ui.view_models.platform.PlatformCatalogViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlatformCatalogActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: PlatformCatalogViewModel by viewModel()
    private lateinit var adapter: CatalogAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusTabs: TabLayout

    private val fallbackCategoryOptions = listOf(
        "عمومی", "پزشکی و سلامت", "زیبایی و سلامت", "آموزش", "خدمات فنی", "ورزش", "مشاوره", "فروشگاهی", "سایر"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requirePlatformAdminPermission()
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.platform_catalog), currentMenuItemId = R.id.drawer_platform_catalog)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        // Setup Search Input
        findViewById<TextInputLayout>(R.id.adminSearchLayout)?.apply {
            isVisible = true
            findViewById<TextInputEditText>(R.id.adminSearchInput)?.apply {
                hint = getString(R.string.search_catalog_hint)
                setText(viewModel.state.value.searchQuery)
                doAfterTextChanged { text ->
                    viewModel.setSearchQuery(text?.toString().orEmpty())
                }
                setOnEditorActionListener { v, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(v.windowToken, 0)
                        true
                    } else false
                }
            }
        }

        // Setup Filter Button
        findViewById<MaterialButton>(R.id.btnAdminFilter)?.apply {
            isVisible = true
            setOnClickListener { onFilterButtonClicked() }
        }

        // Setup Status Tabs (همه، منتشرشده، پنهان، استفاده‌شده، بدون استفاده)
        statusTabs = findViewById(R.id.statusTabs)
        statusTabs.isVisible = true
        statusTabs.removeAllTabs()
        val filterTabs = listOf(
            "" to getString(R.string.tab_all),
            "published" to getString(R.string.catalog_status_published),
            "hidden" to getString(R.string.catalog_status_hidden),
            "used" to getString(R.string.tab_used),
            "unused" to getString(R.string.tab_unused),
        )
        filterTabs.forEach { (key, label) ->
            statusTabs.addTab(statusTabs.newTab().setText(label).also { it.tag = key })
        }
        val currentTab = viewModel.state.value.selectedTab
        val initialTabIndex = filterTabs.indexOfFirst { it.first == currentTab }
        if (initialTabIndex > 0) {
            statusTabs.getTabAt(initialTabIndex)?.select()
        }
        statusTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val key = (tab.tag as? String).orEmpty()
                viewModel.setSelectedTab(key)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        adapter = CatalogAdapter(
            onEdit = { item -> showEditCatalogDialog(item) },
            onTogglePublish = { item -> viewModel.togglePublished(item) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@PlatformCatalogActivity)
            this.adapter = this@PlatformCatalogActivity.adapter
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd).apply {
            visibility = View.VISIBLE
            setOnClickListener { showCreateCatalogDialog() }
        }
    }

    private fun onFilterButtonClicked() {
        if (viewModel.state.value.hasFilters) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_catalog_title)
                .setItems(arrayOf(getString(R.string.filter), getString(R.string.action_clear_filters))) { _, which ->
                    if (which == 0) showFilterDialog() else viewModel.resetFilters()
                }
                .show()
        } else {
            showFilterDialog()
        }
    }

    private fun showFilterDialog() {
        val s = viewModel.state.value

        val categoryOptions = listOf("" to getString(R.string.filter_all_categories)) +
                s.availableCategories.map { it to it }

        val statusOptions = listOf(
            "" to getString(R.string.filter_all_statuses),
            "published" to getString(R.string.catalog_status_published),
            "hidden" to getString(R.string.catalog_status_hidden),
        )

        val usageOptions = listOf(
            "" to getString(R.string.filter_all_usages),
            "used" to getString(R.string.tab_used),
            "unused" to getString(R.string.tab_unused),
        )

        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.filter_catalog_title))
            .dropdownFieldPairs(getString(R.string.form_label_category), categoryOptions, default = s.selectedCategory)
            .dropdownFieldPairs(getString(R.string.status), statusOptions, default = s.statusFilter)
            .dropdownFieldPairs(getString(R.string.filter_all_usages), usageOptions, default = s.usageFilter)
            .onSubmit { values ->
                val selectedCat = values.getOrNull(0).orEmpty()
                val selectedStat = values.getOrNull(1).orEmpty()
                val selectedUse = values.getOrNull(2).orEmpty()
                viewModel.setFilters(category = selectedCat, status = selectedStat, usage = selectedUse)
            }
            .show()
    }

    private fun showCreateCatalogDialog() {
        val categories = viewModel.state.value.availableCategories.ifEmpty { fallbackCategoryOptions }
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_create_catalog_title))
            .field(getString(R.string.form_label_name))
            .field(getString(R.string.form_label_slug))
            .dropdownField(getString(R.string.form_label_category), categories, default = categories.firstOrNull() ?: "عمومی")
            .field(getString(R.string.form_label_description))
            .integerField(getString(R.string.form_label_duration_minutes), default = "30")
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val slug = values.getOrNull(1).orEmpty()
                val category = values.getOrNull(2).orEmpty().ifBlank { categories.firstOrNull() ?: "عمومی" }
                val description = values.getOrNull(3).orEmpty()
                val duration = PersianUtils.toEnglishDigits(values.getOrNull(4)).toIntOrNull() ?: 30
                if (name.isBlank() || slug.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.create(ManagementCatalogInput(
                    name = name,
                    slug = slug,
                    category = category,
                    description = description,
                    suggestedDurationMinutes = duration,
                ))
            }
            .show()
    }

    private fun showEditCatalogDialog(item: ManagementCatalog) {
        val categories = (viewModel.state.value.availableCategories + listOf(item.category)).distinct().ifEmpty { fallbackCategoryOptions }
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_edit_catalog_title))
            .field(getString(R.string.form_label_name), default = item.name)
            .field(getString(R.string.form_label_slug), default = item.slug)
            .dropdownField(getString(R.string.form_label_category), categories, default = item.category)
            .field(getString(R.string.form_label_description), default = item.description)
            .integerField(getString(R.string.form_label_duration_minutes), default = item.suggestedDurationMinutes.toString())
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val slug = values.getOrNull(1).orEmpty()
                val category = values.getOrNull(2).orEmpty().ifBlank { item.category }
                val description = values.getOrNull(3).orEmpty()
                val duration = PersianUtils.toEnglishDigits(values.getOrNull(4)).toIntOrNull() ?: item.suggestedDurationMinutes
                if (name.isBlank() || slug.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.update(item.id, ManagementCatalogInput(
                    name = name,
                    slug = slug,
                    category = category,
                    description = description,
                    suggestedDurationMinutes = duration,
                    isPublished = item.isPublished,
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

    private fun render(state: PlatformCatalogUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)

        // Update Tab counter labels
        if (statusTabs.tabCount == 5) {
            statusTabs.getTabAt(0)?.text = "${getString(R.string.tab_all)} (${PersianUtils.toPersianDigits(state.allCount)})"
            statusTabs.getTabAt(1)?.text = "${getString(R.string.catalog_status_published)} (${PersianUtils.toPersianDigits(state.publishedCount)})"
            statusTabs.getTabAt(2)?.text = "${getString(R.string.catalog_status_hidden)} (${PersianUtils.toPersianDigits(state.hiddenCount)})"
            statusTabs.getTabAt(3)?.text = "${getString(R.string.tab_used)} (${PersianUtils.toPersianDigits(state.usedCount)})"
            statusTabs.getTabAt(4)?.text = "${getString(R.string.tab_unused)} (${PersianUtils.toPersianDigits(state.unusedCount)})"
        }

        // Highlight Filter button when active
        findViewById<MaterialButton>(R.id.btnAdminFilter)?.apply {
            val primaryColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
            val outlineColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
            if (state.hasFilters) {
                strokeColor = ColorStateList.valueOf(primaryColor)
                strokeWidth = (2 * resources.displayMetrics.density).toInt()
                iconTint = ColorStateList.valueOf(primaryColor)
            } else {
                strokeColor = ColorStateList.valueOf(outlineColor)
                strokeWidth = (1 * resources.displayMetrics.density).toInt()
                iconTint = ColorStateList.valueOf(primaryColor)
            }
        }

        state.error?.let { Snackbar.make(swipeRefresh, com.leon.be_nobat.helpers.ErrorFormatter.format(this, it), Snackbar.LENGTH_SHORT).show() }
    }

    private class CatalogAdapter(
        private val onEdit: (ManagementCatalog) -> Unit,
        private val onTogglePublish: (ManagementCatalog) -> Unit,
    ) : BaseAdminAdapter<ManagementCatalog>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementCatalog) {
            val ctx = holder.binding.root.context
            holder.binding.apply {
                tvTitle.text = item.name

                // Status badge (matching Web: published -> green, hidden -> red/amber)
                if (item.isPublished) {
                    tvStatus.text = ctx.getString(R.string.catalog_status_published)
                    tvStatus.setBackgroundResource(R.drawable.bg_status_badge_completed)
                    tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_completed_text))
                } else {
                    tvStatus.text = ctx.getString(R.string.catalog_status_hidden)
                    tvStatus.setBackgroundResource(R.drawable.bg_status_badge_cancelled)
                    tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_cancelled_text))
                }

                // Subtitle: slug + usage count (matching Web: /slug · X کسب‌وکار)
                val usageText = ctx.getString(R.string.catalog_usage_count, PersianUtils.toPersianDigits(item.usageCount))
                tvSubtitle.text = "/${item.slug} · $usageText"

                // Body: category + suggested duration + description
                val durationStr = ctx.getString(R.string.duration_minutes_val, PersianUtils.toPersianDigits(item.suggestedDurationMinutes))
                val categoryDuration = "${item.category} · $durationStr"
                tvBody.text = if (item.description.isNotBlank()) {
                    "$categoryDuration\n${item.description}"
                } else {
                    categoryDuration
                }

                // Button 1: Toggle Publish / Hide
                btnAction1.isVisible = true
                val density = ctx.resources.displayMetrics.density
                if (item.isPublished) {
                    // Danger Outlined: "پنهان‌کردن"
                    btnAction1.text = ctx.getString(R.string.catalog_action_hide)
                    val errorColor = ContextCompat.getColor(ctx, R.color.colorError)
                    btnAction1.setTextColor(errorColor)
                    btnAction1.strokeColor = ColorStateList.valueOf(errorColor)
                    btnAction1.strokeWidth = (1 * density).toInt()
                    btnAction1.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                } else {
                    // Success Outlined: "انتشار"
                    btnAction1.text = ctx.getString(R.string.catalog_action_publish)
                    val successColor = ContextCompat.getColor(ctx, R.color.colorSuccess)
                    btnAction1.setTextColor(successColor)
                    btnAction1.strokeColor = ColorStateList.valueOf(successColor)
                    btnAction1.strokeWidth = (1 * density).toInt()
                    btnAction1.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                }
                btnAction1.setOnClickListener { onTogglePublish(item) }

                // Button 2: Edit
                btnAction2.isVisible = true
                btnAction2.text = ctx.getString(R.string.action_edit)
                btnAction2.setOnClickListener { onEdit(item) }

                root.setOnClickListener { onEdit(item) }
            }
        }
    }
}
