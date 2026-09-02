package com.nur.sahayak.adapters

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

class ProfilePostAdapter(
    private var chatPosts: List<OpenChatPost>,
    private val currentUid: String,
    private val currentUserName: String,
    private val currentUserAvatar: String
) : RecyclerView.Adapter<ProfilePostAdapter.PostViewHolder>() {

    private val expandedContentPositions = mutableSetOf<Int>()
    private var currentlyExpandedReplyPosition: Int = -1

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivChatAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvChatUserName)
        val tvTime: TextView = itemView.findViewById(R.id.tvChatTime)
        val tvContent: TextView = itemView.findViewById(R.id.tvChatContent)
        val btnMenu: ImageButton = itemView.findViewById(R.id.btnPostMenu)
        
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile_post_card, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = chatPosts[position]
        val context = holder.itemView.context

        holder.tvTime.text = TimeUtils.getTimeAgo(post.uploadtime)
        holder.tvContent.movementMethod = LinkMovementMethod.getInstance()

        val isExpandedContent = expandedContentPositions.contains(position)
        holder.tvContent.text = formatContentWithTags(context, post.content, isExpandedContent) {
            if (isExpandedContent) expandedContentPositions.remove(position) else expandedContentPositions.add(position)
            notifyDataSetChanged()
        }

        holder.itemView.setOnClickListener {
            if (currentlyExpandedReplyPosition == position) {
                currentlyExpandedReplyPosition = -1
                notifyDataSetChanged()
            }
        }

        holder.btnMenu.setOnClickListener { anchor ->
            val wrapper = ContextThemeWrapper(context, R.style.PopupMenuThemeOverlay)
            val popup = PopupMenu(wrapper, anchor)
            popup.menu.add(0, 1, 0, "এডিট করুন")
            popup.menu.add(0, 2, 1, "ডিলিট করুন")
            popup.menu.add(0, 3, 2, "কপি করুন")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { showEditPostDialog(context, post, position); true }
                    2 -> { showDeletePostDialog(context, post, position); true }
                    3 -> {
                        var copyText = post.content
                        copyText = copyText.replace(Regex("""<phone>(.*?)</phone>""", RegexOption.IGNORE_CASE), "$1")
                        copyText = copyText.replace(Regex("""<wa>(.*?)</wa>""", RegexOption.IGNORE_CASE), "WhatsApp- $1")

                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Post Content", copyText.trim())
                        clipboard.setPrimaryClip(clip)
                        TopNotification.show(context as? Activity, "পোস্টের বক্তব্য কপি করা হয়েছে!")
                        true
                    }
                    else -> false
                }
            }
            popup.show()
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
            if (currentUid.isEmpty()) return@setOnClickListener
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

        if (isReplyExpanded) {
            holder.llReplySection.visibility = View.VISIBLE
            setupReplySection(holder, context, post)
        } else {
            holder.llReplySection.visibility = View.GONE
            holder.rvReplies.adapter = null
            holder.replyAdapter = null
            holder.lastReplyDoc = null
            holder.isLoadingReplies = false
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
                    isEdited = false,
                    isPinned = false
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

        val isAnonymousPost = post.userName.equals("Anonymous User", ignoreCase = true) ||
                              post.userid.equals("anonymous", ignoreCase = true)

        if (isAnonymousPost) {
            holder.tvName.text = "Anonymous User (গোপন পোস্ট)"
            val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
            Glide.with(context).load(defaultAvatar).circleCrop().into(holder.ivAvatar)
        } else {
            holder.tvName.text = if (post.userName.isNotEmpty()) post.userName else currentUserName
            val avatarSource: Any = if (post.userAvatar.isNotEmpty()) post.userAvatar else try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
            Glide.with(context).load(avatarSource).circleCrop().into(holder.ivAvatar)
        }
    }

    private fun formatContentWithTags(
        context: Context,
        rawContent: String,
        isExpanded: Boolean,
        onToggleExpand: () -> Unit
    ): CharSequence {
        if (rawContent.isEmpty()) return ""

        val cleanForWordCount = rawContent.replace(Regex("<phone>|</phone>|<wa>|</wa>"), "").trim()
        val words = cleanForWordCount.split(Regex("\\s+"))
        val isLongText = words.size > 12

        val textToProcess = if (isExpanded || !isLongText) {
            rawContent
        } else {
            words.take(12).joinToString(" ")
        }

        val sb = SpannableStringBuilder()
        val tagRegex = Regex("""(<phone>.*?</phone>|<wa>.*?</wa>)""", RegexOption.IGNORE_CASE)
        var lastIndex = 0

        for (match in tagRegex.findAll(textToProcess)) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > lastIndex) {
                sb.append(textToProcess.substring(lastIndex, start))
            }

            val tagValue = match.value
            if (tagValue.startsWith("<phone>", ignoreCase = true)) {
                val number = tagValue.replace(Regex("(?i)</?phone>"), "").trim()
                val pillStart = sb.length
                sb.append("📞 $number")
                val pillEnd = sb.length

                sb.setSpan(ForegroundColorSpan(Color.parseColor("#006A4E")), pillStart, pillEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), pillStart, pillEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        showActionBottomSheet(context, "phone", number)
                    }
                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = Color.parseColor("#006A4E")
                        ds.isUnderlineText = false
                    }
                }, pillStart, pillEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            } else if (tagValue.startsWith("<wa>", ignoreCase = true)) {
                val number = tagValue.replace(Regex("(?i)</?wa>"), "").trim()
                val pillStart = sb.length
                sb.append("💬 $number (WhatsApp)")
                val pillEnd = sb.length

                sb.setSpan(ForegroundColorSpan(Color.parseColor("#25D366")), pillStart, pillEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), pillStart, pillEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        showActionBottomSheet(context, "wa", number)
                    }
                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = Color.parseColor("#25D366")
                        ds.isUnderlineText = false
                    }
                }, pillStart, pillEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            lastIndex = end
        }

        if (lastIndex < textToProcess.length) {
            sb.append(textToProcess.substring(lastIndex))
        }

        if (!isExpanded && isLongText) {
            val moreStart = sb.length
            sb.append(" ... more")
            val clickStart = moreStart + 5
            val clickEnd = sb.length

            sb.setSpan(ForegroundColorSpan(Color.parseColor("#006A4E")), clickStart, clickEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), clickStart, clickEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onToggleExpand()
                }
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = Color.parseColor("#006A4E")
                    ds.isUnderlineText = false
                }
            }, clickStart, clickEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return sb
    }

    private fun showActionBottomSheet(context: Context, type: String, rawNumber: String) {
        val dialog = BottomSheetDialog(context)
        val sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_news_action, null)
        dialog.setContentView(sheetView)

        val ivIcon = sheetView.findViewById<ImageView>(R.id.ivActionHeaderIcon)
        val tvNumber = sheetView.findViewById<TextView>(R.id.tvActionTargetNumber)
        val btnCopy = sheetView.findViewById<Button>(R.id.btnActionCopyNumber)
        val btnDirect = sheetView.findViewById<Button>(R.id.btnActionDirectOpen)
        val btnBrowser = sheetView.findViewById<Button>(R.id.btnActionOpenBrowser)

        val isWa = type.equals("wa", ignoreCase = true)
        tvNumber.text = if (isWa) "$rawNumber (WhatsApp)" else rawNumber

        if (isWa) {
            ivIcon.setImageResource(R.drawable.ic_whatsapp)
            ivIcon.setColorFilter(Color.parseColor("#25D366"))
            btnDirect.setBackgroundColor(Color.parseColor("#25D366"))
            btnDirect.text = "💬 WhatsApp-এ চ্যাট করুন"
        } else {
            ivIcon.setImageResource(R.drawable.ic_phone)
            ivIcon.setColorFilter(Color.parseColor("#006A4E"))
            btnDirect.setBackgroundColor(Color.parseColor("#006A4E"))
            btnDirect.text = "📞 সরাসরি কল করুন"
        }

        btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Contact Number", rawNumber)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "নম্বরটি কপি করা হয়েছে: $rawNumber", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnDirect.setOnClickListener {
            dialog.dismiss()
            if (isWa) {
                val cleanNum = if (rawNumber.startsWith("0")) "88$rawNumber" else if (rawNumber.startsWith("+")) rawNumber.removePrefix("+") else rawNumber
                val waUri = Uri.parse("https://wa.me/$cleanNum")
                val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    setPackage("com.whatsapp")
                }
                try {
                    context.startActivity(waIntent)
                } catch (e: Exception) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, waUri)
                        context.startActivity(browserIntent)
                    } catch (e2: Exception) {
                        Toast.makeText(context, "WhatsApp ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                try {
                    val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$rawNumber"))
                    context.startActivity(callIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "ডায়ালার ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnBrowser.setOnClickListener {
            dialog.dismiss()
            if (isWa) {
                val cleanNum = if (rawNumber.startsWith("0")) "88$rawNumber" else rawNumber
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum")))
                } catch (e: Exception) {}
            } else {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$rawNumber")))
                } catch (e: Exception) {}
            }
        }

        dialog.show()
    }

    override fun getItemCount(): Int = chatPosts.size

    fun setPosts(newPosts: List<OpenChatPost>) {
        chatPosts = newPosts.toList()
        notifyDataSetChanged()
    }

    private fun showEditPostDialog(context: Context, post: OpenChatPost, position: Int) {
        val etEdit = EditText(context).apply {
            setText(post.content)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.BLACK)
            textSize = 15f
        }

        MaterialAlertDialogBuilder(context, R.style.CustomDialogTheme)
            .setTitle("পোস্ট সম্পাদনা করুন")
            .setView(etEdit)
            .setPositiveButton("সেভ") { _, _ ->
                val newContent = etEdit.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    post.content = newContent
                    notifyItemChanged(position)

                    FirebaseFirestore.getInstance().collection("openchat").document(post.id)
                        .update("content", newContent)
                    TopNotification.show(context as? Activity, "পোস্টটি সফলভাবে আপডেট হয়েছে!")
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showDeletePostDialog(context: Context, post: OpenChatPost, position: Int) {
        MaterialAlertDialogBuilder(context, R.style.CustomDialogTheme)
            .setTitle("পোস্ট ডিলিট করুন")
            .setMessage("আপনি কি নিশ্চিত যে এই পোস্টটি মুছে ফেলতে চান?")
            .setPositiveButton("ডিলিট") { _, _ ->
                val mutableList = chatPosts.toMutableList()
                mutableList.removeAt(position)
                chatPosts = mutableList
                notifyDataSetChanged()

                FirebaseFirestore.getInstance().collection("openchat").document(post.id).delete()
                TopNotification.show(context as? Activity, "পোস্টটি ডিলিট করা হয়েছে!")
            }
            .setNegativeButton("না", null)
            .show()
    }

    private fun setupReplySection(holder: PostViewHolder, context: Context, post: OpenChatPost) {
        holder.rvReplies.layoutManager = LinearLayoutManager(context)
        holder.replyAdapter = ReplyAdapter(mutableListOf(), currentUid, post.id, post.userid)
        holder.rvReplies.adapter = holder.replyAdapter

        holder.lastReplyDoc = null
        holder.isLoadingReplies = false
        loadReplies(holder, post.id)

        holder.btnLoadMoreReplies.setOnClickListener {
            loadReplies(holder, post.id)
        }
    }

    private fun loadReplies(holder: PostViewHolder, postId: String) {
        if (holder.isLoadingReplies) return
        holder.isLoadingReplies = true

        var query = FirebaseFirestore.getInstance().collection("openchat").document(postId)
            .collection("post_reply").orderBy("uploadtime", Query.Direction.ASCENDING).limit(10)

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
                            isEdited = FirestoreSafeParser.parseBoolean(doc.get("isEdited"), false),
                            isPinned = FirestoreSafeParser.parseBoolean(doc.get("isPinned"), false)
                        )
                    } else null
                }
                holder.replyAdapter?.addReplies(replies)
                holder.btnLoadMoreReplies.visibility = if (snapshot.size() == 10) View.VISIBLE else View.GONE
            } else {
                holder.btnLoadMoreReplies.visibility = View.GONE
            }
            holder.isLoadingReplies = false
        }.addOnFailureListener { holder.isLoadingReplies = false }
    }
}
