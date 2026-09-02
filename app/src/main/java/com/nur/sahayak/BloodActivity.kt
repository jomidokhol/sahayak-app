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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nur.sahayak.adapters.EmergencyBloodPostAdapter
import com.nur.sahayak.models.EmergencyBloodPost
import com.nur.sahayak.utils.FirestoreSafeParser
import com.nur.sahayak.utils.TopNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BloodActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etSearch: EditText
    private lateinit var btnActiveDonors: MaterialButton
    private lateinit var rvBloodPosts: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvEmpty: TextView
    private lateinit var fabCreatePost: FloatingActionButton

    private lateinit var adapter: EmergencyBloodPostAdapter
    private val allBloodPosts = mutableListOf<EmergencyBloodPost>()

    private var currentUid = ""
    private var targetDeepLinkPostId: String = ""
    private val emergencyBloodJsonFileName = "emergency_blood_backup.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blood)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        btnBack = findViewById(R.id.btnBackBlood)
        etSearch = findViewById(R.id.etSearchBloodPosts)
        btnActiveDonors = findViewById(R.id.btnActiveDonorsTop)
        rvBloodPosts = findViewById(R.id.rvBloodPosts)
        swipeRefresh = findViewById(R.id.swipeRefreshBloodPosts)
        tvEmpty = findViewById(R.id.tvEmptyBloodPosts)
        fabCreatePost = findViewById(R.id.fabCreateBloodPost)

        btnBack.setOnClickListener { finish() }

        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        extractTargetPostId(intent)

        rvBloodPosts.layoutManager = LinearLayoutManager(this)
        rvBloodPosts.isNestedScrollingEnabled = true
        adapter = EmergencyBloodPostAdapter(emptyList(), currentUid)
        rvBloodPosts.adapter = adapter

        checkFirstTimeOfflineGuide()
        fetchEmergencyBloodPosts()

        swipeRefresh.setOnRefreshListener {
            fetchEmergencyBloodPosts()
        }

        btnActiveDonors.setOnClickListener {
            startActivity(Intent(this, ActiveDonorsActivity::class.java))
        }

        fabCreatePost.setOnClickListener {
            handleCreatePostClick()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        rvBloodPosts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 12 && fabCreatePost.isShown) {
                    fabCreatePost.hide()
                } else if (dy < -12 && !fabCreatePost.isShown) {
                    fabCreatePost.show()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractTargetPostId(intent)
        fetchEmergencyBloodPosts()
    }

    private fun extractTargetPostId(intent: Intent?) {
        if (intent == null) return
        val directId = intent.getStringExtra("target_post_id")
        if (!directId.isNullOrEmpty()) {
            targetDeepLinkPostId = directId
            return
        }

        val uri = intent.data
        if (uri != null) {
            val pathSegments = uri.pathSegments
            if (pathSegments.isNotEmpty()) {
                targetDeepLinkPostId = pathSegments.last()
            }
        }
    }

    private fun checkFirstTimeOfflineGuide() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val hasSeenGuide = sharedPref.getBoolean("has_seen_blood_offline_guide", false)

        if (!hasSeenGuide) {
            MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                .setTitle("জরুরি অফলাইন সুবিধা 💡")
                .setMessage("জরুরি পরিস্থিতিতে ইন্টারনেট সংযোগ ছাড়াও রক্তের পোস্ট ও কন্টাক্ট নম্বর পাওয়ার জন্য Profile ➔ Settings ➔ 'ডাউনলোড কন্টাক্ট (JSON)' এবং 'অটো ডাউনলোড' চালু রাখুন।\n\nএর ফলে ব্যাকগ্রাউন্ডে নিয়মিত সকল তথ্য সংরক্ষিত থাকবে।")
                .setPositiveButton("সেটিংসে যান") { _, _ ->
                    sharedPref.edit().putBoolean("has_seen_blood_offline_guide", true).apply()
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("বুঝেছি", { _, _ ->
                    sharedPref.edit().putBoolean("has_seen_blood_offline_guide", true).apply()
                })
                .setCancelable(false)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""
    }

    private fun handleCreatePostClick() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)

        if (!isLoggedIn || currentUid.isEmpty()) {
            TopNotification.show(this, "জরুরি রক্তের পোস্ট দিতে লগইন করুন")
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            startActivity(Intent(this, CreateBloodPostActivity::class.java))
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun fetchEmergencyBloodPosts() {
        swipeRefresh.isRefreshing = true

        if (!isNetworkAvailable()) {
            loadBloodPostsFromLocalJson()
            return
        }

        FirebaseFirestore.getInstance().collection("emergency_blood_posts")
            .orderBy("uploadtime", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                allBloodPosts.clear()
                val now = System.currentTimeMillis()

                for (doc in snapshot.documents) {
                    val expiry = FirestoreSafeParser.parseLong(doc.get("expiryTime"), 0L)
                    val status = doc.getString("status") ?: "active"

                    if (status.equals("active", true) && (expiry == 0L || expiry > now)) {
                        val post = EmergencyBloodPost(
                            id = doc.id,
                            userId = FirestoreSafeParser.parseString(doc.get("userId")),
                            userName = FirestoreSafeParser.parseString(doc.get("userName"), "রক্তসন্ধানী"),
                            userAvatar = FirestoreSafeParser.parseString(doc.get("userAvatar")),
                            isVerified = FirestoreSafeParser.parseBoolean(doc.get("isVerified"), false),
                            patientName = FirestoreSafeParser.parseString(doc.get("patientName")),
                            bloodGroup = FirestoreSafeParser.parseString(doc.get("bloodGroup")),
                            bloodAmount = FirestoreSafeParser.parseString(doc.get("bloodAmount"), "১ ব্যাগ"),
                            hospitalName = FirestoreSafeParser.parseString(doc.get("hospitalName")),
                            locationAddress = FirestoreSafeParser.parseString(doc.get("locationAddress")),
                            mobile = FirestoreSafeParser.parseString(doc.get("mobile")),
                            whatsapp = FirestoreSafeParser.parseString(doc.get("whatsapp")),
                            messenger = FirestoreSafeParser.parseString(doc.get("messenger")),
                            description = FirestoreSafeParser.parseString(doc.get("description")),
                            uploadtime = FirestoreSafeParser.parseTimestampToMillis(doc.get("uploadtime")),
                            expiryTime = expiry,
                            status = status
                        )
                        allBloodPosts.add(post)
                    }
                }

                reorderTargetPostToTop()
                saveBloodPostsToLocalJson(allBloodPosts)
                applySearchFilter()
                swipeRefresh.isRefreshing = false
            }
            .addOnFailureListener {
                loadBloodPostsFromLocalJson()
            }
    }

    private fun reorderTargetPostToTop() {
        if (targetDeepLinkPostId.isNotEmpty()) {
            val targetIdx = allBloodPosts.indexOfFirst { it.id == targetDeepLinkPostId }
            if (targetIdx != -1) {
                val targetPost = allBloodPosts.removeAt(targetIdx)
                allBloodPosts.add(0, targetPost)
                rvBloodPosts.post {
                    rvBloodPosts.smoothScrollToPosition(0)
                }
            }
        }
    }

    private fun saveBloodPostsToLocalJson(posts: List<EmergencyBloodPost>) {
        try {
            val jsonArray = JSONArray()
            for (post in posts) {
                val obj = JSONObject().apply {
                    put("id", post.id)
                    put("userId", post.userId)
                    put("userName", post.userName)
                    put("userAvatar", post.userAvatar)
                    put("isVerified", post.isVerified)
                    put("patientName", post.patientName)
                    put("bloodGroup", post.bloodGroup)
                    put("bloodAmount", post.bloodAmount)
                    put("hospitalName", post.hospitalName)
                    put("locationAddress", post.locationAddress)
                    put("mobile", post.mobile)
                    put("whatsapp", post.whatsapp)
                    put("messenger", post.messenger)
                    put("description", post.description)
                    put("uploadtime", post.uploadtime)
                    put("expiryTime", post.expiryTime)
                    put("status", post.status)
                }
                jsonArray.put(obj)
            }

            val file = File(filesDir, emergencyBloodJsonFileName)
            file.writeText(jsonArray.toString())
            Log.d("BloodActivity", "Emergency Blood Posts cached locally: ${posts.size}")
        } catch (e: Exception) {
            Log.e("BloodActivity", "Error caching blood posts to JSON", e)
        }
    }

    private fun loadBloodPostsFromLocalJson() {
        try {
            val file = File(filesDir, emergencyBloodJsonFileName)
            if (file.exists()) {
                val jsonString = file.readText()
                val jsonArray = JSONArray(jsonString)
                allBloodPosts.clear()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val post = EmergencyBloodPost(
                        id = obj.optString("id"),
                        userId = obj.optString("userId"),
                        userName = obj.optString("userName", "রক্তসন্ধানী"),
                        userAvatar = obj.optString("userAvatar"),
                        isVerified = obj.optBoolean("isVerified", false),
                        patientName = obj.optString("patientName"),
                        bloodGroup = obj.optString("bloodGroup"),
                        bloodAmount = obj.optString("bloodAmount", "১ ব্যাগ"),
                        hospitalName = obj.optString("hospitalName"),
                        locationAddress = obj.optString("locationAddress"),
                        mobile = obj.optString("mobile"),
                        whatsapp = obj.optString("whatsapp"),
                        messenger = obj.optString("messenger"),
                        description = obj.optString("description"),
                        uploadtime = obj.optLong("uploadtime", System.currentTimeMillis()),
                        expiryTime = obj.optLong("expiryTime", 0L),
                        status = obj.optString("status", "active")
                    )
                    allBloodPosts.add(post)
                }

                reorderTargetPostToTop()
                applySearchFilter()
                TopNotification.show(this, "অফলাইন ব্যাকআপ থেকে রক্তের পোস্ট প্রদর্শিত হচ্ছে")
            } else {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "ইন্টারনেট নেই এবং কোনো অফলাইন ব্যাকআপ সংরক্ষিত নেই।"
            }
        } catch (e: Exception) {
            Log.e("BloodActivity", "Error loading offline blood posts JSON", e)
            tvEmpty.visibility = View.VISIBLE
        } finally {
            swipeRefresh.isRefreshing = false
        }
    }

    private fun applySearchFilter() {
        val query = etSearch.text.toString().trim()

        val filtered = if (query.isEmpty()) {
            allBloodPosts.toList()
        } else {
            allBloodPosts.filter { post ->
                post.bloodGroup.contains(query, ignoreCase = true) ||
                        post.patientName.contains(query, ignoreCase = true) ||
                        post.hospitalName.contains(query, ignoreCase = true) ||
                        post.locationAddress.contains(query, ignoreCase = true) ||
                        post.description.contains(query, ignoreCase = true)
            }
        }

        adapter.setPosts(filtered)

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvBloodPosts.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvBloodPosts.visibility = View.VISIBLE
        }
    }
}
