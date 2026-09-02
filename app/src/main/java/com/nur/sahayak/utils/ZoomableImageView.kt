package com.nur.sahayak.utils

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var scaleFactor = 1.0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(1.0f, 4.0f)
            scaleX = scaleFactor
            scaleY = scaleFactor
            parent?.requestDisallowInterceptTouchEvent(scaleFactor > 1.05f)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP && scaleFactor <= 1.05f) {
            scaleFactor = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}
