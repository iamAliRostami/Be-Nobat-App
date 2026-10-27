package com.leon.be_nobat.notifications

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.leon.be_nobat.BuildConfig
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.domain.model.Appointment
import com.leon.be_nobat.domain.repository.BookingRepository
import com.leon.be_nobat.ui.activities.AppointmentNotificationsActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

class AppointmentNotificationMonitor(private val context: Context, private val tokens: TokenManager,
    private val bookings: BookingRepository) {
    private val store = AppointmentNoticeStore(context)
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private var foreground: Job? = null
    private var started = 0
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    fun start(application: Application) {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,
            "تغییر وضعیت نوبت‌ها", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تأیید، لغو و سایر تغییرات وضعیت نوبت‌ها"
        })
        scope.launch {
            var previousId: String? = null
            tokens.user.distinctUntilChanged().collectLatest { user ->
                val scheduler = context.getSystemService(JobScheduler::class.java)
                if (previousId != user?.id) manager.cancelAll()
                previousId = user?.id
                if (user == null) {
                    scheduler.cancel(JOB_ID)
                } else {
                    if (scheduler.getPendingJob(JOB_ID) == null) {
                        runCatching {
                            val job = JobInfo.Builder(JOB_ID, ComponentName(context, AppointmentNotificationJob::class.java))
                                .setPeriodic(15 * 60 * 1000L).setPersisted(true)
                            if (!BuildConfig.DEMO_MODE) job.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            scheduler.schedule(job.build())
                        }
                    }
                    try { sync() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                }
                _revision.value++
            }
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) foreground = scope.launch {
                    while (isActive) {
                        try { sync() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                        delay(30_000.milliseconds)
                    }
                }
            }
            override fun onActivityStopped(activity: Activity) { if (--started == 0) foreground?.cancel() }
            override fun onActivityCreated(activity: Activity, state: Bundle?) { }
            override fun onActivityResumed(activity: Activity) { }
            override fun onActivityPaused(activity: Activity) { }
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) { }
            override fun onActivityDestroyed(activity: Activity) { }
        })
    }

    suspend fun sync() = lock.withLock {
        val session = tokens.currentSession() ?: return@withLock
                val appointments = mutableListOf<Appointment>()
        var page = 1
        do {
            val result = bookings.listAppointments(page = page, pageSize = 100).getOrThrow()
            appointments += result.items
            page++
            if (appointments.size < result.total && result.items.isEmpty()) throw java.io.IOException("Incomplete appointment page")
            val more = appointments.size < result.total
        } while (more)
        // A request started under a previous session must never write or post into the new account.
        if (tokens.currentSession()?.accessToken != session.accessToken) return@withLock
        val previous = store.read(session.user.id)
        var next = previous.update(appointments, System.currentTimeMillis())
        if (next != previous) store.write(session.user.id, next)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val delivered = mutableSetOf<String>()
            for (notice in next.notices.filter { !it.posted && !it.read }) {
                if (tokens.currentSession()?.accessToken != session.accessToken) break
                if (post(session.user.id, notice)) delivered += notice.id
            }
            if (delivered.isNotEmpty()) {
                next = next.copy(notices = next.notices.map { if (it.id in delivered) it.copy(posted = true) else it })
                store.write(session.user.id, next)
            }
        }
        if (next != previous) _revision.value++
    }

    suspend fun inbox(): List<AppointmentNotice> = lock.withLock {
        val user = tokens.currentSession()?.user ?: return@withLock emptyList()
                store.read(user.id).notices
    }

    suspend fun markRead() = lock.withLock {
        val user = tokens.currentSession()?.user ?: return@withLock
        val state = store.read(user.id)
        store.write(user.id, state.copy(notices = state.notices.map { it.copy(read = true) }))
        manager.cancelAll()
        _revision.value++
    }

    @Suppress("MissingPermission")
    private fun post(userId: String, notice: AppointmentNotice): Boolean {
        val intent = Intent(context, AppointmentNotificationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = "benobat-notice://${notice.id}".toUri()
            putExtra("account_id", userId)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_appointment_notification).setContentTitle(notice.title).setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body)).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return try { manager.notify(notice.id, 0, notification); true } catch (_: SecurityException) { false }
    }

    companion object { const val JOB_ID = 8017; const val CHANNEL = "appointment_status" }
}