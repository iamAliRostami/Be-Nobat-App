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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.databinding.ItemAdminAppointmentBinding
import com.leon.be_nobat.domain.model.ManagementUser
import com.leon.be_nobat.domain.model.ManagementUserInput
import com.leon.be_nobat.helpers.AdminFormDialog
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.view_models.platform.PlatformAccessUiState
import com.leon.be_nobat.ui.view_models.platform.PlatformAccessViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlatformAccessActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: PlatformAccessViewModel by viewModel()
    private lateinit var adapter: UsersAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<TabLayout>(R.id.statusTabs).visibility = View.GONE
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.platform_access), currentMenuItemId = R.id.drawer_platform_access)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = UsersAdapter(
            onToggleActive = { user -> viewModel.toggleActive(user.id, !user.active) },
            onSetRole = { user -> showRoleDialog(user) },
            onResetPassword = { user -> viewModel.resetPassword(user.id) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@PlatformAccessActivity)
            this.adapter = this@PlatformAccessActivity.adapter
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd).apply {
            visibility = View.VISIBLE
            setOnClickListener { showCreateUserDialog() }
        }
    }

    private fun showCreateUserDialog() {
        val roleOptions = listOf(
            AdminFormDialog.DropdownOption("Customer", getString(R.string.role_customer)),
            AdminFormDialog.DropdownOption("PlatformAdmin", getString(R.string.role_platform_admin)),
        )
        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.form_create_user_title))
            .field(getString(R.string.form_label_name))
            .field(getString(R.string.form_label_email), inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            .field(getString(R.string.form_label_phone), inputType = android.text.InputType.TYPE_CLASS_PHONE)
            .dropdownField(getString(R.string.form_label_role), roleOptions, default = "Customer")
            .onSubmit { values ->
                val name = values.getOrNull(0).orEmpty()
                val email = values.getOrNull(1).orEmpty()
                val phone = values.getOrNull(2).orEmpty()
                val role = values.getOrNull(3).orEmpty().ifBlank { "Customer" }
                if (name.isBlank() || email.isBlank() || phone.isBlank()) {
                    Snackbar.make(findViewById(R.id.swipeRefresh), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show()
                    return@onSubmit
                }
                viewModel.createUser(ManagementUserInput(
                    displayName = name,
                    email = email,
                    phoneNumber = phone,
                    role = role,
                ))
            }
            .show()
    }

    private fun showRoleDialog(user: ManagementUser) {
        val roles = listOf("Customer", "PlatformAdmin")
        val labels = roles.map { if (it == "Customer") getString(R.string.role_customer) else getString(R.string.role_platform_admin) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.platform_access)
            .setItems(labels) { _, which ->
                viewModel.setRole(user.id, roles[which])
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

    private fun render(state: PlatformAccessUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.items)

        state.temporaryPassword?.let { pwd ->
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.temporary_password)
                .setMessage(getString(R.string.temporary_password, pwd))
                .setPositiveButton(R.string.action_close) { _, _ -> viewModel.clearMessage() }
                .show()
        }
        state.error?.let { Snackbar.make(swipeRefresh, com.leon.be_nobat.helpers.ErrorFormatter.format(this, it), Snackbar.LENGTH_SHORT).show() }
    }

    private class UsersAdapter(
        private val onToggleActive: (ManagementUser) -> Unit,
        private val onSetRole: (ManagementUser) -> Unit,
        private val onResetPassword: (ManagementUser) -> Unit,
    ) : BaseAdminAdapter<ManagementUser>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementUser) {
            holder.binding.apply {
                tvTitle.text = item.displayName
                tvSubtitle.text = item.email ?: ""
                val localizedRoles = item.roles.map { role ->
                    when (role) {
                        "Customer" -> root.context.getString(R.string.role_customer)
                        "PlatformAdmin" -> root.context.getString(R.string.role_platform_admin)
                        "Owner", "Manager" -> root.context.getString(R.string.role_manager)
                        "Staff" -> root.context.getString(R.string.role_staff)
                        else -> role
                    }
                }.joinToString("، ")
                tvBody.text = "${root.context.getString(R.string.form_label_role)}: $localizedRoles"
                tvStatus.text = if (item.active) root.context.getString(R.string.set_active) else root.context.getString(R.string.set_inactive)
                btnAction1.text = if (item.active) root.context.getString(R.string.set_inactive) else root.context.getString(R.string.set_active)
                btnAction2.text = root.context.getString(R.string.reset_password)
                btnAction1.setOnClickListener { onToggleActive(item) }
                btnAction2.setOnClickListener { onResetPassword(item) }
                root.setOnClickListener { onSetRole(item) }
                root.setOnLongClickListener { onSetRole(item); true }
            }
        }
    }
}
