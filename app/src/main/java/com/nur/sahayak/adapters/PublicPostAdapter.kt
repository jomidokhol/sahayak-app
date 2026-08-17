package com.nur.sahayak.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nur.sahayak.R
import com.nur.sahayak.models.PublicPost

class PublicPostAdapter(private val posts: MutableList<PublicPost>) :
    RecyclerView.Adapter<PublicPostAdapter.PostViewHolder>() {

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvContent: TextView = itemView.findViewById(R.id.tvPostContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_public_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        holder.tvName.text = "পোস্ট করেছেন: " + if (post.userName.isNotEmpty()) post.userName else "লালপুরবাসী"
        holder.tvContent.text = post.content
    }

    override fun getItemCount(): Int = posts.size

    fun setPosts(newPosts: List<PublicPost>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }
}
