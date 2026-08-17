package com.nur.sahayak.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.nur.sahayak.R

object TopNotification {

    fun show(activity: Activity?, message: String) {
        if (activity == null || activity.isFinishing) return

        val decorView = activity.window.decorView as? ViewGroup ?: return

        val inflater = LayoutInflater.from(activity)
        val bannerView = inflater.inflate(R.layout.layout_top_banner, decorView, false)
        val tvMessage = bannerView.findViewById<TextView>(R.id.tvTopBannerMessage)
        tvMessage.text = message

        decorView.addView(bannerView)

        val animIn = AnimationUtils.loadAnimation(activity, R.anim.punch_hole_in)
        val animOut = AnimationUtils.loadAnimation(activity, R.anim.punch_hole_out)

        bannerView.startAnimation(animIn)

        Handler(Looper.getMainLooper()).postDelayed({
            bannerView.startAnimation(animOut)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    decorView.removeView(bannerView)
                } catch (e: Exception) {}
            }, 400)
        }, 4000)
    }
}
