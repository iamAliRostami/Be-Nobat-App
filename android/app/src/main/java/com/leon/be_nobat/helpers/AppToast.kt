package com.leon.be_nobat.helpers

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.leon.be_nobat.R
import java.lang.ref.WeakReference

/**
 * Modern in-app toast component that renders messages using the application's
 * custom font ([R.font.app_font]) with a stylish rounded floating pill appearance.
 */
class AppToast private constructor(
    private val context: Context,
    private val activity: Activity?,
    private val message: CharSequence,
    private val durationMs: Long
) {
    private var toastView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDismissed = false
    private var systemFallbackToast: Toast? = null

    private val dismissRunnable = Runnable { dismiss(animated = true) }

    fun show() {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            // Fallback for background or application context
            systemFallbackToast = Toast.makeText(
                context,
                message,
                if (durationMs >= LENGTH_LONG) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).also { it.show() }
            return
        }

        handler.post {
            if (activity.isFinishing || activity.isDestroyed) return@post

            // Cancel any previously active in-app toast
            currentToast?.get()?.cancel()
            currentToast = WeakReference(this)

            val rootView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@post
            val inflater = LayoutInflater.from(activity)
            val container = inflater.inflate(R.layout.view_app_toast, rootView, false) as FrameLayout
            val tvMessage = container.findViewById<TextView>(R.id.tvToastMessage)

            // Strictly set the application font typeface
            ResourcesCompat.getFont(activity, R.font.app_font)?.let {
                tvMessage.typeface = it
            }
            tvMessage.text = message

            // Adjust margin for bottom navigation bar / gesture insets
            val bottomInset = ViewCompat.getRootWindowInsets(rootView)
                ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            val lp = container.layoutParams as? FrameLayout.LayoutParams
            if (lp != null && bottomInset > 0) {
                lp.bottomMargin = lp.bottomMargin + bottomInset
                container.layoutParams = lp
            }

            container.alpha = 0f
            container.translationY = 30f
            rootView.addView(container)
            toastView = container

            container.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .start()

            handler.postDelayed(dismissRunnable, durationMs)
        }
    }

    fun cancel() {
        handler.removeCallbacks(dismissRunnable)
        systemFallbackToast?.cancel()
        dismiss(animated = false)
    }

    private fun dismiss(animated: Boolean) {
        if (isDismissed) return
        isDismissed = true
        handler.removeCallbacks(dismissRunnable)

        val view = toastView ?: return
        toastView = null

        val parent = view.parent as? ViewGroup ?: return
        if (animated && activity != null && !activity.isFinishing && !activity.isDestroyed) {
            view.animate()
                .alpha(0f)
                .translationY(30f)
                .setDuration(180L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { parent.removeView(view) }
                .start()
        } else {
            parent.removeView(view)
        }
    }

    companion object {
        const val LENGTH_SHORT = 2000L
        const val LENGTH_LONG = 3500L

        private var currentToast: WeakReference<AppToast>? = null

        fun makeText(context: Context, text: CharSequence, duration: Int): AppToast {
            val durationMs = if (duration == 1) LENGTH_LONG else LENGTH_SHORT
            val activity = findActivity(context)
            return AppToast(context, activity, text, durationMs)
        }

        fun makeText(context: Context, @StringRes resId: Int, duration: Int): AppToast {
            return makeText(context, context.getString(resId), duration)
        }

        fun show(context: Context, text: CharSequence, durationMs: Long = LENGTH_SHORT): AppToast {
            val toast = AppToast(context, findActivity(context), text, durationMs)
            toast.show()
            return toast
        }

        fun show(context: Context, @StringRes resId: Int, durationMs: Long = LENGTH_SHORT): AppToast {
            return show(context, context.getString(resId), durationMs)
        }

        private fun findActivity(context: Context): Activity? {
            var ctx = context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) return ctx
                ctx = ctx.baseContext
            }
            return null
        }
    }
}
