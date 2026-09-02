package com.nur.sahayak.fragments

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnimationUtils
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nur.sahayak.BloodActivity
import com.nur.sahayak.NewsDetailActivity
import com.nur.sahayak.R
import com.nur.sahayak.SettingsActivity
import com.nur.sahayak.adapters.EmergencyBloodPostAdapter
import com.nur.sahayak.adapters.HomeCategoryAdapter
import com.nur.sahayak.adapters.OpenChatAdapter
import com.nur.sahayak.models.EmergencyBloodPost
import com.nur.sahayak.models.News
import com.nur.sahayak.models.OpenChatPost
import com.nur.sahayak.utils.FirestoreSafeParser
import com.nur.sahayak.utils.LalpurInfoHelper
import com.nur.sahayak.utils.TwinklingStarsView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HomeFragment : Fragment() {

    private val adWebUrl = "https://lalpurapp.vercel.app/ad.html"

    private lateinit var swipeRefresh: SwipeRefreshLayout

    // 1. News Slider
    private lateinit var vpNewsSlider: ViewPager2
    private lateinit var llSliderSkeleton: LinearLayout
    private lateinit var ivSliderLoadingNewsImage: ImageView
    private var sliderHandler = Handler(Looper.getMainLooper())
    private var sliderRunnable: Runnable? = null

    // 2. Weather & Prayer
    private lateinit var cardWeatherPrayer: MaterialCardView
    private lateinit var cardWeatherPrayerSkeleton: MaterialCardView
    private lateinit var rlWeatherContainer: RelativeLayout
    private lateinit var twinklingStars: TwinklingStarsView
    private lateinit var ivWeatherIcon: ImageView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var tvWeatherCondition: TextView
    private lateinit var tvWeatherLocation: TextView
    private lateinit var tvCurrentWaqt: TextView
    private lateinit var tvWaqtEndTime: TextView
    private lateinit var tvNextWaqtTime: TextView

    // 3. Category
    private lateinit var rvCategories: RecyclerView
    private lateinit var tvViewAllServices: TextView

    // 4. Blood Posts
    private lateinit var llBloodSection: LinearLayout
    private lateinit var llBloodSkeleton: LinearLayout
    private lateinit var rvBloodPosts: RecyclerView
    private lateinit var btnViewMoreBlood: Button
    private lateinit var tvHeaderBloodMore: TextView
    private lateinit var bloodAdapter: EmergencyBloodPostAdapter
    private val bloodList = mutableListOf<EmergencyBloodPost>()

    // 5. Open Chat
    private lateinit var llChatSection: LinearLayout
    private lateinit var llChatSkeleton: LinearLayout
    private lateinit var rvChatPosts: RecyclerView
    private lateinit var btnViewMoreChat: Button
    private lateinit var tvHeaderChatMore: TextView
    private lateinit var chatAdapter: OpenChatAdapter
    private val chatList = mutableListOf<OpenChatPost>()

    // 6. 9:16 In-Feed Ad Window Views
    private lateinit var layoutNineSixteenParent: ConstraintLayout
    private lateinit var cardNineSixteenContainer: MaterialCardView
    private lateinit var wvNineSixteenAd: WebView
    private lateinit var llNineSixteenAdCover: LinearLayout
    private lateinit var flVerifiedAdLockOverlay: FrameLayout

    private var currentUid = ""
    private var currentUserName = "ইউজার"
    private var currentUserAvatar = ""
    private var isVerifiedUser = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        swipeRefresh = view.findViewById(R.id.swipeRefreshHome)

        // 1. Slider
        vpNewsSlider = view.findViewById(R.id.vpHomesNewsSlider)
        llSliderSkeleton = view.findViewById(R.id.llHomeSliderSkeleton)
        ivSliderLoadingNewsImage = view.findViewById(R.id.ivSliderLoadingNewsImage)

        // 2. Weather & Prayer
        cardWeatherPrayer = view.findViewById(R.id.cardWeatherAndPrayer)
        cardWeatherPrayerSkeleton = view.findViewById(R.id.cardWeatherPrayerSkeleton)
        rlWeatherContainer = view.findViewById(R.id.rlWeatherCardContainer)
        twinklingStars = view.findViewById(R.id.twinklingStarsView)
        ivWeatherIcon = view.findViewById(R.id.ivHomeWeatherIcon)
        tvWeatherTemp = view.findViewById(R.id.tvHomeWeatherTemp)
        tvWeatherCondition = view.findViewById(R.id.tvHomeWeatherCondition)
        tvWeatherLocation = view.findViewById(R.id.tvHomeWeatherLocation)
        tvCurrentWaqt = view.findViewById(R.id.tvHomeCurrentWaqt)
        tvWaqtEndTime = view.findViewById(R.id.tvHomeWaqtEndTime)
        tvNextWaqtTime = view.findViewById(R.id.tvHomeNextWaqtTime)

        // 3. Category
        rvCategories = view.findViewById(R.id.rvHomeCategories)
        tvViewAllServices = view.findViewById(R.id.tvHomeViewAllServices)

        // 4. Blood
        llBloodSection = view.findViewById(R.id.llHomeBloodSection)
        llBloodSkeleton = view.findViewById(R.id.llHomeBloodSkeleton)
        rvBloodPosts = view.findViewById(R.id.rvHomeBloodPosts)
        btnViewMoreBlood = view.findViewById(R.id.btnViewMoreBloodPosts)
        tvHeaderBloodMore = view.findViewById(R.id.tvHeaderBloodViewMore)

        // 5. Chat
        llChatSection = view.findViewById(R.id.llHomeChatSection)
        llChatSkeleton = view.findViewById(R.id.llHomeChatSkeleton)
        rvChatPosts = view.findViewById(R.id.rvHomeChatPosts)
        btnViewMoreChat = view.findViewById(R.id.btnViewMoreChatPosts)
        tvHeaderChatMore = view.findViewById(R.id.tvHeaderChatViewMore)

        // 6. 9:16 Ad Views
        layoutNineSixteenParent = view.findViewById(R.id.layoutNineSixteenAdParent)
        cardNineSixteenContainer = view.findViewById(R.id.cardNineSixteenAdContainer)
        wvNineSixteenAd = view.findViewById(R.id.wvNineSixteenAd)
        llNineSixteenAdCover = view.findViewById(R.id.llNineSixteenAdCover)
        flVerifiedAdLockOverlay = view.findViewById(R.id.flVerifiedAdLockOverlay)

        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""
        currentUserName = sharedPref.getString("user_name", "ইউজার") ?: "ইউজার"
        currentUserAvatar = sharedPref.getString("user_photo_url", "") ?: ""

        // Setup Categories (Green Cards with Colorful Icons)
        rvCategories.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvCategories.adapter = HomeCategoryAdapter { categoryKey ->
            ServicesFragment.initialCategory = categoryKey
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)
            bottomNav?.selectedItemId = R.id.nav_services
        }

        // Setup Blood
        rvBloodPosts.layoutManager = LinearLayoutManager(context)
        bloodAdapter = EmergencyBloodPostAdapter(emptyList(), currentUid)
        rvBloodPosts.adapter = bloodAdapter

        // Setup Chat
        rvChatPosts.layoutManager = LinearLayoutManager(context)
        chatAdapter = OpenChatAdapter(emptyList(), currentUid, currentUserName, currentUserAvatar)
        rvChatPosts.adapter = chatAdapter

        setupListeners()
        loadAllHomeData()

        swipeRefresh.setOnRefreshListener {
            loadAllHomeData()
        }

        checkAndShow30MinInterstitialAd()
        checkGitHubAppUpdate()

        return view
    }

    private fun setupListeners() {
        tvViewAllServices.setOnClickListener {
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)
            bottomNav?.selectedItemId = R.id.nav_services
        }

        val onBloodMoreClick = View.OnClickListener {
            startActivity(Intent(context, BloodActivity::class.java))
        }
        btnViewMoreBlood.setOnClickListener(onBloodMoreClick)
        tvHeaderBloodMore.setOnClickListener(onBloodMoreClick)

        val onChatMoreClick = View.OnClickListener {
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)
            bottomNav?.selectedItemId = R.id.nav_open_chat
        }
        btnViewMoreChat.setOnClickListener(onChatMoreClick)
        tvHeaderChatMore.setOnClickListener(onChatMoreClick)

        cardWeatherPrayer.setOnClickListener {
            showFullPrayerScheduleDialog()
        }
    }

    private fun loadAllHomeData() {
        swipeRefresh.isRefreshing = true
        showAllSkeletons(true)

        checkUserVerificationStatus()
        loadTopNewsCarousel()
        loadWeatherAndPrayerInfo()
        loadTop3BloodPosts()
        loadTop10ChatPosts()
        setupNineSixteenAdWindow()
    }

    private fun showAllSkeletons(show: Boolean) {
        if (show) {
            llSliderSkeleton.visibility = View.VISIBLE
            ivSliderLoadingNewsImage.startAnimation(AnimationUtils.loadAnimation(context, R.anim.pulse_fade))

            cardWeatherPrayerSkeleton.visibility = View.VISIBLE
            cardWeatherPrayer.visibility = View.GONE
            cardWeatherPrayerSkeleton.startAnimation(AnimationUtils.loadAnimation(context, R.anim.shimmer_pulse))

            llBloodSkeleton.visibility = View.VISIBLE
            rvBloodPosts.visibility = View.GONE

            llChatSkeleton.visibility = View.VISIBLE
            rvChatPosts.visibility = View.GONE
        } else {
            llSliderSkeleton.visibility = View.GONE
            ivSliderLoadingNewsImage.clearAnimation()

            cardWeatherPrayerSkeleton.visibility = View.GONE
            cardWeatherPrayerSkeleton.clearAnimation()
            cardWeatherPrayer.visibility = View.VISIBLE

            llBloodSkeleton.visibility = View.GONE
            rvBloodPosts.visibility = View.VISIBLE

            llChatSkeleton.visibility = View.GONE
            rvChatPosts.visibility = View.VISIBLE
        }
    }

    private fun checkUserVerificationStatus() {
        if (currentUid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(currentUid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val isVer = doc.getBoolean("isVerified") ?: false
                        val verUntil = doc.getLong("verifiedUntil") ?: 0L
                        isVerifiedUser = isVer && verUntil > System.currentTimeMillis()

                        if (isVerifiedUser) {
                            flVerifiedAdLockOverlay.visibility = View.VISIBLE
                            wvNineSixteenAd.visibility = View.GONE
                        } else {
                            flVerifiedAdLockOverlay.visibility = View.GONE
                        }
                    }
                }
        }
    }

    private fun setupNineSixteenAdWindow() {
        if (isVerifiedUser) {
            flVerifiedAdLockOverlay.visibility = View.VISIBLE
            wvNineSixteenAd.visibility = View.GONE
            return
        }

        wvNineSixteenAd.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        wvNineSixteenAd.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!isVerifiedUser) {
                    llNineSixteenAdCover.visibility = View.GONE
                    wvNineSixteenAd.visibility = View.VISIBLE
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                layoutNineSixteenParent.visibility = View.GONE
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (errorResponse?.statusCode == 404 || (errorResponse?.statusCode ?: 200) >= 400) {
                    layoutNineSixteenParent.visibility = View.GONE
                }
            }
        }

        wvNineSixteenAd.loadUrl(adWebUrl)
    }

    private fun checkAndShow30MinInterstitialAd() {
        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        if (uid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val isVer = doc.getBoolean("isVerified") ?: false
                    val verUntil = doc.getLong("verifiedUntil") ?: 0L
                    val isVerUser = isVer && verUntil > System.currentTimeMillis()
                    if (!isVerUser) {
                        evaluate30MinAdTrigger(sharedPref)
                    }
                }
        } else {
            evaluate30MinAdTrigger(sharedPref)
        }
    }

    private fun evaluate30MinAdTrigger(sharedPref: android.content.SharedPreferences) {
        val lastAdTime = sharedPref.getLong("last_interstitial_ad_time", 0L)
        val now = System.currentTimeMillis()
        val thirtyMinutesMillis = 30 * 60 * 1000L

        if (now - lastAdTime >= thirtyMinutesMillis) {
            showSquareAdDialog(sharedPref)
        }
    }

    private fun showSquareAdDialog(sharedPref: android.content.SharedPreferences) {
        try {
            val dialog = Dialog(requireContext())
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_interstitial_square_ad, null)
            dialog.setContentView(dialogView)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.setCancelable(false)

            val wvSquare = dialogView.findViewById<WebView>(R.id.wvSquareAd)
            val llCover = dialogView.findViewById<LinearLayout>(R.id.llSquareAdPlaceholder)
            val pbTimer = dialogView.findViewById<ProgressBar>(R.id.pbSquareAdTimer)
            val tvCountdown = dialogView.findViewById<TextView>(R.id.tvSquareAdCountdown)
            val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseSquareAd)

            wvSquare.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
            }

            wvSquare.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    llCover.visibility = View.GONE
                    wvSquare.visibility = View.VISIBLE
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    wvSquare.visibility = View.GONE
                    llCover.visibility = View.VISIBLE
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (errorResponse?.statusCode == 404 || (errorResponse?.statusCode ?: 200) >= 400) {
                        wvSquare.visibility = View.GONE
                        llCover.visibility = View.VISIBLE
                    }
                }
            }
            wvSquare.loadUrl(adWebUrl)

            // 10-Second Countdown & Progress Bar
            val countDownTimer = object : CountDownTimer(10000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / 1000).toInt()
                    val progress = 10 - secondsLeft
                    pbTimer.progress = progress
                    tvCountdown.text = "অপেক্ষা করুন ${LalpurInfoHelper.toBanglaDigits(secondsLeft.toString())} সে."
                }

                override fun onFinish() {
                    pbTimer.progress = 10
                    tvCountdown.visibility = View.GONE
                    btnClose.visibility = View.VISIBLE
                }
            }
            countDownTimer.start()

            btnClose.setOnClickListener {
                sharedPref.edit().putLong("last_interstitial_ad_time", System.currentTimeMillis()).apply()
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {}
    }

    private fun checkGitHubAppUpdate() {
        Thread {
            try {
                val url = URL("https://api.github.com/repos/nurmohammad25/sahayak-app/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val json = JSONObject(reader.readText())
                    reader.close()

                    val remoteVersion = json.optString("tag_name", "").removePrefix("v").trim()
                    val currentVersion = "1.8"

                    if (remoteVersion.isNotEmpty() && remoteVersion != currentVersion) {
                        Handler(Looper.getMainLooper()).post {
                            showUpdateNoticeDialog(remoteVersion)
                        }
                    }
                }
            } catch (e: Exception) {}
        }.start()
    }

    private fun showUpdateNoticeDialog(newVer: String) {
        if (!isAdded || activity == null) return

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("🚀 নতুন আপডেট উপলব্ধ (v$newVer)")
            .setMessage("সহায়ক অ্যাপের নতুন আপডেট প্রকাশিত হয়েছে। নতুন ফিচার, বাগ ফিক্স ও উন্নত পারফরম্যান্স পেতে সেটিংস পেজ থেকে আপডেট করুন।")
            .setPositiveButton("সেটিংসে যান") { _, _ ->
                startActivity(Intent(context, SettingsActivity::class.java))
            }
            .setNegativeButton("পরে", null)
            .show()
    }

    private fun loadTopNewsCarousel() {
        FirebaseFirestore.getInstance().collection("news_list")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { snapshot ->
                llSliderSkeleton.visibility = View.GONE
                ivSliderLoadingNewsImage.clearAnimation()

                val newsList = snapshot.documents.mapNotNull { doc ->
                    val title = FirestoreSafeParser.parseString(doc.get("title"))
                    if (title.isNotEmpty()) {
                        News(
                            id = doc.id,
                            title = title,
                            reporter = FirestoreSafeParser.parseString(doc.get("reporter")),
                            imageUrl = FirestoreSafeParser.parseString(doc.get("imageUrl")),
                            desc = FirestoreSafeParser.parseString(doc.get("desc")),
                            viewCount = FirestoreSafeParser.parseInt(doc.get("viewCount"), 0),
                            timestamp = FirestoreSafeParser.parseTimestampToMillis(doc.get("timestamp"))
                        )
                    } else null
                }

                if (newsList.isNotEmpty()) {
                    setupNewsCarousel(newsList)
                }
            }
            .addOnFailureListener {
                llSliderSkeleton.visibility = View.GONE
                ivSliderLoadingNewsImage.clearAnimation()
            }
    }

    private fun setupNewsCarousel(newsList: List<News>) {
        sliderRunnable?.let { sliderHandler.removeCallbacks(it) }

        vpNewsSlider.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_home_news_carousel, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val news = newsList[position % newsList.size]
                val v = holder.itemView
                val context = v.context

                val ivBlurBg = v.findViewById<ImageView>(R.id.ivCarouselBlurBg)
                val ivPlaceholder = v.findViewById<ImageView>(R.id.ivCarouselLoadingPlaceholder)
                val ivMain = v.findViewById<ImageView>(R.id.ivCarouselMainImage)
                val tvTitle = v.findViewById<TextView>(R.id.tvCarouselNewsTitle)

                tvTitle.text = news.title

                ivPlaceholder.visibility = View.VISIBLE
                ivPlaceholder.startAnimation(AnimationUtils.loadAnimation(context, R.anim.pulse_fade))

                if (news.imageUrl.isNotEmpty()) {
                    Glide.with(context)
                        .load(news.imageUrl)
                        .centerCrop()
                        .into(ivBlurBg)

                    Glide.with(context)
                        .load(news.imageUrl)
                        .fitCenter()
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                ivPlaceholder.clearAnimation()
                                ivPlaceholder.visibility = View.GONE
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                ivPlaceholder.clearAnimation()
                                ivPlaceholder.visibility = View.GONE
                                return false
                            }
                        })
                        .into(ivMain)
                } else {
                    ivPlaceholder.clearAnimation()
                    ivPlaceholder.visibility = View.GONE
                    ivBlurBg.setImageResource(R.drawable.draft_cover)
                    ivMain.setImageResource(R.drawable.draft_cover)
                }

                v.setOnClickListener {
                    val intent = Intent(context, NewsDetailActivity::class.java).apply {
                        putExtra("id", news.id)
                        putExtra("title", news.title)
                        putExtra("reporter", news.reporter)
                        putExtra("imageUrl", news.imageUrl)
                        putExtra("desc", news.desc)
                        putExtra("viewCount", news.viewCount)
                        putExtra("timestamp", news.timestamp)
                    }
                    startActivity(intent)
                }
            }

            override fun getItemCount(): Int = if (newsList.size > 1) Int.MAX_VALUE else newsList.size
        }

        if (newsList.size > 1) {
            val startPos = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % newsList.size)
            vpNewsSlider.setCurrentItem(startPos, false)

            sliderRunnable = object : Runnable {
                override fun run() {
                    try {
                        vpNewsSlider.setCurrentItem(vpNewsSlider.currentItem + 1, true)
                        sliderHandler.postDelayed(this, 4000)
                    } catch (e: Exception) {}
                }
            }
            sliderHandler.postDelayed(sliderRunnable!!, 4000)
        }
    }

    private fun loadWeatherAndPrayerInfo() {
        val prayerSchedule = LalpurInfoHelper.calculateLalpurPrayers()
        tvCurrentWaqt.text = prayerSchedule.currentWaqtTitle
        tvWaqtEndTime.text = prayerSchedule.currentWaqtEndTime
        tvNextWaqtTime.text = "${prayerSchedule.nextWaqtTitle} (${prayerSchedule.nextWaqtStartTime})"

        // All text colors strictly White (#FFFFFF) with Deep Black Shadow
        tvWeatherTemp.setTextColor(Color.WHITE)
        tvWeatherCondition.setTextColor(Color.WHITE)
        tvWeatherLocation.setTextColor(Color.WHITE)
        tvCurrentWaqt.setTextColor(Color.WHITE)
        tvWaqtEndTime.setTextColor(Color.WHITE)
        tvNextWaqtTime.setTextColor(Color.WHITE)

        if (prayerSchedule.isDaytime) {
            rlWeatherContainer.setBackgroundResource(R.drawable.bg_weather_card_day)
            twinklingStars.setNightMode(false)
        } else {
            rlWeatherContainer.setBackgroundResource(R.drawable.bg_weather_card_night)
            twinklingStars.setNightMode(true)
        }

        LalpurInfoHelper.fetchLalpurWeather { temp, condition, iconRes, isDay ->
            tvWeatherTemp.text = temp
            tvWeatherCondition.text = condition
            ivWeatherIcon.setImageResource(iconRes)

            cardWeatherPrayerSkeleton.visibility = View.GONE
            cardWeatherPrayerSkeleton.clearAnimation()
            cardWeatherPrayer.visibility = View.VISIBLE
        }
    }

    private fun showFullPrayerScheduleDialog() {
        val s = LalpurInfoHelper.calculateLalpurPrayers()
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("🕌 লালপুর উপজেলার নামাজের পূর্ণাঙ্গ সময়সূচি")
            .setMessage("ফজর: ${s.fajr}\nযোহর: ${s.dhuhr}\nআসর: ${s.asr}\nমাগরিব: ${s.maghrib}\nএশা: ${s.isha}\n\n* ${s.currentWaqtTitle} (${s.currentWaqtEndTime})\n* ${s.nextWaqtTitle} (${s.nextWaqtStartTime})")
            .setPositiveButton("ঠিক আছে", null)
            .show()
    }

    private fun loadTop3BloodPosts() {
        FirebaseFirestore.getInstance().collection("emergency_blood_posts")
            .orderBy("uploadtime", Query.Direction.DESCENDING)
            .limit(3)
            .get()
            .addOnSuccessListener { snapshot ->
                bloodList.clear()
                val now = System.currentTimeMillis()

                for (doc in snapshot.documents) {
                    val expiry = FirestoreSafeParser.parseLong(doc.get("expiryTime"), 0L)
                    val status = doc.getString("status") ?: "active"

                    if (status.equals("active", true) && (expiry == 0L || expiry > now)) {
                        bloodList.add(
                            EmergencyBloodPost(
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
                        )
                    }
                }

                llBloodSkeleton.visibility = View.GONE
                if (bloodList.isNotEmpty()) {
                    llBloodSection.visibility = View.VISIBLE
                    rvBloodPosts.visibility = View.VISIBLE
                    bloodAdapter.setPosts(bloodList)
                } else {
                    llBloodSection.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                llBloodSkeleton.visibility = View.GONE
            }
    }

    private fun loadTop10ChatPosts() {
        FirebaseFirestore.getInstance().collection("openchat")
            .orderBy("uploadtime", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { snapshot ->
                chatList.clear()
                for (doc in snapshot.documents) {
                    val content = FirestoreSafeParser.parseString(doc.get("content"))
                    if (content.isNotEmpty()) {
                        val likedByRaw = doc.get("likedBy") as? List<*>
                        val likedByList = likedByRaw?.map { it.toString() } ?: emptyList()

                        chatList.add(
                            OpenChatPost(
                                id = doc.id,
                                userid = FirestoreSafeParser.parseString(doc.get("userid")),
                                userName = FirestoreSafeParser.parseString(doc.get("userName"), "লালপুরবাসী"),
                                userAvatar = FirestoreSafeParser.parseString(doc.get("userAvatar")),
                                content = content,
                                postImageUrl = FirestoreSafeParser.parseString(doc.get("postImageUrl")),
                                isVerified = FirestoreSafeParser.parseBoolean(doc.get("isVerified"), false),
                                verifiedUntil = FirestoreSafeParser.parseLong(doc.get("verifiedUntil"), 0L),
                                uploadtime = FirestoreSafeParser.parseTimestampToMillis(doc.get("uploadtime")),
                                likesCount = FirestoreSafeParser.parseInt(doc.get("likesCount"), 0),
                                repliesCount = FirestoreSafeParser.parseInt(doc.get("repliesCount"), 0),
                                likedBy = likedByList
                            )
                        )
                    }
                }

                llChatSkeleton.visibility = View.GONE
                if (chatList.isNotEmpty()) {
                    llChatSection.visibility = View.VISIBLE
                    rvChatPosts.visibility = View.VISIBLE
                    chatAdapter.setPosts(chatList)
                } else {
                    llChatSection.visibility = View.GONE
                }
                swipeRefresh.isRefreshing = false
            }
            .addOnFailureListener {
                llChatSkeleton.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sliderRunnable?.let { sliderHandler.removeCallbacks(it) }
    }
}
