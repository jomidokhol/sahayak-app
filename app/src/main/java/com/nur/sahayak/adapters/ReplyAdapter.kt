package com.nur.sahayak.adapters

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
    private val postId: String,
    private val postAuthorId: String
) : RecyclerView.Adapter<ReplyAdapter.ReplyViewHolder>() {

    private val userVerifiedCache = mutableMapOf<String, Boolean>()

    class ReplyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivReplyAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvReplyName)
        val ivVerifiedBadge: ImageView = itemView.findViewById(R.id.ivReplyVerifiedBadge)
        val tvContent: TextView = itemView.findViewById(R.id.tvReplyContent)
        val tvTime: TextView = itemView.findViewById(R.id.tvReplyTime)
        val tvEdited: TextView = itemView.findViewById(R.id.tvReplyEdited)
        val btnMenu: ImageButton = itemView.findViewById(R.id.btnReplyMenu)
        val llAuthorBadge: LinearLayout = itemView.findViewById(R.id.llAuthorBadge)
        val llPinnedBadge: LinearLayout = itemView.findViewById(R.id.llPinnedBadge)
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

        // 1. Verified Badge on Comment (Dynamic check)
        setupCommentVerifiedBadge(holder, reply, position)

        // 2. Author Badge Condition
        val isAuthor = reply.userid.isNotEmpty() && (reply.userid == postAuthorId)
        holder.llAuthorBadge.visibility = if (isAuthor) View.VISIBLE else View.GONE

        // 3. Pinned Badge Condition
        holder.llPinnedBadge.visibility = if (reply.isPinned) View.VISIBLE else View.GONE

        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (reply.userAvatar.isNotEmpty()) {
            Glide.with(context).load(reply.userAvatar).circleCrop().into(holder.ivAvatar)
        } else {
            Glide.with(context).load(defaultAvatar).circleCrop().into(holder.ivAvatar)
        }

        // 4. 3-Dot Menu
        holder.btnMenu.visibility = View.VISIBLE
        holder.btnMenu.setOnClickListener { anchor ->
            val wrapper = ContextThemeWrapper(context, R.style.PopupMenuThemeOverlay)
            val popup = PopupMenu(wrapper, anchor)

            val isPostOwner = currentUid.isNotEmpty() && (currentUid == postAuthorId)
            val isReplyOwner = reply.userid.isNotEmpty() && (reply.userid == currentUid)

            if (isPostOwner) {
                val pinTitle = if (reply.isPinned) "আনপিন করুন" else "পিন করুন"
                popup.menu.add(0, 1, 0, pinTitle)
            }

            if (isReplyOwner) {
                popup.menu.add(0, 2, 1, "এডিট করুন")
                popup.menu.add(0, 3, 2, "ডিলিট করুন")
            }

            popup.menu.add(0, 4, 3, "কপি করুন")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        togglePinComment(context, reply)
                        true
                    }
                    2 -> {
                        showEditDialog(context, reply, position)
                        true
                    }
                    3 -> {
                        showDeleteDialog(context, reply, position)
                        true
                    }
                    4 -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Comment Content", reply.content)
                        clipboard.setPrimaryClip(clip)
                        TopNotification.show(context as? Activity, "কমেন্ট কপি করা হয়েছে!")
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun setupCommentVerifiedBadge(holder: ReplyViewHolder, reply: ReplyPost, position: Int) {
        if (reply.isUserVerified) {
            holder.ivVerifiedBadge.visibility = View.VISIBLE
        } else if (reply.userid.isNotEmpty()) {
            val cached = userVerifiedCache[reply.userid]
            if (cached != null) {
                holder.ivVerifiedBadge.visibility = if (cached) View.VISIBLE else View.GONE
            } else {
                holder.ivVerifiedBadge.visibility = View.GONE
                FirebaseFirestore.getInstance().collection("users").document(reply.userid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val isVer = (doc.getBoolean("isVerified") ?: false) && ((doc.getLong("verifiedUntil") ?: 0L) > System.currentTimeMillis())
                            userVerifiedCache[reply.userid] = isVer
                            if (holder.adapterPosition == position) {
                                holder.ivVerifiedBadge.visibility = if (isVer) View.VISIBLE else View.GONE
                            }
                        }
                    }
            }
        } else {
            holder.ivVerifiedBadge.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = replies.size

    fun addReplies(newReplies: List<ReplyPost>) {
        val startPos = replies.size
        replies.addAll(newReplies)
        replies.sortWith(compareByDescending<ReplyPost> { it.isPinned }.thenBy { it.uploadtime })
        notifyItemRangeInserted(startPos, newReplies.size)
    }

    private fun togglePinComment(context: Context, reply: ReplyPost) {
        val newPinnedState = !reply.isPinned
        reply.isPinned = newPinnedState

        FirebaseFirestore.getInstance().collection("openchat").document(postId)
            .collection("post_reply").document(reply.id)
            .update("isPinned", newPinnedState)

        replies.sortWith(compareByDescending<ReplyPost> { it.isPinned }.thenBy { it.uploadtime })
        notifyDataSetChanged()

        val msg = if (newPinnedState) "কমেন্ট পিন করা হয়েছে!" else "কমেন্ট আনপিন করা হয়েছে!"
        TopNotification.show(context as? Activity, msg)
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
                val removeIndex = replies.indexOf(reply)
                if (removeIndex != -1) {
                    replies.removeAt(removeIndex)
                    notifyItemRemoved(removeIndex)
                }

                val db = FirebaseFirestore.getInstance()
                db.collection("openchat").document(postId).collection("post_reply").document(reply.id).delete()
                db.collection("openchat").document(postId).update("repliesCount", com.google.firebase.firestore.FieldValue.increment(-1))
            }
            .setNegativeButton("না", null)
            .show()
    }
}
