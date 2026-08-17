package com.nur.sahayak.adapters

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.R
import com.nur.sahayak.models.ReplyPost
import com.nur.sahayak.utils.TimeUtils
import com.nur.sahayak.utils.TopNotification

class ReplyAdapter(
    private val replies: MutableList<ReplyPost>,
    private val currentUid: String,
    private val postId: String
) : RecyclerView.Adapter<ReplyAdapter.ReplyViewHolder>() {

    class ReplyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivReplyAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvReplyName)
        val tvContent: TextView = itemView.findViewById(R.id.tvReplyContent)
        val tvTime: TextView = itemView.findViewById(R.id.tvReplyTime)
        val tvEdited: TextView = itemView.findViewById(R.id.tvReplyEdited)
        val btnMenu: ImageButton = itemView.findViewById(R.id.btnReplyMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reply_card, parent, false)
        return ReplyViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
        val reply = replies[position]
        val context = holder.itemView.context

        holder.tvName.text = reply.userName
        holder.tvContent.text = reply.content
        holder.tvTime.text = TimeUtils.getTimeAgo(reply.uploadtime)
        holder.tvEdited.visibility = if (reply.isEdited) View.VISIBLE else View.GONE

        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (reply.userAvatar.isNotEmpty()) {
            Glide.with(context).load(reply.userAvatar).circleCrop().into(holder.ivAvatar)
        } else {
            Glide.with(context).load(defaultAvatar).circleCrop().into(holder.ivAvatar)
        }

        if (reply.userid == currentUid && currentUid.isNotEmpty()) {
            holder.btnMenu.visibility = View.VISIBLE
            holder.btnMenu.setOnClickListener { anchor ->
                // Apply the Light Theme Wrapper for Popup
                val wrapper = ContextThemeWrapper(context, R.style.PopupMenuThemeOverlay)
                val popup = PopupMenu(wrapper, anchor)
                popup.menu.add(0, 1, 0, "এডিট করুন")
                popup.menu.add(0, 2, 1, "ডিলিট করুন")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { showEditDialog(context, reply, position); true }
                        2 -> { showDeleteDialog(context, reply, position); true }
                        else -> false
                    }
                }
                popup.show()
            }
        } else {
            holder.btnMenu.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = replies.size

    fun addReplies(newReplies: List<ReplyPost>) {
        val startPos = replies.size
        replies.addAll(newReplies)
        notifyItemRangeInserted(startPos, newReplies.size)
    }

    private fun showEditDialog(context: Context, reply: ReplyPost, position: Int) {
        val etEdit = EditText(context).apply {
            setText(reply.content)
            setPadding(32, 24, 32, 24)
            setTextColor(android.graphics.Color.BLACK)
        }
        MaterialAlertDialogBuilder(context, R.style.CustomDialogTheme)
            .setTitle("রিপ্লাই এডিট করুন")
            .setView(etEdit)
            .setPositiveButton("সেভ") { _, _ ->
                val newContent = etEdit.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    reply.content = newContent
                    reply.isEdited = true
                    notifyItemChanged(position)

                    FirebaseFirestore.getInstance().collection("openchat").document(postId)
                        .collection("post_reply").document(reply.id)
                        .update("content", newContent, "isEdited", true)
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showDeleteDialog(context: Context, reply: ReplyPost, position: Int) {
        MaterialAlertDialogBuilder(context, R.style.CustomDialogTheme)
            .setTitle("রিপ্লাই ডিলিট")
            .setMessage("আপনি কি নিশ্চিত যে এই রিপ্লাইটি মুছে ফেলতে চান?")
            .setPositiveButton("ডিলিট") { _, _ ->
                replies.removeAt(position)
                notifyItemRemoved(position)

                val db = FirebaseFirestore.getInstance()
                db.collection("openchat").document(postId).collection("post_reply").document(reply.id).delete()
                
                db.collection("openchat").document(postId).update("repliesCount", com.google.firebase.firestore.FieldValue.increment(-1))
            }
            .setNegativeButton("না", null)
            .show()
    }
}
