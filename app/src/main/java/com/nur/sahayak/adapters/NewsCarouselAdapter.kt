package com.nur.sahayak.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nur.sahayak.NewsDetailActivity
import com.nur.sahayak.R
import com.nur.sahayak.models.NewsItem

class NewsCarouselAdapter(private val newsList: List<NewsItem>) :
    RecyclerView.Adapter<NewsCarouselAdapter.CarouselViewHolder>() {

    class CarouselViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivBlurBg: ImageView = itemView.findViewById(R.id.ivCarouselBlurBg)
        val ivMain: ImageView = itemView.findViewById(R.id.ivCarouselMain)
        val tvTitle: TextView = itemView.findViewById(R.id.tvCarouselTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_carousel, parent, false)
        return CarouselViewHolder(view)
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        if (newsList.isEmpty()) return
        val actualPos = position % newsList.size
        val item = newsList[actualPos]

        holder.tvTitle.text = item.title

        val loadingRes = try { R.drawable.news_loading } catch (e: Exception) { R.drawable.flogo }

        if (item.imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.imageUrl)
                .placeholder(loadingRes)
                .into(holder.ivBlurBg)

            Glide.with(holder.itemView.context)
                .load(item.imageUrl)
                .placeholder(loadingRes)
                .into(holder.ivMain)
        } else {
            holder.ivBlurBg.setImageResource(loadingRes)
            holder.ivMain.setImageResource(loadingRes)
        }

        // Launch NewsDetailActivity with full metadata
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, NewsDetailActivity::class.java).apply {
                putExtra("id", item.id)
                putExtra("title", item.title)
                putExtra("reporter", item.reporter)
                putExtra("imageUrl", item.imageUrl)
                putExtra("desc", item.desc)
                putExtra("viewCount", item.viewCount)
                putExtra("timestamp", item.timestamp)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return if (newsList.size > 1) Int.MAX_VALUE else newsList.size
    }
}
