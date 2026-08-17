package com.nur.sahayak.adapters

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
import com.nur.sahayak.utils.TimeUtils

class NewsAdapter(private var newsList: List<NewsItem>) :
    RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivBlurBg: ImageView = itemView.findViewById(R.id.ivNewsCardBlurBg)
        val ivCover: ImageView = itemView.findViewById(R.id.ivNewsCardCover)
        val tvTitle: TextView = itemView.findViewById(R.id.tvNewsCardTitle)
        val tvDate: TextView = itemView.findViewById(R.id.tvNewsCardDate)
        val tvDesc: TextView = itemView.findViewById(R.id.tvNewsCardDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_card, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val item = newsList[position]
        holder.tvTitle.text = item.title
        holder.tvDate.text = TimeUtils.getTimeAgo(item.timestamp)

        holder.tvDesc.text = get20WordExcerpt(item.desc)

        val loadingRes = try { R.drawable.news_loading } catch (e: Exception) { R.drawable.flogo }

        if (item.imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.imageUrl)
                .placeholder(loadingRes)
                .into(holder.ivCover)

            Glide.with(holder.itemView.context)
                .load(item.imageUrl)
                .placeholder(loadingRes)
                .into(holder.ivBlurBg)
        } else {
            holder.ivCover.setImageResource(loadingRes)
            holder.ivBlurBg.setImageResource(loadingRes)
        }

        // Pass ID, Reporter & ViewCount to NewsDetailActivity
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

    override fun getItemCount(): Int = newsList.size

    fun updateList(newList: List<NewsItem>) {
        newsList = newList
        notifyDataSetChanged()
    }

    private fun get20WordExcerpt(htmlDesc: String): CharSequence {
        if (htmlDesc.isEmpty()) return ""
        val plainText = try {
            Html.fromHtml(htmlDesc).toString().trim()
        } catch (e: Exception) {
            htmlDesc.trim()
        }

        val words = plainText.split(Regex("\\s+"))
        return if (words.size > 20) {
            val truncated = words.take(20).joinToString(" ")
            val spannable = SpannableStringBuilder("$truncated... ")
            val start = spannable.length
            spannable.append("more")

            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#006A4E")),
                start,
                spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable
        } else {
            plainText
        }
    }
}
