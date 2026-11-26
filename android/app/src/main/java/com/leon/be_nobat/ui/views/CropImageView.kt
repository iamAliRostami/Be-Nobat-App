package com.leon.be_nobat.ui.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive image crop view with draggable/resizable crop rectangle,
 * optional aspect-ratio lock, and 90-degree rotation.
 */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private val imageRect = RectF()
    private val cropRect = RectF()
    private var aspectRatio: Float = 0f // <= 0 means free crop

    private val minCropSizePx = 96f * resources.displayMetrics.density
    private val handleHitRadiusPx = 28f * resources.displayMetrics.density

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private enum class DragMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private var dragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    fun setImageBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        computeImageRect()
        resetCropRect()
        invalidate()
    }

    fun setAspectRatio(ratio: Float) {
        aspectRatio = ratio
        resetCropRect()
        invalidate()
    }

    fun rotate90() {
        val current = sourceBitmap ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        sourceBitmap = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        computeImageRect()
        resetCropRect()
        invalidate()
    }

    fun getCroppedBitmap(): Bitmap? {
        val bmp = sourceBitmap ?: return null
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return bmp
        val scaleX = bmp.width / imageRect.width()
        val scaleY = bmp.height / imageRect.height()

        val left = ((cropRect.left - imageRect.left) * scaleX).toInt().coerceIn(0, bmp.width - 1)
        val top = ((cropRect.top - imageRect.top) * scaleY).toInt().coerceIn(0, bmp.height - 1)
        val right = ((cropRect.right - imageRect.left) * scaleX).toInt().coerceIn(left + 1, bmp.width)
        val bottom = ((cropRect.bottom - imageRect.top) * scaleY).toInt().coerceIn(top + 1, bmp.height)

        return Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeImageRect()
        resetCropRect()
    }

    private fun computeImageRect() {
        val bmp = sourceBitmap ?: return
        if (width <= 0 || height <= 0) return
        val pad = 16f * resources.displayMetrics.density
        val availW = max(1f, width - pad * 2)
        val availH = max(1f, height - pad * 2)
        val scale = min(availW / bmp.width, availH / bmp.height)
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        imageRect.set(left, top, left + drawW, top + drawH)
    }

    private fun resetCropRect() {
        if (imageRect.isEmpty) return
        if (aspectRatio <= 0f) {
            val insetX = imageRect.width() * 0.08f
            val insetY = imageRect.height() * 0.08f
            cropRect.set(
                imageRect.left + insetX,
                imageRect.top + insetY,
                imageRect.right - insetX,
                imageRect.bottom - insetY,
            )
            return
        }
        val maxW = imageRect.width() * 0.9f
        val maxH = imageRect.height() * 0.9f
        var cropW = maxW
        var cropH = cropW / aspectRatio
        if (cropH > maxH) {
            cropH = maxH
            cropW = cropH * aspectRatio
        }
        val cx = imageRect.centerX()
        val cy = imageRect.centerY()
        cropRect.set(cx - cropW / 2f, cy - cropH / 2f, cx + cropW / 2f, cy + cropH / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap ?: return
        canvas.drawBitmap(bmp, null, imageRect, bitmapPaint)

        // Dimmed regions outside cropRect
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, overlayPaint)
        canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, overlayPaint)
        canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint)
        canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, overlayPaint)

        // Rule-of-thirds grid
        val thirdW = cropRect.width() / 3f
        val thirdH = cropRect.height() / 3f
        canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + thirdW * 2, cropRect.top, cropRect.left + thirdW * 2, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + thirdH * 2, cropRect.right, cropRect.top + thirdH * 2, gridPaint)

        // Border & corner handles
        canvas.drawRect(cropRect, borderPaint)
        val cLen = min(22f * resources.displayMetrics.density, min(cropRect.width(), cropRect.height()) * 0.3f)
        // Top-left
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left + cLen, cropRect.top, handlePaint)
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left, cropRect.top + cLen, handlePaint)
        // Top-right
        canvas.drawLine(cropRect.right - cLen, cropRect.top, cropRect.right, cropRect.top, handlePaint)
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right, cropRect.top + cLen, handlePaint)
        // Bottom-left
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left + cLen, cropRect.bottom, handlePaint)
        canvas.drawLine(cropRect.left, cropRect.bottom - cLen, cropRect.left, cropRect.bottom, handlePaint)
        // Bottom-right
        canvas.drawLine(cropRect.right - cLen, cropRect.bottom, cropRect.right, cropRect.bottom, handlePaint)
        canvas.drawLine(cropRect.right, cropRect.bottom - cLen, cropRect.right, cropRect.bottom, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (sourceBitmap == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragMode = detectDragMode(event.x, event.y)
                if (dragMode != DragMode.NONE) parent?.requestDisallowInterceptTouchEvent(true)
                return dragMode != DragMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                applyDrag(dx, dy)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun detectDragMode(x: Float, y: Float): DragMode {
        fun near(px: Float, py: Float) = abs(x - px) <= handleHitRadiusPx && abs(y - py) <= handleHitRadiusPx
        return when {
            near(cropRect.left, cropRect.top) -> DragMode.TOP_LEFT
            near(cropRect.right, cropRect.top) -> DragMode.TOP_RIGHT
            near(cropRect.left, cropRect.bottom) -> DragMode.BOTTOM_LEFT
            near(cropRect.right, cropRect.bottom) -> DragMode.BOTTOM_RIGHT
            cropRect.contains(x, y) -> DragMode.MOVE
            else -> DragMode.NONE
        }
    }

    private fun applyDrag(dx: Float, dy: Float) {
        when (dragMode) {
            DragMode.NONE -> return
            DragMode.MOVE -> {
                val clampedDx = dx.coerceIn(imageRect.left - cropRect.left, imageRect.right - cropRect.right)
                val clampedDy = dy.coerceIn(imageRect.top - cropRect.top, imageRect.bottom - cropRect.bottom)
                cropRect.offset(clampedDx, clampedDy)
            }
            DragMode.TOP_LEFT -> resizeCorner(dx, dy, anchorRight = true, anchorBottom = true)
            DragMode.TOP_RIGHT -> resizeCorner(dx, dy, anchorRight = false, anchorBottom = true)
            DragMode.BOTTOM_LEFT -> resizeCorner(dx, dy, anchorRight = true, anchorBottom = false)
            DragMode.BOTTOM_RIGHT -> resizeCorner(dx, dy, anchorRight = false, anchorBottom = false)
        }
    }

    private fun resizeCorner(dx: Float, dy: Float, anchorRight: Boolean, anchorBottom: Boolean) {
        val minW = min(minCropSizePx, imageRect.width())
        val minH = min(minCropSizePx, imageRect.height())
        val fixedX = if (anchorRight) cropRect.right else cropRect.left
        val fixedY = if (anchorBottom) cropRect.bottom else cropRect.top

        var movingX = (if (anchorRight) cropRect.left else cropRect.right) + dx
        var movingY = (if (anchorBottom) cropRect.top else cropRect.bottom) + dy

        movingX = if (anchorRight) movingX.coerceIn(imageRect.left, fixedX - minW) else movingX.coerceIn(fixedX + minW, imageRect.right)
        movingY = if (anchorBottom) movingY.coerceIn(imageRect.top, fixedY - minH) else movingY.coerceIn(fixedY + minH, imageRect.bottom)

        if (aspectRatio > 0f) {
            var newW = abs(fixedX - movingX)
            var newH = newW / aspectRatio
            val maxH = if (anchorBottom) fixedY - imageRect.top else imageRect.bottom - fixedY
            if (newH > maxH) {
                newH = maxH
                newW = newH * aspectRatio
            }
            val maxW = if (anchorRight) fixedX - imageRect.left else imageRect.right - fixedX
            if (newW > maxW) {
                newW = maxW
                newH = newW / aspectRatio
            }
            movingX = if (anchorRight) fixedX - newW else fixedX + newW
            movingY = if (anchorBottom) fixedY - newH else fixedY + newH
        }

        cropRect.set(
            min(fixedX, movingX),
            min(fixedY, movingY),
            max(fixedX, movingX),
            max(fixedY, movingY),
        )
    }
}
