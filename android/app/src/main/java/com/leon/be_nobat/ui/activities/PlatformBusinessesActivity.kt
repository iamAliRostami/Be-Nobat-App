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
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementBusinessInput
import com.leon.be_nobat.helpers.AdminFormDialog
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.view_models.platform.PlatformBusinessesUiState
import com.leon.be_nobat.ui.view_models.platform.PlatformBusinessesViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlatformBusinessesActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: PlatformBusinessesViewModel by viewModel()
    private lateinit var adapter: BusinessesAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val categoryOptions = listOf(
        "عمومی", "پزشکی و سلامت", "زیبایی و سلامت", "آموزش", "خدمات فنی", "ورزش", "مشاوره", "فروشگاهی", "رستوران و کافه", "سایر"
    )

    private val cityOptions = listOf(
        "تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "قم", "اهواز", "رشت", "کرمانشاه", "یزد", "سایر"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<TabLayout>(R.id.statusTabs).visibility = View.GONE
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.platform_businesses), currentMenuItemId = R.id.drawer_platform_businesses)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = BusinessesAdapter(
            onToggle = { biz -> viewModel.toggleActive(biz.id, !biz.active) },
            onEdit = { biz -> showEditBusinessDialog(biz) },
            onDelete = { biz -> viewModel.delete(biz.id) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@PlatformBusinessesActivity)
            this.adapter = this@PlatformBusinessesActivity.adapter
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd).apply {
            visibility = View.VISIBLE
            setOnClickListener { showCreateBusinessDialog() }
        }
    }

    private fun showCreateBusinessDialog() {
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_create_business_title))
            .field(getString(R.string.form_label_name))
            .field(getString(R.string.form_label_slug))
            .dropdownField(getString(R.string.form_label_category), categoryOptions, default = "عمومی")
            .dropdownField(getString(R.string.form_label_city), cityOptions, default = "تهران")
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val slug = values.getOrNull(1).orEmpty()
                val category = values.getOrNull(2).orEmpty().ifBlank { "عمومی" }
                val city = values.getOrNull(3).orEmpty().ifBlank { "تهران" }
                if (name.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.create(ManagementBusinessInput(
                    name = name,
                    slug = slug,
                    category = category,
                    city = city,
                ))
            }
            .show()
    }

    private fun showEditBusinessDialog(item: ManagementBusiness) {
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_edit_business_title))
            .field(getString(R.string.form_label_name), default = item.name)
            .field(getString(R.string.form_label_slug), default = item.slug)
            .dropdownField(getString(R.string.form_label_category), categoryOptions, default = item.category)
            .dropdownField(getString(R.string.form_label_city), cityOptions, default = item.city)
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val slug = values.getOrNull(1).orEmpty()
                val category = values.getOrNull(2).orEmpty().ifBlank { item.category }
                val city = values.getOrNull(3).orEmpty().ifBlank { item.city }
                if (name.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.update(item.id, ManagementBusinessInput(
                    name = name,
                    slug = slug,
                    category = category,
                    city = city,
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

    private fun render(state: PlatformBusinessesUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)
        state.error?.let { Snackbar.make(swipeRefresh, com.leon.be_nobat.helpers.ErrorFormatter.format(this, it), Snackbar.LENGTH_SHORT).show() }
    }

    private class BusinessesAdapter(
        private val onToggle: (ManagementBusiness) -> Unit,
        private val onEdit: (ManagementBusiness) -> Unit,
        private val onDelete: (ManagementBusiness) -> Unit,
    ) : BaseAdminAdapter<ManagementBusiness>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementBusiness) {
            holder.binding.apply {
                tvTitle.text = item.name
                tvSubtitle.text = "${item.category} • ${item.city}"
                tvBody.text = item.description
                tvStatus.text = if (item.active) root.context.getString(R.string.set_active) else root.context.getString(R.string.set_inactive)
                btnAction1.text = if (item.active) root.context.getString(R.string.set_inactive) else root.context.getString(R.string.set_active)
                btnAction2.isVisible = true
                btnAction2.text = root.context.getString(R.string.action_edit)
                btnAction1.setOnClickListener { onToggle(item) }
                btnAction2.setOnClickListener { onEdit(item) }
                root.setOnClickListener { onEdit(item) }
            }
        }
    }
}
