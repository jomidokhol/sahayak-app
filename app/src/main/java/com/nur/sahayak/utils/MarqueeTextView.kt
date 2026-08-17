package com.nur.sahayak.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class MarqueeTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var textToDraw: String = "লালপুর উপজেলায় আপনাকে স্বাগতম!"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var textX = 0f
    private var textWidth = 0f
    private val scrollSpeed = 2.2f
    private var isRunning = false

    private val animRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                textX -= scrollSpeed
                if (textX < -textWidth) {
                    textX = width.toFloat()
                }
                invalidate()
                postOnAnimation(this)
            }
        }
    }

    fun setText(newText: String) {
        if (newText.isEmpty() || newText == textToDraw) return
        textToDraw = newText
        calculateTextWidth()
        invalidate()
    }

    private fun calculateTextWidth() {
        textWidth = paint.measureText(textToDraw)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateTextWidth()
        if (textX == 0f || textX > w) {
            textX = w.toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val yPos = (height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(textToDraw, textX, yPos, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRunning = true
        removeCallbacks(animRunnable)
        post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
        removeCallbacks(animRunnable)
    }
}
