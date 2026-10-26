package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.helpers.DeepLinkHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class StartActivity : AppCompatActivity() {
    companion object {
        private const val DEFAULT_LANGUAGE_TAG = "fa"
    }

    private val tokenManager: TokenManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setDefaultLocaleIfNeeded()
        lifecycleScope.launch {
            val deepLinkUri = intent?.data
            val target = if (deepLinkUri != null) {
                val parsed = DeepLinkHelper.parse(deepLinkUri)
                if (parsed != null && (!parsed.businessId.isNullOrBlank() || !parsed.branchId.isNullOrBlank())) {
                    if (parsed.isBookingRoute && !parsed.businessId.isNullOrBlank()) {
                        Intent(this@StartActivity, BookingActivity::class.java).apply {
                            putExtra("businessId", parsed.businessId)
                            parsed.branchId?.let { putExtra("branchId", it) }
                        }
                    } else {
                        // Scenario 2: Show business profile and details first, preserving branch filter
                        Intent(this@StartActivity, BusinessDetailActivity::class.java).apply {
                            parsed.businessId?.let { putExtra("businessId", it) }
                            parsed.branchId?.let { putExtra("branchId", it) }
                        }
                    }
                } else null
            } else null

            if (target != null) {
                startActivity(target)
                finish()
                return@launch
            }

            val session = tokenManager.currentSession()
            val defaultTarget = if (session != null) {
                if (session.user.isStaff) {
                    Intent(this@StartActivity, AdminHomeActivity::class.java)
                } else {
                    Intent(this@StartActivity, HomeActivity::class.java)
                }
            } else {
                Intent(this@StartActivity, LoginActivity::class.java)
            }
            startActivity(defaultTarget)
            finish()
        }
    }

    private fun setDefaultLocaleIfNeeded() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(DEFAULT_LANGUAGE_TAG)
            )
        }
    }
}
