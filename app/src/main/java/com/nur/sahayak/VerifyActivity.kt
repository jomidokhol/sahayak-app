package com.nur.sahayak

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.utils.TopNotification

class VerifyActivity : AppCompatActivity() {

    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView

    private lateinit var card1M: MaterialCardView
    private lateinit var card6M: MaterialCardView
    private lateinit var card1Y: MaterialCardView

    private lateinit var tv1MPrice: TextView
    private lateinit var tv6MPrice: TextView
    private lateinit var tv1YPrice: TextView

    private lateinit var tvOriginal6M: TextView
    private lateinit var tvOriginal1Y: TextView
    private lateinit var tvDiscount6M: TextView
    private lateinit var tvDiscount1Y: TextView

    private lateinit var btnCheckout: Button
    private lateinit var btnBack: ImageButton

    private var selectedPlanType: String = ""
    private var selectedPlanTitle: String = ""
    private var selectedPlanDurationDays: Int = 0
    private var selectedFinalPrice: Int = 0
    private var selectedOriginalPrice: Int = 0

    private var baseMonthlyPrice: Int = 50
    private var discount6MPercent: Int = 15
    private var discount1YPercent: Int = 30

    private var currentUid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        ivAvatar = findViewById(R.id.ivPlanUserAvatar)
        tvName = findViewById(R.id.tvPlanUserName)

        card1M = findViewById(R.id.cardPlan1Month)
        card6M = findViewById(R.id.cardPlan6Month)
        card1Y = findViewById(R.id.cardPlan1Year)

        tv1MPrice = findViewById(R.id.tvPlan1MPrice)
        tv6MPrice = findViewById(R.id.tvPlan6MPrice)
        tv1YPrice = findViewById(R.id.tvPlan1YPrice)

        tvOriginal6M = findViewById(R.id.tvOriginalPrice6M)
        tvOriginal1Y = findViewById(R.id.tvOriginalPrice1Y)
        tvDiscount6M = findViewById(R.id.tvDiscountTag6M)
        tvDiscount1Y = findViewById(R.id.tvDiscountTag1Y)

        btnCheckout = findViewById(R.id.btnGoToCheckout)
        btnBack = findViewById(R.id.btnBackVerify)

        btnBack.setOnClickListener { finish() }

        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""
        val currentName = sharedPref.getString("user_name", "সম্মানিত ইউজার") ?: "সম্মানিত ইউজার"
        val photoUrl = sharedPref.getString("user_photo_url", "") ?: ""

        tvName.text = currentName
        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (photoUrl.isNotEmpty()) {
            Glide.with(this).load(photoUrl).circleCrop().into(ivAvatar)
        } else {
            ivAvatar.setImageResource(defaultAvatar)
        }

        // 1. Check if user already has an active plan
        checkActivePlanAndRedirect()

        // 2. Setup Strikethrough
        tvOriginal6M.paintFlags = tvOriginal6M.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        tvOriginal1Y.paintFlags = tvOriginal1Y.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

        // 3. Start Shimmer/Reflect Animation on Gold Discount Badges
        try {
            val shimmerAnim = AnimationUtils.loadAnimation(this, R.anim.badge_shimmer)
            tvDiscount6M.startAnimation(shimmerAnim)
            tvDiscount1Y.startAnimation(shimmerAnim)
        } catch (e: Exception) {}

        // 4. Load Dynamic Pricing from Firestore
        loadPricingFromFirestore()

        // 5. Setup Plan Click Listeners
        setupPlanClickListeners()

        btnCheckout.setOnClickListener {
            if (selectedPlanType.isEmpty()) {
                Toast.makeText(this, "অনুগ্রহ করে একটি প্ল্যান নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val checkoutIntent = Intent(this, CheckoutActivity::class.java).apply {
                putExtra("plan_type", selectedPlanType)
                putExtra("plan_title", selectedPlanTitle)
                putExtra("plan_duration_days", selectedPlanDurationDays)
                putExtra("final_price", selectedFinalPrice)
                putExtra("original_price", selectedOriginalPrice)
            }
            startActivity(checkoutIntent)
        }
    }

    private fun checkActivePlanAndRedirect() {
        if (currentUid.isEmpty()) return

        FirebaseFirestore.getInstance().collection("users").document(currentUid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val isVerified = doc.getBoolean("isVerified") ?: false
                    val verifiedUntil = doc.getLong("verifiedUntil") ?: 0L

                    if (isVerified && verifiedUntil > System.currentTimeMillis()) {
                        Toast.makeText(this, "আপনার একটি ভেরিফিকেশন প্ল্যান ইতিমধ্যে সক্রিয় রয়েছে!", Toast.LENGTH_LONG).show()
                        TopNotification.show(this, "আপনার একটি ভেরিফিকেশন প্ল্যান ইতিমধ্যে সক্রিয় রয়েছে!")
                        finish()
                    }
                }
            }
    }

    private fun loadPricingFromFirestore() {
        FirebaseFirestore.getInstance().collection("settings").document("verification_plans")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    baseMonthlyPrice = snapshot.getLong("monthlyPrice")?.toInt() ?: 50
                    discount6MPercent = snapshot.getLong("discount6m")?.toInt() ?: 15
                    discount1YPercent = snapshot.getLong("discount12m")?.toInt() ?: 30
                }
                calculateAndRenderPrices()
            }
    }

    private fun calculateAndRenderPrices() {
        val price1M = baseMonthlyPrice
        tv1MPrice.text = "৳$price1M"

        val orig6M = baseMonthlyPrice * 6
        val final6M = Math.ceil(orig6M * (1.0 - (discount6MPercent / 100.0))).toInt()
        tvOriginal6M.text = "৳$orig6M"
        tv6MPrice.text = "৳$final6M"
        tvDiscount6M.text = "$discount6MPercent% ছাড়"

        val orig1Y = baseMonthlyPrice * 12
        val final1Y = Math.ceil(orig1Y * (1.0 - (discount1YPercent / 100.0))).toInt()
        tvOriginal1Y.text = "৳$orig1Y"
        tv1YPrice.text = "৳$final1Y"
        tvDiscount1Y.text = "$discount1YPercent% ছাড়"

        when (selectedPlanType) {
            "1_month" -> {
                selectedFinalPrice = price1M
                selectedOriginalPrice = price1M
            }
            "6_months" -> {
                selectedFinalPrice = final6M
                selectedOriginalPrice = orig6M
            }
            "1_year" -> {
                selectedFinalPrice = final1Y
                selectedOriginalPrice = orig1Y
            }
        }
    }

    private fun setupPlanClickListeners() {
        card1M.setOnClickListener {
            selectPlanCard("1_month", "১ মাস মেয়াদী ভেরিফিকেশন", 30, baseMonthlyPrice, baseMonthlyPrice)
        }

        card6M.setOnClickListener {
            val orig = baseMonthlyPrice * 6
            val finalPrice = Math.ceil(orig * (1.0 - (discount6MPercent / 100.0))).toInt()
            selectPlanCard("6_months", "৬ মাস মেয়াদী ভেরিফিকেশন", 180, finalPrice, orig)
        }

        card1Y.setOnClickListener {
            val orig = baseMonthlyPrice * 12
            val finalPrice = Math.ceil(orig * (1.0 - (discount1YPercent / 100.0))).toInt()
            selectPlanCard("1_year", "১ বছর মেয়াদী ভেরিফিকেশন", 365, finalPrice, orig)
        }
    }

    private fun selectPlanCard(type: String, title: String, days: Int, finalPrice: Int, originalPrice: Int) {
        selectedPlanType = type
        selectedPlanTitle = title
        selectedPlanDurationDays = days
        selectedFinalPrice = finalPrice
        selectedOriginalPrice = originalPrice

        resetCardUI(card1M)
        resetCardUI(card6M)
        resetCardUI(card1Y)

        when (type) {
            "1_month" -> highlightCardUI(card1M)
            "6_months" -> highlightCardUI(card6M)
            "1_year" -> highlightCardUI(card1Y)
        }

        btnCheckout.text = "এগিয়ে যান (৳$finalPrice) ➔"
    }

    private fun resetCardUI(card: MaterialCardView) {
        card.strokeColor = Color.parseColor("#CCCCCC")
        card.strokeWidth = dpToPx(1.5f)
        card.setCardBackgroundColor(Color.WHITE)
    }

    private fun highlightCardUI(card: MaterialCardView) {
        card.strokeColor = Color.parseColor("#006A4E")
        card.strokeWidth = dpToPx(2.5f)
        card.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
