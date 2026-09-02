package com.nur.sahayak.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.nur.sahayak.R

class GuideSliderAdapter(private val imageResList: List<Int>) :
    RecyclerView.Adapter<GuideSliderAdapter.SliderViewHolder>() {

    class SliderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivGuide: ImageView = itemView.findViewById(R.id.ivGuideSliderImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SliderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_guide_slider_image, parent, false)
        return SliderViewHolder(view)
    }

    override fun onBindViewHolder(holder: SliderViewHolder, position: Int) {
        if (imageResList.isEmpty()) return
        val actualPos = position % imageResList.size
        holder.ivGuide.setImageResource(imageResList[actualPos])
    }

    override fun getItemCount(): Int {
        return if (imageResList.size > 1) Int.MAX_VALUE else imageResList.size
    }
}
