package com.nur.sahayak

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.fragments.HomeFragment
import com.nur.sahayak.fragments.NewsFragment
import com.nur.sahayak.fragments.OpenChatFragment
import com.nur.sahayak.fragments.ProfileFragment
import com.nur.sahayak.fragments.ServicesFragment
import com.nur.sahayak.utils.FirestoreSafeParser
import com.nur.sahayak.utils.MarqueeTextView
import com.nur.sahayak.utils.TopNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvNoticeText: MarqueeTextView
    private lateinit var btnHeaderLogin: MaterialButton
    private lateinit var btnHeaderAddPost: ImageButton
    private lateinit var btnHeaderSearch: ImageButton
    private lateinit var ivHeaderCrown: ImageButton
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var noticeBar: LinearLayout
    private lateinit var cardFloatingBlood: MaterialCardView

    private var userVerifiedUntil: Long = 0L

    // Runtime Notification Permission Request Launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.d("MainActivity", "Notification permission denied by user")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            window.statusBarColor = Color.TRANSPARENT
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )

            bottomNav = findViewById(R.id.bottomNavigation)
            bottomNav.itemIconTintList = null

            noticeBar = findViewById(R.id.noticeBar)
            val btnCloseNotice = findViewById<ImageView>(R.id.btnCloseNotice)
            tvNoticeText = findViewById(R.id.tvNoticeText)
            btnHeaderSearch = findViewById(R.id.btnHeaderSearch)
            btnHeaderAddPost = findViewById(R.id.btnHeaderAddPost)
            btnHeaderLogin = findViewById(R.id.btnHeaderLogin)
            ivHeaderCrown = findViewById(R.id.ivHeaderCrownBadge)
            cardFloatingBlood = findViewById(R.id.cardFloatingBlood)

            val fragmentContainer = findViewById<View>(R.id.fragmentContainer)
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                if (imeInsets.bottom > 0) {
                    bottomNav.visibility = View.GONE
                    cardFloatingBlood.visibility = View.GONE
                    fragmentContainer.setPadding(0, 0, 0, imeInsets.bottom)
                } else {
                    bottomNav.visibility = View.VISIBLE
                    cardFloatingBlood.visibility = View.VISIBLE
                    fragmentContainer.setPadding(0, 0, 0, 0)
                }
                insets
            }

            loadFirestoreNotice()
            askNotificationPermissionIfNeeded()

            btnCloseNotice.setOnClickListener {
                noticeBar.visibility = View.GONE
            }

            btnHeaderSearch.setOnClickListener {
                bottomNav.selectedItemId = R.id.nav_services
            }

            btnHeaderAddPost.setOnClickListener { anchor ->
                showFloatingPlusMenu(anchor)
            }

            ivHeaderCrown.setOnClickListener {
                showCrownExpiryCountdownDialog()
            }

            btnHeaderLogin.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }

            setupDraggableFloatingBloodButton()

            if (savedInstanceState == null) {
                loadFragment(HomeFragment(), "HOME")
            }

            if (intent?.data != null) {
                window.decorView.post {
                    handleDeepLink(intent)
                }
            }

            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> loadFragment(HomeFragment(), "HOME")
                    R.id.nav_services -> loadFragment(ServicesFragment(), "SERVICES")
                    R.id.nav_news -> loadFragment(NewsFragment(), "NEWS")
                    R.id.nav_open_chat -> loadFragment(OpenChatFragment(), "OPEN_CHAT")
                    R.id.nav_auth -> {
                        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
                        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)
                        if (isLoggedIn) {
                            loadFragment(ProfileFragment(), "PROFILE")
                        } else {
                            startActivity(Intent(this, LoginActivity::class.java))
                            false
                        }
                    }
                    else -> false
                }
            }

            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    try {
                        val currentFragment = supportFragmentManager.primaryNavigationFragment
                        if (currentFragment?.tag != "HOME") {
                            bottomNav.selectedItemId = R.id.nav_home
                        } else {
                            finish()
                        }
                    } catch (e: Exception) {
                        finish()
                    }
                }
            })

        } catch (e: Exception) {
            Log.e("MainActivity", "Fatal Inflation Error", e)
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupDraggableFloatingBloodButton() {
        var dX = 0f
        var dY = 0f
        var downRawX = 0f
        var downRawY = 0f
        var isDragging = false
        val clickThreshold = dpToPx(8f)

        cardFloatingBlood.setOnTouchListener { view, event ->
            val parentView = view.parent as? View ?: return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = Math.abs(event.rawX - downRawX)
                    val deltaY = Math.abs(event.rawY - downRawY)

                    if (deltaX > clickThreshold || deltaY > clickThreshold) {
                        isDragging = true
                    }

                    if (isDragging) {
                        val parentWidth = parentView.width.toFloat()
                        val parentHeight = parentView.height.toFloat()

                        val newX = event.rawX + dX
                        val newY = event.rawY + dY

                        val minX = 0f
                        val maxX = parentWidth - view.width.toFloat()
                        val minY = 0f
                        val maxY = parentHeight - view.height.toFloat()

                        view.x = newX.coerceIn(minX, maxX)
                        view.y = newY.coerceIn(minY, maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        startActivity(Intent(this, BloodActivity::class.java))
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        window.decorView.post {
            handleDeepLink(intent)
            handleNotificationNavigation(intent)
        }
    }

    private fun showFloatingPlusMenu(anchorView: View) {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)

        if (!isLoggedIn) {
            TopNotification.show(this, "পোস্ট বা কন্টাক্ট যুক্ত করতে লগইন করুন")
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }

        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_header_plus_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 20f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        val optionPost = popupView.findViewById<LinearLayout>(R.id.llMenuOptionPost)
        val optionContact = popupView.findViewById<LinearLayout>(R.id.llMenuOptionContact)
        val optionBloodPost = popupView.findViewById<LinearLayout>(R.id.llMenuOptionBloodPost)

        optionPost.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, CreatePostActivity::class.java))
        }

        optionContact.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, AddContactActivity::class.java))
        }

        optionBloodPost.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, CreateBloodPostActivity::class.java))
        }

        val xOffset = -dpToPx(150f)
        val yOffset = dpToPx(8f)
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)
    }

    private fun showCrownExpiryCountdownDialog() {
        val remainingMillis = userVerifiedUntil - System.currentTimeMillis()
        if (remainingMillis <= 0) {
            ivHeaderCrown.visibility = View.GONE
            return
        }

        val days = remainingMillis / (1000L * 60L * 60L * 24L)
        val hours = (remainingMillis / (1000L * 60L * 60L)) % 24L
        val minutes = (remainingMillis / (1000L * 60L)) % 60L

        val sdf = SimpleDateFormat("dd MMM, yyyy (hh:mm a)", Locale.getDefault())
        val expiryDateStr = sdf.format(Date(userVerifiedUntil))

        val countdownText = "$days দিন $hours ঘণ্টা $minutes মিনিট"

        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("👑 ভেরিফাইড মেম্বারশিপ স্ট্যাটাস")
            .setMessage("আপনার অ্যাকাউন্টে গোল্ডেন ভেরিফায়েড ক্রাউন সক্রিয় রয়েছে।\n\n⏳ মেয়াদ শেষ হতে বাকি:\n$countdownText\n\n📅 মেয়াদের শেষ তারিখ:\n$expiryDateStr")
            .setPositiveButton("ঠিক আছে", null)
            .show()
    }

    private fun handleNotificationNavigation(intent: Intent?) {
        val openTab = intent?.getStringExtra("open_tab") ?: return
        when (openTab) {
            "PROFILE" -> bottomNav.selectedItemId = R.id.nav_auth
            "SERVICES" -> bottomNav.selectedItemId = R.id.nav_services
            "PROFILE_SETTINGS" -> {
                bottomNav.selectedItemId = R.id.nav_auth
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val targetUri = intent?.data ?: return
        val scheme = targetUri.scheme ?: ""
        val host = targetUri.host ?: ""
        val pathSegments = targetUri.pathSegments ?: emptyList()

        val isTargetDomain = (scheme.equals("https", true) || scheme.equals("http", true)) && (host == "app-sahayak.vercel.app")
        val isCustomScheme = scheme.equals("sahayak", true)

        if (isTargetDomain || isCustomScheme) {
            var routeType = ""
            var param1 = ""
            var param2 = ""

            if (isCustomScheme && host != "app-sahayak.vercel.app" && host.isNotEmpty()) {
                routeType = host
                if (pathSegments.isNotEmpty()) param1 = pathSegments[0]
                if (pathSegments.size >= 2) param2 = pathSegments[1]
            } else if (pathSegments.isNotEmpty()) {
                routeType = pathSegments[0]
                if (pathSegments.size >= 2) param1 = pathSegments[1]
                if (pathSegments.size >= 3) param2 = pathSegments[2]
            }

            if (routeType.equals("need-emergency-blood", true) && param1.isNotEmpty()) {
                val bloodIntent = Intent(this, BloodActivity::class.java).apply {
                    putExtra("target_post_id", param1)
                }
                startActivity(bloodIntent)
                return
            }

            if (routeType.equals("contact", true) && param1.isNotEmpty()) {
                val category = param1
                val contactId = param2

                ServicesFragment.initialCategory = category
                ServicesFragment.targetContactId = contactId

                bottomNav.post {
                    bottomNav.selectedItemId = R.id.nav_services
                    val servicesFragment = supportFragmentManager.findFragmentByTag("SERVICES") as? ServicesFragment
                    servicesFragment?.checkAndApplyInitialCategory()
                }
            } else if (routeType.equals("news", true) && param1.isNotEmpty()) {
                val newsId = param1
                openNewsFromDeepLink(newsId)
            }
        }
    }

    private fun openNewsFromDeepLink(newsId: String) {
        FirebaseFirestore.getInstance().collection("news_list").document(newsId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val newsIntent = Intent(this, NewsDetailActivity::class.java).apply {
                        putExtra("id", doc.id)
                        putExtra("title", FirestoreSafeParser.parseString(doc.get("title")))
                        putExtra("reporter", FirestoreSafeParser.parseString(doc.get("reporter")))
                        putExtra("imageUrl", FirestoreSafeParser.parseString(doc.get("imageUrl")))
                        putExtra("desc", FirestoreSafeParser.parseString(doc.get("desc")))
                        putExtra("viewCount", FirestoreSafeParser.parseInt(doc.get("viewCount"), 0))
                        putExtra("timestamp", FirestoreSafeParser.parseTimestampToMillis(doc.get("timestamp")))
                    }
                    startActivity(newsIntent)
                } else {
                    TopNotification.show(this, "সংবাদটি খুঁজে পাওয়া যায়নি")
                }
            }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateAuthState()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating auth state", e)
        }
    }

    fun updateAuthState() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)
        val uid = sharedPref.getString("user_uid", "") ?: ""

        val menu = bottomNav.menu
        val authItem = menu.findItem(R.id.nav_auth)

        if (isLoggedIn && uid.isNotEmpty()) {
            btnHeaderLogin.visibility = View.GONE
            btnHeaderAddPost.visibility = View.VISIBLE
            authItem.title = "প্রোফাইল"

            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val isVerified = doc.getBoolean("isVerified") ?: false
                        userVerifiedUntil = doc.getLong("verifiedUntil") ?: 0L
                        val isCurrentlyVerified = isVerified && userVerifiedUntil > System.currentTimeMillis()

                        if (isCurrentlyVerified) {
                            ivHeaderCrown.visibility = View.VISIBLE
                        } else {
                            ivHeaderCrown.visibility = View.GONE
                        }
                    } else {
                        ivHeaderCrown.visibility = View.GONE
                    }
                }
                .addOnFailureListener {
                    ivHeaderCrown.visibility = View.GONE
                }

            val localPhotoUrl = sharedPref.getString("user_photo_url", "") ?: ""
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            val photoUrl = if (localPhotoUrl.isNotEmpty()) localPhotoUrl else firebaseUser?.photoUrl?.toString()

            val avatarSource: Any = if (!photoUrl.isNullOrEmpty()) photoUrl else try {
                R.drawable.draft_user
            } catch (e: Exception) {
                R.drawable.ic_profile
            }

            Glide.with(this)
                .asBitmap()
                .load(avatarSource)
                .circleCrop()
                .into(object : CustomTarget<Bitmap>(64, 64) {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        authItem.icon = BitmapDrawable(resources, resource)
                    }
                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                })

        } else {
            btnHeaderLogin.visibility = View.VISIBLE
            btnHeaderAddPost.visibility = View.GONE
            ivHeaderCrown.visibility = View.GONE
            authItem.setIcon(R.drawable.ic_login_colored)
            authItem.title = "লগইন"
        }
    }

    private fun loadFirestoreNotice() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("settings").document("notice")
                .addSnapshotListener { snapshot, _ ->
                    val notice = snapshot?.getString("text")?.trim() ?: ""
                    if (notice.isNotEmpty()) {
                        noticeBar.visibility = View.VISIBLE
                        tvNoticeText.setText(notice)
                    } else {
                        noticeBar.visibility = View.GONE
                    }
                }
        } catch (e: Exception) {
            noticeBar.visibility = View.GONE
        }
    }

    private fun loadFragment(fragment: Fragment, tag: String): Boolean {
        return try {
            val fm = supportFragmentManager
            val currentFragment = fm.primaryNavigationFragment

            if (currentFragment?.tag == tag) return false

            val transaction = fm.beginTransaction()

            var newFragment = fm.findFragmentByTag(tag)
            if (newFragment == null) {
                newFragment = fragment
                transaction.add(R.id.fragmentContainer, newFragment, tag)
            }

            if (currentFragment != null) {
                transaction.hide(currentFragment)
            }

            transaction.show(newFragment)
            transaction.setPrimaryNavigationFragment(newFragment)
            transaction.commit()
            true
        } catch (e: Exception) {
            Log.e("MainActivity", "Fragment transaction error", e)
            false
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
