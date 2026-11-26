package com.leon.be_nobat.ui.views

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

/**
 * ImageView supporting pinch-to-zoom, double-tap zoom, and single-finger pan when zoomed.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrixValues = FloatArray(9)
    private val displayMatrix = Matrix()
    private var currentScale = 1f
    private val minScale = 1f
    private val maxScale = 5f
    private var lastTouch = PointF()
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val targetScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
            val factor = targetScale / currentScale
            currentScale = targetScale
            displayMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            fixTranslation()
            imageMatrix = displayMatrix
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (currentScale > 1.2f) minScale else 2.5f
            val factor = target / currentScale
            currentScale = target
            displayMatrix.postScale(factor, factor, e.x, e.y)
            fixTranslation()
            imageMatrix = displayMatrix
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitToScreen()
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        post { fitToScreen() }
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        post { fitToScreen() }
    }

    private fun fitToScreen() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        displayMatrix.reset()
        currentScale = 1f
        val scale = min(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        val dx = (width - d.intrinsicWidth * scale) / 2f
        val dy = (height - d.intrinsicHeight * scale) / 2f
        displayMatrix.postScale(scale, scale)
        displayMatrix.postTranslate(dx, dy)
        imageMatrix = displayMatrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.set(event.x, event.y)
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && !scaleDetector.isInProgress && currentScale > 1f) {
                    val dx = event.x - lastTouch.x
                    val dy = event.y - lastTouch.y
                    displayMatrix.postTranslate(dx, dy)
                    fixTranslation()
                    imageMatrix = displayMatrix
                }
                lastTouch.set(event.x, event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
        }
        return true
    }

    private fun fixTranslation() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        displayMatrix.getValues(matrixValues)
        val scaleX = matrixValues[Matrix.MSCALE_X]
        val scaleY = matrixValues[Matrix.MSCALE_Y]
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val contentW = d.intrinsicWidth * scaleX
        val contentH = d.intrinsicHeight * scaleY

        val fixX = when {
            contentW <= width -> (width - contentW) / 2f - transX
            transX > 0f -> -transX
            transX + contentW < width -> width - (transX + contentW)
            else -> 0f
        }
        val fixY = when {
            contentH <= height -> (height - contentH) / 2f - transY
            transY > 0f -> -transY
            transY + contentH < height -> height - (transY + contentH)
            else -> 0f
        }
        if (fixX != 0f || fixY != 0f) displayMatrix.postTranslate(fixX, fixY)
    }
}
