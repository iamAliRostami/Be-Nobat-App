package com.leon.be_nobat.ui.activities.auth

import android.content.Context
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.leon.be_nobat.domain.usecase.LogoutUseCase
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.leon.be_nobat.ui.activities.StartActivity

/**
 * Shared logout helper used by both customer and admin surfaces.
 *
 * Calling [logoutAndRestart] clears the local session and routes back to
 * StartActivity. The actual `/auth/logout` network call is best-effort —
 * we always clear locally so the user gets a working landing screen even
 * if the call fails (e.g. the token was already revoked server-side).
 */
object LogoutHelper {
    fun logoutAndRestart(context: Context) {
        val logoutUseCase by lazy {
            org.koin.core.context.GlobalContext.getOrNull()
                ?.get<LogoutUseCase>()
        }
        val scope = logoutUseCase?.let {
            kotlinx.coroutines.GlobalScope.launch { it() }
        }
        val intent = Intent(context, StartActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
