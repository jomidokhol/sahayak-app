package com.nur.sahayak.adapters

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nur.sahayak.R
import com.nur.sahayak.models.ActiveDonor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActiveDonorAdapter(
    private var donorList: List<ActiveDonor>
) : RecyclerView.Adapter<ActiveDonorAdapter.DonorViewHolder>() {

    class DonorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvBloodGroupBadge: TextView = itemView.findViewById(R.id.tvActiveDonorBloodGroupBadge)
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivActiveDonorAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvActiveDonorName)
        val ivVerifiedBadge: ImageView = itemView.findViewById(R.id.ivActiveDonorVerifiedBadge)
        val tvAddressHeader: TextView = itemView.findViewById(R.id.tvActiveDonorAddressHeader)
        
        val llStatusCapsule: LinearLayout = itemView.findViewById(R.id.llActiveDonorStatusCapsule)
        val ivStatusIcon: ImageView = itemView.findViewById(R.id.ivActiveDonorStatusIcon)
        val tvReadinessStatus: TextView = itemView.findViewById(R.id.tvActiveDonorReadinessStatus)
        val tvLastDonationDateText: TextView = itemView.findViewById(R.id.tvActiveDonorLastDonationDateText)
        val btnShare: ImageButton = itemView.findViewById(R.id.btnShareDonorCard)

        val btnCall: Button = itemView.findViewById(R.id.btnActiveDonorCall)
        val btnWhatsApp: Button = itemView.findViewById(R.id.btnActiveDonorWhatsApp)
        val btnMessenger: Button = itemView.findViewById(R.id.btnActiveDonorMessenger)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DonorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_active_donor_card, parent, false)
        return DonorViewHolder(view)
    }

    override fun onBindViewHolder(holder: DonorViewHolder, position: Int) {
        val donor = donorList[position]
        val context = holder.itemView.context

        holder.tvBloodGroupBadge.text = donor.bloodGroup
        holder.tvName.text = donor.name
        holder.ivVerifiedBadge.visibility = if (donor.isUserVerified) View.VISIBLE else View.GONE

        val addressParts = listOf(donor.village, donor.upazila, donor.district).filter { it.isNotEmpty() }
        holder.tvAddressHeader.text = if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else "লালপুর, নাটোর"

        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (donor.avatarUrl.isNotEmpty()) {
            Glide.with(context)
                .load(donor.avatarUrl)
                .placeholder(defaultAvatar)
                .error(defaultAvatar)
                .circleCrop()
                .into(holder.ivAvatar)
        } else {
            Glide.with(context)
                .load(defaultAvatar)
                .circleCrop()
                .into(holder.ivAvatar)
        }

        // Vector Icon & Pure Text Status
        if (donor.lastDonationTimestamp == 0L) {
            holder.tvReadinessStatus.text = "রক্তদানে সক্ষম"
            holder.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
            holder.llStatusCapsule.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
            holder.tvLastDonationDateText.text = "পূর্বে কখনো রক্তদান করেননি"
        } else {
            val sdf = SimpleDateFormat("dd MMMM, yyyy", Locale.getDefault())
            holder.tvReadinessStatus.text = "রক্তদানে সক্ষম"
            holder.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
            holder.llStatusCapsule.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
            holder.tvLastDonationDateText.text = "সর্বশেষ রক্তদান: ${sdf.format(Date(donor.lastDonationTimestamp))}"
        }

        // Share Button Action
        holder.btnShare.setOnClickListener {
            val encodedBlood = Uri.encode(donor.bloodGroup)
            val shareUrl = "https://app-sahayak.vercel.app/donar/$encodedBlood/${donor.uid}"
            val shareText = "🩸 জরুরি প্রয়োজনে রক্তদাতা:\nনাম: ${donor.name}\nব্লাড গ্রুপ: ${donor.bloodGroup}\nঠিকানা: ${holder.tvAddressHeader.text}\n\nবিস্তারিত যোগাযোগ ও প্রোফাইল লিংক:\n$shareUrl"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "রক্তদাতা: ${donor.name} (${donor.bloodGroup})")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, "রক্তদাতার তথ্য শেয়ার করুন"))
        }

        // Call Button
        if (donor.mobile.isNotEmpty()) {
            holder.btnCall.visibility = View.VISIBLE
            holder.btnCall.setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${donor.mobile}")))
            }
        } else {
            holder.btnCall.visibility = View.GONE
        }

        // WhatsApp Button
        if (donor.whatsapp.isNotEmpty()) {
            holder.btnWhatsApp.visibility = View.VISIBLE
            holder.btnWhatsApp.setOnClickListener {
                val cleanNum = if (donor.whatsapp.startsWith("0")) "88${donor.whatsapp}" else if (donor.whatsapp.startsWith("+")) donor.whatsapp.removePrefix("+") else donor.whatsapp
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum")).apply { setPackage("com.whatsapp") })
                } catch (e: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum")))
                }
            }
        } else {
            holder.btnWhatsApp.visibility = View.GONE
        }

        // Messenger Button
        if (donor.messenger.isNotEmpty()) {
            holder.btnMessenger.visibility = View.VISIBLE
            holder.btnMessenger.setOnClickListener {
                val cleanLink = if (!donor.messenger.startsWith("http")) "https://${donor.messenger}" else donor.messenger
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanLink)))
                } catch (e: Exception) {}
            }
        } else {
            holder.btnMessenger.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = donorList.size

    fun setDonors(newDonors: List<ActiveDonor>) {
        donorList = newDonors.toList()
        notifyDataSetChanged()
    }
}
