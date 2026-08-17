package com.nur.sahayak.fragments

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nur.sahayak.Category
import com.nur.sahayak.R
import com.nur.sahayak.adapters.CategoryAdapter
import com.nur.sahayak.adapters.NewsCarouselAdapter
import com.nur.sahayak.adapters.OpenChatAdapter
import com.nur.sahayak.models.NewsItem
import com.nur.sahayak.models.OpenChatPost
import com.nur.sahayak.utils.FirestoreSafeParser

class HomeFragment : Fragment() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var vpNewsCarousel: ViewPager2
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var incNewsSkeleton: View
    
    private lateinit var rvHomeOpenChat: RecyclerView
    private lateinit var llHomeChatSkeleton: LinearLayout
    private lateinit var btnViewMoreChat: MaterialButton
    private lateinit var homeChatAdapter: OpenChatAdapter
    
    private lateinit var wvAdBrowser: WebView
    private lateinit var llAdPlaceholder: LinearLayout
    private lateinit var ivAdLogo: ImageView

    private val newsList = mutableListOf<NewsItem>()
    private val homeChatList = mutableListOf<OpenChatPost>()

    private val carouselHandler = Handler(Looper.getMainLooper())
    private val carouselRunnable = object : Runnable {
        override fun run() {
            if (newsList.size > 1 && ::vpNewsCarousel.isInitialized) {
                vpNewsCarousel.setCurrentItem(vpNewsCarousel.currentItem + 1, true)
                carouselHandler.postDelayed(this, 3500)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        try {
            rvCategories = view.findViewById(R.id.rvCategories)
            vpNewsCarousel = view.findViewById(R.id.vpNewsCarousel)
            swipeRefresh = view.findViewById(R.id.swipeRefreshHome)
            incNewsSkeleton = view.findViewById(R.id.incNewsSkeleton)
            
            rvHomeOpenChat = view.findViewById(R.id.rvHomeOpenChat)
            llHomeChatSkeleton = view.findViewById(R.id.llHomeChatSkeleton)
            btnViewMoreChat = view.findViewById(R.id.btnViewMoreChat)
            
            wvAdBrowser = view.findViewById(R.id.wvAdBrowser)
            llAdPlaceholder = view.findViewById(R.id.llAdPlaceholder)
            ivAdLogo = view.findViewById(R.id.ivAdLogo)

            setupCategoryGrid()
            setupHomeOpenChatAdapter()
            setupWebViewAd()

            loadInitialData()

            swipeRefresh.setOnRefreshListener {
                loadInitialData()
            }

            btnViewMoreChat.setOnClickListener {
                activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_open_chat
            }

        } catch (e: Exception) {
            Log.e("HomeFragment", "Inflate Error", e)
        }

        return view
    }

    private fun setupCategoryGrid() {
        val categories = listOf(
            Category("doctor", "ডাক্তার", "cat_doctor"),
            Category("hospital", "হাসপাতাল", "cat_hospital"),
            Category("police", "পুলিশ স্টেশন", "cat_police"),
            Category("fire", "ফায়ার সার্ভিস", "cat_fire"),
            Category("mechanic", "মেকানিক", "cat_mechanic"),
            Category("electronics", "ইলেকট্রনিক্স", "cat_electronics"),
            Category("mobile", "মোবাইল সার্ভিস", "cat_mobile"),
            Category("grocery", "মুদি খানা", "cat_grocery"),
            Category("pharmacy", "ফার্মেসি", "cat_pharmacy"),
            Category("diagnostic", "ডায়াগনস্টিক", "cat_diagnostic"),
            Category("computer", "কম্পিউটার", "cat_computer"),
            Category("hotel", "হোটেল", "cat_hotel"),
            Category("restaurant", "রেস্টুরেন্ট", "cat_restaurant"),
            Category("petrol", "পেট্রোল পাম্প", "cat_petrol"),
            Category("gas", "গ্যাস সেবা", "cat_gas"),
            Category("ambulance", "অ্যাম্বুলেন্স", "cat_ambulance"),
            Category("courier", "কুরিয়ার", "cat_courier"),
            Category("other", "অন্যান্য", "cat_other")
        )
        rvCategories.layoutManager = GridLayoutManager(context, 4)
        rvCategories.adapter = CategoryAdapter(categories) { category ->
            ServicesFragment.initialCategory = category.id
            val servicesFragment = parentFragmentManager.findFragmentByTag("SERVICES") as? ServicesFragment
            servicesFragment?.checkAndApplyInitialCategory()
            activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_services
        }
    }

    private fun setupHomeOpenChatAdapter() {
        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val uid = sharedPref.getString("user_uid", "") ?: ""
        val uName = sharedPref.getString("user_name", "লালপুরবাসী") ?: "লালপুরবাসী"
        val uAvatar = sharedPref.getString("user_photo_url", "") ?: ""

        rvHomeOpenChat.layoutManager = LinearLayoutManager(context)
        homeChatAdapter = OpenChatAdapter(mutableListOf(), uid, uName, uAvatar)
        rvHomeOpenChat.adapter = homeChatAdapter
    }

    private fun setupWebViewAd() {
        val pulse = AnimationUtils.loadAnimation(context, R.anim.shimmer_pulse)
        ivAdLogo.startAnimation(pulse)

        wvAdBrowser.settings.javaScriptEnabled = true
        wvAdBrowser.settings.domStorageEnabled = true
        wvAdBrowser.setOnTouchListener { _, _ -> true } 
        
        wvAdBrowser.webViewClient = object : WebViewClient() {
            var hasError = false
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                hasError = true
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                hasError = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!hasError) {
                    ivAdLogo.clearAnimation()
                    llAdPlaceholder.visibility = View.GONE
                    wvAdBrowser.visibility = View.VISIBLE
                } else {
                    ivAdLogo.clearAnimation()
                    llAdPlaceholder.visibility = View.VISIBLE
                    wvAdBrowser.visibility = View.GONE
                }
            }
        }
        
        wvAdBrowser.loadUrl("https://lalpurapp.vercel.app/ad.html")
    }

    private fun loadInitialData() {
        swipeRefresh.isRefreshing = true
        
        incNewsSkeleton.visibility = View.VISIBLE
        vpNewsCarousel.visibility = View.GONE
        
        llHomeChatSkeleton.visibility = View.VISIBLE
        rvHomeOpenChat.visibility = View.GONE
        btnViewMoreChat.visibility = View.GONE
        
        val pulse = AnimationUtils.loadAnimation(context, R.anim.shimmer_pulse)
        incNewsSkeleton.startAnimation(pulse)
        llHomeChatSkeleton.startAnimation(pulse)

        loadNewsListFromFirestore()
        loadTop10OpenChatPosts()
    }

    private fun loadNewsListFromFirestore() {
        FirebaseFirestore.getInstance().collection("news_list").addSnapshotListener { snapshot, _ ->
            try {
                carouselHandler.removeCallbacks(carouselRunnable)
                newsList.clear()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        newsList.add(NewsItem(
                            id = doc.id,
                            title = FirestoreSafeParser.parseString(doc.get("title")),
                            reporter = FirestoreSafeParser.parseString(doc.get("reporter")),
                            imageUrl = FirestoreSafeParser.parseString(doc.get("imageUrl")),
                            desc = FirestoreSafeParser.parseString(doc.get("desc")),
                            viewCount = FirestoreSafeParser.parseInt(doc.get("viewCount"), 0),
                            timestamp = FirestoreSafeParser.parseTimestampToMillis(doc.get("timestamp"))
                        ))
                    }
                }

                if (newsList.isEmpty()) {
                    newsList.add(NewsItem(title = "লালপুর উপজেলায় আপনাকে স্বাগতম!"))
                }

                vpNewsCarousel.adapter = NewsCarouselAdapter(newsList)

                if (newsList.size > 1) {
                    val startPosition = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % newsList.size)
                    vpNewsCarousel.setCurrentItem(startPosition, false)
                    carouselHandler.postDelayed(carouselRunnable, 3500)
                }

                incNewsSkeleton.clearAnimation()
                incNewsSkeleton.visibility = View.GONE
                vpNewsCarousel.visibility = View.VISIBLE
                swipeRefresh.isRefreshing = false
            } catch (e: Exception) {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadTop10OpenChatPosts() {
        FirebaseFirestore.getInstance().collection("openchat")
            .orderBy("uploadtime", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    llHomeChatSkeleton.visibility = View.GONE
                    return@addSnapshotListener
                }

                homeChatList.clear()
                for (doc in snapshot.documents) {
                    val content = FirestoreSafeParser.parseString(doc.get("content"))
                    if (content.isNotEmpty()) {
                        val likedByRaw = doc.get("likedBy") as? List<*>
                        val likedByList = likedByRaw?.map { it.toString() } ?: emptyList()

                        homeChatList.add(OpenChatPost(
                            id = doc.id,
                            userid = FirestoreSafeParser.parseString(doc.get("userid")),
                            userName = FirestoreSafeParser.parseString(doc.get("userName"), "লালপুরবাসী"),
                            userAvatar = FirestoreSafeParser.parseString(doc.get("userAvatar")),
                            content = content,
                            uploadtime = FirestoreSafeParser.parseTimestampToMillis(doc.get("uploadtime")),
                            likesCount = FirestoreSafeParser.parseInt(doc.get("likesCount"), 0),
                            repliesCount = FirestoreSafeParser.parseInt(doc.get("repliesCount"), 0),
                            likedBy = likedByList
                        ))
                    }
                }

                homeChatAdapter.setPosts(homeChatList)
                llHomeChatSkeleton.clearAnimation()
                llHomeChatSkeleton.visibility = View.GONE
                rvHomeOpenChat.visibility = View.VISIBLE
                
                if (homeChatList.isNotEmpty()) {
                    btnViewMoreChat.visibility = View.VISIBLE
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        carouselHandler.removeCallbacks(carouselRunnable)
    }
}
