package com.nur.sahayak

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.adapters.ActiveDonorAdapter
import com.nur.sahayak.models.ActiveDonor
import com.nur.sahayak.utils.TopNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ActiveDonorsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etSearch: EditText
    private lateinit var btnFilter: MaterialButton
    private lateinit var llSearchContainer: LinearLayout
    private lateinit var rvDonors: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvEmpty: TextView
    private lateinit var cardBecomeDonor: MaterialCardView

    private lateinit var adapter: ActiveDonorAdapter
    private val allEligibleDonors = mutableListOf<ActiveDonor>()
    private var selectedBloodFilter = "সকল"

    private var isCurrentUserDonor = false
    private var currentUid = ""
    private val donorJsonFileName = "donors_backup.json"

    private var targetBloodGroupParam = ""
    private var targetDonorUidParam = ""
    private var isSearchRowHidden = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_donors)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        btnBack = findViewById(R.id.btnBackActiveDonors)
        etSearch = findViewById(R.id.etSearchDonors)
        btnFilter = findViewById(R.id.btnOpenBloodFilter)
        llSearchContainer = findViewById(R.id.llSearchRowContainer)
        rvDonors = findViewById(R.id.rvActiveDonors)
        swipeRefresh = findViewById(R.id.swipeRefreshActiveDonors)
        tvEmpty = findViewById(R.id.tvEmptyActiveDonors)
        cardBecomeDonor = findViewById(R.id.cardBecomeDonorFloating)

        btnBack.setOnClickListener { finish() }

        rvDonors.layoutManager = LinearLayoutManager(this)
        adapter = ActiveDonorAdapter(emptyList())
        rvDonors.adapter = adapter

        targetBloodGroupParam = intent.getStringExtra("target_blood_group") ?: ""
        targetDonorUidParam = intent.getStringExtra("target_donor_uid") ?: ""

        if (targetBloodGroupParam.isNotEmpty()) {
            selectedBloodFilter = targetBloodGroupParam
            btnFilter.text = targetBloodGroupParam
        }

        checkFirstTimeOfflineGuide()
        checkUserDonorStatus()
        fetchEligibleActiveDonors()

        swipeRefresh.setOnRefreshListener {
            checkUserDonorStatus()
            fetchEligibleActiveDonors()
        }

        btnFilter.setOnClickListener {
            showBloodFilterBottomSheet()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchAndFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        cardBecomeDonor.setOnClickListener {
            handleBecomeDonorClick()
        }

        // Smooth Scroll-Down Hide (Underneath Header) & Scroll-Up Reveal
        rvDonors.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // 1. Search Bar slides under pinned Header
                if (dy > 4 && !isSearchRowHidden) {
                    isSearchRowHidden = true
                    llSearchContainer.animate()
                        .translationY(-llSearchContainer.height.toFloat() - 20f)
                        .setInterpolator(DecelerateInterpolator())
                        .setDuration(220)
                        .start()
                } else if (dy < -4 && isSearchRowHidden) {
                    isSearchRowHidden = false
                    llSearchContainer.animate()
                        .translationY(0f)
                        .setInterpolator(DecelerateInterpolator())
                        .setDuration(220)
                        .start()
                }

                // 2. Bottom Floating Button
                if (!isCurrentUserDonor) {
                    if (dy > 12 && cardBecomeDonor.visibility == View.VISIBLE) {
                        cardBecomeDonor.animate().translationY(cardBecomeDonor.height.toFloat() + 100f).setDuration(220).start()
                    } else if (dy < -12 && cardBecomeDonor.visibility == View.VISIBLE) {
                        cardBecomeDonor.animate().translationY(0f).setDuration(220).start()
                    }
                }
            }
        })
    }

    private fun checkFirstTimeOfflineGuide() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val hasSeenGuide = sharedPref.getBoolean("has_seen_donor_offline_guide", false)

        if (!hasSeenGuide) {
            MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                .setTitle("জরুরি অফলাইন সেবা 💡")
                .setMessage("জরুরি পরিস্থিতিতে ইন্টারনেট সংযোগ ছাড়াও সকল রক্তদাতার নম্বর ও তথ্য পাওয়ার জন্য সেটিংস থেকে 'ডাউনলোড কন্টাক্ট (JSON)' এবং 'অটো ডাউনলোড' সুইচ অন রাখুন।\n\nএর ফলে স্বয়ংক্রিয়ভাবে ব্যাকগ্রাউন্ডে নিয়মিত সকল রক্তদাতার তথ্য আপডেট হয়ে থাকবে।")
                .setPositiveButton("সেটিংসে যান") { _, _ ->
                    sharedPref.edit().putBoolean("has_seen_donor_offline_guide", true).apply()
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("বুঝেছি", { _, _ ->
                    sharedPref.edit().putBoolean("has_seen_donor_offline_guide", true).apply()
                })
                .setCancelable(false)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkUserDonorStatus()
    }

    private fun checkUserDonorStatus() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        if (currentUid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(currentUid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val isDonorReg = doc.getBoolean("isDonorRegistered") ?: false
                        val bloodGroup = doc.getString("bloodGroup") ?: ""
                        isCurrentUserDonor = isDonorReg && bloodGroup.isNotEmpty()

                        if (isCurrentUserDonor) {
                            cardBecomeDonor.visibility = View.GONE
                        } else {
                            cardBecomeDonor.visibility = View.VISIBLE
                        }
                    } else {
                        cardBecomeDonor.visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener {
                    cardBecomeDonor.visibility = View.VISIBLE
                }
        } else {
            isCurrentUserDonor = false
            cardBecomeDonor.visibility = View.VISIBLE
        }
    }

    private fun handleBecomeDonorClick() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)

        if (!isLoggedIn || currentUid.isEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    private fun showBloodFilterBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_blood_filter, null)
        dialog.setContentView(sheetView)

        val cgFilter = sheetView.findViewById<ChipGroup>(R.id.cgSheetBloodFilter)
        val btnApply = sheetView.findViewById<Button>(R.id.btnCloseBloodFilterSheet)

        for (i in 0 until cgFilter.childCount) {
            val chip = cgFilter.getChildAt(i) as? Chip
            val chipText = chip?.text?.toString() ?: ""
            if (selectedBloodFilter == "সকল" && chipText.contains("সকল")) {
                chip?.isChecked = true
            } else if (chipText.equals(selectedBloodFilter, ignoreCase = true)) {
                chip?.isChecked = true
            }
        }

        cgFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                val chipText = chip?.text?.toString() ?: "সকল"
                selectedBloodFilter = if (chipText.contains("সকল")) "সকল" else chipText
            }
        }

        btnApply.setOnClickListener {
            dialog.dismiss()
            btnFilter.text = if (selectedBloodFilter == "সকল") "ফিল্টার" else selectedBloodFilter
            applySearchAndFilter()
        }

        dialog.show()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun fetchEligibleActiveDonors() {
        swipeRefresh.isRefreshing = true

        if (!isNetworkAvailable()) {
            loadDonorsFromLocalJson()
            return
        }

        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("isDonorRegistered", true)
            .get()
            .addOnSuccessListener { snapshot ->
                allEligibleDonors.clear()

                for (doc in snapshot.documents) {
                    val bloodGroup = doc.getString("bloodGroup") ?: ""
                    val isVisible = doc.getBoolean("isDonorVisible") ?: true
                    val lastDonationTime = doc.getLong("lastDonationDateTimestamp") ?: 0L

                    if (bloodGroup.isNotEmpty() && isVisible) {
                        val fName = doc.getString("firstName") ?: ""
                        val lName = doc.getString("lastName") ?: ""
                        val fullName = if ("$fName $lName".trim().isNotEmpty()) "$fName $lName".trim() else (doc.getString("name") ?: "রক্তদাতা")

                        val donor = ActiveDonor(
                            uid = doc.id,
                            name = fullName,
                            avatarUrl = doc.getString("photoUrl") ?: "",
                            bloodGroup = bloodGroup,
                            district = doc.getString("district") ?: "নাটোর",
                            upazila = doc.getString("upazila") ?: "লালপুর",
                            village = doc.getString("village") ?: "",
                            mobile = doc.getString("mobile") ?: "",
                            whatsapp = doc.getString("whatsappUsername") ?: "",
                            messenger = doc.getString("messengerLink") ?: "",
                            isVerified = doc.getBoolean("isVerified") ?: false,
                            verifiedUntil = doc.getLong("verifiedUntil") ?: 0L,
                            lastDonationTimestamp = lastDonationTime,
                            isVisible = isVisible
                        )

                        if (donor.isReadyToDonate) {
                            allEligibleDonors.add(donor)
                        }
                    }
                }

                allEligibleDonors.sortWith(compareByDescending<ActiveDonor> { it.isUserVerified }.thenBy { it.name })

                saveDonorsToLocalJson(allEligibleDonors)
                applySearchAndFilter()
                swipeRefresh.isRefreshing = false
            }
            .addOnFailureListener {
                loadDonorsFromLocalJson()
            }
    }

    private fun saveDonorsToLocalJson(donors: List<ActiveDonor>) {
        try {
            val jsonArray = JSONArray()
            for (donor in donors) {
                val obj = JSONObject().apply {
                    put("uid", donor.uid)
                    put("name", donor.name)
                    put("avatarUrl", donor.avatarUrl)
                    put("bloodGroup", donor.bloodGroup)
                    put("district", donor.district)
                    put("upazila", donor.upazila)
                    put("village", donor.village)
                    put("mobile", donor.mobile)
                    put("whatsapp", donor.whatsapp)
                    put("messenger", donor.messenger)
                    put("isVerified", donor.isVerified)
                    put("verifiedUntil", donor.verifiedUntil)
                    put("lastDonationTimestamp", donor.lastDonationTimestamp)
                    put("isVisible", donor.isVisible)
                }
                jsonArray.put(obj)
            }

            val file = File(filesDir, donorJsonFileName)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("ActiveDonorsActivity", "Error caching donors to JSON", e)
        }
    }

    private fun loadDonorsFromLocalJson() {
        try {
            val file = File(filesDir, donorJsonFileName)
            if (file.exists()) {
                val jsonString = file.readText()
                val jsonArray = JSONArray(jsonString)
                allEligibleDonors.clear()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val donor = ActiveDonor(
                        uid = obj.optString("uid"),
                        name = obj.optString("name", "রক্তদাতা"),
                        avatarUrl = obj.optString("avatarUrl"),
                        bloodGroup = obj.optString("bloodGroup"),
                        district = obj.optString("district", "নাটোর"),
                        upazila = obj.optString("upazila", "লালপুর"),
                        village = obj.optString("village"),
                        mobile = obj.optString("mobile"),
                        whatsapp = obj.optString("whatsapp"),
                        messenger = obj.optString("messenger"),
                        isVerified = obj.optBoolean("isVerified", false),
                        verifiedUntil = obj.optLong("verifiedUntil", 0L),
                        lastDonationTimestamp = obj.optLong("lastDonationTimestamp", 0L),
                        isVisible = obj.optBoolean("isVisible", true)
                    )
                    if (donor.isReadyToDonate) {
                        allEligibleDonors.add(donor)
                    }
                }

                allEligibleDonors.sortWith(compareByDescending<ActiveDonor> { it.isUserVerified }.thenBy { it.name })
                applySearchAndFilter()
                TopNotification.show(this, "অফলাইন মেমোরি থেকে রক্তদাতা তথ্য প্রদর্শিত হচ্ছে")
            } else {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "ইন্টারনেট নেই এবং অফলাইন ব্যাকআপ সংরক্ষিত নেই।"
            }
        } catch (e: Exception) {
            tvEmpty.visibility = View.VISIBLE
        } finally {
            swipeRefresh.isRefreshing = false
        }
    }

    private fun applySearchAndFilter() {
        val query = etSearch.text.toString().trim()

        val filtered = allEligibleDonors.filter { donor ->
            val matchesBlood = if (selectedBloodFilter == "সকল") true else donor.bloodGroup.equals(selectedBloodFilter, ignoreCase = true)
            val matchesSearch = if (query.isEmpty()) true else {
                donor.name.contains(query, ignoreCase = true) ||
                        donor.village.contains(query, ignoreCase = true) ||
                        donor.upazila.contains(query, ignoreCase = true) ||
                        donor.district.contains(query, ignoreCase = true)
            }
            matchesBlood && matchesSearch
        }.toMutableList()

        if (targetDonorUidParam.isNotEmpty()) {
            val targetIndex = filtered.indexOfFirst { it.uid == targetDonorUidParam }
            if (targetIndex != -1) {
                val targetDonor = filtered.removeAt(targetIndex)
                filtered.add(0, targetDonor)
                rvDonors.post { rvDonors.smoothScrollToPosition(0) }
            } else {
                TopNotification.show(this, "লিঙ্কটি ভাঙা")
            }
            targetDonorUidParam = ""
        }

        adapter.setDonors(filtered)

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvDonors.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvDonors.visibility = View.VISIBLE
        }
    }
}
