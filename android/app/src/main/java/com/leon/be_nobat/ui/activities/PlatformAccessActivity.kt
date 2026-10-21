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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
        requirePlatformAdminPermission()
    }

    private val smsPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        viewModel.smsPermissionPending = false
        sendPasswordSms()
    }

    private fun processPasswordSms() {
        val pending = viewModel.passwordSms.value ?: return
        if (viewModel.smsPermissionPending) return
        if (com.leon.be_nobat.BuildConfig.DEMO_MODE ||
            com.leon.be_nobat.notifications.AppointmentMessages.phone(pending.phoneNumber) == null ||
            !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY) ||
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            sendPasswordSms()
        } else {
            viewModel.smsPermissionPending = true
            com.leon.be_nobat.helpers.AppToast.show(this, "برای ارسال رمز جدید با سیم‌کارت پیش‌فرض گوشی، مجوز پیامک لازم است؛ هزینه طبق تعرفهٔ سیم‌کارت است.")
            smsPermission.launch(android.Manifest.permission.SEND_SMS)
        }
    }

    private fun sendPasswordSms() {
        // Consume before telephony handoff to avoid sending again after Activity recreation.
        val pending = viewModel.takePasswordSms() ?: return
        val result = com.leon.be_nobat.notifications.DeviceSms.send(this, pending.phoneNumber, pending.body(), "رمز بازنشانی شد")
        MaterialAlertDialogBuilder(this)
            .setTitle("رمز موقت کاربر")
            .setMessage("$result\n\n${getString(R.string.temporary_password, pending.password)}")
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.platform_access), currentMenuItemId = R.id.drawer_platform_access)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        // Setup Tabs (همه، مشتریان، مدیران، پرسنل، مدیران سامانه، غیرفعال)
        val tabs = findViewById<TabLayout>(R.id.statusTabs)
        tabs.isVisible = true
        tabs.removeAllTabs()
        val userTabs = listOf(
            "" to getString(R.string.tab_users_all),
            "Customer" to getString(R.string.tab_users_customers),
            "Manager" to getString(R.string.tab_users_managers),
            "Staff" to getString(R.string.tab_users_staff),
            "PlatformAdmin" to getString(R.string.role_platform_admin),
            "Inactive" to getString(R.string.status_inactive),
        )
        userTabs.forEach { (key, label) ->
            tabs.addTab(tabs.newTab().setText(label).also { it.tag = key })
        }
        val currentTab = viewModel.state.value.selectedTab
        val initialTabIndex = userTabs.indexOfFirst { it.first == currentTab }
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
                hint = getString(R.string.search_users_hint)
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
                launch { viewModel.passwordSms.collect { processPasswordSms() } }
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: PlatformAccessUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.filteredItems.isEmpty()
        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.filteredItems)

        state.temporaryPassword?.takeIf { state.message != "reset_done" }?.let { pwd ->
            MaterialAlertDialogBuilder(this)
                .setTitle("رمز موقت کاربر")
                .setMessage(getString(R.string.temporary_password, pwd))
                .setPositiveButton(R.string.action_close) { _, _ -> viewModel.clearMessage() }
                .show()
        }
        state.error?.let {
            val formatted = com.leon.be_nobat.helpers.ErrorFormatter.resolve(this, it)
            val snackbar = Snackbar.make(swipeRefresh, formatted.description, Snackbar.LENGTH_LONG)
            if (formatted.isAuthError) {
                snackbar.setAction(R.string.action_login_again) {
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_RETURN_TARGET, this@PlatformAccessActivity::class.java.name)
                    }
                    startActivity(intent)
                }
            }
            snackbar.show()
        }
    }

    private class UsersAdapter(
        private val onToggleActive: (ManagementUser) -> Unit,
        private val onSetRole: (ManagementUser) -> Unit,
        private val onResetPassword: (ManagementUser) -> Unit,
    ) : BaseAdminAdapter<ManagementUser>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {
        override fun bindHolder(holder: AdminViewHolder, item: ManagementUser) {
            holder.binding.apply {
                com.leon.be_nobat.helpers.StatusBadgeHelper.applyActiveStatusIndicator(viewStatusIndicator, item.active)
                tvTitle.text = item.displayName
                val contacts = listOfNotNull(
                    item.email?.takeIf { it.isNotBlank() }?.let { "✉ $it" },
                    item.phoneNumber?.takeIf { it.isNotBlank() }?.let { "☎ $it" },
                ).joinToString("   •   ")
                tvSubtitle.text = contacts
                tvSubtitle.isVisible = contacts.isNotBlank()

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
                com.leon.be_nobat.helpers.StatusBadgeHelper.applyActiveStatus(tvStatus, item.active)
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
