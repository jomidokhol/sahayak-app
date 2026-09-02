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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.R
import com.nur.sahayak.models.EmergencyBloodPost
import com.nur.sahayak.utils.TimeUtils
import com.nur.sahayak.utils.TopNotification

class EmergencyBloodPostAdapter(
    private var postList: List<EmergencyBloodPost>,
    private val currentUid: String
) : RecyclerView.Adapter<EmergencyBloodPostAdapter.BloodPostViewHolder>() {

    class BloodPostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivCreatorAvatar: ImageView = itemView.findViewById(R.id.ivBloodPostCreatorAvatar)
        val tvCreatorName: TextView = itemView.findViewById(R.id.tvBloodPostCreatorName)
        val ivVerifiedBadge: ImageView = itemView.findViewById(R.id.ivBloodPostVerifiedBadge)
        val tvPostTime: TextView = itemView.findViewById(R.id.tvBloodPostTime)
        val btnMenu: ImageButton = itemView.findViewById(R.id.btnBloodPostMenu)

        val tvBloodGroup: TextView = itemView.findViewById(R.id.tvBloodPostGroup)
        val tvPatientName: TextView = itemView.findViewById(R.id.tvBloodPostPatientName)
        val tvBloodAmount: TextView = itemView.findViewById(R.id.tvBloodPostAmount)
        val tvHospital: TextView = itemView.findViewById(R.id.tvBloodPostHospital)
        val tvLocation: TextView = itemView.findViewById(R.id.tvBloodPostLocation)

        val tvDescription: TextView = itemView.findViewById(R.id.tvBloodPostDescription)
        val btnCall: Button = itemView.findViewById(R.id.btnBloodPostCall)
        val btnWhatsApp: Button = itemView.findViewById(R.id.btnBloodPostWhatsApp)
        val btnMessenger: Button = itemView.findViewById(R.id.btnBloodPostMessenger)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BloodPostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_blood_post_card, parent, false)
        return BloodPostViewHolder(view)
    }

    override fun onBindViewHolder(holder: BloodPostViewHolder, position: Int) {
        val post = postList[position]
        val context = holder.itemView.context

        holder.tvCreatorName.text = post.userName
        holder.ivVerifiedBadge.visibility = if (post.isVerified) View.VISIBLE else View.GONE
        holder.tvPostTime.text = TimeUtils.getTimeAgo(post.uploadtime)

        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (post.userAvatar.isNotEmpty()) {
            Glide.with(context)
                .load(post.userAvatar)
                .placeholder(defaultAvatar)
                .error(defaultAvatar)
                .circleCrop()
                .into(holder.ivCreatorAvatar)
        } else {
            Glide.with(context)
                .load(defaultAvatar)
                .circleCrop()
                .into(holder.ivCreatorAvatar)
        }

        holder.tvBloodGroup.text = post.bloodGroup
        holder.tvPatientName.text = post.patientName
        val formattedAmount = formatBloodAmount(post.bloodAmount)
        holder.tvBloodAmount.text = "$formattedAmount রক্ত প্রয়োজন"

        holder.tvHospital.text = post.hospitalName
        holder.tvLocation.text = post.locationAddress

        if (post.description.isNotEmpty()) {
            holder.tvDescription.visibility = View.VISIBLE
            holder.tvDescription.movementMethod = LinkMovementMethod.getInstance()
            holder.tvDescription.text = formatContentWithTags(context, post.description)
        } else {
            holder.tvDescription.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            showPostDetailsBottomSheet(context, post)
        }

        holder.btnMenu.setOnClickListener { anchor ->
            val wrapper = ContextThemeWrapper(context, R.style.PopupMenuThemeOverlay)
            val popup = PopupMenu(wrapper, anchor)

            val isPostOwner = currentUid.isNotEmpty() && (currentUid == post.userId)
            if (isPostOwner) {
                popup.menu.add(0, 1, 0, "এডিট করুন")
                popup.menu.add(0, 2, 1, "পোস্ট ডিলিট করুন")
            }
            popup.menu.add(0, 3, 2, "শেয়ার করুন")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        showEditBloodPostBottomSheet(context, post, position)
                        true
                    }
                    2 -> {
                        showDeletePostDialog(context, post, position)
                        true
                    }
                    3 -> {
                        shareBloodPost(context, post)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        if (post.mobile.isNotEmpty()) {
            holder.btnCall.visibility = View.VISIBLE
            holder.btnCall.setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${post.mobile}")))
            }
        } else {
            holder.btnCall.visibility = View.GONE
        }

        if (post.whatsapp.isNotEmpty()) {
            holder.btnWhatsApp.visibility = View.VISIBLE
            holder.btnWhatsApp.setOnClickListener {
                openWhatsApp(context, post.whatsapp)
            }
        } else {
            holder.btnWhatsApp.visibility = View.GONE
        }

        if (post.messenger.isNotEmpty()) {
            holder.btnMessenger.visibility = View.VISIBLE
            holder.btnMessenger.setOnClickListener {
                openMessenger(context, post.messenger)
            }
        } else {
            holder.btnMessenger.visibility = View.GONE
        }
    }

    private fun shareBloodPost(context: Context, post: EmergencyBloodPost) {
        val formattedAmount = formatBloodAmount(post.bloodAmount)
        val deepLinkUrl = "https://app-sahayak.vercel.app/need-emergency-blood/${post.id}"
        val shareInfo = "🩸 জরুরি রক্তের প্রয়োজন!\n\nরোগী: ${post.patientName}\nব্লাড গ্রুপ: ${post.bloodGroup}\nপরিমাণ: $formattedAmount\nহাসপাতাল: ${post.hospitalName}\nঠিকানা: ${post.locationAddress}\nমোবাইল: ${post.mobile}\nWhatsApp: ${post.whatsapp}\n\nবিস্তারিত দেখতে ক্লিক করুন:\n$deepLinkUrl"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareInfo)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "রক্তের রিকোয়েস্ট শেয়ার করুন"))
    }

    private fun formatBloodAmount(rawAmount: String): String {
        val trimmed = rawAmount.trim()
        if (trimmed.isEmpty()) return "১ ব্যাগ"
        val isOnlyDigits = trimmed.matches(Regex("^[0-9০-৯]+$"))
        return if (isOnlyDigits) {
            "$trimmed ব্যাগ"
        } else {
            trimmed
        }
    }

    private fun showEditBloodPostBottomSheet(context: Context, post: EmergencyBloodPost, position: Int) {
        val dialog = BottomSheetDialog(context)
        val sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_edit_blood_post, null)
        dialog.setContentView(sheetView)

        val etPatient = sheetView.findViewById<EditText>(R.id.etEditPatientName)
        val etAmount = sheetView.findViewById<EditText>(R.id.etEditBloodAmount)
        val btnSave = sheetView.findViewById<Button>(R.id.btnSaveEditBloodPost)
        val btnCancel = sheetView.findViewById<Button>(R.id.btnCancelEditBloodPost)

        etPatient.setText(post.patientName)
        etAmount.setText(post.bloodAmount)

        btnSave.setOnClickListener {
            val newPatient = etPatient.text.toString().trim()
            val newAmount = etAmount.text.toString().trim()

            if (newPatient.isEmpty() || newAmount.isEmpty()) {
                Toast.makeText(context, "রোগীর নাম ও রক্তের পরিমাণ দিন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updateMap = hashMapOf<String, Any>(
                "patientName" to newPatient,
                "bloodAmount" to newAmount
            )

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("emergency_blood_posts").document(post.id)
                .update(updateMap)

            if (post.userId.isNotEmpty()) {
                firestore.collection("users").document(post.userId)
                    .collection("my_blood_posts").document(post.id)
                    .update(updateMap)
            }

            val mutableList = postList.toMutableList()
            val updatedPost = post.copy(patientName = newPatient, bloodAmount = newAmount)
            mutableList[position] = updatedPost
            postList = mutableList
            notifyItemChanged(position)

            dialog.dismiss()
            TopNotification.show(context as? Activity, "পোস্ট সফলভাবে আপডেট হয়েছে!")
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPostDetailsBottomSheet(context: Context, post: EmergencyBloodPost) {
        val dialog = BottomSheetDialog(context)
        val sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_blood_post_detail, null)
        dialog.setContentView(sheetView)

        val tvGroup = sheetView.findViewById<TextView>(R.id.tvDetailBloodGroup)
        val tvPatient = sheetView.findViewById<TextView>(R.id.tvDetailPatientName)
        val tvAmount = sheetView.findViewById<TextView>(R.id.tvDetailBloodAmount)
        val tvHospital = sheetView.findViewById<TextView>(R.id.tvDetailHospital)
        val tvLocation = sheetView.findViewById<TextView>(R.id.tvDetailLocation)
        val tvDesc = sheetView.findViewById<TextView>(R.id.tvDetailDescription)

        val ivAvatar = sheetView.findViewById<ImageView>(R.id.ivDetailCreatorAvatar)
        val tvCreator = sheetView.findViewById<TextView>(R.id.tvDetailCreatorName)
        val ivVerified = sheetView.findViewById<ImageView>(R.id.ivDetailVerifiedBadge)
        val tvTime = sheetView.findViewById<TextView>(R.id.tvDetailPostTime)

        val btnCall = sheetView.findViewById<Button>(R.id.btnDetailCall)
        val btnWa = sheetView.findViewById<Button>(R.id.btnDetailWhatsApp)
        val btnMsg = sheetView.findViewById<Button>(R.id.btnDetailMessenger)
        val btnShare = sheetView.findViewById<Button>(R.id.btnDetailShare)
        val btnClose = sheetView.findViewById<Button>(R.id.btnDetailClose)

        tvGroup.text = post.bloodGroup
        tvPatient.text = "রোগী: ${post.patientName}"
        val formattedAmount = formatBloodAmount(post.bloodAmount)
        tvAmount.text = "প্রয়োজন: $formattedAmount"
        tvHospital.text = post.hospitalName
        tvLocation.text = post.locationAddress

        if (post.description.isNotEmpty()) {
            tvDesc.visibility = View.VISIBLE
            tvDesc.movementMethod = LinkMovementMethod.getInstance()
            tvDesc.text = formatContentWithTags(context, post.description)
        } else {
            tvDesc.text = "কোনো বিশেষ নির্দেশনা দেওয়া হয়নি।"
        }

        tvCreator.text = post.userName
        ivVerified.visibility = if (post.isVerified) View.VISIBLE else View.GONE
        tvTime.text = TimeUtils.getTimeAgo(post.uploadtime)

        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (post.userAvatar.isNotEmpty()) {
            Glide.with(context).load(post.userAvatar).placeholder(defaultAvatar).circleCrop().into(ivAvatar)
        } else {
            Glide.with(context).load(defaultAvatar).circleCrop().into(ivAvatar)
        }

        if (post.mobile.isNotEmpty()) {
            btnCall.visibility = View.VISIBLE
            btnCall.setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${post.mobile}")))
            }
        } else {
            btnCall.visibility = View.GONE
        }

        if (post.whatsapp.isNotEmpty()) {
            btnWa.visibility = View.VISIBLE
            btnWa.setOnClickListener {
                openWhatsApp(context, post.whatsapp)
            }
        } else {
            btnWa.visibility = View.GONE
        }

        if (post.messenger.isNotEmpty()) {
            btnMsg.visibility = View.VISIBLE
            btnMsg.setOnClickListener {
                openMessenger(context, post.messenger)
            }
        } else {
            btnMsg.visibility = View.GONE
        }

        btnShare.setOnClickListener {
            shareBloodPost(context, post)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun openWhatsApp(context: Context, rawNumber: String) {
        val cleanNum = if (rawNumber.startsWith("0")) "88$rawNumber" else if (rawNumber.startsWith("+")) rawNumber.removePrefix("+") else rawNumber
        val waUri = Uri.parse("https://wa.me/$cleanNum")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, waUri).apply { setPackage("com.whatsapp") })
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, waUri))
        }
    }

    private fun openMessenger(context: Context, rawLink: String) {
        val cleanLink = if (!rawLink.startsWith("http")) "https://$rawLink" else rawLink
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanLink)))
        } catch (e: Exception) {}
    }

    private fun showDeletePostDialog(context: Context, post: EmergencyBloodPost, position: Int) {
        MaterialAlertDialogBuilder(context, R.style.CustomDialogTheme)
            .setTitle("পোস্ট মুছে ফেলতে চান?")
            .setMessage("আপনি কি নিশ্চিত যে এই রক্তের পোস্টটি ডিলিট করতে চান?")
            .setPositiveButton("ডিলিট") { _, _ ->
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("emergency_blood_posts").document(post.id).delete()
                if (post.userId.isNotEmpty()) {
                    firestore.collection("users").document(post.userId).collection("my_blood_posts").document(post.id).delete()
                }

                val mutableList = postList.toMutableList()
                if (position in mutableList.indices) {
                    mutableList.removeAt(position)
                    postList = mutableList
                    notifyItemRemoved(position)
                }
                TopNotification.show(context as? Activity, "পোস্টটি ডিলিট করা হয়েছে!")
            }
            .setNegativeButton("না", null)
            .show()
    }

    private fun formatContentWithTags(context: Context, rawContent: String): CharSequence {
        val sb = SpannableStringBuilder()
        val tagRegex = Regex("""(<phone>.*?</phone>|<wa>.*?</wa>)""", RegexOption.IGNORE_CASE)
        var lastIndex = 0

        for (match in tagRegex.findAll(rawContent)) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > lastIndex) {
                sb.append(rawContent.substring(lastIndex, start))
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
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
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
                        openWhatsApp(context, number)
                    }
                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = Color.parseColor("#25D366")
                        ds.isUnderlineText = false
                    }
                }, pillStart, pillEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            lastIndex = end
        }

        if (lastIndex < rawContent.length) {
            sb.append(rawContent.substring(lastIndex))
        }

        return sb
    }

    override fun getItemCount(): Int = postList.size

    fun setPosts(newPosts: List<EmergencyBloodPost>) {
        postList = newPosts.toList()
        notifyDataSetChanged()
    }
}
