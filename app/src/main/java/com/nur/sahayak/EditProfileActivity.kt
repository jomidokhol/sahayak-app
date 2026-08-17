package com.nur.sahayak

import android.app.Activity
import android.app.Dialog
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
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.utils.ImgBBUploader
import com.nur.sahayak.utils.TopNotification

class EditProfileActivity : AppCompatActivity() {

    private lateinit var ivCover: ImageView
    private lateinit var ivAvatar: ImageView
    private lateinit var etName: EditText
    private lateinit var etAge: EditText
    private lateinit var etMobile: EditText
    private lateinit var btnChangeCover: Button
    private lateinit var btnChangeAvatar: Button
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton

    private var selectedAvatarBitmap: Bitmap? = null
    private var selectedCoverBitmap: Bitmap? = null
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
                Toast.makeText(this, "ছবি প্রসেস করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        // Make Status Bar Transparent for Notch Gradient Header
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        ivCover = findViewById(R.id.ivCoverPreview)
        ivAvatar = findViewById(R.id.ivAvatarPreview)
        etName = findViewById(R.id.etEditName)
        etAge = findViewById(R.id.etEditAge)
        etMobile = findViewById(R.id.etEditMobile)
        btnChangeCover = findViewById(R.id.btnChangeCover)
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar)
        btnSave = findViewById(R.id.btnSaveProfile)
        btnBack = findViewById(R.id.btnBackEditProfile)

        btnBack.setOnClickListener { finish() }

        loadCurrentUserData()

        btnChangeCover.setOnClickListener {
            isPickingCover = true
            imagePickerLauncher.launch("image/*")
        }

        btnChangeAvatar.setOnClickListener {
            isPickingCover = false
            imagePickerLauncher.launch("image/*")
        }

        btnSave.setOnClickListener {
            saveProfileChanges()
        }
    }

    private fun loadCurrentUserData() {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        val currentName = sharedPref.getString("user_name", "") ?: ""
        etName.setText(currentName)

        if (uid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val mobile = doc.getString("mobile") ?: ""
                        val age = doc.getLong("age")?.toString() ?: ""
                        val photoUrl = doc.getString("photoUrl") ?: ""
                        val coverUrl = doc.getString("coverUrl") ?: ""

                        if (mobile.isNotEmpty()) etMobile.setText(mobile)
                        if (age.isNotEmpty()) etAge.setText(age)

                        if (photoUrl.isNotEmpty()) {
                            Glide.with(this).load(photoUrl).circleCrop().into(ivAvatar)
                        }
                        if (coverUrl.isNotEmpty()) {
                            Glide.with(this).load(coverUrl).into(ivCover)
                        }
                    }
                }
        }
    }

    private fun saveProfileChanges() {
        val newName = etName.text.toString().trim()
        val newAge = etAge.text.toString().trim()
        val newMobile = etMobile.text.toString().trim()

        if (newName.isEmpty()) {
            Toast.makeText(this, "আপনার নাম প্রদান করুন", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        val loadingDialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        loadingDialog.setContentView(R.layout.dialog_auth_ripple)
        val vRed = loadingDialog.findViewById<View>(R.id.vRippleRed)
        val vGreen = loadingDialog.findViewById<View>(R.id.vRippleGreen)
        val animRed = AnimationUtils.loadAnimation(this, R.anim.ripple_red)
        val animGreen = AnimationUtils.loadAnimation(this, R.anim.ripple_green)
        vRed?.startAnimation(animRed)
        vGreen?.startAnimation(animGreen)
        loadingDialog.show()

        FirebaseFirestore.getInstance().collection("settings").document("config").get()
            .addOnCompleteListener { configTask ->
                val dynamicApiKey = if (configTask.isSuccessful && configTask.result.exists()) {
                    configTask.result.getString("imgbbApiKey") ?: ""
                } else ""

                var uploadedAvatarUrl: String? = null
                var uploadedCoverUrl: String? = null

                fun updateFirestoreAndFinish() {
                    val updateMap = hashMapOf<String, Any>(
                        "firstName" to newName,
                        "mobile" to newMobile,
                        "age" to (newAge.toIntOrNull() ?: 0)
                    )

                    uploadedAvatarUrl?.let { updateMap["photoUrl"] = it }
                    uploadedCoverUrl?.let { updateMap["coverUrl"] = it }

                    if (uid.isNotEmpty()) {
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                            .update(updateMap)
                    }

                    sharedPref.edit().apply {
                        putString("user_name", newName)
                        uploadedAvatarUrl?.let { putString("user_photo_url", it) }
                        apply()
                    }

                    Handler(Looper.getMainLooper()).post {
                        loadingDialog.dismiss()
                        TopNotification.show(this, "প্রোফাইল সফলভাবে আপডেট করা হয়েছে!")
                        Handler(Looper.getMainLooper()).postDelayed({
                            finish()
                        }, 1200)
                    }
                }

                if (selectedAvatarBitmap != null) {
                    ImgBBUploader.uploadBitmap(selectedAvatarBitmap!!, dynamicApiKey) { avatarUrl ->
                        uploadedAvatarUrl = avatarUrl
                        if (selectedCoverBitmap != null) {
                            ImgBBUploader.uploadBitmap(selectedCoverBitmap!!, dynamicApiKey) { coverUrl ->
                                uploadedCoverUrl = coverUrl
                                updateFirestoreAndFinish()
                            }
                        } else {
                            updateFirestoreAndFinish()
                        }
                    }
                } else if (selectedCoverBitmap != null) {
                    ImgBBUploader.uploadBitmap(selectedCoverBitmap!!, dynamicApiKey) { coverUrl ->
                        uploadedCoverUrl = coverUrl
                        updateFirestoreAndFinish()
                    }
                } else {
                    updateFirestoreAndFinish()
                }
            }
    }
}
