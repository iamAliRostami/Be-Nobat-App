package com.leon.be_nobat.ui.activities

import android.os.Bundle
import android.view.View
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
        findViewById<TabLayout>(R.id.statusTabs).visibility = View.GONE
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.platform_categories), currentMenuItemId = R.id.drawer_platform_categories)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = CategoriesAdapter(
            onEdit = { category -> showEditCategoryDialog(category) },
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
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)
    }

    private class CategoriesAdapter(
        private val onEdit: (ManagementCategory) -> Unit,
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
                tvStatus.text = if (item.active) root.context.getString(R.string.set_active) else root.context.getString(R.string.set_inactive)
                btnAction1.isVisible = false
                btnAction2.isVisible = true
                btnAction2.text = root.context.getString(R.string.action_edit)
                btnAction2.setOnClickListener { onEdit(item) }
                root.setOnClickListener { onEdit(item) }
            }
        }
    }
}
