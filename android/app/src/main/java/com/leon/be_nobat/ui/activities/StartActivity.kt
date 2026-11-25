package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.leon.be_nobat.BuildConfig
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.helpers.DeepLinkHelper
import com.leon.be_nobat.helpers.PersianUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class StartActivity : AppCompatActivity() {
    companion object {
        private const val DEFAULT_LANGUAGE_TAG = "fa"
        private const val STANDARD_SPLASH_DURATION_MS = 6500L
        private const val TARGET_SPLASH_DURATION_MS = 5500f
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

        // Edge-to-edge transparent system bars with matching background
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true
        window.decorView.setBackgroundColor(getColor(R.color.splash_background))

        setContentView(R.layout.activity_start)

        val tvVersion = findViewById<TextView>(R.id.tvAppVersion)
        val formattedVersion = PersianUtils.toPersianDigits(BuildConfig.VERSION_NAME)
        tvVersion?.text = getString(R.string.app_version_format, formattedVersion)

        // Set initial state for slide-up entrance animation: translated down and transparent
        tvVersion?.alpha = 0f
        tvVersion?.translationY = 50f * resources.displayMetrics.density

        // Ensure bottom version text is never overlapped by system navigation bars
        val root = findViewById<View>(R.id.splashRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val baseMargin = (32 * resources.displayMetrics.density).toInt()
            (tvVersion?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.bottomMargin = baseMargin + navBars.bottom
                tvVersion.layoutParams = lp
            }
            insets
        }

        // Animate version TextView sliding up from the bottom with smooth deceleration
        tvVersion?.animate()
            ?.translationY(0f)
            ?.alpha(1f)
            ?.setStartDelay(600L)
            ?.setDuration(800L)
            ?.setInterpolator(DecelerateInterpolator(1.8f))
            ?.start()

        setupSplashAnimation()

        // Allow tapping anywhere to skip the remaining splash duration
        findViewById<View>(R.id.splashRoot)?.setOnClickListener {
            resolvedTarget?.let { target -> navigateToTarget(target) }
        }

        lifecycleScope.launch {
            // Resolve target route in background while animation runs
            val target = resolveTargetIntent()
            resolvedTarget = target

            // Fallback timeout to guarantee transition even if video playback has issues
            delay(STANDARD_SPLASH_DURATION_MS)
            navigateToTarget(target)
        }
    }

    private var videoWidth = 720
    private var videoHeight = 1280

    private fun setupSplashAnimation() {
        val videoView = findViewById<VideoView>(R.id.vvSplashVideo)
        val root = findViewById<View>(R.id.splashRoot)

        root?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (videoView != null && videoWidth > 0 && videoHeight > 0) {
                adjustVideoAspect(videoView, videoWidth, videoHeight)
            }
        }

        try {
            val videoUri = Uri.parse("android.resource://$packageName/${R.raw.benobat_splash}")
            videoView?.apply {
                visibility = View.VISIBLE
                setVideoURI(videoUri)
                setOnPreparedListener { mp ->
                    mp.isLooping = false
                    if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                        videoWidth = mp.videoWidth
                        videoHeight = mp.videoHeight
                    }
                    adjustVideoAspect(this, videoWidth, videoHeight)
                    try {
                        val naturalDurationMs = mp.duration
                        val speed = if (naturalDurationMs > 0) {
                            (naturalDurationMs.toFloat() / TARGET_SPLASH_DURATION_MS).coerceIn(1.0f, 3.0f)
                        } else {
                            1.4f
                        }
                        val params = mp.playbackParams
                        params.speed = speed
                        mp.playbackParams = params
                    } catch (_: Exception) {
                    }
                    mp.setOnCompletionListener {
                        resolvedTarget?.let { target -> navigateToTarget(target) }
                    }
                    start()
                }
                setOnCompletionListener {
                    resolvedTarget?.let { target -> navigateToTarget(target) }
                }
                setOnErrorListener { _, _, _ ->
                    resolvedTarget?.let { target -> navigateToTarget(target) }
                    true
                }
            }
        } catch (_: Exception) {
            resolvedTarget?.let { target -> navigateToTarget(target) }
        }
    }

    /**
     * Dynamically adjusts VideoView layout dimensions to strictly preserve the video's aspect ratio
     * (e.g. 720:1280) across any device screen (phones, tablets, foldables), preventing any stretching
     * or distortion.
     */
    private fun adjustVideoAspect(
        videoView: VideoView,
        vWidth: Int,
        vHeight: Int,
        cropToFill: Boolean = false
    ) {
        val parent = videoView.parent as? View ?: return
        parent.post {
            val containerWidth = parent.width
            val containerHeight = parent.height
            if (containerWidth <= 0 || containerHeight <= 0) return@post

            val videoRatio = vWidth.toFloat() / vHeight.toFloat()

            val targetWidth: Int
            val targetHeight: Int

            if (cropToFill) {
                targetWidth = kotlin.math.max(containerWidth, (containerHeight * videoRatio).toInt())
                targetHeight = kotlin.math.max(containerHeight, (containerWidth / videoRatio).toInt())
            } else {
                targetWidth = kotlin.math.min(containerWidth, (containerHeight * videoRatio).toInt())
                targetHeight = kotlin.math.min(containerHeight, (containerWidth / videoRatio).toInt())
            }

            val lp = videoView.layoutParams
            if (lp.width != targetWidth || lp.height != targetHeight) {
                lp.width = targetWidth
                lp.height = targetHeight
                videoView.layoutParams = lp
            }
        }
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
                } else if (!resolvedBranchId.isNullOrBlank() && parsed.businessId.isNullOrBlank()) {
                    return BranchDetailActivity.createIntent(this, branchId = resolvedBranchId, businessId = resolvedBizId)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
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
