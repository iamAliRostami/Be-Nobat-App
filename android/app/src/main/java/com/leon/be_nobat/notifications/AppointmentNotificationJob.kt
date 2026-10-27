package com.leon.be_nobat.notifications

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

class AppointmentNotificationJob : JobService() {
    private val monitor: AppointmentNotificationMonitor by inject()
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var retry = false
            try { monitor.sync() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { retry = true }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    override fun onDestroy() { work?.cancel(); super.onDestroy() }
}
