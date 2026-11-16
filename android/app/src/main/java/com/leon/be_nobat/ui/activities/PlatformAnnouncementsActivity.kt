package com.leon.be_nobat.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.PlatformAnnouncementDto
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.view_models.platform.PlatformAnnouncementsUiState
import com.leon.be_nobat.ui.view_models.platform.PlatformAnnouncementsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlatformAnnouncementsActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: PlatformAnnouncementsViewModel by viewModel()
    private lateinit var adapter: AnnouncementsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requirePlatformAdminPermission()
    }

    override fun setupViews() {
        setupAdminHeader(
            getString(R.string.platform_announcements_title),
            currentMenuItemId = R.id.drawer_platform_announcements
        )

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        val rv = findViewById<RecyclerView>(R.id.list)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = AnnouncementsAdapter(
            onDelete = { item -> showDeleteConfirmDialog(item) }
        )
        rv.adapter = adapter

        // Setup FAB for creating new announcement
        findViewById<FloatingActionButton>(R.id.fabAdd)?.apply {
            isVisible = true
            contentDescription = getString(R.string.action_send_announcement)
            setOnClickListener { showSendAnnouncementDialog() }
        }
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: PlatformAnnouncementsUiState) {
        swipeRefresh.isRefreshing = state.loading
        adapter.submitList(state.items)

        val emptyView = findViewById<TextView>(R.id.emptyStateText)
        emptyView?.isVisible = !state.loading && state.items.isEmpty()
        emptyView?.text = "تاکنون هیچ اعلانی ارسال نشده است."

        findViewById<View>(R.id.cardError)?.isVisible = state.error != null
        findViewById<TextView>(R.id.tvError)?.text = state.error

        state.successMessage?.let { showToast(it) }
    }

    private fun formatDisplayDate(iso: String): String {
        return com.leon.be_nobat.helpers.AppDateFormatter.formatDateTime(iso, getCurrentLanguage())
    }

    private fun showSendAnnouncementDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_send_announcement, null)
        val etTitle = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etContent = view.findViewById<TextInputEditText>(R.id.etContent)
        val rgPriority = view.findViewById<RadioGroup>(R.id.rgPriority)
        val rgTarget = view.findViewById<RadioGroup>(R.id.rgTarget)
        val layoutTargetUser = view.findViewById<TextInputLayout>(R.id.layoutTargetUser)
        val etTargetUser = view.findViewById<TextInputEditText>(R.id.etTargetUser)

        val users = viewModel.state.value.users
        var selectedUserId: String? = null

        val userLabels = users.map { user ->
            val phone = user.phoneNumber.orEmpty().ifBlank { user.email ?: "بدون تماس" }
            val localizedPhone = if (getCurrentLanguage() == "en") phone else com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(phone)
            "${user.displayName} ($localizedPhone)"
        }

        val openUserPicker = {
            if (users.isEmpty()) {
                showToast("کاربری برای انتخاب یافت نشد.")
            } else {
                val currentIdx = users.indexOfFirst { it.id == selectedUserId }
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.select_target_user)
                    .setSingleChoiceItems(userLabels.toTypedArray(), if (currentIdx >= 0) currentIdx else 0) { dialog, which ->
                        val chosen = users[which]
                        selectedUserId = chosen.id
                        etTargetUser.setText(userLabels[which])
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
        }

        etTargetUser.setOnClickListener { openUserPicker() }
        layoutTargetUser.setEndIconOnClickListener { openUserPicker() }

        rgTarget.setOnCheckedChangeListener { _, checkedId ->
            layoutTargetUser.isVisible = checkedId == R.id.rbTargetSingle
            if (checkedId == R.id.rbTargetSingle && selectedUserId == null && users.isNotEmpty()) {
                selectedUserId = users.first().id
                etTargetUser.setText(userLabels.first())
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_send_announcement)
            .setView(view)
            .setPositiveButton(R.string.action_send_announcement) { _, _ ->
                val title = etTitle.text.toString().trim()
                val content = etContent.text.toString().trim()
                val priority = when (rgPriority.checkedRadioButtonId) {
                    R.id.rbUrgent -> "Urgent"
                    R.id.rbImportant -> "Important"
                    else -> "Normal"
                }

                val targetUserId = if (rgTarget.checkedRadioButtonId == R.id.rbTargetSingle) {
                    selectedUserId
                } else null

                if (title.isBlank()) {
                    showToast(getString(R.string.msg_invalid_input))
                    return@setPositiveButton
                }

                viewModel.sendAnnouncement(title, content, priority, targetUserId)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showDeleteConfirmDialog(item: PlatformAnnouncementDto) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage("آیا از حذف این پیام اطمینان دارید؟")
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.delete(item.id)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    inner class AnnouncementsAdapter(
        private val onDelete: (PlatformAnnouncementDto) -> Unit,
    ) : ListAdapter<PlatformAnnouncementDto, AnnouncementsAdapter.Holder>(
        object : DiffUtil.ItemCallback<PlatformAnnouncementDto>() {
            override fun areItemsTheSame(old: PlatformAnnouncementDto, new: PlatformAnnouncementDto) = old.id == new.id
            override fun areContentsTheSame(old: PlatformAnnouncementDto, new: PlatformAnnouncementDto) = old == new
        }
    ) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_platform_announcement, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            private val tvTitle: TextView = v.findViewById(R.id.tvTitle)
            private val tvPriorityBadge: TextView = v.findViewById(R.id.tvPriorityBadge)
            private val tvTargetUser: TextView = v.findViewById(R.id.tvTargetUser)
            private val tvDate: TextView = v.findViewById(R.id.tvDate)
            private val tvContent: TextView = v.findViewById(R.id.tvContent)
            private val btnDelete: MaterialButton = v.findViewById(R.id.btnDelete)

            fun bind(item: PlatformAnnouncementDto) {
                tvTitle.text = item.title
                tvContent.text = item.content
                tvDate.text = formatDisplayDate(item.createdAt)

                val isUrgent = item.priority.equals("Urgent", ignoreCase = true)
                val isImportant = item.priority.equals("Important", ignoreCase = true)

                when {
                    isUrgent -> {
                        tvPriorityBadge.text = getString(R.string.priority_urgent)
                        tvPriorityBadge.setBackgroundResource(R.drawable.bg_drawer_badge_red)
                    }
                    isImportant -> {
                        tvPriorityBadge.text = getString(R.string.priority_important)
                        tvPriorityBadge.setBackgroundResource(R.drawable.bg_drawer_badge_amber)
                    }
                    else -> {
                        tvPriorityBadge.text = getString(R.string.priority_normal)
                        tvPriorityBadge.setBackgroundResource(R.drawable.bg_drawer_badge_teal)
                    }
                }

                val targetText = if (!item.targetUserName.isNullOrBlank()) {
                    "گیرنده: ${item.targetUserName}"
                } else {
                    "گیرنده: تمام مدیران و پرسنل (همگانی)"
                }
                tvTargetUser.text = if (getCurrentLanguage() == "en") targetText else com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(targetText)

                btnDelete.setOnClickListener { onDelete(item) }
            }
        }
    }
}
