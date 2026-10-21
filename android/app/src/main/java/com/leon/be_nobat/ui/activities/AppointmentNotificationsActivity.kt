package com.leon.be_nobat.ui.activities

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.notifications.AppointmentNotificationMonitor
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AppointmentNotificationsActivity : AppCompatActivity() {
    private val monitor: AppointmentNotificationMonitor by inject()
    private val tokens: TokenManager by inject()
    private lateinit var rows: LinearLayout
    private lateinit var permission: MaterialButton
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { renderPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(this).apply {
            title = "اعلان‌های نوبت"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        content.addView(toolbar)
        permission = MaterialButton(this).apply {
            text = "فعال‌سازی اعلان گوشی"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 33 && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                    !getPreferences(MODE_PRIVATE).getBoolean("asked", false)) {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("asked", true).apply()
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        content.addView(permission)
        content.addView(MaterialButton(this).apply {
            text = "تازه‌سازی"
            setOnClickListener { refresh() }
        })
        content.addView(MaterialButton(this).apply {
            text = "نمایش نوبت‌ها"
            setOnClickListener { startActivity(Intent(this@AppointmentNotificationsActivity, AppointmentsActivity::class.java)) }
        })
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 24) }
        content.addView(ScrollView(this).apply { addView(rows) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        lifecycleScope.launch {
            val user = tokens.currentSession()?.user
            val targetAccount = intent.getStringExtra("account_id")
            if (user == null || !user.isCustomer || (targetAccount != null && targetAccount != user.id)) {
                com.leon.be_nobat.helpers.AppToast.show(this@AppointmentNotificationsActivity, "برای مشاهدهٔ اعلان‌ها با حساب نوبت‌گیرنده وارد شوید.")
                finish(); return@launch
            }
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                monitor.revision.collect {
                    if (tokens.currentSession()?.user?.id != user.id) { finish(); return@collect }
                    val notices = monitor.inbox()
                    rows.removeAllViews()
                    if (notices.isEmpty()) rows.addView(TextView(this@AppointmentNotificationsActivity).apply {
                        text = "هنوز تغییری در وضعیت نوبت‌های شما ثبت نشده است."
                        setPadding(12, 24, 12, 24)
                    })
                    notices.forEach { notice ->
                        rows.addView(TextView(this@AppointmentNotificationsActivity).apply {
                            text = "${notice.title}\n${notice.body}"
                            textSize = 15f; setPadding(16, 24, 16, 24)
                            setOnClickListener { startActivity(Intent(this@AppointmentNotificationsActivity, AppointmentsActivity::class.java)) }
                        }, ViewGroup.LayoutParams(-1, -2))
                    }
                    if (notices.any { !it.read }) monitor.markRead()
                }
            }
        }
        refresh()
    }
    override fun onResume() { super.onResume(); if (::permission.isInitialized) renderPermission() }
    private fun renderPermission() {
        permission.visibility = if (NotificationManagerCompat.from(this).areNotificationsEnabled()) android.view.View.GONE else android.view.View.VISIBLE
    }
    private fun refresh() = lifecycleScope.launch {
        try { monitor.sync() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { com.leon.be_nobat.helpers.AppToast.show(this@AppointmentNotificationsActivity, "بررسی نوبت‌ها انجام نشد؛ اتصال را بررسی کنید.") }
    }
}
