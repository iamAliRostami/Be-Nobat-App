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
import com.leon.be_nobat.domain.model.ManagementCatalog
import com.leon.be_nobat.domain.model.ManagementCatalogInput
import com.leon.be_nobat.helpers.AdminFormDialog
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.view_models.platform.PlatformCatalogUiState
import com.leon.be_nobat.ui.view_models.platform.PlatformCatalogViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlatformCatalogActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: PlatformCatalogViewModel by viewModel()
    private lateinit var adapter: CatalogAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val categoryOptions = listOf(
        "عمومی", "پزشکی و سلامت", "زیبایی و سلامت", "آموزش", "خدمات فنی", "ورزش", "مشاوره", "فروشگاهی", "سایر"
    )

    private val durationOptions = listOf(
        AdminFormDialog.DropdownOption("15", "۱۵ دقیقه"),
        AdminFormDialog.DropdownOption("30", "۳۰ دقیقه"),
        AdminFormDialog.DropdownOption("45", "۴۵ دقیقه"),
        AdminFormDialog.DropdownOption("60", "۱ ساعت"),
        AdminFormDialog.DropdownOption("90", "۱.۵ ساعت"),
        AdminFormDialog.DropdownOption("120", "۲ ساعت"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<TabLayout>(R.id.statusTabs).visibility = View.GONE
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.platform_catalog), currentMenuItemId = R.id.drawer_platform_catalog)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = CatalogAdapter(
            onEdit = { item -> showEditCatalogDialog(item) },
            onDelete = { item -> viewModel.delete(item.id) },
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

    private fun showCreateCatalogDialog() {
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_create_catalog_title))
            .field(getString(R.string.form_label_name))
            .field(getString(R.string.form_label_slug))
            .dropdownField(getString(R.string.form_label_category), categoryOptions, default = "عمومی")
            .field(getString(R.string.form_label_description))
            .dropdownField(getString(R.string.form_label_duration_minutes), durationOptions, default = "30")
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val slug = values.getOrNull(1).orEmpty()
                val category = values.getOrNull(2).orEmpty().ifBlank { "عمومی" }
                val description = values.getOrNull(3).orEmpty()
                val duration = values.getOrNull(4)?.toIntOrNull() ?: 30
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
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_edit_catalog_title))
            .field(getString(R.string.form_label_name), default = item.name)
            .field(getString(R.string.form_label_slug), default = item.slug)
            .dropdownField(getString(R.string.form_label_category), categoryOptions, default = item.category)
            .field(getString(R.string.form_label_description), default = item.description)
            .dropdownField(getString(R.string.form_label_duration_minutes), durationOptions, default = item.suggestedDurationMinutes.toString())
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val slug = values.getOrNull(1).orEmpty()
                val category = values.getOrNull(2).orEmpty().ifBlank { item.category }
                val description = values.getOrNull(3).orEmpty()
                val duration = values.getOrNull(4)?.toIntOrNull() ?: item.suggestedDurationMinutes
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
        state.error?.let { Snackbar.make(swipeRefresh, com.leon.be_nobat.helpers.ErrorFormatter.format(this, it), Snackbar.LENGTH_SHORT).show() }
    }

    private class CatalogAdapter(
        private val onEdit: (ManagementCatalog) -> Unit,
        private val onDelete: (ManagementCatalog) -> Unit,
    ) : BaseAdminAdapter<ManagementCatalog>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementCatalog) {
            holder.binding.apply {
                tvTitle.text = item.name
                val durationStr = "${PersianUtils.toPersianDigits(item.suggestedDurationMinutes)} دقیقه"
                tvSubtitle.text = "${item.category} • $durationStr"
                tvBody.text = item.description
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
