package com.nur.sahayak.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nur.sahayak.R
import com.nur.sahayak.models.OpenChatPost
import com.nur.sahayak.models.ReplyPost
import com.nur.sahayak.utils.FirestoreSafeParser
import com.nur.sahayak.utils.FormatUtils
import com.nur.sahayak.utils.TimeUtils
import com.nur.sahayak.utils.TopNotification

class OpenChatAdapter(
    private var chatPosts: List<OpenChatPost>,
    private val currentUid: String,
    private val currentUserName: String,
    private val currentUserAvatar: String
) : RecyclerView.Adapter<OpenChatAdapter.ChatViewHolder>() {

    private val expandedContentPositions = mutableSetOf<Int>()
    private val userCache = mutableMapOf<String, Pair<String, String>>()
    private var currentlyExpandedReplyPosition: Int = -1

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivChatAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvChatUserName)
        val tvTime: TextView = itemView.findViewById(R.id.tvChatTime)
        val tvContent: TextView = itemView.findViewById(R.id.tvChatContent)
        
        val llLikeBtn: LinearLayout = itemView.findViewById(R.id.llLikeBtn)
        val ivLikeIcon: ImageView = itemView.findViewById(R.id.ivLikeIcon)
        val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
        
        val llCommentBtn: LinearLayout = itemView.findViewById(R.id.llCommentBtn)
        val tvCommentCount: TextView = itemView.findViewById(R.id.tvCommentCount)
        
        val llReplySection: LinearLayout = itemView.findViewById(R.id.llReplySection)
        val rvReplies: RecyclerView = itemView.findViewById(R.id.rvReplies)
        val btnLoadMoreReplies: Button = itemView.findViewById(R.id.btnLoadMoreReplies)
        val etReplyInput: EditText = itemView.findViewById(R.id.etReplyInput)
        val btnSendReply: ImageButton = itemView.findViewById(R.id.btnSendReply)

        var replyAdapter: ReplyAdapter? = null
        var lastReplyDoc: DocumentSnapshot? = null
        var isLoadingReplies = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_open_chat_card, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val post = chatPosts[position]
        val context = holder.itemView.context

        holder.tvTime.text = TimeUtils.getTimeAgo(post.uploadtime)

        val isExpandedContent = expandedContentPositions.contains(position)
        holder.tvContent.text = format12WordContent(post.content, isExpandedContent)
        holder.tvContent.setOnClickListener {
            if (isExpandedContent) expandedContentPositions.remove(position) else expandedContentPositions.add(position)
            notifyDataSetChanged()
        }

        holder.itemView.setOnClickListener {
            if (currentlyExpandedReplyPosition == position) {
                currentlyExpandedReplyPosition = -1
                notifyDataSetChanged()
            }
        }

        val isLiked = post.likedBy.contains(currentUid)
        if (isLiked) {
            holder.ivLikeIcon.setImageResource(R.drawable.ic_like_filled)
            holder.tvLikeCount.setTextColor(Color.parseColor("#F42A41"))
        } else {
            holder.ivLikeIcon.setImageResource(R.drawable.ic_like_outline)
            holder.tvLikeCount.setTextColor(Color.parseColor("#757575"))
        }
        holder.tvLikeCount.text = FormatUtils.formatCount(post.likesCount)
        holder.tvCommentCount.text = FormatUtils.formatCount(post.repliesCount)

        holder.llLikeBtn.setOnClickListener {
            if (currentUid.isEmpty()) {
                TopNotification.show(context as? android.app.Activity, "লাইক দিতে লগইন করুন")
                return@setOnClickListener
            }
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("openchat").document(post.id)
            if (isLiked) {
                post.likedBy = post.likedBy - currentUid
                post.likesCount -= 1
                docRef.update("likedBy", FieldValue.arrayRemove(currentUid), "likesCount", FieldValue.increment(-1))
            } else {
                post.likedBy = post.likedBy + currentUid
                post.likesCount += 1
                docRef.update("likedBy", FieldValue.arrayUnion(currentUid), "likesCount", FieldValue.increment(1))
            }
            notifyItemChanged(position)
        }

        val isReplyExpanded = (currentlyExpandedReplyPosition == position)
        holder.llReplySection.visibility = if (isReplyExpanded) View.VISIBLE else View.GONE
        
        if (isReplyExpanded && holder.replyAdapter == null) {
            setupReplySection(holder, context, post)
        }

        holder.llCommentBtn.setOnClickListener {
            if (isReplyExpanded) {
                currentlyExpandedReplyPosition = -1
            } else {
                currentlyExpandedReplyPosition = position
            }
            notifyDataSetChanged()
        }

        holder.btnSendReply.setOnClickListener {
            if (currentUid.isEmpty()) {
                TopNotification.show(context as? android.app.Activity, "কমেন্ট করতে লগইন করুন")
                return@setOnClickListener
            }
            val replyText = holder.etReplyInput.text.toString().trim()
            if (replyText.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                val replyRef = db.collection("openchat").document(post.id).collection("post_reply").document()
                
                val newReply = ReplyPost(
                    id = replyRef.id,
                    userid = currentUid,
                    userName = currentUserName,
                    userAvatar = currentUserAvatar,
                    content = replyText,
                    uploadtime = System.currentTimeMillis(),
                    isEdited = false
                )

                replyRef.set(newReply).addOnSuccessListener {
                    holder.etReplyInput.setText("")
                    holder.replyAdapter?.addReplies(listOf(newReply))
                    post.repliesCount += 1
                    holder.tvCommentCount.text = FormatUtils.formatCount(post.repliesCount)
                    db.collection("openchat").document(post.id).update("repliesCount", FieldValue.increment(1))
                }
            }
        }

        // Strict Anonymous Interceptor
        val isAnonymousPost = post.userName.equals("Anonymous User", ignoreCase = true) ||
                              post.userid.equals("anonymous", ignoreCase = true)

        if (isAnonymousPost) {
            holder.tvName.text = "Anonymous User"
            val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
            Glide.with(context).load(defaultAvatar).circleCrop().into(holder.ivAvatar)
        } else {
            // Normal Dynamic User Fetching
            val cached = userCache[post.userid]
            if (cached != null) {
                holder.tvName.text = cached.first
                loadAvatar(context, holder.ivAvatar, cached.second)
            } else {
                holder.tvName.text = if (post.userName.isNotEmpty()) post.userName else "লালপুরবাসী"
                loadAvatar(context, holder.ivAvatar, "")

                if (post.userid.isNotEmpty()) {
                    FirebaseFirestore.getInstance().collection("users").document(post.userid).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                val fName = doc.getString("firstName") ?: ""
                                val lName = doc.getString("lastName") ?: ""
                                val fullName = if ("$fName $lName".trim().isNotEmpty()) "$fName $lName".trim() else post.userName
                                val photoUrl = doc.getString("photoUrl") ?: ""
                                userCache[post.userid] = Pair(fullName, photoUrl)
                                if (holder.adapterPosition == position) {
                                    holder.tvName.text = fullName
                                    loadAvatar(context, holder.ivAvatar, photoUrl)
                                }
                            }
                        }
                }
            }
        }
    }

    override fun getItemCount(): Int = chatPosts.size

    fun addPosts(newPosts: List<OpenChatPost>) {
        val startPos = chatPosts.size
        val mutableList = chatPosts.toMutableList()
        mutableList.addAll(newPosts)
        chatPosts = mutableList
        notifyItemRangeInserted(startPos, newPosts.size)
    }
    
    fun setPosts(newPosts: List<OpenChatPost>) {
        chatPosts = newPosts.toList()
        notifyDataSetChanged()
    }

    private fun setupReplySection(holder: ChatViewHolder, context: Context, post: OpenChatPost) {
        holder.rvReplies.layoutManager = LinearLayoutManager(context)
        holder.replyAdapter = ReplyAdapter(mutableListOf(), currentUid, post.id)
        holder.rvReplies.adapter = holder.replyAdapter

        loadReplies(holder, post.id)

        holder.btnLoadMoreReplies.setOnClickListener {
            loadReplies(holder, post.id)
        }
    }

    private fun loadReplies(holder: ChatViewHolder, postId: String) {
        if (holder.isLoadingReplies) return
        holder.isLoadingReplies = true

        var query = FirebaseFirestore.getInstance().collection("openchat").document(postId)
            .collection("post_reply").orderBy("uploadtime", Query.Direction.ASCENDING).limit(5)

        if (holder.lastReplyDoc != null) {
            query = query.startAfter(holder.lastReplyDoc!!)
        }

        query.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                holder.lastReplyDoc = snapshot.documents.last()
                val replies = snapshot.documents.mapNotNull { doc ->
                    val content = FirestoreSafeParser.parseString(doc.get("content"))
                    if (content.isNotEmpty()) {
                        ReplyPost(
                            id = doc.id,
                            userid = FirestoreSafeParser.parseString(doc.get("userid")),
                            userName = FirestoreSafeParser.parseString(doc.get("userName"), "ইউজার"),
                            userAvatar = FirestoreSafeParser.parseString(doc.get("userAvatar")),
                            content = content,
                            uploadtime = FirestoreSafeParser.parseTimestampToMillis(doc.get("uploadtime")),
                            isEdited = FirestoreSafeParser.parseBoolean(doc.get("isEdited"), false)
                        )
                    } else null
                }
                holder.replyAdapter?.addReplies(replies)
                holder.btnLoadMoreReplies.visibility = if (snapshot.size() == 5) View.VISIBLE else View.GONE
            } else {
                holder.btnLoadMoreReplies.visibility = View.GONE
            }
            holder.isLoadingReplies = false
        }.addOnFailureListener { holder.isLoadingReplies = false }
    }

    private fun loadAvatar(context: Context, imageView: ImageView, url: String) {
        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (url.isNotEmpty()) {
            Glide.with(context).load(url).placeholder(defaultAvatar).circleCrop().into(imageView)
        } else {
            Glide.with(context).load(defaultAvatar).circleCrop().into(imageView)
        }
    }

    private fun format12WordContent(content: String, isExpanded: Boolean): CharSequence {
        if (content.isEmpty()) return ""
        val words = content.trim().split(Regex("\\s+"))

        if (words.size <= 12 || isExpanded) return content

        val truncated = words.take(12).joinToString(" ")
        val spannable = SpannableStringBuilder("$truncated... ")
        val start = spannable.length
        spannable.append("more")

        spannable.setSpan(ForegroundColorSpan(Color.parseColor("#006A4E")), start, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), start, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }
}
