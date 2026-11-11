package com.leon.be_nobat.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.SystemMessageDto
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.view_models.admin.AdminSystemMessagesUiState
import com.leon.be_nobat.ui.view_models.admin.AdminSystemMessagesViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminSystemMessagesActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_list

    private val viewModel: AdminSystemMessagesViewModel by viewModel()
    private lateinit var adapter: SystemMessagesAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireBusinessManagementPermission()
    }

    override fun setupViews() {
        setupAdminHeader(
            getString(R.string.system_messages_title),
            currentMenuItemId = R.id.drawer_system_messages
        )

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        val rv = findViewById<RecyclerView>(R.id.list)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = SystemMessagesAdapter(
            onMarkRead = { msg -> viewModel.markAsRead(msg.id) }
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

    private fun render(state: AdminSystemMessagesUiState) {
        swipeRefresh.isRefreshing = state.loading
        adapter.submitList(state.messages)

        val emptyView = findViewById<TextView>(R.id.emptyStateText)
        emptyView?.isVisible = !state.loading && state.messages.isEmpty()
        emptyView?.text = "هیچ پیام یا اطلاعیه‌ای در سامانه ثبت نشده است."

        findViewById<View>(R.id.cardError)?.isVisible = state.error != null
        findViewById<TextView>(R.id.tvError)?.text = state.error
    }

    private fun formatDisplayDate(iso: String): String {
        val clean = iso.substringBefore(".").replace("Z", "")
        val parts = clean.split("T")
        return if (parts.size >= 2) "${parts[0]} - ${parts[1].take(5)}" else iso
    }

    inner class SystemMessagesAdapter(
        private val onMarkRead: (SystemMessageDto) -> Unit,
    ) : ListAdapter<SystemMessageDto, SystemMessagesAdapter.Holder>(
        object : DiffUtil.ItemCallback<SystemMessageDto>() {
            override fun areItemsTheSame(old: SystemMessageDto, new: SystemMessageDto) = old.id == new.id
            override fun areContentsTheSame(old: SystemMessageDto, new: SystemMessageDto) = old == new
        }
    ) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_system_message, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            private val tvTitle: TextView = v.findViewById(R.id.tvTitle)
            private val tvPriorityBadge: TextView = v.findViewById(R.id.tvPriorityBadge)
            private val tvUnreadBadge: TextView = v.findViewById(R.id.tvUnreadBadge)
            private val tvDate: TextView = v.findViewById(R.id.tvDate)
            private val tvContent: TextView = v.findViewById(R.id.tvContent)
            private val btnMarkRead: MaterialButton = v.findViewById(R.id.btnMarkRead)

            fun bind(item: SystemMessageDto) {
                tvTitle.text = item.title
                tvContent.text = item.content
                tvDate.text = formatDisplayDate(item.createdAt)

                tvUnreadBadge.isVisible = !item.isRead
                btnMarkRead.isVisible = !item.isRead

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

                btnMarkRead.setOnClickListener { onMarkRead(item) }
            }
        }
    }
}
