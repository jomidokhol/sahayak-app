package com.nur.sahayak.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nur.sahayak.ContactItem
import com.nur.sahayak.R

class ContactAdapter(private var contacts: List<ContactItem>) :
    RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPhoto: ImageView = itemView.findViewById(R.id.ivContactPhoto)
        val tvCategory: TextView = itemView.findViewById(R.id.tvContactCategory)
        val tvName: TextView = itemView.findViewById(R.id.tvContactName)
        val tvTitle: TextView = itemView.findViewById(R.id.tvContactTitle)
        val tvLocation: TextView = itemView.findViewById(R.id.tvContactLocation)
        val ivWatermark: ImageView = itemView.findViewById(R.id.ivWatermark)
        val btnShare: ImageButton = itemView.findViewById(R.id.btnShareContact)
        val btnCall: Button = itemView.findViewById(R.id.btnCall)
        val btnWhatsapp: Button = itemView.findViewById(R.id.btnWhatsapp)
        val btnFacebook: Button = itemView.findViewById(R.id.btnFacebook)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact: ContactItem = contacts[position]
        val context = holder.itemView.context
        val categoryStr: String = contact.category
        
        holder.tvCategory.text = getCategoryTitle(categoryStr)
        holder.tvName.text = contact.name
        holder.tvTitle.text = if (contact.title.isEmpty()) "তথ্য নেই" else contact.title
        holder.tvLocation.text = if (contact.location.isEmpty()) "লালপুর" else contact.location

        // Load Square Contact Image with draft_con.png Placeholder
        val defaultCon = try { R.drawable.draft_con } catch (e: Exception) { R.drawable.flogo }

        if (contact.imageUrl.isNotEmpty()) {
            Glide.with(context)
                .load(contact.imageUrl)
                .placeholder(defaultCon)
                .error(defaultCon)
                .centerCrop()
                .into(holder.ivPhoto)
        } else {
            holder.ivPhoto.setImageResource(defaultCon)
        }

        // Watermark Icon
        val resName = getCategoryDrawableName(categoryStr)
        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
        if (resId != 0) {
            holder.ivWatermark.setImageResource(resId)
        } else {
            holder.ivWatermark.setImageResource(R.drawable.flogo)
        }

        // Deep Link Share Handler
        holder.btnShare.setOnClickListener {
            val catKey = if (contact.category.isNotEmpty()) contact.category else "other"
            val shareUrl = "https://app-sahayak.vercel.app/contact/$catKey/${contact.id}"
            val shareText = """
                📌 ${contact.name}
                ${contact.title}
                📞 মোবাইল: ${contact.phone}
                📍 ঠিকানা: ${contact.location}
                
                সহায়ক অ্যাপে বিস্তারিত দেখুন:
                $shareUrl
            """.trimIndent()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, contact.name)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, "কন্টাক্ট শেয়ার করুন"))
        }

        // Call Action
        holder.btnCall.setOnClickListener {
            if (contact.phone.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${contact.phone}")
                }
                context.startActivity(intent)
            }
        }

        // WhatsApp Action
        if (contact.whatsapp.isNotEmpty()) {
            holder.btnWhatsapp.visibility = View.VISIBLE
            holder.btnWhatsapp.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${contact.whatsapp}"))
                context.startActivity(intent)
            }
        } else {
            holder.btnWhatsapp.visibility = View.GONE
        }

        // Facebook Action
        if (contact.facebook.isNotEmpty()) {
            holder.btnFacebook.visibility = View.VISIBLE
            holder.btnFacebook.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(contact.facebook))
                context.startActivity(intent)
            }
        } else {
            holder.btnFacebook.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = contacts.size

    fun updateList(newList: List<ContactItem>) {
        contacts = newList
        notifyDataSetChanged()
    }

    private fun getCategoryDrawableName(cat: String): String {
        return when (cat) {
            "doctor" -> "cat_doctor"
            "hospital" -> "cat_hospital"
            "police" -> "cat_police"
            "fire" -> "cat_fire"
            "mechanic" -> "cat_mechanic"
            "electronics" -> "cat_electronics"
            "mobile" -> "cat_mobile"
            "grocery" -> "cat_grocery"
            "pharmacy" -> "cat_pharmacy"
            "diagnostic" -> "cat_diagnostic"
            "computer" -> "cat_computer"
            "hotel" -> "cat_hotel"
            "restaurant" -> "cat_restaurant"
            "petrol" -> "cat_petrol"
            "fuel" -> "cat_petrol"
            "gas" -> "cat_gas"
            "ambulance" -> "cat_ambulance"
            "courier" -> "cat_courier"
            else -> "cat_other"
        }
    }

    private fun getCategoryTitle(cat: String): String {
        return when (cat) {
            "doctor" -> "ডাক্তার"
            "hospital" -> "হাসপাতাল"
            "police" -> "পুলিশ স্টেশন"
            "fire" -> "ফায়ার সার্ভিস"
            "mechanic" -> "মেকানিক ও গ্যারেজ"
            "electronics" -> "ইলেকট্রনিক্স"
            "mobile" -> "মোবাইল সার্ভিস"
            "grocery" -> "মুদি খানা"
            "pharmacy" -> "ফার্মেসি"
            "diagnostic" -> "ডায়াগনস্টিক"
            "computer" -> "কম্পিউটার"
            "hotel" -> "হোটেল"
            "restaurant" -> "রেস্টুরেন্ট"
            "petrol" -> "পেট্রোল পাম্প"
            "fuel" -> "পেট্রোল পাম্প"
            "gas" -> "গ্যাস সেবা"
            "ambulance" -> "অ্যাম্বুলেন্স"
            "courier" -> "কুরিয়ার"
            else -> "অন্যান্য"
        }
    }
}
