package com.nur.sahayak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.adapters.UserContactAdapter
import com.nur.sahayak.models.Contact
import com.nur.sahayak.utils.ImgBBUploader
import com.nur.sahayak.utils.TopNotification

class UserContactActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var rvContacts: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: UserContactAdapter
    private val myContactsList = mutableListOf<Contact>()
    private var currentUid = ""

    // Contact Photo Editing State
    private var selectedCroppedBitmap: Bitmap? = null
    private var currentEditingAvatarImageView: ImageView? = null

    // 1:1 Crop Launcher
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val cropped = CropImageActivity.tempSourceBitmap
            if (cropped != null) {
                selectedCroppedBitmap = cropped
                currentEditingAvatarImageView?.setImageBitmap(cropped)
            }
        }
    }

    // Gallery Picker Launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                CropImageActivity.tempSourceBitmap = bitmap

                // Launch 1:1 Square Crop (is_cover = false)
                val cropIntent = Intent(this, CropImageActivity::class.java).apply {
                    putExtra("image_uri", it.toString())
                    putExtra("is_cover", false)
                }
                cropLauncher.launch(cropIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "ছবি লোড করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_contact)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        btnBack = findViewById(R.id.btnBackUserContact)
        rvContacts = findViewById(R.id.rvUserContacts)
        swipeRefresh = findViewById(R.id.swipeRefreshUserContacts)
        tvEmpty = findViewById(R.id.tvEmptyUserContacts)

        btnBack.setOnClickListener { finish() }

        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        rvContacts.layoutManager = LinearLayoutManager(this)
        adapter = UserContactAdapter(
            emptyList(),
            onEditContact = { contact, pos ->
                openEditContactBottomSheet(contact, pos)
            },
            onContactDeleted = {
                if (adapter.itemCount == 0) {
                    tvEmpty.visibility = View.VISIBLE
                }
            }
        )
        rvContacts.adapter = adapter

        fetchUserSubmittedContacts()

        swipeRefresh.setOnRefreshListener {
            fetchUserSubmittedContacts()
        }
    }

    private fun openEditContactBottomSheet(contact: Contact, position: Int) {
        selectedCroppedBitmap = null

        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_edit_user_contact, null)
        dialog.setContentView(sheetView)

        val ivAvatar = sheetView.findViewById<ImageView>(R.id.ivEditContactAvatar)
        val btnChangeImage = sheetView.findViewById<ImageButton>(R.id.btnChangeContactImage)
        val cardAvatar = sheetView.findViewById<View>(R.id.cardEditContactAvatar)
        currentEditingAvatarImageView = ivAvatar

        val tvCategory = sheetView.findViewById<TextView>(R.id.tvFixedCategoryDisplay)
        val tvLocation = sheetView.findViewById<TextView>(R.id.tvFixedLocationDisplay)

        val etName = sheetView.findViewById<EditText>(R.id.etEditContactName)
        val etTitle = sheetView.findViewById<EditText>(R.id.etEditContactTitle)
        val etPhone = sheetView.findViewById<EditText>(R.id.etEditContactPhone)
        val etWhatsapp = sheetView.findViewById<EditText>(R.id.etEditContactWhatsapp)
        val etFacebook = sheetView.findViewById<EditText>(R.id.etEditContactFacebook)
        val pbUpload = sheetView.findViewById<ProgressBar>(R.id.pbEditContactUpload)

        val btnSave = sheetView.findViewById<Button>(R.id.btnSaveUserContactEdit)
        val btnCancel = sheetView.findViewById<Button>(R.id.btnCancelUserContactEdit)

        tvCategory.text = "ক্যাটাগরি: ${getCategoryBangla(contact.category)} (স্থির / অপরিবর্তনযোগ্য)"
        tvLocation.text = "ঠিকানা: ${if (contact.location.isNotEmpty()) contact.location else "লালপুর"} (স্থির / অপরিবর্তনযোগ্য)"

        etName.setText(contact.name)
        etTitle.setText(contact.title)
        etPhone.setText(contact.phone)
        etWhatsapp.setText(contact.whatsapp ?: "")
        etFacebook.setText(contact.facebook ?: "")

        val defaultAvatar = try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
        if (!contact.imageUrl.isNullOrEmpty()) {
            Glide.with(this).load(contact.imageUrl).placeholder(defaultAvatar).circleCrop().into(ivAvatar)
        } else {
            ivAvatar.setImageResource(defaultAvatar)
        }

        // Trigger 1:1 Square Crop Image Picker
        val onImagePickerClick = View.OnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        btnChangeImage.setOnClickListener(onImagePickerClick)
        cardAvatar.setOnClickListener(onImagePickerClick)

        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
            val newTitle = etTitle.text.toString().trim()
            val newPhone = etPhone.text.toString().trim()
            val newWhatsapp = etWhatsapp.text.toString().trim()
            val newFacebook = etFacebook.text.toString().trim()

            if (newName.isEmpty() || newPhone.isEmpty()) {
                Toast.makeText(this, "নাম ও মোবাইল নম্বর আবশ্যক", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            pbUpload.visibility = View.VISIBLE

            fun pushFirestoreUpdate(finalImageUrl: String) {
                val updateMap = hashMapOf<String, Any>(
                    "name" to newName,
                    "title" to newTitle,
                    "phone" to newPhone,
                    "whatsapp" to newWhatsapp,
                    "facebook" to newFacebook,
                    "imageUrl" to finalImageUrl,
                    "isApproved" to false, // Becomes pending for re-approval by Admin
                    "updatedAt" to Timestamp.now()
                )

                FirebaseFirestore.getInstance().collection("contacts").document(contact.id)
                    .update(updateMap)
                    .addOnSuccessListener {
                        contact.name = newName
                        contact.title = newTitle
                        contact.phone = newPhone
                        contact.whatsapp = newWhatsapp
                        contact.facebook = newFacebook
                        contact.imageUrl = finalImageUrl
                        contact.isApproved = false
                        adapter.notifyItemChanged(position)

                        dialog.dismiss()
                        TopNotification.show(this, "কন্টাক্ট তথ্য আপডেট হয়েছে (এডমিন অনুমোদনের অপেক্ষায়)")
                    }
                    .addOnFailureListener { e ->
                        btnSave.isEnabled = true
                        pbUpload.visibility = View.GONE
                        Toast.makeText(this, "আপডেট ব্যর্থ: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }

            if (selectedCroppedBitmap != null) {
                FirebaseFirestore.getInstance().collection("settings").document("config").get()
                    .addOnCompleteListener { configTask ->
                        val apiKey = if (configTask.isSuccessful && configTask.result.exists()) {
                            configTask.result.getString("imgbbApiKey") ?: ""
                        } else ""

                        ImgBBUploader.uploadBitmap(selectedCroppedBitmap!!, apiKey) { imgUrl ->
                            pushFirestoreUpdate(imgUrl ?: (contact.imageUrl ?: ""))
                        }
                    }
            } else {
                pushFirestoreUpdate(contact.imageUrl ?: "")
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun fetchUserSubmittedContacts() {
        if (currentUid.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "কন্টাক্ট দেখতে লগইন করুন।"
            swipeRefresh.isRefreshing = false
            return
        }

        swipeRefresh.isRefreshing = true

        FirebaseFirestore.getInstance().collection("contacts")
            .whereEqualTo("createdBy", currentUid)
            .get()
            .addOnSuccessListener { snapshot ->
                myContactsList.clear()

                for (doc in snapshot.documents) {
                    val name = doc.getString("name") ?: ""
                    if (name.isNotEmpty()) {
                        val contact = Contact(
                            id = doc.id,
                            name = name,
                            category = doc.getString("category") ?: "other",
                            phone = doc.getString("phone") ?: "",
                            title = doc.getString("title") ?: "",
                            location = doc.getString("location") ?: "",
                            whatsapp = doc.getString("whatsapp"),
                            facebook = doc.getString("facebook"),
                            imageUrl = doc.getString("imageUrl"),
                            isApproved = doc.getBoolean("isApproved") ?: false,
                            createdBy = doc.getString("createdBy")
                        )
                        myContactsList.add(contact)
                    }
                }

                adapter.setContacts(myContactsList)

                if (myContactsList.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    tvEmpty.visibility = View.GONE
                }

                swipeRefresh.isRefreshing = false
            }
            .addOnFailureListener {
                swipeRefresh.isRefreshing = false
                if (myContactsList.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                }
            }
    }

    private fun getCategoryBangla(cat: String): String {
        return when (cat.lowercase()) {
            "doctor" -> "ডাক্তার"
            "hospital" -> "হাসপাতাল"
            "police" -> "পুলিশ স্টেশন"
            "fire" -> "ফায়ার সার্ভিস"
            "mechanic" -> "মেকানিক ও গ্যারেজ"
            "electronics" -> "ইলেকট্রনিক্স"
            "mobile" -> "মোবাইল সার্ভিস"
            "grocery" -> "মুদি খানা"
            "pharmacy" -> "ফার্মেসি"
            "diagnostic" -> "ডায়াগনস্টিক"
            "computer" -> "কম্পিউটার"
            "hotel" -> "হোটেল"
            "restaurant" -> "রেস্টুরেন্ট"
            "petrol" -> "পেট্রোল পাম্প"
            "gas" -> "গ্যাস সেবা"
            "ambulance" -> "অ্যাম্বুলেন্স"
            "courier" -> "কুরিয়ার"
            else -> "অন্যান্য সেবা"
        }
    }
}
