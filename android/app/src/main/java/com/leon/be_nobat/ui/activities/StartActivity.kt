package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.helpers.DeepLinkHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class StartActivity : AppCompatActivity() {
    companion object {
        private const val DEFAULT_LANGUAGE_TAG = "fa"
        private const val STANDARD_SPLASH_DURATION_MS = 2300L
    }

    private val tokenManager: TokenManager by inject()
    private var isNavigated = false
    private var resolvedTarget: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }
        super.onCreate(savedInstanceState)
        setDefaultLocaleIfNeeded()

        // Edge-to-edge transparent system bars for seamless gradient background
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContentView(R.layout.activity_start)

        startSplashAnimations()

        // Allow tapping anywhere to skip the remaining splash duration
        findViewById<View>(R.id.splashRoot)?.setOnClickListener {
            resolvedTarget?.let { target -> navigateToTarget(target) }
        }

        lifecycleScope.launch {
            // Resolve target route in background while animations run
            val target = resolveTargetIntent()
            resolvedTarget = target

            // Keep standard splash screen visible for standard duration
            delay(STANDARD_SPLASH_DURATION_MS)

            navigateToTarget(target)
        }
    }

    private fun startSplashAnimations() {
        val ivEmblem = findViewById<ImageView>(R.id.ivSplashEmblem)
        val ivRing = findViewById<ImageView>(R.id.ivSplashRing)
        val ivTitle = findViewById<ImageView>(R.id.ivSplashTitle)
        val ivSubtitle = findViewById<ImageView>(R.id.ivSplashSubtitle)
        val ivFeatures = findViewById<ImageView>(R.id.ivSplashFeatures)

        // 1. Red marked part: Central Emblem (zooms in from 0.15 scale with overshoot)
        ivEmblem.scaleX = 0.15f
        ivEmblem.scaleY = 0.15f
        ivEmblem.alpha = 0f

        // 2. Blue marked part: Glowing Crescent Ring (rotates around the logo)
        ivRing.alpha = 0f
        ivRing.rotation = -120f

        // 3. Yellow marked parts: Staggered slide up from bottom
        val slideOffset = 50f * resources.displayMetrics.density
        ivTitle.translationY = slideOffset
        ivTitle.alpha = 0f

        ivSubtitle.translationY = slideOffset
        ivSubtitle.alpha = 0f

        ivFeatures.translationY = slideOffset
        ivFeatures.alpha = 0f

        // Execute Animations:
        // A) Emblem Zoom-In
        ivEmblem.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(900)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()

        // B) Ring Rotation around Logo
        ivRing.animate()
            .rotation(360f)
            .alpha(1.0f)
            .setDuration(1600)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()

        // C) Bottom items slide up part by part
        // Part 1: Typography "به‌نوبت"
        ivTitle.animate()
            .translationY(0f)
            .alpha(1.0f)
            .setStartDelay(550)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Part 2: Subtitle "سامانه هوشمند نوبت‌دهی آنلاین"
        ivSubtitle.animate()
            .translationY(0f)
            .alpha(1.0f)
            .setStartDelay(800)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Part 3: Feature Badges (مدیریت هوشمند | مطمئن و امن | سریع و آسان)
        ivFeatures.animate()
            .translationY(0f)
            .alpha(1.0f)
            .setStartDelay(1050)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private suspend fun resolveTargetIntent(): Intent {
        val deepLinkUri = intent?.data
        if (deepLinkUri != null) {
            val parsed = DeepLinkHelper.parse(deepLinkUri)
            if (parsed != null && (!parsed.businessId.isNullOrBlank() || !parsed.branchId.isNullOrBlank() || !parsed.providerId.isNullOrBlank())) {
                val businessRepository: com.leon.be_nobat.domain.repository.BusinessRepository =
                    org.koin.java.KoinJavaComponent.get(com.leon.be_nobat.domain.repository.BusinessRepository::class.java)
                var resolvedBizId = parsed.businessId
                var resolvedBranchId = parsed.branchId

                if (resolvedBizId.isNullOrBlank() && !parsed.providerId.isNullOrBlank()) {
                    val providerInfo = businessRepository.findBusinessIdByProvider(parsed.providerId)
                    if (providerInfo != null) {
                        resolvedBizId = providerInfo.first
                        resolvedBranchId = providerInfo.second
                    }
                }

                if (resolvedBizId.isNullOrBlank() && !resolvedBranchId.isNullOrBlank()) {
                    resolvedBizId = businessRepository.findBusinessIdByBranch(resolvedBranchId)
                }

                if (parsed.isBookingRoute && !resolvedBizId.isNullOrBlank()) {
                    return Intent(this, BookingActivity::class.java).apply {
                        putExtra("businessId", resolvedBizId)
                        resolvedBranchId?.let { putExtra("branchId", it) }
                        parsed.providerId?.let { putExtra("providerId", it) }
                    }
                } else if (!resolvedBizId.isNullOrBlank()) {
                    return Intent(this, BusinessDetailActivity::class.java).apply {
                        putExtra("businessId", resolvedBizId)
                        resolvedBranchId?.let { putExtra("branchId", it) }
                        parsed.providerId?.let { putExtra("providerId", it) }
                    }
                }
            }
        }

        val session = tokenManager.currentSession()
        return if (session != null) {
            if (session.user.isStaff) {
                Intent(this, AdminHomeActivity::class.java)
            } else {
                Intent(this, HomeActivity::class.java)
            }
        } else {
            Intent(this, LoginActivity::class.java)
        }
    }

    private fun navigateToTarget(target: Intent) {
        if (isNavigated || isFinishing || isDestroyed) return
        isNavigated = true
        startActivity(target)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun setDefaultLocaleIfNeeded() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(DEFAULT_LANGUAGE_TAG)
            )
        }
    }
}
