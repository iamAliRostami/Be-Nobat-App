package com.leon.be_nobat.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ContactMessageDto
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.view_models.platform.PlatformMessagesUiState
import com.leon.be_nobat.ui.view_models.platform.PlatformMessagesViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlatformMessagesActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: PlatformMessagesViewModel by viewModel()
    private lateinit var adapter: ContactMessagesAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requirePlatformAdminPermission()
    }

    override fun setupViews() {
        setupAdminHeader(
            getString(R.string.platform_messages_title),
            currentMenuItemId = R.id.drawer_platform_messages
        )

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        // Setup Tabs (همه، خوانده‌نشده، بررسی‌شده)
        val tabs = findViewById<TabLayout>(R.id.statusTabs)
        tabs.isVisible = true
        tabs.removeAllTabs()
        val filterTabs = listOf(
            "" to "همه پیام‌ها",
            "unread" to "خوانده‌نشده",
            "read" to "بررسی‌شده",
        )
        filterTabs.forEach { (key, label) ->
            tabs.addTab(tabs.newTab().setText(label).also { it.tag = key })
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val key = (tab.tag as? String).orEmpty()
                viewModel.setSelectedTab(key)
                adapter.submitList(viewModel.state.value.filteredItems)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Setup Search
        findViewById<TextInputLayout>(R.id.adminSearchLayout)?.apply {
            isVisible = true
            findViewById<TextInputEditText>(R.id.adminSearchInput)?.apply {
                hint = getString(R.string.search)
                doAfterTextChanged { text ->
                    viewModel.setSearchQuery(text?.toString().orEmpty())
                    adapter.submitList(viewModel.state.value.filteredItems)
                }
            }
        }

        val rv = findViewById<RecyclerView>(R.id.list)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ContactMessagesAdapter(
            onToggleRead = { msg -> viewModel.markAsRead(msg.id, !msg.isRead) },
            onReply = { msg -> showReplyDialog(msg) },
            onDelete = { msg -> showDeleteConfirmDialog(msg) }
        )
        rv.adapter = adapter

        findViewById<View>(R.id.fabAdd)?.isVisible = false
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: PlatformMessagesUiState) {
        swipeRefresh.isRefreshing = state.loading
        adapter.submitList(state.filteredItems)

        val emptyView = findViewById<TextView>(R.id.emptyStateText)
        emptyView?.isVisible = !state.loading && state.filteredItems.isEmpty()
        emptyView?.text = "هیچ پیامی با این مشخصات یافت نشد."

        findViewById<View>(R.id.cardError)?.isVisible = state.error != null
        findViewById<TextView>(R.id.tvError)?.text = state.error

        state.successMessage?.let { showToast(it) }
    }

    private fun formatDisplayDate(iso: String): String {
        val clean = iso.substringBefore(".").replace("Z", "")
        val parts = clean.split("T")
        return if (parts.size >= 2) "${parts[0]} - ${parts[1].take(5)}" else iso
    }

    private fun showReplyDialog(msg: ContactMessageDto) {
        val input = EditText(this).apply {
            hint = getString(R.string.contact_reply_hint)
            setLines(4)
            setText(msg.adminReply.orEmpty())
            setPadding(32, 24, 32, 24)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("پاسخ به ${msg.name}")
            .setView(input)
            .setPositiveButton("ثبت و ارسال پاسخ") { _, _ ->
                val replyText = input.text.toString().trim()
                if (replyText.isNotEmpty()) {
                    viewModel.reply(msg.id, replyText)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showDeleteConfirmDialog(msg: ContactMessageDto) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage("آیا از حذف این پیام ارسالی توسط ${msg.name} اطمینان دارید؟")
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.delete(msg.id)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    inner class ContactMessagesAdapter(
        private val onToggleRead: (ContactMessageDto) -> Unit,
        private val onReply: (ContactMessageDto) -> Unit,
        private val onDelete: (ContactMessageDto) -> Unit,
    ) : ListAdapter<ContactMessageDto, ContactMessagesAdapter.Holder>(
        object : DiffUtil.ItemCallback<ContactMessageDto>() {
            override fun areItemsTheSame(old: ContactMessageDto, new: ContactMessageDto) = old.id == new.id
            override fun areContentsTheSame(old: ContactMessageDto, new: ContactMessageDto) = old == new
        }
    ) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_platform_contact_message, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            private val tvSenderName: TextView = v.findViewById(R.id.tvSenderName)
            private val tvContactInfo: TextView = v.findViewById(R.id.tvContactInfo)
            private val tvSubject: TextView = v.findViewById(R.id.tvSubject)
            private val tvStatusBadge: TextView = v.findViewById(R.id.tvStatusBadge)
            private val tvDate: TextView = v.findViewById(R.id.tvDate)
            private val tvMessage: TextView = v.findViewById(R.id.tvMessage)
            private val cardReply: View = v.findViewById(R.id.cardReply)
            private val tvAdminReply: TextView = v.findViewById(R.id.tvAdminReply)
            private val btnReply: MaterialButton = v.findViewById(R.id.btnReply)
            private val btnToggleRead: MaterialButton = v.findViewById(R.id.btnToggleRead)
            private val btnDelete: MaterialButton = v.findViewById(R.id.btnDelete)

            fun bind(item: ContactMessageDto) {
                tvSenderName.text = item.name
                tvContactInfo.text = item.contactInfo
                tvSubject.text = item.subject
                tvMessage.text = item.message
                tvDate.text = formatDisplayDate(item.createdAt)

                if (item.isRead) {
                    tvStatusBadge.text = "بررسی‌شده"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_drawer_badge_teal)
                    btnToggleRead.text = "علامت به عنوان نخوانده"
                } else {
                    tvStatusBadge.text = "خوانده‌نشده"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_drawer_badge_red)
                    btnToggleRead.text = "علامت به عنوان بررسی‌شده"
                }

                if (!item.adminReply.isNullOrBlank()) {
                    cardReply.isVisible = true
                    tvAdminReply.text = item.adminReply
                } else {
                    cardReply.isVisible = false
                }

                btnToggleRead.setOnClickListener { onToggleRead(item) }
                btnReply.setOnClickListener { onReply(item) }
                btnDelete.setOnClickListener { onDelete(item) }
            }
        }
    }
}
