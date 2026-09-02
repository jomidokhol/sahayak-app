package com.nur.sahayak.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random

class TwinklingStarsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val stars = mutableListOf<Star>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val random = Random()
    private var isNight = false

    private data class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        var alpha: Float,
        var alphaSpeed: Float
    )

    fun setNightMode(night: Boolean) {
        isNight = night
        if (isNight) {
            initStars()
            visibility = VISIBLE
            invalidate()
        } else {
            visibility = GONE
        }
    }

    private fun initStars() {
        stars.clear()
        val w = if (width > 0) width.toFloat() else 400f
        val h = if (height > 0) height.toFloat() else 200f

        // 25 gentle twinkling stars on the night sky
        for (i in 0 until 25) {
            stars.add(
                Star(
                    x = random.nextFloat() * (w * 0.55f),
                    y = random.nextFloat() * (h * 0.9f),
                    radius = 1.5f + random.nextFloat() * 2.5f,
                    alpha = random.nextFloat() * 255f,
                    alphaSpeed = 1.5f + random.nextFloat() * 3f
                )
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (isNight) initStars()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isNight || stars.isEmpty()) return

        for (s in stars) {
            s.alpha += s.alphaSpeed
            if (s.alpha > 255f) {
                s.alpha = 255f
                s.alphaSpeed = -s.alphaSpeed
            } else if (s.alpha < 40f) {
                s.alpha = 40f
                s.alphaSpeed = -s.alphaSpeed
            }

            paint.alpha = s.alpha.toInt()
            canvas.drawCircle(s.x, s.y, s.radius, paint)
        }

        postInvalidateOnAnimation()
    }
}
