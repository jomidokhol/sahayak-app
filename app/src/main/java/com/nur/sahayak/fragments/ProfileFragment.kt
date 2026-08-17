package com.nur.sahayak.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.DeveloperActivity
import com.nur.sahayak.EditProfileActivity
import com.nur.sahayak.R
import com.nur.sahayak.TermsActivity
import com.nur.sahayak.adapters.ProfilePostAdapter
import com.nur.sahayak.models.OpenChatPost
import com.nur.sahayak.utils.FirestoreSafeParser
import com.nur.sahayak.utils.TopNotification

class ProfileFragment : Fragment() {

    private lateinit var ivCover: ImageView
    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvMobile: TextView
    private lateinit var tvAge: TextView
    private lateinit var btnEdit: Button
    private lateinit var btnSettings: Button
    private lateinit var btnLogout: Button
    private lateinit var btnDeveloper: Button
    private lateinit var btnTerms: Button
    private lateinit var rvProfilePosts: RecyclerView
    private lateinit var tvNoProfilePosts: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var profilePostAdapter: ProfilePostAdapter

    private val myPostsList = mutableListOf<OpenChatPost>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        ivCover = view.findViewById(R.id.ivCoverPhoto)
        ivAvatar = view.findViewById(R.id.ivProfileAvatar)
        tvName = view.findViewById(R.id.tvProfileName)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        tvMobile = view.findViewById(R.id.tvProfileMobile)
        tvAge = view.findViewById(R.id.tvProfileAge)
        btnEdit = view.findViewById(R.id.btnEditProfile)
        btnSettings = view.findViewById(R.id.btnSettings)
        btnLogout = view.findViewById(R.id.btnLogout)
        btnDeveloper = view.findViewById(R.id.btnDeveloperPage)
        btnTerms = view.findViewById(R.id.btnTerms)
        rvProfilePosts = view.findViewById(R.id.rvProfilePosts)
        tvNoProfilePosts = view.findViewById(R.id.tvNoProfilePosts)
        swipeRefresh = view.findViewById(R.id.swipeRefreshProfile)

        val auth = FirebaseAuth.getInstance()
        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""
        val currentUName = sharedPref.getString("user_name", "লালপুরবাসী") ?: "লালপুরবাসী"
        val currentUAvatar = sharedPref.getString("user_photo_url", "") ?: ""

        rvProfilePosts.layoutManager = LinearLayoutManager(context)
        profilePostAdapter = ProfilePostAdapter(myPostsList, currentUid, currentUName, currentUAvatar)
        rvProfilePosts.adapter = profilePostAdapter

        loadUserData()

        swipeRefresh.setOnRefreshListener {
            loadUserData()
        }

        btnEdit.setOnClickListener {
            startActivity(Intent(context, EditProfileActivity::class.java))
        }

        btnSettings.setOnClickListener {
            TopNotification.show(activity, "সেটিংস পরবর্তীতে যুক্ত হচ্ছে")
        }

        btnLogout.setOnClickListener {
            performLogout()
        }

        btnDeveloper.setOnClickListener {
            startActivity(Intent(context, DeveloperActivity::class.java))
        }

        btnTerms.setOnClickListener {
            startActivity(Intent(context, TermsActivity::class.java))
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun loadUserData() {
        swipeRefresh.isRefreshing = true
        val auth = FirebaseAuth.getInstance()
        val firebaseUser = auth.currentUser
        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val uid = firebaseUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        val userName = sharedPref.getString("user_name", firebaseUser?.displayName ?: "লালপুরবাসী") ?: "লালপুরবাসী"
        val userEmail = firebaseUser?.email ?: "গুগল / ইমেইল ইউজার"

        tvName.text = userName
        tvEmail.text = userEmail

        if (uid.isNotEmpty()) {
            val firestore = FirebaseFirestore.getInstance()
            
            // 1. Fetch User Profile Data
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val mobile = doc.getString("mobile") ?: ""
                        val age = doc.getLong("age")?.toString() ?: ""
                        val photoUrl = doc.getString("photoUrl")
                        val coverUrl = doc.getString("coverUrl")

                        if (mobile.isNotEmpty()) tvMobile.text = mobile
                        if (age.isNotEmpty()) tvAge.text = "$age বছর"

                        if (!photoUrl.isNullOrEmpty()) {
                            Glide.with(this).load(photoUrl).circleCrop().into(ivAvatar)
                        } else {
                            val avatarSource: Any = firebaseUser?.photoUrl ?: try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
                            Glide.with(this).load(avatarSource).circleCrop().into(ivAvatar)
                        }

                        if (!coverUrl.isNullOrEmpty()) {
                            Glide.with(this).load(coverUrl).into(ivCover)
                        } else {
                            val coverRes = try { R.drawable.draft_cover } catch (e: Exception) { R.color.primary_green }
                            Glide.with(this).load(coverRes).into(ivCover)
                        }
                    }
                }

            // 2. Fetch User's Own Posts from openchat collection
            firestore.collection("openchat").whereEqualTo("userid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        swipeRefresh.isRefreshing = false
                        return@addSnapshotListener
                    }

                    myPostsList.clear()
                    for (doc in snapshot.documents) {
                        val content = FirestoreSafeParser.parseString(doc.get("content"))
                        if (content.isNotEmpty()) {
                            val likedByRaw = doc.get("likedBy") as? List<*>
                            val likedByList = likedByRaw?.map { it.toString() } ?: emptyList()

                            myPostsList.add(
                                OpenChatPost(
                                    id = doc.id,
                                    userid = FirestoreSafeParser.parseString(doc.get("userid")),
                                    userName = FirestoreSafeParser.parseString(doc.get("userName"), userName),
                                    userAvatar = FirestoreSafeParser.parseString(doc.get("userAvatar")),
                                    content = content,
                                    uploadtime = FirestoreSafeParser.parseTimestampToMillis(doc.get("uploadtime")),
                                    likesCount = FirestoreSafeParser.parseInt(doc.get("likesCount"), 0),
                                    repliesCount = FirestoreSafeParser.parseInt(doc.get("repliesCount"), 0),
                                    likedBy = likedByList
                                )
                            )
                        }
                    }

                    // Sort newest first
                    myPostsList.sortByDescending { it.uploadtime }
                    profilePostAdapter.setPosts(myPostsList)

                    if (myPostsList.isEmpty()) {
                        tvNoProfilePosts.visibility = View.VISIBLE
                        rvProfilePosts.visibility = View.GONE
                    } else {
                        tvNoProfilePosts.visibility = View.GONE
                        rvProfilePosts.visibility = View.VISIBLE
                    }

                    swipeRefresh.isRefreshing = false
                }
        } else {
            val avatarSource: Any = firebaseUser?.photoUrl ?: try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
            Glide.with(this).load(avatarSource).circleCrop().into(ivAvatar)
            val coverRes = try { R.drawable.draft_cover } catch (e: Exception) { R.color.primary_green }
            Glide.with(this).load(coverRes).into(ivCover)
            swipeRefresh.isRefreshing = false
        }
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        TopNotification.show(activity, "সফলভাবে লগআউট করা হয়েছে!")

        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav?.selectedItemId = R.id.nav_home
    }
}
