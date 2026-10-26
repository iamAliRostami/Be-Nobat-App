package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementPerson
import com.leon.be_nobat.helpers.AdminFormDialog
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.helpers.StatusBadgeHelper
import com.leon.be_nobat.ui.adapters.AdminViewHolder
import com.leon.be_nobat.ui.adapters.BaseAdminAdapter
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.view_models.admin.AdminTeamUiState
import com.leon.be_nobat.ui.view_models.admin.AdminTeamViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminTeamActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: AdminTeamViewModel by viewModel()
    private lateinit var adapter: TeamAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusTabs: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireBusinessManagementPermission()
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.tab_team), currentMenuItemId = R.id.drawer_team)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        // Setup Search Input
        val searchLayout = findViewById<TextInputLayout>(R.id.adminSearchLayout)
        val searchInput = findViewById<TextInputEditText>(R.id.adminSearchInput)
        searchLayout.isVisible = true
        searchInput.hint = getString(R.string.search_people_hint)
        searchInput.doAfterTextChanged {
            viewModel.setQuery(it?.toString().orEmpty())
        }

        // Setup Status & Navigation Tabs
        statusTabs = findViewById(R.id.statusTabs)
        statusTabs.isVisible = true
        statusTabs.removeAllTabs()

        statusTabs.addTab(statusTabs.newTab().setText(R.string.all).also { it.tag = "all" })
        statusTabs.addTab(statusTabs.newTab().setText(R.string.status_active).also { it.tag = "active" })
        statusTabs.addTab(statusTabs.newTab().setText(R.string.status_inactive).also { it.tag = "disabled" })
        statusTabs.addTab(statusTabs.newTab().setText("${getString(R.string.tab_customers)} ↗").also { it.tag = "nav_customers" })

        statusTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.tag == "nav_customers") {
                    val intent = Intent(this@AdminTeamActivity, AdminCustomersActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                    startActivity(intent)
                    statusTabs.post {
                        when (viewModel.state.value.statusFilter) {
                            "active" -> statusTabs.getTabAt(1)?.select()
                            "disabled" -> statusTabs.getTabAt(2)?.select()
                            else -> statusTabs.getTabAt(0)?.select()
                        }
                    }
                } else {
                    val status = tab.tag as? String ?: "all"
                    viewModel.setStatusFilter(status)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Setup Floating Action Button
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.isVisible = true
        fabAdd.contentDescription = getString(R.string.add_team_member)
        fabAdd.setOnClickListener {
            showCreateTeamMemberDialog()
        }

        // Setup RecyclerView & Adapter
        adapter = TeamAdapter(
            onItemClick = { member -> showTeamMemberDetailsDialog(member) },
            onSendEmail = { member -> sendEmail(member.email) },
            onCallPhone = { member -> callPhone(member.phoneNumber) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@AdminTeamActivity)
            this.adapter = this@AdminTeamActivity.adapter
        }
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AdminTeamUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.filteredItems.isEmpty()

        val emptyText = findViewById<TextView>(R.id.emptyStateText)
        emptyText.isVisible = !state.loading && state.filteredItems.isEmpty()
        emptyText.setText(R.string.empty_team_filtered)

        renderAdminError(state.error) { viewModel.load() }
        adapter.submitList(state.filteredItems)

        state.message?.let { msg ->
            when (msg) {
                "team_member_created" -> {
                    Snackbar.make(swipeRefresh, R.string.team_member_created_success, Snackbar.LENGTH_SHORT).show()
                    viewModel.clearMessage()
                }
                "person_activated", "person_disabled" -> {
                    Snackbar.make(swipeRefresh, R.string.person_status_updated, Snackbar.LENGTH_SHORT).show()
                    viewModel.clearMessage()
                }
            }
        }
    }

    private fun sendEmail(email: String?) {
        if (email.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
        startActivity(intent)
    }

    private fun callPhone(phoneNumber: String?) {
        if (phoneNumber.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
        startActivity(intent)
    }

    private fun showTeamMemberDetailsDialog(member: ManagementPerson) {
        val phoneFormatted = member.phoneNumber?.takeIf { it.isNotBlank() }
            ?.let { PersianUtils.toPersianDigits(it) } ?: "ثبت نشده"
        val emailFormatted = member.email?.takeIf { it.isNotBlank() } ?: "—"
        val roleFormatted = getRoleDisplayName(member.role)
        val branchFormatted = member.branchName?.takeIf { it.isNotBlank() } ?: "—"
        val statusText = if (member.active) getString(R.string.status_active) else getString(R.string.status_inactive)

        val message = buildString {
            append("👤 ").append(getString(R.string.person_role_hint)).append(": ").append(roleFormatted).append("\n")
            append("🏢 ").append(getString(R.string.person_branch_hint)).append(": ").append(branchFormatted).append("\n")
            append("✉ ").append(getString(R.string.label_email, emailFormatted)).append("\n")
            append("☎ ").append(getString(R.string.label_phone, phoneFormatted)).append("\n")
            append("🏷 ").append(getString(R.string.label_status, statusText))
        }

        val toggleActionText = if (member.active) getString(R.string.set_inactive) else getString(R.string.set_active)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(member.displayName)
            .setMessage(message)
            .setNeutralButton(toggleActionText) { _, _ ->
                viewModel.toggleActive(member.id, !member.active)
            }
            .setNegativeButton(R.string.action_close, null)

        if (!member.phoneNumber.isNullOrBlank()) {
            builder.setPositiveButton(R.string.action_call) { _, _ ->
                callPhone(member.phoneNumber)
            }
        }

        builder.show()
    }

    private fun showCreateTeamMemberDialog() {
        val branches = viewModel.state.value.branches
        if (branches.isEmpty()) {
            Snackbar.make(swipeRefresh, R.string.error_branch_required, Snackbar.LENGTH_LONG).show()
            return
        }

        val branchOptions = branches.map {
            val label = if (!it.businessName.isNullOrBlank()) "${it.businessName} · ${it.name}" else it.name
            AdminFormDialog.DropdownOption(value = it.id, label = label)
        }

        val roleOptions = listOf(
            AdminFormDialog.DropdownOption(value = "Staff", label = "پرسنل"),
            AdminFormDialog.DropdownOption(value = "Manager", label = "مدیر"),
            AdminFormDialog.DropdownOption(value = "Owner", label = "مالک"),
        )

        AdminFormDialog.Builder(this)
            .setTitle(getString(R.string.add_team_member))
            .field(getString(R.string.person_name_hint))
            .field(
                label = getString(R.string.person_phone_hint),
                inputType = android.text.InputType.TYPE_CLASS_PHONE,
            )
            .field(
                label = getString(R.string.person_email_hint),
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            )
            .dropdownField(getString(R.string.person_branch_hint), branchOptions)
            .dropdownField(getString(R.string.person_role_hint), roleOptions)
            .validate { values ->
                val name = values.getOrNull(0)?.trim().orEmpty()
                val phone = values.getOrNull(1)?.trim().orEmpty()
                val email = values.getOrNull(2)?.trim().orEmpty()
                val branchId = values.getOrNull(3)?.trim().orEmpty()

                if (name.isBlank() || phone.isBlank() || !phone.startsWith("09") || phone.length != 11) {
                    getString(R.string.error_name_phone_required)
                } else if (email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    getString(R.string.error_invalid_email)
                } else if (branchId.isBlank()) {
                    getString(R.string.error_branch_required)
                } else {
                    null
                }
            }
            .onSubmit { values ->
                val name = values[0].trim()
                val phone = values[1].trim()
                val email = values[2].trim().ifBlank { null }
                val branchId = values[3]
                val role = values[4]
                viewModel.createTeamMember(name, email, phone, branchId, role)
            }
            .show()
    }

    private class TeamAdapter(
        private val onItemClick: (ManagementPerson) -> Unit,
        private val onSendEmail: (ManagementPerson) -> Unit,
        private val onCallPhone: (ManagementPerson) -> Unit,
    ) : BaseAdminAdapter<ManagementPerson>(simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })) {

        override fun bindHolder(holder: AdminViewHolder, item: ManagementPerson) {
            holder.binding.apply {
                tvTitle.text = item.displayName
                StatusBadgeHelper.applyActiveStatus(tvStatus, item.active)

                val roleName = getRoleDisplayName(item.role)
                val branchName = item.branchName?.takeIf { it.isNotBlank() } ?: "شعبه"
                val emailStr = item.email?.takeIf { it.isNotBlank() } ?: "—"
                tvSubtitle.text = "$roleName · $branchName · ✉ $emailStr"

                val phoneStr = item.phoneNumber?.takeIf { it.isNotBlank() }?.let { PersianUtils.toPersianDigits(it) } ?: "شماره تماس ثبت نشده"
                tvBody.text = "☎ $phoneStr"

                // Action 1: Send Email (if email present)
                val hasEmail = !item.email.isNullOrBlank()
                btnAction1.isVisible = hasEmail
                if (hasEmail) {
                    btnAction1.text = root.context.getString(R.string.action_send_email)
                    btnAction1.setOnClickListener { onSendEmail(item) }
                }

                // Action 2: Call Phone (if phone present)
                val hasPhone = !item.phoneNumber.isNullOrBlank()
                btnAction2.isVisible = hasPhone
                if (hasPhone) {
                    btnAction2.text = root.context.getString(R.string.action_call)
                    btnAction2.setOnClickListener { onCallPhone(item) }
                }

                root.setOnClickListener { onItemClick(item) }
            }
        }
    }

    companion object {
        fun getRoleDisplayName(role: String?): String {
            return when (role?.lowercase()) {
                "owner", "مالک" -> "مالک"
                "manager", "مدیر" -> "مدیر"
                "staff", "پرسنل" -> "پرسنل"
                else -> role ?: "پرسنل"
            }
        }
    }
}
