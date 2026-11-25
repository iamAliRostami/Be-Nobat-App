package com.leon.be_nobat.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Keep
import com.leon.be_nobat.R

class SplashCheckmarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val checkmarkBitmap: Bitmap? =
        BitmapFactory.decodeResource(resources, R.drawable.splash_checkmark)

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    private val dstRect = RectF()
    private val maskPath = Path()

    // Normalized points based on original 650x392 canvas
    private val startX = 74f / 650f
    private val startY = 131f / 392f
    private val vertexX = 262f / 650f
    private val vertexY = 319f / 392f
    private val endX = 554f / 650f
    private val endY = 2f / 392f

    @Keep
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bm = checkmarkBitmap ?: return
        if (progress <= 0f) return

        val w = width.toFloat()
        val h = height.toFloat()
        dstRect.set(0f, 0f, w, h)

        if (progress >= 1.0f) {
            canvas.drawBitmap(bm, null, dstRect, bitmapPaint)
            return
        }

        maskPaint.strokeWidth = w * (90f / 650f)

        val p1x = startX * w
        val p1y = startY * h
        val pVx = vertexX * w
        val pVy = vertexY * h
        val p2x = endX * w
        val p2y = endY * h

        val len1 = Math.hypot((pVx - p1x).toDouble(), (pVy - p1y).toDouble()).toFloat()
        val len2 = Math.hypot((p2x - pVx).toDouble(), (p2y - pVy).toDouble()).toFloat()
        val totalLen = len1 + len2
        val currLen = progress * totalLen

        maskPath.reset()
        maskPath.moveTo(p1x, p1y)

        if (currLen <= len1) {
            val frac = if (len1 > 0f) currLen / len1 else 1f
            maskPath.lineTo(p1x + frac * (pVx - p1x), p1y + frac * (pVy - p1y))
        } else {
            maskPath.lineTo(pVx, pVy)
            val frac = if (len2 > 0f) (currLen - len1) / len2 else 1f
            maskPath.lineTo(pVx + frac * (p2x - pVx), pVy + frac * (p2y - pVy))
        }

        val saveCount = canvas.saveLayer(dstRect, null)
        canvas.drawBitmap(bm, null, dstRect, bitmapPaint)
        canvas.drawPath(maskPath, maskPaint)
        canvas.restoreToCount(saveCount)
    }
}
