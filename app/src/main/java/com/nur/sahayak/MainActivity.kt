package com.nur.sahayak

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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

class MainActivity : AppCompatActivity() {

    private lateinit var tvNoticeText: MarqueeTextView
    private lateinit var btnHeaderLogin: MaterialButton
    private lateinit var btnHeaderAddPost: ImageButton
    private lateinit var bottomNav: BottomNavigationView

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

            val noticeBar = findViewById<LinearLayout>(R.id.noticeBar)
            val btnCloseNotice = findViewById<ImageView>(R.id.btnCloseNotice)
            tvNoticeText = findViewById(R.id.tvNoticeText)
            val btnHeaderSearch = findViewById<ImageButton>(R.id.btnHeaderSearch)
            btnHeaderAddPost = findViewById(R.id.btnHeaderAddPost)
            btnHeaderLogin = findViewById(R.id.btnHeaderLogin)

            loadFirestoreNotice()

            btnCloseNotice.setOnClickListener {
                noticeBar.visibility = View.GONE
            }

            btnHeaderSearch.setOnClickListener {
                bottomNav.selectedItemId = R.id.nav_services
            }

            btnHeaderAddPost.setOnClickListener {
                showCreateOptionBottomSheet()
            }

            btnHeaderLogin.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }

            if (savedInstanceState == null) {
                loadFragment(HomeFragment(), "HOME")
            }

            // Handle Incoming Deep Link on App Launch
            handleDeepLink(intent)

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.host == "app-sahayak.vercel.app") {
            val pathSegments = data.pathSegments
            if (pathSegments.isNotEmpty()) {
                val routeType = pathSegments[0]

                // Route 1: Contact Deep Link (e.g. /contact/doctor/doc_123)
                if (routeType == "contact" && pathSegments.size >= 3) {
                    val category = pathSegments[1]
                    val contactId = pathSegments[2]

                    ServicesFragment.initialCategory = category
                    ServicesFragment.targetContactId = contactId

                    bottomNav.selectedItemId = R.id.nav_services
                    val servicesFragment = supportFragmentManager.findFragmentByTag("SERVICES") as? ServicesFragment
                    servicesFragment?.checkAndApplyInitialCategory()
                }

                // Route 2: News Deep Link (e.g. /news/news_123)
                else if (routeType == "news" && pathSegments.size >= 2) {
                    val newsId = pathSegments[1]
                    openNewsFromDeepLink(newsId)
                }
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

    private fun showCreateOptionBottomSheet() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)

        if (!isLoggedIn) {
            TopNotification.show(this, "পোস্ট বা কন্টাক্ট যুক্ত করতে লগইন করুন")
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }

        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_create_choice, null)
        dialog.setContentView(sheetView)

        val cardCreatePost = sheetView.findViewById<MaterialCardView>(R.id.cardOptionCreatePost)
        val cardAddContact = sheetView.findViewById<MaterialCardView>(R.id.cardOptionAddContact)

        cardCreatePost.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, CreatePostActivity::class.java))
        }

        cardAddContact.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AddContactActivity::class.java))
        }

        dialog.show()
    }

    private fun updateAuthState() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)

        val menu = bottomNav.menu
        val authItem = menu.findItem(R.id.nav_auth)

        if (isLoggedIn) {
            btnHeaderLogin.visibility = View.GONE
            btnHeaderAddPost.visibility = View.VISIBLE
            authItem.title = "প্রোফাইল"

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
            authItem.setIcon(R.drawable.ic_login)
            authItem.title = "লগইন"
        }
    }

    private fun loadFirestoreNotice() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("settings").document("notice")
                .addSnapshotListener { snapshot, _ ->
                    try {
                        val notice = snapshot?.getString("text")
                        tvNoticeText.setText(notice ?: "লালপুর উপজেলায় আপনাকে স্বাগতম!")
                    } catch (e: Exception) {
                        tvNoticeText.setText("লালপুর উপজেলায় আপনাকে স্বাগতম!")
                    }
                }
        } catch (e: Exception) {
            tvNoticeText.setText("লালপুর উপজেলায় আপনাকে স্বাগতম!")
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
}
