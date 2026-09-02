package com.nur.sahayak.adapters

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.R
import com.nur.sahayak.models.Contact
import com.nur.sahayak.utils.TopNotification

class UserContactAdapter(
    private var contactList: List<Contact>,
    private val onEditContact: (Contact, Int) -> Unit,
    private val onContactDeleted: (Contact) -> Unit
) : RecyclerView.Adapter<UserContactAdapter.UserContactViewHolder>() {

    class UserContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivUserContactAvatar)
        val tvCategory: TextView = itemView.findViewById(R.id.tvUserContactCategoryBadge)
        val tvStatus: TextView = itemView.findViewById(R.id.tvUserContactStatusBadge)
        val tvName: TextView = itemView.findViewById(R.id.tvUserContactName)
        val tvTitle: TextView = itemView.findViewById(R.id.tvUserContactTitle)
        val tvPhone: TextView = itemView.findViewById(R.id.tvUserContactPhone)
        val tvLocation: TextView = itemView.findViewById(R.id.tvUserContactLocation)
        val btnMenu: ImageButton = itemView.findViewById(R.id.btnUserContactMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_contact_card, parent, false)
        return UserContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserContactViewHolder, position: Int) {
        val contact = contactList[position]
        val context = holder.itemView.context

        holder.tvName.text = contact.name
        holder.tvTitle.text = if (contact.title.isNotEmpty()) contact.title else "বিবরণ নেই"
        holder.tvPhone.text = "📞 ${contact.phone}"
        holder.tvLocation.text = "📍 ${if (contact.location.isNotEmpty()) contact.location else "লালপুর"}"

        holder.tvCategory.text = getCategoryBanglaName(contact.category)

        if (contact.isApproved) {
            holder.tvStatus.text = "অনুমোদিত"
            holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
            holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
        } else {
            holder.tvStatus.text = "অপেক্ষমাণ (পেন্ডিং)"
            holder.tvStatus.setTextColor(Color.parseColor("#E65100"))
            holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFF3E0"))
        }

        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (!contact.imageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(contact.imageUrl)
                .placeholder(defaultAvatar)
                .error(defaultAvatar)
                .circleCrop()
                .into(holder.ivAvatar)
        } else {
            holder.ivAvatar.setImageResource(defaultAvatar)
        }

        holder.btnMenu.setOnClickListener { anchor ->
            val wrapper = ContextThemeWrapper(context, R.style.PopupMenuThemeOverlay)
            val popup = PopupMenu(wrapper, anchor)
            popup.menu.add(0, 1, 0, "এডিট করুন")
            popup.menu.add(0, 2, 1, "ডিলিট করুন")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        onEditContact(contact, position)
                        true
                    }
                    2 -> {
                        showDeleteConfirmDialog(context, contact, position)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun showDeleteConfirmDialog(context: Context, contact: Contact, position: Int) {
        MaterialAlertDialogBuilder(context, R.style.CustomDialogTheme)
            .setTitle("কন্টাক্ট ডিলিট")
            .setMessage("আপনি কি নিশ্চিত যে এই কন্টাক্টটি মুছে ফেলতে চান?")
            .setPositiveButton("ডিলিট") { _, _ ->
                FirebaseFirestore.getInstance().collection("contacts").document(contact.id).delete()
                    .addOnSuccessListener {
                        val mutable = contactList.toMutableList()
                        if (position in mutable.indices) {
                            mutable.removeAt(position)
                            contactList = mutable
                            notifyItemRemoved(position)
                        }
                        onContactDeleted(contact)
                        TopNotification.show(context as? Activity, "কন্টাক্ট সফলভাবে মুছে ফেলা হয়েছে!")
                    }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    override fun getItemCount(): Int = contactList.size

    fun setContacts(newContacts: List<Contact>) {
        contactList = newContacts.toList()
        notifyDataSetChanged()
    }

    private fun getCategoryBanglaName(cat: String): String {
        return when (cat.lowercase()) {
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
            "gas" -> "গ্যাস সেবা"
            "ambulance" -> "অ্যাম্বুলেন্স"
            "courier" -> "কুরিয়ার"
            else -> "অন্যান্য সেবা"
        }
    }
}
