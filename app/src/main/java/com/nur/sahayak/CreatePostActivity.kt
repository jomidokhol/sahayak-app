package com.nur.sahayak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.utils.ImgBBUploader
import com.nur.sahayak.utils.TopNotification
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CreatePostActivity : AppCompatActivity() {

    private val maxWordLimit = 1001

    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var ivVerifiedBadge: ImageView
    private lateinit var tvVisibilityStatus: TextView
    private lateinit var etContent: EditText
    private lateinit var tvWordCounter: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: ImageButton

    // Attached Image Preview
    private lateinit var cardImagePreview: MaterialCardView
    private lateinit var ivAttachedImage: ImageView
    private lateinit var btnRemoveImage: ImageButton
    private lateinit var pbUpload: ProgressBar

    // Smart Number Detection Suggestion Card Views
    private lateinit var cardDetectedNumberHelper: MaterialCardView
    private lateinit var tvDetectedNumberDisplay: TextView
    private lateinit var btnSetAsPhone: Button
    private lateinit var btnSetAsWhatsApp: Button
    private lateinit var btnIgnoreDetectedNumber: ImageButton

    // Tools
    private lateinit var btnToolAddPhoto: MaterialButton
    private lateinit var btnToolSchedule: MaterialButton
    private lateinit var btnToolTemporary: MaterialButton
    private lateinit var btnToolAnonymous: MaterialButton
    private lateinit var llActiveBadges: LinearLayout
    private lateinit var tvActiveScheduleBadge: TextView
    private lateinit var tvActiveExpiryBadge: TextView

    private var scheduledTimestamp: Long = 0L
    private var expiryTimestamp: Long = 0L
    private var isAnonymous: Boolean = false

    private var originalUserName: String = "লালপুরবাসী"
    private var originalUserAvatar: String = ""
    private var isUserVerified: Boolean = false
    private var userVerifiedUntil: Long = 0L
    private var finalUid: String = ""

    private var attachedBitmap: Bitmap? = null

    // State for Number Detection & Inline Chips
    private var currentCandidateNumber: String = ""
    private val ignoredNumbersSet = mutableSetOf<String>()
    private var isSelfUpdatingText: Boolean = false

    // Custom Span Model to hold data invisibly behind the ImageSpan Chip
    class InlineActionSpan(
        val type: String, // "phone" or "wa"
        var number: String,
        drawable: Drawable
    ) : ImageSpan(drawable, ALIGN_BASELINE)

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                attachedBitmap = bitmap
                ivAttachedImage.setImageBitmap(bitmap)
                cardImagePreview.visibility = View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(this, "ছবি লোড করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        ivAvatar = findViewById(R.id.ivPostUserAvatar)
        tvName = findViewById(R.id.tvPostUserName)
        ivVerifiedBadge = findViewById(R.id.ivPostUserVerifiedBadge)
        tvVisibilityStatus = findViewById(R.id.tvPostVisibilityStatus)
        etContent = findViewById(R.id.etPostContent)
        tvWordCounter = findViewById(R.id.tvWordCounter)
        btnSubmit = findViewById(R.id.btnSubmitPost)
        btnBack = findViewById(R.id.btnBackCreatePost)

        cardImagePreview = findViewById(R.id.cardPostImagePreview)
        ivAttachedImage = findViewById(R.id.ivAttachedPostImage)
        btnRemoveImage = findViewById(R.id.btnRemoveAttachedImage)
        pbUpload = findViewById(R.id.pbPostUpload)

        cardDetectedNumberHelper = findViewById(R.id.cardDetectedNumberHelper)
        tvDetectedNumberDisplay = findViewById(R.id.tvDetectedNumberDisplay)
        btnSetAsPhone = findViewById(R.id.btnSetAsPhone)
        btnSetAsWhatsApp = findViewById(R.id.btnSetAsWhatsApp)
        btnIgnoreDetectedNumber = findViewById(R.id.btnIgnoreDetectedNumber)

        btnToolAddPhoto = findViewById(R.id.btnToolAddPhoto)
        btnToolSchedule = findViewById(R.id.btnToolSchedule)
        btnToolTemporary = findViewById(R.id.btnToolTemporary)
        btnToolAnonymous = findViewById(R.id.btnToolAnonymous)
        llActiveBadges = findViewById(R.id.llActiveToolBadges)
        tvActiveScheduleBadge = findViewById(R.id.tvActiveScheduleBadge)
        tvActiveExpiryBadge = findViewById(R.id.tvActiveExpiryBadge)

        btnBack.setOnClickListener { finish() }

        // Enable clickable spans inside EditText for tapping chips to edit/delete
        etContent.movementMethod = LinkMovementMethod.getInstance()

        btnRemoveImage.setOnClickListener {
            attachedBitmap = null
            cardImagePreview.visibility = View.GONE
        }

        loadUserData()
        setupTools()
        setupNumberDetectionActions()

        etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isSelfUpdatingText) return

                val rawText = s?.toString() ?: ""
                val cleanTextForWordCount = rawText.trim()
                val wordCount = if (cleanTextForWordCount.isEmpty()) 0 else cleanTextForWordCount.split(Regex("\\s+")).size
                tvWordCounter.text = "শব্দ সংখ্যা: $wordCount / $maxWordLimit"

                if (wordCount > maxWordLimit) {
                    tvWordCounter.setTextColor(Color.RED)
                    btnSubmit.isEnabled = false
                } else {
                    tvWordCounter.setTextColor(Color.parseColor("#666666"))
                    btnSubmit.isEnabled = true
                }

                // Scan for Contiguous Digits (>= 4 digits) without spans
                scanTextForContiguousNumbers()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSubmit.setOnClickListener {
            submitPost()
        }
    }

    private fun setupNumberDetectionActions() {
        // Option 1: Convert to Inline Phone Button Chip
        btnSetAsPhone.setOnClickListener {
            if (currentCandidateNumber.isNotEmpty()) {
                convertNumberToInlineChip(currentCandidateNumber, "phone")
                cardDetectedNumberHelper.visibility = View.GONE
                TopNotification.show(this, "ফোন বাটন ইনপুট বক্সে যুক্ত হয়েছে!")
            }
        }

        // Option 2: Convert to Inline WhatsApp Button Chip
        btnSetAsWhatsApp.setOnClickListener {
            if (currentCandidateNumber.isNotEmpty()) {
                convertNumberToInlineChip(currentCandidateNumber, "wa")
                cardDetectedNumberHelper.visibility = View.GONE
                TopNotification.show(this, "WhatsApp বাটন ইনপুট বক্সে যুক্ত হয়েছে!")
            }
        }

        // Option 3: Ignore as plain number
        btnIgnoreDetectedNumber.setOnClickListener {
            if (currentCandidateNumber.isNotEmpty()) {
                ignoredNumbersSet.add(currentCandidateNumber)
                cardDetectedNumberHelper.visibility = View.GONE
            }
        }
    }

    private fun scanTextForContiguousNumbers() {
        val editable = etContent.text ?: return
        val fullText = editable.toString()

        if (fullText.isEmpty()) {
            cardDetectedNumberHelper.visibility = View.GONE
            return
        }

        // Find matches for contiguous numbers (>= 4 digits)
        val regex = Regex("""\b([0-9০-৯]{4,15})\b""")
        val matches = regex.findAll(fullText)

        for (match in matches) {
            val num = match.value.trim()
            val start = match.range.first
            val end = match.range.last + 1

            // Check if this range already has an action span
            val existingSpans = editable.getSpans(start, end, InlineActionSpan::class.java)
            if (existingSpans.isEmpty() && !ignoredNumbersSet.contains(num)) {
                currentCandidateNumber = num
                tvDetectedNumberDisplay.text = num
                cardDetectedNumberHelper.visibility = View.VISIBLE
                return
            }
        }

        cardDetectedNumberHelper.visibility = View.GONE
    }

    private fun convertNumberToInlineChip(number: String, tagType: String) {
        val editable = etContent.text ?: return
        val fullText = editable.toString()

        // Find the exact occurrence of this number not already spanned
        val regex = Regex("""\b$number\b""")
        val match = regex.findAll(fullText).firstOrNull { m ->
            val s = m.range.first
            val e = m.range.last + 1
            editable.getSpans(s, e, InlineActionSpan::class.java).isEmpty()
        } ?: return

        val start = match.range.first
        val end = match.range.last + 1

        isSelfUpdatingText = true

        // Create chip drawable
        val chipDrawable = createChipDrawable(this, tagType, number)
        val actionSpan = InlineActionSpan(tagType, number, chipDrawable)

        // Clickable span to tap on chip and trigger Edit/Delete Dialog
        val clickSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                showChipEditDeleteDialog(actionSpan)
            }
        }

        editable.setSpan(actionSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(clickSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        isSelfUpdatingText = false
    }

    private fun showChipEditDeleteDialog(span: InlineActionSpan) {
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
            .setMessage("নম্বর পরিবর্তন করতে নতুন নম্বর লিখে সেভ করুন অথবা বাটনটি মুছে ফেলতে ডিলিট চাপুন:")
            .setView(etEditNumber)
            .setPositiveButton("আপডেট করুন") { _, _ ->
                val newNumber = etEditNumber.text.toString().trim()
                if (newNumber.isNotEmpty()) {
                    updateExistingChip(span, newNumber)
                    TopNotification.show(this, "বাটনের নম্বর সফলভাবে আপডেট হয়েছে!")
                }
            }
            .setNeutralButton("বাটন ডিলিট করুন") { _, _ ->
                removeChipAndRevertText(span)
                TopNotification.show(this, "বাটনটি মুছে সাধারণ সংখ্যায় রূপান্তর করা হয়েছে!")
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun updateExistingChip(span: InlineActionSpan, newNumber: String) {
        val editable = etContent.text ?: return
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
        val newActionSpan = InlineActionSpan(span.type, newNumber, newDrawable)

        val newClickSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                showChipEditDeleteDialog(newActionSpan)
            }
        }

        editable.setSpan(newActionSpan, start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(newClickSpan, start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        isSelfUpdatingText = false
    }

    private fun removeChipAndRevertText(span: InlineActionSpan) {
        val editable = etContent.text ?: return
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        if (start < 0 || end < start) return

        isSelfUpdatingText = true

        editable.removeSpan(span)
        val clickSpans = editable.getSpans(start, end, ClickableSpan::class.java)
        clickSpans.forEach { editable.removeSpan(it) }

        // Number remains as pure plain text in the input box!
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

        // Measure and draw into bitmap
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

        if (finalUid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(finalUid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        isUserVerified = doc.getBoolean("isVerified") ?: false
                        userVerifiedUntil = doc.getLong("verifiedUntil") ?: 0L
                        val isCurrentlyVerified = isUserVerified && userVerifiedUntil > System.currentTimeMillis()

                        if (isCurrentlyVerified && !isAnonymous) {
                            ivVerifiedBadge.visibility = View.VISIBLE
                        } else {
                            ivVerifiedBadge.visibility = View.GONE
                        }
                    }
                }
        }
    }

    private fun setupTools() {
        // 1. Add Photo
        btnToolAddPhoto.setOnClickListener {
            val isCurrentlyVerified = isUserVerified && userVerifiedUntil > System.currentTimeMillis()
            if (isCurrentlyVerified) {
                imagePickerLauncher.launch("image/*")
            } else {
                MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                    .setTitle("ভেরিফাইড ফিচার")
                    .setMessage("পাবলিক পোস্টে ছবি যুক্ত করার সুবিধাটি শুধুমাত্র ভেরিফাইড মেম্বারদের জন্য। আপনিও ভেরিফাইড হন!")
                    .setPositiveButton("প্ল্যান দেখুন") { _, _ ->
                        startActivity(Intent(this, VerifyActivity::class.java))
                    }
                    .setNegativeButton("বন্ধ করুন", null)
                    .show()
            }
        }

        // 2. Schedule
        btnToolSchedule.setOnClickListener {
            showMaterialSchedulePicker()
        }

        // 3. Temporary
        btnToolTemporary.setOnClickListener {
            showExpirySelectionDialog()
        }

        // 4. Anonymous Toggle
        btnToolAnonymous.setOnClickListener {
            isAnonymous = !isAnonymous
            if (isAnonymous) {
                tvName.text = "Anonymous User"
                tvVisibilityStatus.text = "গোপন পরিচয় পোস্ট"
                ivAvatar.setImageResource(R.drawable.draft_user)
                ivVerifiedBadge.visibility = View.GONE
                btnToolAnonymous.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#006A4E")))
                btnToolAnonymous.setTextColor(Color.parseColor("#006A4E"))
                Toast.makeText(this, "Anonymous মোড চালু করা হয়েছে", Toast.LENGTH_SHORT).show()
            } else {
                tvName.text = originalUserName
                tvVisibilityStatus.text = "পাবলিক পোস্ট"
                loadUserData()
                btnToolAnonymous.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#CCCCCC")))
                btnToolAnonymous.setTextColor(Color.parseColor("#212121"))
                Toast.makeText(this, "Anonymous মোড বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
            btnToolSchedule.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#006A4E")))
            btnToolSchedule.setTextColor(Color.parseColor("#006A4E"))
        } else {
            tvActiveScheduleBadge.visibility = View.GONE
            btnToolSchedule.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#CCCCCC")))
            btnToolSchedule.setTextColor(Color.parseColor("#212121"))
        }

        if (expiryTimestamp > 0) {
            tvActiveExpiryBadge.visibility = View.VISIBLE
            tvActiveExpiryBadge.text = "⏳ মেয়াদ শেষ: ${sdf.format(Date(expiryTimestamp))}"
            btnToolTemporary.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E65100")))
            btnToolTemporary.setTextColor(Color.parseColor("#E65100"))
        } else {
            tvActiveExpiryBadge.visibility = View.GONE
            btnToolTemporary.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#CCCCCC")))
            btnToolTemporary.setTextColor(Color.parseColor("#212121"))
        }

        if (scheduledTimestamp > 0 || expiryTimestamp > 0) {
            llActiveBadges.visibility = View.VISIBLE
        } else {
            llActiveBadges.visibility = View.GONE
        }
    }

    private fun submitPost() {
        // Extract plain content with underlying tags encoded for Firestore
        val editable = SpannableStringBuilder(etContent.text ?: "")
        val spans = editable.getSpans(0, editable.length, InlineActionSpan::class.java)

        // Replace Spans with backend tags from end to start to prevent offset corruption
        spans.sortByDescending { editable.getSpanStart(it) }

        for (span in spans) {
            val start = editable.getSpanStart(span)
            val end = editable.getSpanEnd(span)
            if (start >= 0 && end >= start) {
                val tag = if (span.type == "phone") "<phone>${span.number}</phone>" else "<wa>${span.number}</wa>"
                editable.replace(start, end, tag)
            }
        }

        val content = editable.toString().trim()
        val cleanTextForWordCount = content.replace(Regex("<phone>|</phone>|<wa>|</wa>"), "")
        val wordCount = if (cleanTextForWordCount.trim().isEmpty()) 0 else cleanTextForWordCount.trim().split(Regex("\\s+")).size

        if (content.isEmpty() && attachedBitmap == null) {
            Toast.makeText(this, "পোস্টের বক্তব্য বা ছবি দিন", Toast.LENGTH_SHORT).show()
            return
        }

        if (wordCount > maxWordLimit) {
            Toast.makeText(this, "শব্দ সংখ্যা ১০০১ এর বেশি হতে পারবে না", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmit.isEnabled = false
        pbUpload.visibility = View.VISIBLE
        val nowTimestamp = if (scheduledTimestamp > 0) Timestamp(Date(scheduledTimestamp)) else Timestamp.now()

        fun savePostToFirestore(uploadedImageUrl: String) {
            val firestore = FirebaseFirestore.getInstance()
            val newDocRef = firestore.collection("openchat").document()
            val postId = newDocRef.id

            val isCurrentlyVerified = isUserVerified && userVerifiedUntil > System.currentTimeMillis()

            val postMap = hashMapOf<String, Any>(
                "id" to postId,
                "userid" to finalUid,
                "userName" to if (isAnonymous) "Anonymous User" else originalUserName,
                "userAvatar" to if (isAnonymous) "" else originalUserAvatar,
                "content" to content,
                "postImageUrl" to uploadedImageUrl,
                "isVerified" to if (isAnonymous) false else isCurrentlyVerified,
                "verifiedUntil" to if (isAnonymous) 0L else userVerifiedUntil,
                "uploadtime" to nowTimestamp,
                "scheduledTime" to scheduledTimestamp,
                "expiryTime" to expiryTimestamp,
                "isAnonymous" to isAnonymous,
                "likesCount" to 0,
                "repliesCount" to 0
            )

            newDocRef.set(postMap).addOnSuccessListener {
                pbUpload.visibility = View.GONE
                TopNotification.show(this, if (scheduledTimestamp > 0) "পোস্টটি সিডিউল করা হয়েছে!" else "পোস্ট সফলভাবে প্রকাশিত হয়েছে!")
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 1200)
            }.addOnFailureListener { err ->
                btnSubmit.isEnabled = true
                pbUpload.visibility = View.GONE
                Toast.makeText(this, "পোস্ট ব্যর্থ: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }

        if (attachedBitmap != null) {
            FirebaseFirestore.getInstance().collection("settings").document("config").get()
                .addOnCompleteListener { configTask ->
                    val apiKey = if (configTask.isSuccessful && configTask.result.exists()) {
                        configTask.result.getString("imgbbApiKey") ?: ""
                    } else ""

                    ImgBBUploader.uploadBitmap(attachedBitmap!!, apiKey) { imgUrl ->
                        savePostToFirestore(imgUrl ?: "")
                    }
                }
        } else {
            savePostToFirestore("")
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
