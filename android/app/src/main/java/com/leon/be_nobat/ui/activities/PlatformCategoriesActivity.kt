package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementCategory
import com.leon.be_nobat.domain.model.ManagementCategoryInput
import com.leon.be_nobat.helpers.AdminFormDialog
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.view_models.platform.PlatformCategoriesUiState
import com.leon.be_nobat.ui.view_models.platform.PlatformCategoriesViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlatformCategoriesActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: PlatformCategoriesViewModel by viewModel()
    private lateinit var adapter: CategoriesAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val kindOptions by lazy {
        listOf(
            AdminFormDialog.DropdownOption("Business", getString(R.string.category_kind_business)),
            AdminFormDialog.DropdownOption("Service", getString(R.string.category_kind_service)),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requirePlatformAdminPermission()
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.platform_categories), currentMenuItemId = R.id.drawer_platform_categories)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        // Setup Tabs (همه، کسب‌وکار، خدمت، فعال، غیرفعال)
        val tabs = findViewById<TabLayout>(R.id.statusTabs)
        tabs.isVisible = true
        tabs.removeAllTabs()
        val categoryTabs = listOf(
            "" to getString(R.string.tab_all),
            "Business" to getString(R.string.category_kind_business),
            "Service" to getString(R.string.category_kind_service),
            "Active" to getString(R.string.status_active),
            "Inactive" to getString(R.string.status_inactive),
        )
        categoryTabs.forEach { (key, label) ->
            tabs.addTab(tabs.newTab().setText(label).also { it.tag = key })
        }
        val currentTab = viewModel.state.value.selectedTab
        val initialTabIndex = categoryTabs.indexOfFirst { it.first == currentTab }
        if (initialTabIndex > 0) {
            tabs.getTabAt(initialTabIndex)?.select()
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val key = (tab.tag as? String).orEmpty()
                viewModel.setSelectedTab(key)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Setup Search
        findViewById<TextInputLayout>(R.id.adminSearchLayout)?.apply {
            isVisible = true
            findViewById<TextInputEditText>(R.id.adminSearchInput)?.apply {
                hint = getString(R.string.search_categories_hint)
                setText(viewModel.state.value.searchQuery)
                doAfterTextChanged { text ->
                    viewModel.setQuery(text?.toString().orEmpty())
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

        adapter = CategoriesAdapter(
            onEdit = { category -> showEditCategoryDialog(category) },
            onToggleActive = { category -> viewModel.toggleActive(category) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@PlatformCategoriesActivity)
            this.adapter = this@PlatformCategoriesActivity.adapter
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd).apply {
            visibility = View.VISIBLE
            setOnClickListener { showCreateCategoryDialog() }
        }
    }

    private fun showCreateCategoryDialog() {
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_create_category_title))
            .field(getString(R.string.form_label_name))
            .dropdownField(getString(R.string.form_label_kind), kindOptions, default = "Business")
            .numberField(getString(R.string.form_label_sort_order), "0")
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val kind = values.getOrNull(1).orEmpty().ifBlank { "Business" }
                val sortOrder = values.getOrNull(2)?.toIntOrNull() ?: 0
                if (name.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.create(ManagementCategoryInput(
                    name = name,
                    kind = kind,
                    sortOrder = sortOrder,
                ))
            }
            .show()
    }

    private fun showEditCategoryDialog(item: ManagementCategory) {
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_edit_category_title))
            .field(getString(R.string.form_label_name), default = item.name)
            .dropdownField(getString(R.string.form_label_kind), kindOptions, default = item.kind)
            .numberField(getString(R.string.form_label_sort_order), default = item.sortOrder.toString())
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val kind = values.getOrNull(1).orEmpty().ifBlank { item.kind }
                val sortOrder = values.getOrNull(2)?.toIntOrNull() ?: item.sortOrder
                if (name.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.update(item.id, ManagementCategoryInput(
                    name = name,
                    kind = kind,
                    sortOrder = sortOrder,
                    isActive = item.active,
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

    private fun render(state: PlatformCategoriesUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        val emptyView = findViewById<android.widget.TextView>(R.id.emptyStateText)
        emptyView.isVisible = !state.loading && state.filteredItems.isEmpty()
        if (state.searchQuery.isNotBlank() || state.selectedTab.isNotBlank()) {
            emptyView.setText(R.string.empty_categories_filtered)
        } else {
            emptyView.setText(R.string.empty_list)
        }
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.filteredItems)

        state.error?.let { err ->
            val formatted = com.leon.be_nobat.helpers.ErrorFormatter.resolve(this, err)
            val snackbar = Snackbar.make(swipeRefresh, formatted.description, Snackbar.LENGTH_LONG)
            if (formatted.isAuthError) {
                snackbar.setAction(R.string.action_login_again) {
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_RETURN_TARGET, this@PlatformCategoriesActivity::class.java.name)
                    }
                    startActivity(intent)
                }
            }
            snackbar.show()
        }
    }

    private class CategoriesAdapter(
        private val onEdit: (ManagementCategory) -> Unit,
        private val onToggleActive: (ManagementCategory) -> Unit,
    ) : BaseAdminAdapter<ManagementCategory>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementCategory) {
            holder.binding.apply {
                tvTitle.text = item.name
                tvSubtitle.text = if (item.kind == "Business") {
                    root.context.getString(R.string.category_kind_business)
                } else {
                    root.context.getString(R.string.category_kind_service)
                }
                tvBody.text = "${root.context.getString(R.string.form_label_sort_order)}: ${PersianUtils.toPersianDigits(item.sortOrder)}"
                com.leon.be_nobat.helpers.StatusBadgeHelper.applyActiveStatus(tvStatus, item.active)
                com.leon.be_nobat.helpers.StatusBadgeHelper.applyActiveStatusIndicator(viewStatusIndicator, item.active)
                btnAction1.isVisible = true
                btnAction1.text = if (item.active) root.context.getString(R.string.set_inactive) else root.context.getString(R.string.set_active)
                btnAction1.setOnClickListener { onToggleActive(item) }

                btnAction2.isVisible = true
                btnAction2.text = root.context.getString(R.string.action_edit)
                btnAction2.setOnClickListener { onEdit(item) }
                root.setOnClickListener { onEdit(item) }
            }
        }
    }
}
