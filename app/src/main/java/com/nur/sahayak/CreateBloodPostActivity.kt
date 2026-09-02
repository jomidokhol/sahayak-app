package com.nur.sahayak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.utils.TopNotification
import java.util.Date

class CreateBloodPostActivity : AppCompatActivity() {

    private val maxWordLimit = 300

    private lateinit var svRoot: ScrollView
    private lateinit var btnBack: ImageButton
    private lateinit var etPatientName: EditText
    private lateinit var btnSelectBloodGroup: MaterialButton
    private lateinit var etBloodAmount: EditText
    private lateinit var etHospitalName: EditText
    private lateinit var etLocationAddress: EditText

    private lateinit var etMobile: EditText
    private lateinit var etWhatsapp: EditText
    private lateinit var etMessenger: EditText

    private lateinit var etDescription: EditText
    private lateinit var tvWordCounter: TextView
    private lateinit var cardDetectedNumberHelper: MaterialCardView
    private lateinit var tvDetectedNumber: TextView
    private lateinit var btnSetPhone: Button
    private lateinit var btnSetWhatsApp: Button
    private lateinit var btnIgnoreNumber: ImageButton

    private lateinit var btnSelectExpiry: MaterialButton
    private lateinit var pbSubmit: ProgressBar
    private lateinit var btnSubmit: Button

    private var selectedBloodGroup = ""
    private var selectedDurationMillis = 24 * 3600 * 1000L // Default 24 Hours

    private var currentCandidateNumber = ""
    private val ignoredNumbersSet = mutableSetOf<String>()
    private var isSelfUpdatingText = false

    private var currentUid = ""
    private var currentUserName = "সম্মানিত রক্তসন্ধানী"
    private var currentUserAvatar = ""
    private var isCurrentUserVerified = false

    class BloodInlineActionSpan(
        val type: String,
        var number: String,
        drawable: Drawable
    ) : ImageSpan(drawable, ALIGN_BASELINE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_blood_post)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        svRoot = findViewById(R.id.svBloodPostRoot)
        btnBack = findViewById(R.id.btnBackCreateBloodPost)
        etPatientName = findViewById(R.id.etPatientName)
        btnSelectBloodGroup = findViewById(R.id.btnSelectBloodGroupPost)
        etBloodAmount = findViewById(R.id.etBloodAmount)
        etHospitalName = findViewById(R.id.etHospitalName)
        etLocationAddress = findViewById(R.id.etLocationAddress)

        etMobile = findViewById(R.id.etBloodMobile)
        etWhatsapp = findViewById(R.id.etBloodWhatsapp)
        etMessenger = findViewById(R.id.etBloodMessenger)

        etDescription = findViewById(R.id.etBloodDescription)
        tvWordCounter = findViewById(R.id.tvBloodWordCounter)
        cardDetectedNumberHelper = findViewById(R.id.cardBloodNumberHelper)
        tvDetectedNumber = findViewById(R.id.tvBloodDetectedNumber)
        btnSetPhone = findViewById(R.id.btnSetBloodPhone)
        btnSetWhatsApp = findViewById(R.id.btnSetBloodWhatsApp)
        btnIgnoreNumber = findViewById(R.id.btnIgnoreBloodNumber)

        btnSelectExpiry = findViewById(R.id.btnSelectBloodExpiry)
        pbSubmit = findViewById(R.id.pbBloodPostSubmit)
        btnSubmit = findViewById(R.id.btnSubmitBloodPost)

        btnBack.setOnClickListener { finish() }

        // Dynamic WindowInsets for Soft Keyboard handling
        val rootLayout = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            if (imeInsets.bottom > 0) {
                rootLayout.setPadding(0, 0, 0, imeInsets.bottom)
            } else {
                rootLayout.setPadding(0, 0, 0, 0)
            }
            insets
        }

        // Setup Precise Focused Auto-Scroll for all input fields
        setupFocusAutoScroll(etPatientName)
        setupFocusAutoScroll(etBloodAmount)
        setupFocusAutoScroll(etHospitalName)
        setupFocusAutoScroll(etLocationAddress)
        setupFocusAutoScroll(etMobile)
        setupFocusAutoScroll(etWhatsapp)
        setupFocusAutoScroll(etMessenger)
        setupFocusAutoScroll(etDescription)

        loadUserData()

        btnSelectBloodGroup.setOnClickListener {
            showBloodGroupSelectionDialog()
        }

        btnSelectExpiry.setOnClickListener {
            showExpirySelectionDialog()
        }

        setupNumberDetection()

        etDescription.movementMethod = LinkMovementMethod.getInstance()
        etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isSelfUpdatingText) return

                val rawText = s?.toString() ?: ""
                val cleanText = rawText.trim()
                val wordCount = if (cleanText.isEmpty()) 0 else cleanText.split(Regex("\\s+")).size
                tvWordCounter.text = "শব্দ সংখ্যা: $wordCount / $maxWordLimit"

                if (wordCount > maxWordLimit) {
                    tvWordCounter.setTextColor(Color.RED)
                    btnSubmit.isEnabled = false
                } else {
                    tvWordCounter.setTextColor(Color.parseColor("#666666"))
                    btnSubmit.isEnabled = true
                }

                scanTextForNumbers()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSubmit.setOnClickListener {
            submitEmergencyBloodPost()
        }
    }

    // Precise descendant coordinate offset scrolling to keep focused field in upper-middle view
    private fun setupFocusAutoScroll(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                svRoot.postDelayed({
                    val rect = Rect()
                    v.getDrawingRect(rect)
                    svRoot.offsetDescendantRectToMyCoords(v, rect)
                    val targetY = Math.max(0, rect.top - dpToPx(70f))
                    svRoot.smoothScrollTo(0, targetY)
                }, 200)
            }
        }
    }

    private fun loadUserData() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        val authUser = auth.currentUser
        currentUid = authUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        // Immediate session fallback
        currentUserName = sharedPref.getString("user_name", authUser?.displayName ?: "সম্মানিত রক্তসন্ধানী") ?: "সম্মানিত রক্তসন্ধানী"
        currentUserAvatar = sharedPref.getString("user_photo_url", authUser?.photoUrl?.toString() ?: "") ?: ""

        if (currentUid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(currentUid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val fName = doc.getString("firstName") ?: ""
                        val lName = doc.getString("lastName") ?: ""
                        val fullName = "$fName $lName".trim()
                        if (fullName.isNotEmpty()) {
                            currentUserName = fullName
                        } else {
                            val name = doc.getString("name") ?: ""
                            if (name.isNotEmpty()) currentUserName = name
                        }

                        val photo = doc.getString("photoUrl") ?: ""
                        if (photo.isNotEmpty()) {
                            currentUserAvatar = photo
                        }

                        val isVer = doc.getBoolean("isVerified") ?: false
                        val verUntil = doc.getLong("verifiedUntil") ?: 0L
                        isCurrentUserVerified = isVer && verUntil > System.currentTimeMillis()

                        val userMobile = doc.getString("mobile") ?: ""
                        val userWa = doc.getString("whatsappUsername") ?: ""
                        val userMsg = doc.getString("messengerLink") ?: ""

                        if (etMobile.text.isEmpty() && userMobile.isNotEmpty()) etMobile.setText(userMobile)
                        if (etWhatsapp.text.isEmpty() && userWa.isNotEmpty()) etWhatsapp.setText(userWa)
                        if (etMessenger.text.isEmpty() && userMsg.isNotEmpty()) etMessenger.setText(userMsg)
                    }
                }
        }
    }

    private fun showBloodGroupSelectionDialog() {
        val groups = arrayOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("ব্লাড গ্রুপ নির্বাচন করুন")
            .setItems(groups) { _, which ->
                selectedBloodGroup = groups[which]
                btnSelectBloodGroup.text = "গ্রুপ: $selectedBloodGroup ▾"
            }
            .show()
    }

    private fun showExpirySelectionDialog() {
        val options = arrayOf(
            "২৪ ঘণ্টা (১ দিন)",
            "৪৮ ঘণ্টা (২ দিন)",
            "৩ দিন",
            "৫ দিন",
            "৭ দিন"
        )
        val durations = arrayOf(
            24 * 3600 * 1000L,
            48 * 3600 * 1000L,
            3 * 24 * 3600 * 1000L,
            5 * 24 * 3600 * 1000L,
            7 * 24 * 3600 * 1000L
        )

        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("পোস্টের মেয়াদ নির্ধারণ করুন")
            .setItems(options) { _, which ->
                selectedDurationMillis = durations[which]
                btnSelectExpiry.text = "${options[which]} ▾"
            }
            .show()
    }

    private fun setupNumberDetection() {
        btnSetPhone.setOnClickListener {
            if (currentCandidateNumber.isNotEmpty()) {
                convertNumberToInlineChip(currentCandidateNumber, "phone")
                cardDetectedNumberHelper.visibility = View.GONE
                TopNotification.show(this, "ফোন বাটন যুক্ত হয়েছে!")
            }
        }

        btnSetWhatsApp.setOnClickListener {
            if (currentCandidateNumber.isNotEmpty()) {
                convertNumberToInlineChip(currentCandidateNumber, "wa")
                cardDetectedNumberHelper.visibility = View.GONE
                TopNotification.show(this, "WhatsApp বাটন যুক্ত হয়েছে!")
            }
        }

        btnIgnoreNumber.setOnClickListener {
            if (currentCandidateNumber.isNotEmpty()) {
                ignoredNumbersSet.add(currentCandidateNumber)
                cardDetectedNumberHelper.visibility = View.GONE
            }
        }
    }

    private fun scanTextForNumbers() {
        val editable = etDescription.text ?: return
        val fullText = editable.toString()

        if (fullText.isEmpty()) {
            cardDetectedNumberHelper.visibility = View.GONE
            return
        }

        val regex = Regex("""\b([0-9০-৯]{4,15})\b""")
        val matches = regex.findAll(fullText)

        for (match in matches) {
            val num = match.value.trim()
            val start = match.range.first
            val end = match.range.last + 1

            val existingSpans = editable.getSpans(start, end, BloodInlineActionSpan::class.java)
            if (existingSpans.isEmpty() && !ignoredNumbersSet.contains(num)) {
                currentCandidateNumber = num
                tvDetectedNumber.text = num
                cardDetectedNumberHelper.visibility = View.VISIBLE
                return
            }
        }

        cardDetectedNumberHelper.visibility = View.GONE
    }

    private fun convertNumberToInlineChip(number: String, tagType: String) {
        val editable = etDescription.text ?: return
        val fullText = editable.toString()

        val regex = Regex("""\b$number\b""")
        val match = regex.findAll(fullText).firstOrNull { m ->
            val s = m.range.first
            val e = m.range.last + 1
            editable.getSpans(s, e, BloodInlineActionSpan::class.java).isEmpty()
        } ?: return

        val start = match.range.first
        val end = match.range.last + 1

        isSelfUpdatingText = true

        val chipDrawable = createChipDrawable(this, tagType, number)
        val actionSpan = BloodInlineActionSpan(tagType, number, chipDrawable)

        val clickSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                showChipEditDeleteDialog(actionSpan)
            }
        }

        editable.setSpan(actionSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(clickSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        isSelfUpdatingText = false
    }

    private fun showChipEditDeleteDialog(span: BloodInlineActionSpan) {
        val isWa = span.type == "wa"
        val etEditNumber = EditText(this).apply {
            setText(span.number)
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            setTextColor(Color.BLACK)
            textSize = 15f
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle(if (isWa) "WhatsApp বাটন এডিট বা ডিলিট" else "ফোন বাটন এডিট বা ডিলিট")
            .setView(etEditNumber)
            .setPositiveButton("আপডেট") { _, _ ->
                val newNumber = etEditNumber.text.toString().trim()
                if (newNumber.isNotEmpty()) {
                    updateExistingChip(span, newNumber)
                }
            }
            .setNeutralButton("ডিলিট") { _, _ ->
                removeChipAndRevertText(span)
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun updateExistingChip(span: BloodInlineActionSpan, newNumber: String) {
        val editable = etDescription.text ?: return
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        if (start < 0 || end < start) return

        isSelfUpdatingText = true

        editable.removeSpan(span)
        val clickSpans = editable.getSpans(start, end, ClickableSpan::class.java)
        clickSpans.forEach { editable.removeSpan(it) }

        editable.replace(start, end, newNumber)
        val newEnd = start + newNumber.length

        span.number = newNumber
        val newDrawable = createChipDrawable(this, span.type, newNumber)
        val newActionSpan = BloodInlineActionSpan(span.type, newNumber, newDrawable)

        val newClickSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                showChipEditDeleteDialog(newActionSpan)
            }
        }

        editable.setSpan(newActionSpan, start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(newClickSpan, start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        isSelfUpdatingText = false
    }

    private fun removeChipAndRevertText(span: BloodInlineActionSpan) {
        val editable = etDescription.text ?: return
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        if (start < 0 || end < start) return

        isSelfUpdatingText = true

        editable.removeSpan(span)
        val clickSpans = editable.getSpans(start, end, ClickableSpan::class.java)
        clickSpans.forEach { editable.removeSpan(it) }

        ignoredNumbersSet.add(span.number)
        isSelfUpdatingText = false
    }

    private fun createChipDrawable(context: Context, type: String, number: String): Drawable {
        val isWa = type == "wa"
        val chipView = LayoutInflater.from(context).inflate(R.layout.view_inline_action_chip, null)
        val ivIcon = chipView.findViewById<ImageView>(R.id.ivChipIcon)
        val tvText = chipView.findViewById<TextView>(R.id.tvChipText)
        val root = chipView.findViewById<LinearLayout>(R.id.llChipRoot)

        if (isWa) {
            ivIcon.setImageResource(R.drawable.ic_whatsapp)
            ivIcon.setColorFilter(Color.parseColor("#25D366"))
            tvText.text = "$number (WhatsApp)"
            tvText.setTextColor(Color.parseColor("#25D366"))
            ViewCompat.setBackgroundTintList(root, ColorStateList.valueOf(Color.parseColor("#E8F8F0")))
        } else {
            ivIcon.setImageResource(R.drawable.ic_phone)
            ivIcon.setColorFilter(Color.parseColor("#006A4E"))
            tvText.text = number
            tvText.setTextColor(Color.parseColor("#006A4E"))
            ViewCompat.setBackgroundTintList(root, ColorStateList.valueOf(Color.parseColor("#E8F5E9")))
        }

        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        chipView.measure(spec, spec)
        chipView.layout(0, 0, chipView.measuredWidth, chipView.measuredHeight)

        val bitmap = Bitmap.createBitmap(chipView.measuredWidth, chipView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        chipView.draw(canvas)

        val drawable = BitmapDrawable(context.resources, bitmap)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        return drawable
    }

    private fun submitEmergencyBloodPost() {
        val patientName = etPatientName.text.toString().trim()
        val bloodAmount = etBloodAmount.text.toString().trim()
        val hospitalName = etHospitalName.text.toString().trim()
        val locationAddress = etLocationAddress.text.toString().trim()

        val mobile = etMobile.text.toString().trim()
        val whatsapp = etWhatsapp.text.toString().trim()
        val messenger = etMessenger.text.toString().trim()

        if (patientName.isEmpty()) {
            Toast.makeText(this, "রোগীর নাম প্রদান করুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedBloodGroup.isEmpty()) {
            Toast.makeText(this, "ব্লাড গ্রুপ নির্বাচন করুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (bloodAmount.isEmpty()) {
            Toast.makeText(this, "রক্তের পরিমাণ প্রদান করুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (hospitalName.isEmpty()) {
            Toast.makeText(this, "রক্তদানের স্থান বা হাসপাতালের নাম লিখুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (locationAddress.isEmpty()) {
            Toast.makeText(this, "ঠিকানা বা এলাকার নাম লিখুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (mobile.isEmpty() && whatsapp.isEmpty() && messenger.isEmpty()) {
            Toast.makeText(this, "মোবাইল, WhatsApp বা Messenger এর অন্তত একটি যোগাযোগ মাধ্যম দিন", Toast.LENGTH_LONG).show()
            return
        }

        val editable = SpannableStringBuilder(etDescription.text ?: "")
        val spans = editable.getSpans(0, editable.length, BloodInlineActionSpan::class.java)
        spans.sortByDescending { editable.getSpanStart(it) }

        for (span in spans) {
            val start = editable.getSpanStart(span)
            val end = editable.getSpanEnd(span)
            if (start >= 0 && end >= start) {
                val tag = if (span.type == "phone") "<phone>${span.number}</phone>" else "<wa>${span.number}</wa>"
                editable.replace(start, end, tag)
            }
        }
        val description = editable.toString().trim()

        btnSubmit.isEnabled = false
        pbSubmit.visibility = View.VISIBLE

        val firestore = FirebaseFirestore.getInstance()
        val mainPostRef = firestore.collection("emergency_blood_posts").document()
        val postId = mainPostRef.id

        val nowTime = System.currentTimeMillis()
        val expiryTime = nowTime + selectedDurationMillis

        // 1. Main Collection Document Map with Creator Name and Profile Photo
        val mainPostMap = hashMapOf<String, Any>(
            "id" to postId,
            "userId" to currentUid,
            "userName" to currentUserName,
            "userAvatar" to currentUserAvatar,
            "userPhotoUrl" to currentUserAvatar,
            "isVerified" to isCurrentUserVerified,
            "patientName" to patientName,
            "bloodGroup" to selectedBloodGroup,
            "bloodAmount" to bloodAmount,
            "hospitalName" to hospitalName,
            "locationAddress" to locationAddress,
            "mobile" to mobile,
            "whatsapp" to whatsapp,
            "messenger" to messenger,
            "description" to description,
            "uploadtime" to Timestamp(Date(nowTime)),
            "expiryTime" to expiryTime,
            "status" to "active"
        )

        // 2. User Subcollection Document Map
        val userSubcollectionMap = hashMapOf<String, Any>(
            "id" to postId,
            "userId" to currentUid,
            "userName" to currentUserName,
            "userAvatar" to currentUserAvatar,
            "userPhotoUrl" to currentUserAvatar,
            "patientName" to patientName,
            "bloodGroup" to selectedBloodGroup,
            "bloodAmount" to bloodAmount,
            "hospitalName" to hospitalName,
            "locationAddress" to locationAddress,
            "mobile" to mobile,
            "whatsapp" to whatsapp,
            "messenger" to messenger,
            "description" to description,
            "uploadtime" to Timestamp(Date(nowTime)),
            "expiryTime" to expiryTime,
            "status" to "active"
        )

        mainPostRef.set(mainPostMap).addOnSuccessListener {
            if (currentUid.isNotEmpty()) {
                firestore.collection("users").document(currentUid)
                    .collection("my_blood_posts").document(postId)
                    .set(userSubcollectionMap)
            }

            pbSubmit.visibility = View.GONE
            TopNotification.show(this, "ইমার্জেন্সি ব্লাড পোস্ট সফলভাবে প্রকাশিত হয়েছে!")
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 1200)

        }.addOnFailureListener { err ->
            btnSubmit.isEnabled = true
            pbSubmit.visibility = View.GONE
            Toast.makeText(this, "পোস্ট ব্যর্থ হয়েছে: ${err.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
