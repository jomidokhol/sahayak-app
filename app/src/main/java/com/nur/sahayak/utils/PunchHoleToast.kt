package com.nur.sahayak.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.nur.sahayak.R

object PunchHoleToast {

    fun show(activity: Activity?, message: String) {
        if (activity == null || activity.isFinishing) return

        val decorView = activity.window.decorView as? ViewGroup ?: return

        // Inflate Toast View
        val inflater = LayoutInflater.from(activity)
        val toastView = inflater.inflate(R.layout.layout_punch_hole_toast, decorView, false)
        val tvMessage = toastView.findViewById<TextView>(R.id.tvPunchHoleMessage)
        tvMessage.text = message

        // Add to Root View
        decorView.addView(toastView)

        // Load Animations
        val animIn = AnimationUtils.loadAnimation(activity, R.anim.punch_hole_in)
        val animOut = AnimationUtils.loadAnimation(activity, R.anim.punch_hole_out)

        toastView.startAnimation(animIn)

        // Dismiss after 4 seconds (4000 ms)
        Handler(Looper.getMainLooper()).postDelayed({
            toastView.startAnimation(animOut)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    decorView.removeView(toastView)
                } catch (e: Exception) {}
            }, 400)
        }, 4000)
    }
}
