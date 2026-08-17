package com.nur.sahayak

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.utils.TopNotification
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CreatePostActivity : AppCompatActivity() {

    private val maxWordLimit = 1001

    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvVisibilityStatus: TextView
    private lateinit var etContent: EditText
    private lateinit var tvWordCounter: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: ImageButton

    // Tools
    private lateinit var btnToolSchedule: MaterialButton
    private lateinit var btnToolTemporary: MaterialButton
    private lateinit var btnToolAnonymous: MaterialButton
    private lateinit var llActiveBadges: LinearLayout
    private lateinit var tvActiveScheduleBadge: TextView
    private lateinit var tvActiveExpiryBadge: TextView

    private var scheduledTimestamp: Long = 0L
    private var expiryTimestamp: Long = 0L
    private var isAnonymous: Boolean = false

    private var originalUserName = "লালপুরবাসী"
    private var originalUserAvatar = ""
    private var finalUid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        ivAvatar = findViewById(R.id.ivPostUserAvatar)
        tvName = findViewById(R.id.tvPostUserName)
        tvVisibilityStatus = findViewById(R.id.tvPostVisibilityStatus)
        etContent = findViewById(R.id.etPostContent)
        tvWordCounter = findViewById(R.id.tvWordCounter)
        btnSubmit = findViewById(R.id.btnSubmitPost)
        btnBack = findViewById(R.id.btnBackCreatePost)

        btnToolSchedule = findViewById(R.id.btnToolSchedule)
        btnToolTemporary = findViewById(R.id.btnToolTemporary)
        btnToolAnonymous = findViewById(R.id.btnToolAnonymous)
        llActiveBadges = findViewById(R.id.llActiveToolBadges)
        tvActiveScheduleBadge = findViewById(R.id.tvActiveScheduleBadge)
        tvActiveExpiryBadge = findViewById(R.id.tvActiveExpiryBadge)

        btnBack.setOnClickListener { finish() }

        loadUserData()
        setupTools()

        etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString().trim()
                val wordCount = if (text.isEmpty()) 0 else text.split(Regex("\\s+")).size
                tvWordCounter.text = "শব্দ সংখ্যা: $wordCount / $maxWordLimit"

                if (wordCount > maxWordLimit) {
                    tvWordCounter.setTextColor(Color.RED)
                    btnSubmit.isEnabled = false
                } else {
                    tvWordCounter.setTextColor(Color.parseColor("#666666"))
                    btnSubmit.isEnabled = true
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSubmit.setOnClickListener {
            submitPost()
        }
    }

    private fun loadUserData() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        val authUser = auth.currentUser
        val authUid = authUser?.uid ?: ""
        val prefUid = sharedPref.getString("user_uid", "") ?: ""
        finalUid = if (authUid.isNotEmpty()) authUid else if (prefUid.isNotEmpty()) prefUid else "user_${System.currentTimeMillis()}"

        originalUserName = sharedPref.getString("user_name", authUser?.displayName ?: "লালপুরবাসী") ?: "লালপুরবাসী"
        originalUserAvatar = sharedPref.getString("user_photo_url", authUser?.photoUrl?.toString() ?: "") ?: ""

        tvName.text = originalUserName

        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (originalUserAvatar.isNotEmpty()) {
            Glide.with(this).load(originalUserAvatar).circleCrop().into(ivAvatar)
        } else {
            ivAvatar.setImageResource(defaultAvatar)
        }
    }

    private fun setupTools() {
        btnToolSchedule.setOnClickListener {
            showMaterialSchedulePicker()
        }

        btnToolTemporary.setOnClickListener {
            showExpirySelectionDialog()
        }

        btnToolAnonymous.setOnClickListener {
            isAnonymous = !isAnonymous
            if (isAnonymous) {
                tvName.text = "Anonymous User"
                tvVisibilityStatus.text = "গোপন পরিচয় পোস্ট"
                ivAvatar.setImageResource(R.drawable.draft_user)
                btnToolAnonymous.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#006A4E"))
                btnToolAnonymous.setTextColor(Color.parseColor("#006A4E"))
                Toast.makeText(this, "Anonymous মোড চালু করা হয়েছে", Toast.LENGTH_SHORT).show()
            } else {
                tvName.text = originalUserName
                tvVisibilityStatus.text = "পাবলিক পোস্ট"
                loadUserData()
                btnToolAnonymous.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#CCCCCC"))
                btnToolAnonymous.setTextColor(Color.parseColor("#212121"))
                Toast.makeText(this, "Anonymous মোড বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Material Date & Time Picker (100% Glitch-free & Modern)
    private fun showMaterialSchedulePicker() {
        val now = System.currentTimeMillis()
        val maxScheduleMillis = now + (7L * 24 * 60 * 60 * 1000L)

        val constraints = CalendarConstraints.Builder()
            .setStart(now)
            .setEnd(maxScheduleMillis)
            .setValidator(DateValidatorPointForward.now())
            .build()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("সিডিউল তারিখ নির্বাচন করুন")
            .setSelection(now)
            .setCalendarConstraints(constraints)
            .build()

        datePicker.addOnPositiveButtonClickListener { selectedDateMillis ->
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(12)
                .setMinute(0)
                .setTitleText("সিডিউল সময় নির্বাচন করুন")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = selectedDateMillis
                    set(Calendar.HOUR_OF_DAY, timePicker.hour)
                    set(Calendar.MINUTE, timePicker.minute)
                    set(Calendar.SECOND, 0)
                }

                val finalSelectedMillis = cal.timeInMillis
                if (finalSelectedMillis <= System.currentTimeMillis()) {
                    Toast.makeText(this, "বর্তমান সময়ের পরবর্তী কোনো সময় নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                } else if (finalSelectedMillis > maxScheduleMillis) {
                    Toast.makeText(this, "সর্বোচ্চ ৭ দিন পর্যন্ত সিডিউল করা যাবে", Toast.LENGTH_SHORT).show()
                } else {
                    scheduledTimestamp = finalSelectedMillis
                    updateToolBadges()
                    Toast.makeText(this, "পোস্ট সিডিউল করা হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
            timePicker.show(supportFragmentManager, "SCHEDULE_TIME_PICKER")
        }

        datePicker.show(supportFragmentManager, "SCHEDULE_DATE_PICKER")
    }

    private fun showExpirySelectionDialog() {
        val options = arrayOf("২৪ ঘণ্টা (১ দিন)", "৪৮ ঘণ্টা (২ দিন)", "৩ দিন", "৫ দিন", "৭ দিন (সর্বোচ্চ)", "মেয়াদ বাতিল করুন")
        val durations = arrayOf(
            24 * 3600 * 1000L,
            48 * 3600 * 1000L,
            3 * 24 * 3600 * 1000L,
            5 * 24 * 3600 * 1000L,
            7 * 24 * 3600 * 1000L,
            0L
        )

        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("পোস্টের মেয়াদ নির্বাচন করুন")
            .setItems(options) { _, which ->
                val selectedDuration = durations[which]
                if (selectedDuration == 0L) {
                    expiryTimestamp = 0L
                    Toast.makeText(this, "মেয়াদ বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
                } else {
                    expiryTimestamp = System.currentTimeMillis() + selectedDuration
                    Toast.makeText(this, "মেয়াদ নির্ধারণ করা হয়েছে: ${options[which]}", Toast.LENGTH_SHORT).show()
                }
                updateToolBadges()
            }
            .show()
    }

    private fun updateToolBadges() {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        if (scheduledTimestamp > 0) {
            tvActiveScheduleBadge.visibility = View.VISIBLE
            tvActiveScheduleBadge.text = "🕒 সিডিউল: ${sdf.format(Date(scheduledTimestamp))}"
            btnToolSchedule.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#006A4E"))
            btnToolSchedule.setTextColor(Color.parseColor("#006A4E"))
        } else {
            tvActiveScheduleBadge.visibility = View.GONE
            btnToolSchedule.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#CCCCCC"))
            btnToolSchedule.setTextColor(Color.parseColor("#212121"))
        }

        if (expiryTimestamp > 0) {
            tvActiveExpiryBadge.visibility = View.VISIBLE
            tvActiveExpiryBadge.text = "⏳ মেয়াদ শেষ: ${sdf.format(Date(expiryTimestamp))}"
            btnToolTemporary.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#E65100"))
            btnToolTemporary.setTextColor(Color.parseColor("#E65100"))
        } else {
            tvActiveExpiryBadge.visibility = View.GONE
            btnToolTemporary.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#CCCCCC"))
            btnToolTemporary.setTextColor(Color.parseColor("#212121"))
        }

        if (scheduledTimestamp > 0 || expiryTimestamp > 0) {
            llActiveBadges.visibility = View.VISIBLE
        } else {
            llActiveBadges.visibility = View.GONE
        }
    }

    private fun submitPost() {
        val content = etContent.text.toString().trim()
        val wordCount = if (content.isEmpty()) 0 else content.split(Regex("\\s+")).size

        if (content.isEmpty()) {
            Toast.makeText(this, "পোস্টের বক্তব্য লিখুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (wordCount > maxWordLimit) {
            Toast.makeText(this, "শব্দ সংখ্যা ১০০১ এর বেশি হতে পারবে না", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmit.isEnabled = false
        val nowTimestamp = if (scheduledTimestamp > 0) Timestamp(Date(scheduledTimestamp)) else Timestamp.now()

        val firestore = FirebaseFirestore.getInstance()
        val newDocRef = firestore.collection("openchat").document()
        val postId = newDocRef.id

        // Real user UID is ALWAYS stored in userid!
        val postMap = hashMapOf<String, Any>(
            "id" to postId,
            "userid" to finalUid,
            "userName" to if (isAnonymous) "Anonymous User" else originalUserName,
            "userAvatar" to if (isAnonymous) "" else originalUserAvatar,
            "content" to content,
            "uploadtime" to nowTimestamp,
            "scheduledTime" to scheduledTimestamp,
            "expiryTime" to expiryTimestamp,
            "isAnonymous" to isAnonymous,
            "likesCount" to 0,
            "repliesCount" to 0
        )

        newDocRef.set(postMap).addOnSuccessListener {
            TopNotification.show(this, if (scheduledTimestamp > 0) "পোস্টটি সিডিউল করা হয়েছে!" else "পোস্ট সফলভাবে প্রকাশিত হয়েছে!")
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 1200)
        }.addOnFailureListener { err ->
            btnSubmit.isEnabled = true
            Toast.makeText(this, "পোস্ট ব্যর্থ: ${err.message}", Toast.LENGTH_LONG).show()
        }
    }
}
