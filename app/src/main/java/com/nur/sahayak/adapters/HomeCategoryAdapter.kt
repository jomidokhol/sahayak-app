package com.nur.sahayak.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nur.sahayak.R

class HomeCategoryAdapter(
    private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<HomeCategoryAdapter.CategoryViewHolder>() {

    data class HomeCategoryItem(
        val key: String,
        val name: String,
        val iconRes: Int
    )

    private val categoryList = listOf(
        HomeCategoryItem("doctor", "ডাক্তার", R.drawable.cat_doctor),
        HomeCategoryItem("hospital", "হাসপাতাল", R.drawable.cat_hospital),
        HomeCategoryItem("ambulance", "অ্যাম্বুলেন্স", R.drawable.cat_ambulance),
        HomeCategoryItem("pharmacy", "ফার্মেসি", R.drawable.cat_pharmacy),
        HomeCategoryItem("diagnostic", "ডায়াগনস্টিক", R.drawable.cat_diagnostic),
        HomeCategoryItem("police", "পুলিশ", R.drawable.cat_police),
        HomeCategoryItem("fire", "ফায়ার সার্ভিস", R.drawable.cat_fire),
        HomeCategoryItem("mechanic", "মেকানিক", R.drawable.cat_mechanic),
        HomeCategoryItem("electronics", "ইলেকট্রনিক্স", R.drawable.cat_electronics),
        HomeCategoryItem("mobile", "মোবাইল", R.drawable.cat_mobile),
        HomeCategoryItem("computer", "কম্পিউটার", R.drawable.cat_computer),
        HomeCategoryItem("grocery", "মুদি খানা", R.drawable.cat_grocery),
        HomeCategoryItem("hotel", "হোটেল", R.drawable.cat_hotel),
        HomeCategoryItem("restaurant", "রেস্টুরেন্ট", R.drawable.cat_restaurant),
        HomeCategoryItem("courier", "কুরিয়ার", R.drawable.cat_courier),
        HomeCategoryItem("petrol", "পেট্রোল পাম্প", R.drawable.cat_petrol),
        HomeCategoryItem("gas", "গ্যাস সেবা", R.drawable.cat_gas),
        HomeCategoryItem("other", "অন্যান্য সেবা", R.drawable.cat_other)
    )

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivCategoryItemIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvCategoryItemName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val cat = categoryList[position]
        holder.tvName.text = cat.name
        holder.ivIcon.setImageResource(cat.iconRes)

        holder.itemView.setOnClickListener {
            onCategoryClick(cat.key)
        }
    }

    override fun getItemCount(): Int = categoryList.size
}
