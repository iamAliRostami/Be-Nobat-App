package com.leon.be_nobat.ui.activities

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.helpers.AppToast
import com.leon.be_nobat.helpers.JalaliDateHelper
import com.leon.be_nobat.notifications.AppointmentNotice
import com.leon.be_nobat.notifications.AppointmentNotificationMonitor
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AppointmentNotificationsActivity : AppCompatActivity() {
    private val monitor: AppointmentNotificationMonitor by inject()
    private val tokens: TokenManager by inject()

    private lateinit var cardPermission: MaterialCardView
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var recyclerNotices: RecyclerView
    private lateinit var layoutEmptyState: View
    private val adapter = NoticesAdapter {
        startActivity(Intent(this, AppointmentsActivity::class.java))
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        renderPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appointment_notifications)

        val root = findViewById<View>(R.id.rootNotifications)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        cardPermission = findViewById(R.id.cardPermission)
        findViewById<View>(R.id.btnEnableNotifications).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                !getPreferences(MODE_PRIVATE).getBoolean("asked", false)) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("asked", true).apply()
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }

        findViewById<View>(R.id.btnHeaderRefresh).setOnClickListener { refresh() }
                findViewById<View>(R.id.btnViewAppointments).setOnClickListener {
            startActivity(Intent(this, AppointmentsActivity::class.java))
        }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
        swipeRefresh.setOnRefreshListener { refresh() }

        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        recyclerNotices = findViewById(R.id.recyclerNotices)
        recyclerNotices.layoutManager = LinearLayoutManager(this)
        recyclerNotices.adapter = adapter

        lifecycleScope.launch {
            if (tokens.currentSession() == null) {
                val intent = Intent(this@AppointmentNotificationsActivity, LoginActivity::class.java).apply {
                    putExtra(LoginActivity.EXTRA_RETURN_TARGET, AppointmentNotificationsActivity::class.java.name)
                }
                startActivity(intent)
                finish()
                return@launch
            }

            val user = tokens.currentSession()?.user ?: return@launch
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                monitor.revision.collect {
                    val currentSession = tokens.currentSession()
                    if (currentSession == null || currentSession.user.id != user.id) {
                        finish()
                        return@collect
                    }
                    val notices = monitor.inbox()
                    adapter.submitList(notices)
                    layoutEmptyState.visibility = if (notices.isEmpty()) View.VISIBLE else View.GONE
                    recyclerNotices.visibility = if (notices.isEmpty()) View.GONE else View.VISIBLE
                    if (notices.any { !it.read }) monitor.markRead()
                }
            }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        renderPermission()
    }

    private fun renderPermission() {
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        cardPermission.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun refresh() = lifecycleScope.launch {
        swipeRefresh.isRefreshing = true
        try {
            monitor.sync()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            AppToast.show(this@AppointmentNotificationsActivity, "بررسی نوبت‌ها انجام نشد؛ اتصال را بررسی کنید.")
        } finally {
            swipeRefresh.isRefreshing = false
        }
    }

    private class NoticesAdapter(
        private val onItemClick: (AppointmentNotice) -> Unit
    ) : RecyclerView.Adapter<NoticeViewHolder>() {
        private var items = listOf<AppointmentNotice>()

        fun submitList(newItems: List<AppointmentNotice>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoticeViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_appointment_notice, parent, false)
            return NoticeViewHolder(view, onItemClick)
        }

        override fun onBindViewHolder(holder: NoticeViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private class NoticeViewHolder(
        itemView: View,
        private val onItemClick: (AppointmentNotice) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvNoticeTitle)
        private val tvBody: TextView = itemView.findViewById(R.id.tvNoticeBody)
        private val tvDate: TextView = itemView.findViewById(R.id.tvNoticeDate)
        private val dotUnread: View = itemView.findViewById(R.id.dotUnread)

        fun bind(notice: AppointmentNotice) {
            tvTitle.text = notice.title
            tvBody.text = notice.body
            tvDate.text = formatNoticeDate(notice.createdAt)
            dotUnread.visibility = if (!notice.read) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onItemClick(notice) }
        }

        private fun formatNoticeDate(timeMs: Long): String {
            if (timeMs <= 0) return ""
            val diff = System.currentTimeMillis() - timeMs
            return when {
                diff < 60_000 -> "همین الان"
                diff < 3600_000 -> "${diff / 60_000} دقیقه پیش"
                diff < 86400_000 -> "${diff / 3600_000} ساعت پیش"
                else -> {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
                    val j = JalaliDateHelper.gregorianToJalali(
                        cal.get(java.util.Calendar.YEAR),
                        cal.get(java.util.Calendar.MONTH) + 1,
                        cal.get(java.util.Calendar.DAY_OF_MONTH)
                    )
                    val time = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                    "${j.year}/${j.month}/${j.day} ساعت $time"
                }
            }
        }
    }
}
