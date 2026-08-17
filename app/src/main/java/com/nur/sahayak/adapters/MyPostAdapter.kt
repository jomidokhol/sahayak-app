package com.nur.sahayak.adapters

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.R
import com.nur.sahayak.models.PublicPost
import com.nur.sahayak.utils.TimeUtils
import com.nur.sahayak.utils.TopNotification

class MyPostAdapter(
    private val posts: MutableList<PublicPost>,
    private val currentUid: String
) : RecyclerView.Adapter<MyPostAdapter.MyPostViewHolder>() {

    class MyPostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvTime: TextView = itemView.findViewById(R.id.tvTimeAgo)
        val tvContent: TextView = itemView.findViewById(R.id.tvPostContent)
        val btnMenu: ImageButton = itemView.findViewById(R.id.btnPostMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyPostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_post, parent, false)
        return MyPostViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyPostViewHolder, position: Int) {
        val post = posts[position]
        holder.tvName.text = post.userName
        holder.tvTime.text = TimeUtils.getTimeAgo(post.timestamp)
        holder.tvContent.text = post.content

        val context = holder.itemView.context

        holder.btnMenu.setOnClickListener { anchor ->
            // Apply the Light Theme Wrapper for Popup
            val wrapper = ContextThemeWrapper(context, R.style.PopupMenuThemeOverlay)
            val popup = PopupMenu(wrapper, anchor)
            popup.menu.add(0, 1, 0, "Edit")
            popup.menu.add(0, 2, 1, "Delete")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        showEditDialog(context, post, position)
                        true
                    }
                    2 -> {
                        showDeleteConfirmDialog(context, post, position)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = posts.size

    fun setPosts(newPosts: List<PublicPost>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }

    private fun showEditDialog(context: Context, post: PublicPost, position: Int) {
        val etEdit = EditText(context).apply {
            setText(post.content)
            setPadding(32, 24, 32, 24)
            textSize = 15f
            setTextColor(android.graphics.Color.BLACK)
        }

        MaterialAlertDialogBuilder(context, R.style.CustomDialogTheme)
            .setTitle("Edit Post")
            .setView(etEdit)
            .setPositiveButton("Save") { _, _ ->
                val newContent = etEdit.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    post.content = newContent
                    notifyItemChanged(position)

                    val firestore = FirebaseFirestore.getInstance()
                    firestore.collection("public_box").document(post.id).update("content", newContent)
                    if (currentUid.isNotEmpty()) {
                        firestore.collection("users").document(currentUid)
                            .collection("posts").document(post.id).update("content", newContent)
                    }
                    TopNotification.show(context as? Activity, "পোস্টটি সফলভাবে আপডেট করা হয়েছে!")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmDialog(context: Context, post: PublicPost, position: Int) {
        MaterialAlertDialogBuilder(context, R.style.CustomDialogTheme)
            .setTitle("Delete Post")
            .setMessage("আপনি কি নিশ্চিত যে পোস্টটি মুছে ফেলতে চান?")
            .setPositiveButton("Delete") { _, _ ->
                posts.removeAt(position)
                notifyItemRemoved(position)

                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("public_box").document(post.id).delete()
                if (currentUid.isNotEmpty()) {
                    firestore.collection("users").document(currentUid)
                        .collection("posts").document(post.id).delete()
                }
                TopNotification.show(context as? Activity, "পোস্টটি ডিলিট করা হয়েছে!")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
