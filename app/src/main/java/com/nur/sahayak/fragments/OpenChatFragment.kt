package com.nur.sahayak.fragments

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nur.sahayak.R
import com.nur.sahayak.adapters.OpenChatAdapter
import com.nur.sahayak.models.OpenChatPost
import com.nur.sahayak.utils.FirestoreSafeParser

class OpenChatFragment : Fragment() {

    private lateinit var rvOpenChat: RecyclerView
    private lateinit var etChatSearch: EditText
    private lateinit var llSearchContainer: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvEmptyChat: TextView
    private lateinit var llChatSkeleton: LinearLayout
    private lateinit var adapter: OpenChatAdapter

    private var lastVisible: DocumentSnapshot? = null
    private var isLoading = false
    private var isLastPage = false
    private var allChatPosts = mutableListOf<OpenChatPost>()

    private var isSearchHidden = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_open_chat, container, false)

        rvOpenChat = view.findViewById(R.id.rvOpenChat)
        etChatSearch = view.findViewById(R.id.etChatSearch)
        llSearchContainer = view.findViewById(R.id.llChatSearchContainer)
        swipeRefresh = view.findViewById(R.id.swipeRefreshChat)
        tvEmptyChat = view.findViewById(R.id.tvEmptyChat)
        llChatSkeleton = view.findViewById(R.id.llChatSkeleton)

        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val authUser = FirebaseAuth.getInstance().currentUser
        val uid = authUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""
        val uName = sharedPref.getString("user_name", authUser?.displayName ?: "লালপুরবাসী") ?: "লালপুরবাসী"
        val uAvatar = sharedPref.getString("user_photo_url", authUser?.photoUrl?.toString() ?: "") ?: ""

        val layoutManager = LinearLayoutManager(context)
        rvOpenChat.layoutManager = layoutManager
        adapter = OpenChatAdapter(mutableListOf(), uid, uName, uAvatar)
        rvOpenChat.adapter = adapter

        fetchOpenChatPosts(isRefresh = true)

        swipeRefresh.setOnRefreshListener {
            fetchOpenChatPosts(isRefresh = true)
        }

        // Scroll-Down Hide & Scroll-Up Reveal for Search Bar
        rvOpenChat.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Search bar translation animation
                if (dy > 4 && !isSearchHidden) {
                    isSearchHidden = true
                    llSearchContainer.animate()
                        .translationY(-llSearchContainer.height.toFloat() - 20f)
                        .setInterpolator(DecelerateInterpolator())
                        .setDuration(220)
                        .start()
                } else if (dy < -4 && isSearchHidden) {
                    isSearchHidden = false
                    llSearchContainer.animate()
                        .translationY(0f)
                        .setInterpolator(DecelerateInterpolator())
                        .setDuration(220)
                        .start()
                }

                // Pagination Trigger
                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && !isLastPage) {
                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount) {
                            fetchOpenChatPosts(isRefresh = false)
                        }
                    }
                }
            }
        })

        etChatSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return view
    }

    private fun showSkeleton(show: Boolean) {
        if (show) {
            llChatSkeleton.visibility = View.VISIBLE
            rvOpenChat.visibility = View.GONE
            val pulse = AnimationUtils.loadAnimation(context, R.anim.shimmer_pulse)
            llChatSkeleton.startAnimation(pulse)
        } else {
            llChatSkeleton.clearAnimation()
            llChatSkeleton.visibility = View.GONE
            rvOpenChat.visibility = View.VISIBLE
        }
    }

    private fun fetchOpenChatPosts(isRefresh: Boolean) {
        if (isLoading) return
        isLoading = true

        if (isRefresh) {
            swipeRefresh.isRefreshing = true
            isLastPage = false
            lastVisible = null
            showSkeleton(true)
        }

        val firestore = FirebaseFirestore.getInstance()
        var query = firestore.collection("openchat")
            .orderBy("uploadtime", Query.Direction.DESCENDING)
            .limit(10)

        if (!isRefresh && lastVisible != null) {
            query = query.startAfter(lastVisible!!)
        }

        query.get().addOnSuccessListener { snapshot ->
            if (snapshot.size() < 10) {
                isLastPage = true
            }

            if (!snapshot.isEmpty) {
                lastVisible = snapshot.documents.last()
                val newPosts = snapshot.documents.mapNotNull { doc ->
                    val content = FirestoreSafeParser.parseString(doc.get("content"))
                    if (content.isNotEmpty()) {
                        val likedByRaw = doc.get("likedBy") as? List<*>
                        val likedByList = likedByRaw?.map { it.toString() } ?: emptyList()

                        val isVer = FirestoreSafeParser.parseBoolean(doc.get("isVerified"), false)
                        val verUntil = FirestoreSafeParser.parseLong(doc.get("verifiedUntil"), 0L)
                        val postImg = FirestoreSafeParser.parseString(doc.get("postImageUrl"))

                        OpenChatPost(
                            id = doc.id,
                            userid = FirestoreSafeParser.parseString(doc.get("userid")),
                            userName = FirestoreSafeParser.parseString(doc.get("userName"), "লালপুরবাসী"),
                            userAvatar = FirestoreSafeParser.parseString(doc.get("userAvatar")),
                            content = content,
                            postImageUrl = postImg,
                            isVerified = isVer,
                            verifiedUntil = verUntil,
                            uploadtime = FirestoreSafeParser.parseTimestampToMillis(doc.get("uploadtime")),
                            likesCount = FirestoreSafeParser.parseInt(doc.get("likesCount"), 0),
                            repliesCount = FirestoreSafeParser.parseInt(doc.get("repliesCount"), 0),
                            likedBy = likedByList
                        )
                    } else null
                }

                if (isRefresh) {
                    allChatPosts.clear()
                    allChatPosts.addAll(newPosts)
                } else {
                    allChatPosts.addAll(newPosts)
                }

                // Verified Priority Ranking: Verified Posts at Top, then by Newest uploadtime
                allChatPosts.sortWith(
                    compareByDescending<OpenChatPost> { it.isCreatorVerified }
                        .thenByDescending { it.uploadtime }
                )

                adapter.setPosts(allChatPosts)
            } else if (isRefresh) {
                allChatPosts.clear()
                adapter.setPosts(emptyList())
            }

            if (adapter.itemCount == 0) {
                tvEmptyChat.visibility = View.VISIBLE
            } else {
                tvEmptyChat.visibility = View.GONE
            }

            swipeRefresh.isRefreshing = false
            showSkeleton(false)
            isLoading = false
        }.addOnFailureListener {
            swipeRefresh.isRefreshing = false
            showSkeleton(false)
            isLoading = false
        }
    }

    private fun applySearchFilter() {
        val query = etChatSearch.text.toString().trim()

        val filteredList = if (query.isEmpty()) {
            allChatPosts.toList()
        } else {
            allChatPosts.filter {
                it.content.contains(query, ignoreCase = true) ||
                        it.userName.contains(query, ignoreCase = true)
            }
        }

        adapter.setPosts(filteredList)

        if (filteredList.isEmpty()) {
            tvEmptyChat.visibility = View.VISIBLE
            rvOpenChat.visibility = View.GONE
        } else {
            tvEmptyChat.visibility = View.GONE
            rvOpenChat.visibility = View.VISIBLE
        }
    }
}
