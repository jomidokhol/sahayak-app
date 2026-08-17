package com.nur.sahayak.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3000000") // 70% Dark Background
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#006A4E") // Green Border
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    var aspectRatio: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

    val cropRect = RectF()

    init {
        // Hardware acceleration needs to be disabled for CLEAR mode to work optimally on some devices
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw the dark dimmed background over the entire view
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Padding from left and right edges
        val padding = 40f
        val availableWidth = viewWidth - (padding * 2)

        val rectWidth = availableWidth
        val rectHeight = rectWidth / aspectRatio

        val left = (viewWidth - rectWidth) / 2
        val top = (viewHeight - rectHeight) / 2
        val right = left + rectWidth
        val bottom = top + rectHeight

        cropRect.set(left, top, right, bottom)

        // 2. Punch a transparent hole in the middle
        canvas.drawRect(cropRect, clearPaint)

        // 3. Draw the green border around the hole
        canvas.drawRect(cropRect, borderPaint)
    }
}
