package com.nur.sahayak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.adapters.GuideSliderAdapter
import com.nur.sahayak.utils.ImgBBUploader
import com.nur.sahayak.utils.TopNotification
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditProfileActivity : AppCompatActivity() {

    private lateinit var svRoot: ScrollView
    private lateinit var ivCover: ImageView
    private lateinit var ivAvatar: ImageView
    private lateinit var btnChangeCover: ImageButton
    private lateinit var btnChangeAvatar: ImageButton
    private lateinit var btnBack: ImageButton

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var btnSelectDob: MaterialButton
    private lateinit var etAge: EditText
    private lateinit var etWeight: EditText
    private lateinit var btnSelectGender: MaterialButton
    private lateinit var btnSelectBloodGroup: MaterialButton

    private lateinit var etDistrict: EditText
    private lateinit var etUpazila: EditText
    private lateinit var etVillage: EditText
    private lateinit var tvMobileLabel: TextView
    private lateinit var etMobile: EditText

    // Female Privacy Views
    private lateinit var llFemalePrivacySection: LinearLayout
    private lateinit var etWhatsappUsername: EditText
    private lateinit var etMessengerLink: EditText
    private lateinit var btnHowToAddWa: MaterialButton
    private lateinit var btnHowToAddMessenger: MaterialButton

    private lateinit var pbUpload: ProgressBar
    private lateinit var btnSave: Button

    private var selectedAvatarBitmap: Bitmap? = null
    private var selectedCoverBitmap: Bitmap? = null

    private var selectedDobTimestamp: Long = 0L
    private var calculatedAge: Int = 0
    private var selectedGender: String = ""
    private var selectedBloodGroup: String = ""

    private var currentUid = ""
    private var existingPhotoUrl: String = ""
    private var existingCoverUrl: String = ""

    private var isPickingCover = false

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedBitmap = CropImageActivity.tempSourceBitmap
            if (croppedBitmap != null) {
                if (isPickingCover) {
                    selectedCoverBitmap = croppedBitmap
                    ivCover.setImageBitmap(croppedBitmap)
                } else {
                    selectedAvatarBitmap = croppedBitmap
                    ivAvatar.setImageBitmap(croppedBitmap)
                }
            }
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                CropImageActivity.tempSourceBitmap = bitmap

                val cropIntent = Intent(this, CropImageActivity::class.java).apply {
                    putExtra("image_uri", it.toString())
                    putExtra("is_cover", isPickingCover)
                }
                cropLauncher.launch(cropIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "ছবি লোড করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        svRoot = findViewById(R.id.svEditProfileRoot)
        ivCover = findViewById(R.id.ivEditCoverPhoto)
        ivAvatar = findViewById(R.id.ivEditAvatar)
        btnChangeCover = findViewById(R.id.btnChangeCover)
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar)
        btnBack = findViewById(R.id.btnBackEditProfile)

        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        btnSelectDob = findViewById(R.id.btnSelectDob)
        etAge = findViewById(R.id.etAge)
        etWeight = findViewById(R.id.etWeight)
        btnSelectGender = findViewById(R.id.btnSelectGender)
        btnSelectBloodGroup = findViewById(R.id.btnSelectBloodGroup)

        etDistrict = findViewById(R.id.etDistrict)
        etUpazila = findViewById(R.id.etUpazila)
        etVillage = findViewById(R.id.etVillage)
        tvMobileLabel = findViewById(R.id.tvMobileLabel)
        etMobile = findViewById(R.id.etMobile)

        llFemalePrivacySection = findViewById(R.id.llFemalePrivacySection)
        etWhatsappUsername = findViewById(R.id.etWhatsappUsername)
        etMessengerLink = findViewById(R.id.etMessengerLink)
        btnHowToAddWa = findViewById(R.id.btnHowToAddWa)
        btnHowToAddMessenger = findViewById(R.id.btnHowToAddMessenger)

        pbUpload = findViewById(R.id.pbEditProfileUpload)
        btnSave = findViewById(R.id.btnSaveProfile)

        btnBack.setOnClickListener { finish() }

        // Dynamic WindowInsets Listener to lift input fields above Soft Keyboard
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

        // Auto-Scroll to keep bottom input fields visible when focused
        setupFocusAutoScroll(etVillage)
        setupFocusAutoScroll(etMobile)
        setupFocusAutoScroll(etWhatsappUsername)
        setupFocusAutoScroll(etMessengerLink)

        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        loadExistingUserData()

        btnChangeAvatar.setOnClickListener {
            isPickingCover = false
            imagePickerLauncher.launch("image/*")
        }

        btnChangeCover.setOnClickListener {
            isPickingCover = true
            imagePickerLauncher.launch("image/*")
        }

        btnSelectDob.setOnClickListener {
            showDobDatePicker()
        }

        btnSelectGender.setOnClickListener {
            showGenderSelectionDialog()
        }

        btnSelectBloodGroup.setOnClickListener {
            showBloodGroupSelectionDialog()
        }

        btnHowToAddWa.setOnClickListener {
            showWaGuideBottomSheet()
        }

        btnHowToAddMessenger.setOnClickListener {
            showMessengerGuideBottomSheet()
        }

        btnSave.setOnClickListener {
            validateAndSaveProfileData()
        }
    }

    private fun setupFocusAutoScroll(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                svRoot.postDelayed({
                    svRoot.smoothScrollTo(0, v.bottom + 200)
                }, 250)
            }
        }
    }

    private fun loadExistingUserData() {
        if (currentUid.isEmpty()) return

        FirebaseFirestore.getInstance().collection("users").document(currentUid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etFirstName.setText(doc.getString("firstName") ?: "")
                    etLastName.setText(doc.getString("lastName") ?: "")

                    calculatedAge = doc.getLong("age")?.toInt() ?: 0
                    selectedDobTimestamp = doc.getLong("dobTimestamp") ?: 0L

                    // Auto Calculation of 01/01/BirthYear when Age exists but DOB is missing
                    if (selectedDobTimestamp > 0) {
                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        btnSelectDob.text = sdf.format(Date(selectedDobTimestamp))
                    } else if (calculatedAge > 0) {
                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                        val birthYear = currentYear - calculatedAge
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, birthYear)
                            set(Calendar.MONTH, Calendar.JANUARY)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        selectedDobTimestamp = cal.timeInMillis
                        btnSelectDob.text = "01/01/$birthYear"
                    }

                    if (calculatedAge > 0) {
                        etAge.setText("$calculatedAge বছর")
                    }

                    val weight = doc.getLong("weight")?.toString() ?: ""
                    etWeight.setText(weight)

                    selectedGender = doc.getString("gender") ?: ""
                    if (selectedGender.isNotEmpty()) {
                        btnSelectGender.text = "$selectedGender ▾"
                        handleGenderChangeUI(selectedGender, showWelcomeAlert = false)
                    }

                    selectedBloodGroup = doc.getString("bloodGroup") ?: ""
                    if (selectedBloodGroup.isNotEmpty()) {
                        btnSelectBloodGroup.text = "গ্রুপ: $selectedBloodGroup ▾"
                    }

                    etDistrict.setText(doc.getString("district") ?: "নাটোর")
                    etUpazila.setText(doc.getString("upazila") ?: "লালপুর")
                    etVillage.setText(doc.getString("village") ?: "")
                    etMobile.setText(doc.getString("mobile") ?: "")

                    etWhatsappUsername.setText(doc.getString("whatsappUsername") ?: "")
                    etMessengerLink.setText(doc.getString("messengerLink") ?: "")

                    existingPhotoUrl = doc.getString("photoUrl") ?: ""
                    existingCoverUrl = doc.getString("coverUrl") ?: ""

                    if (existingPhotoUrl.isNotEmpty()) {
                        Glide.with(this).load(existingPhotoUrl).circleCrop().into(ivAvatar)
                    }
                    if (existingCoverUrl.isNotEmpty()) {
                        Glide.with(this).load(existingCoverUrl).into(ivCover)
                    }
                }
            }
    }

    private fun showDobDatePicker() {
        val now = System.currentTimeMillis()
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("জন্ম তারিখ নির্বাচন করুন")
            .setSelection(if (selectedDobTimestamp > 0) selectedDobTimestamp else now)
            .setCalendarConstraints(constraints)
            .build()

        datePicker.addOnPositiveButtonClickListener { selectedDateMillis ->
            selectedDobTimestamp = selectedDateMillis
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            btnSelectDob.text = sdf.format(Date(selectedDateMillis))

            val birthCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
                age--
            }

            calculatedAge = maxOf(0, age)
            etAge.setText("$calculatedAge বছর")
        }

        datePicker.show(supportFragmentManager, "DOB_DATE_PICKER")
    }

    private fun showGenderSelectionDialog() {
        val genders = arrayOf("পুরুষ", "মহিলা", "অন্যান্য")
        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("জেন্ডার নির্বাচন করুন")
            .setItems(genders) { _, which ->
                selectedGender = genders[which]
                btnSelectGender.text = "$selectedGender ▾"
                handleGenderChangeUI(selectedGender, showWelcomeAlert = true)
            }
            .show()
    }

    private fun handleGenderChangeUI(gender: String, showWelcomeAlert: Boolean) {
        if (gender == "মহিলা") {
            tvMobileLabel.text = "মোবাইল নম্বর (ঐচ্ছিক):"
            llFemalePrivacySection.visibility = View.VISIBLE

            if (showWelcomeAlert) {
                MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                    .setTitle("নারী রক্তদাতাদের নিরাপত্তা ও সতর্কতা")
                    .setMessage("সম্মানিত রক্তদাতা, আপনার কন্টাক্ট তথ্য রক্ত সন্ধানকারীদের জন্য উন্মুক্ত থাকবে। অনাকাঙ্ক্ষিত ঝামেলা এড়াতে মোবাইল নম্বরের বিকল্প হিসেবে হোয়াটসঅ্যাপ ইউজারনেম অথবা মেসেঞ্জার লিংক ব্যবহার করতে পারেন।")
                    .setPositiveButton("বুঝেছি", null)
                    .show()
            }
        } else {
            tvMobileLabel.text = "মোবাইল নম্বর (*):"
            llFemalePrivacySection.visibility = View.GONE
        }
    }

    private fun showBloodGroupSelectionDialog() {
        val bloodGroups = arrayOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("ব্লাড গ্রুপ নির্বাচন করুন")
            .setItems(bloodGroups) { _, which ->
                selectedBloodGroup = bloodGroups[which]
                btnSelectBloodGroup.text = "গ্রুপ: $selectedBloodGroup ▾"
            }
            .show()
    }

    private fun showWaGuideBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_wa_guide, null)
        dialog.setContentView(sheetView)

        val vpWa = sheetView.findViewById<ViewPager2>(R.id.vpWaGuideSlider)
        val btnClose = sheetView.findViewById<Button>(R.id.btnCloseWaGuideBottomSheet)

        val waImages = listOf(R.drawable.wa_s1, R.drawable.wa_s2, R.drawable.wa_s3)
        vpWa?.adapter = GuideSliderAdapter(waImages)
        if (waImages.size > 1) {
            val startPos = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % waImages.size)
            vpWa?.setCurrentItem(startPos, false)
        }

        val sliderHandler = Handler(Looper.getMainLooper())
        var isAutoScrollRunning = true
        val sliderRunnable = object : Runnable {
            override fun run() {
                try {
                    if (isAutoScrollRunning && vpWa != null && waImages.size > 1) {
                        vpWa.setCurrentItem(vpWa.currentItem + 1, true)
                        sliderHandler.postDelayed(this, 4000)
                    }
                } catch (e: Exception) {}
            }
        }
        sliderHandler.postDelayed(sliderRunnable, 4000)

        vpWa?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    isAutoScrollRunning = false
                    sliderHandler.removeCallbacksAndMessages(null)
                }
            }
        })

        dialog.setOnDismissListener {
            isAutoScrollRunning = false
            sliderHandler.removeCallbacksAndMessages(null)
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showMessengerGuideBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_messenger_guide, null)
        dialog.setContentView(sheetView)

        val vpMessenger = sheetView.findViewById<ViewPager2>(R.id.vpMessengerGuideSlider)
        val btnClose = sheetView.findViewById<Button>(R.id.btnCloseMessengerGuideBottomSheet)

        val messengerImages = listOf(R.drawable.m_s1, R.drawable.m_s2, R.drawable.m_s3, R.drawable.m_s4)
        vpMessenger?.adapter = GuideSliderAdapter(messengerImages)
        if (messengerImages.size > 1) {
            val startPos = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % messengerImages.size)
            vpMessenger?.setCurrentItem(startPos, false)
        }

        val sliderHandler = Handler(Looper.getMainLooper())
        var isAutoScrollRunning = true
        val sliderRunnable = object : Runnable {
            override fun run() {
                try {
                    if (isAutoScrollRunning && vpMessenger != null && messengerImages.size > 1) {
                        vpMessenger.setCurrentItem(vpMessenger.currentItem + 1, true)
                        sliderHandler.postDelayed(this, 4000)
                    }
                } catch (e: Exception) {}
            }
        }
        sliderHandler.postDelayed(sliderRunnable, 4000)

        vpMessenger?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    isAutoScrollRunning = false
                    sliderHandler.removeCallbacksAndMessages(null)
                }
            }
        })

        dialog.setOnDismissListener {
            isAutoScrollRunning = false
            sliderHandler.removeCallbacksAndMessages(null)
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun validateAndSaveProfileData() {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val weightStr = etWeight.text.toString().trim()
        val weight = weightStr.toIntOrNull() ?: 0

        val district = etDistrict.text.toString().trim()
        val upazila = etUpazila.text.toString().trim()
        val village = etVillage.text.toString().trim()
        val mobile = etMobile.text.toString().trim()
        val whatsappUsername = etWhatsappUsername.text.toString().trim()
        val messengerLink = etMessengerLink.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "নামের প্রথম ও শেষ অংশ প্রদান করুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedBloodGroup.isNotEmpty() || weight > 0 || calculatedAge > 0) {
            if (calculatedAge < 18 || calculatedAge > 60) {
                Toast.makeText(this, "রক্তদানের জন্য আপনার বয়স ১৮ থেকে ৬০ বছরের মধ্যে হতে হবে", Toast.LENGTH_LONG).show()
                return
            }

            if (weight < 50) {
                Toast.makeText(this, "রক্তদানের জন্য সর্বনিম্ন ওজন ৫০ কেজি হতে হবে", Toast.LENGTH_LONG).show()
                return
            }

            if (selectedGender == "মহিলা") {
                if (mobile.isEmpty() && whatsappUsername.isEmpty() && messengerLink.isEmpty()) {
                    Toast.makeText(this, "মোবাইল, WhatsApp ইউজারনেম বা Messenger এর অন্তত একটি যোগাযোগ মাধ্যম দিন", Toast.LENGTH_LONG).show()
                    return
                }
            } else {
                if (mobile.isEmpty()) {
                    Toast.makeText(this, "মোবাইল নম্বর প্রদান করুন", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        btnSave.isEnabled = false
        pbUpload.visibility = View.VISIBLE

        uploadImagesAndSaveToFirestore(
            firstName, lastName, weight, district, upazila, village, mobile, whatsappUsername, messengerLink
        )
    }

    private fun uploadImagesAndSaveToFirestore(
        firstName: String,
        lastName: String,
        weight: Int,
        district: String,
        upazila: String,
        village: String,
        mobile: String,
        whatsappUsername: String,
        messengerLink: String
    ) {
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("settings").document("config").get().addOnCompleteListener { configTask ->
            val apiKey = if (configTask.isSuccessful && configTask.result.exists()) {
                configTask.result.getString("imgbbApiKey") ?: ""
            } else ""

            fun uploadCover(newAvatarUrl: String) {
                if (selectedCoverBitmap != null) {
                    ImgBBUploader.uploadBitmap(selectedCoverBitmap!!, apiKey) { coverUrl ->
                        pushFinalData(newAvatarUrl, coverUrl ?: existingCoverUrl)
                    }
                } else {
                    pushFinalData(newAvatarUrl, existingCoverUrl)
                }
            }

            if (selectedAvatarBitmap != null) {
                ImgBBUploader.uploadBitmap(selectedAvatarBitmap!!, apiKey) { avatarUrl ->
                    uploadCover(avatarUrl ?: existingPhotoUrl)
                }
            } else {
                uploadCover(existingPhotoUrl)
            }
        }
    }

    private fun pushFinalData(finalAvatarUrl: String, finalCoverUrl: String) {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val fullName = "$firstName $lastName".trim()
        val weight = etWeight.text.toString().trim().toIntOrNull() ?: 0

        val district = etDistrict.text.toString().trim()
        val upazila = etUpazila.text.toString().trim()
        val village = etVillage.text.toString().trim()
        val mobile = etMobile.text.toString().trim()
        val whatsappUsername = etWhatsappUsername.text.toString().trim()
        val messengerLink = etMessengerLink.text.toString().trim()

        val isDonorRegistered = selectedBloodGroup.isNotEmpty() && calculatedAge in 18..60 && weight >= 50

        val updateMap = hashMapOf<String, Any>(
            "firstName" to firstName,
            "lastName" to lastName,
            "name" to fullName,
            "dobTimestamp" to selectedDobTimestamp,
            "age" to calculatedAge,
            "weight" to weight,
            "gender" to selectedGender,
            "bloodGroup" to selectedBloodGroup,
            "district" to district,
            "upazila" to upazila,
            "village" to village,
            "mobile" to mobile,
            "whatsappUsername" to whatsappUsername,
            "messengerLink" to messengerLink,
            "isDonorRegistered" to isDonorRegistered,
            "photoUrl" to finalAvatarUrl,
            "coverUrl" to finalCoverUrl
        )

        FirebaseFirestore.getInstance().collection("users").document(currentUid)
            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
                sharedPref.edit().apply {
                    putString("user_name", fullName)
                    putString("user_photo_url", finalAvatarUrl)
                    apply()
                }

                pbUpload.visibility = View.GONE
                TopNotification.show(this, "প্রোফাইল ও ডোনার তথ্য সফলভাবে সংরক্ষিত হয়েছে!")
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 1200)
            }
            .addOnFailureListener { err ->
                btnSave.isEnabled = true
                pbUpload.visibility = View.GONE
                Toast.makeText(this, "সংরক্ষণ ব্যর্থ: ${err.message}", Toast.LENGTH_LONG).show()
            }
    }
}
