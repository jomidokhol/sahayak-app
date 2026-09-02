package com.nur.sahayak.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random

class ConfettiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colors = intArrayOf(
        Color.parseColor("#F44336"),
        Color.parseColor("#4CAF50"),
        Color.parseColor("#FFEB3B"),
        Color.parseColor("#2196F3"),
        Color.parseColor("#9C27B0"),
        Color.parseColor("#FF9800"),
        Color.parseColor("#00E676"),
        Color.parseColor("#FF4081"),
        Color.parseColor("#00BCD4"),
        Color.parseColor("#FFD700")
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()
    private var isRunning = false

    private data class Particle(
        var x: Float,
        var y: Float,
        var width: Float,
        var height: Float,
        var speedX: Float,
        var speedY: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var color: Int,
        var alpha: Int = 255
    )

    fun startConfetti() {
        particles.clear()
        val screenWidth = if (this.width > 0) this.width else 1080
        val screenHeight = if (this.height > 0) this.height else 1920

        // Increased particle density (300+ particles)
        for (i in 0 until 300) {
            val isRibbon = random.nextBoolean()
            val w = if (isRibbon) 10f + random.nextFloat() * 10f else 14f + random.nextFloat() * 16f
            val h = if (isRibbon) 22f + random.nextFloat() * 18f else w

            particles.add(
                Particle(
                    x = random.nextFloat() * screenWidth,
                    y = -random.nextFloat() * (screenHeight * 0.8f),
                    width = w,
                    height = h,
                    speedX = -5f + random.nextFloat() * 10f,
                    speedY = 10f + random.nextFloat() * 20f,
                    rotation = random.nextFloat() * 360f,
                    rotationSpeed = -8f + random.nextFloat() * 16f,
                    color = colors[random.nextInt(colors.size)]
                )
            )
        }

        visibility = VISIBLE
        isRunning = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isRunning || particles.isEmpty()) return

        var hasAliveParticles = false

        for (p in particles) {
            p.x += p.speedX
            p.y += p.speedY
            p.rotation += p.rotationSpeed

            if (p.y > height * 0.75f) {
                p.alpha = maxOf(0, p.alpha - 5)
            }

            if (p.alpha > 0 && p.y < height + 60) {
                hasAliveParticles = true
                paint.color = p.color
                paint.alpha = p.alpha

                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.rotation)
                canvas.drawRect(-p.width / 2, -p.height / 2, p.width / 2, p.height / 2, paint)
                canvas.restore()
            }
        }

        if (hasAliveParticles) {
            postInvalidateOnAnimation()
        } else {
            isRunning = false
            visibility = GONE
        }
    }
}
