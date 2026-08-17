package com.nur.sahayak

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.utils.TopNotification

class AddContactActivity : AppCompatActivity() {

    private lateinit var btnSelectCategory: Button
    private lateinit var etName: EditText
    private lateinit var etTitle: EditText
    private lateinit var etLocation: EditText
    private lateinit var etPhone: EditText
    private lateinit var etWhatsapp: EditText
    private lateinit var etFacebook: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: ImageButton

    private var selectedCategoryKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_contact)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        btnBack = findViewById(R.id.btnBackAddContact)
        btnSelectCategory = findViewById(R.id.btnSelectCategory)
        etName = findViewById(R.id.etNewContactName)
        etTitle = findViewById(R.id.etNewContactTitle)
        etLocation = findViewById(R.id.etNewContactLocation)
        etPhone = findViewById(R.id.etNewContactPhone)
        etWhatsapp = findViewById(R.id.etNewContactWhatsapp)
        etFacebook = findViewById(R.id.etNewContactFacebook)
        btnSubmit = findViewById(R.id.btnSaveContactSubmit)

        btnBack.setOnClickListener { finish() }

        btnSelectCategory.setOnClickListener {
            showCategorySelectionDialog()
        }

        btnSubmit.setOnClickListener {
            saveContactToFirestore()
        }
    }

    private fun showCategorySelectionDialog() {
        val categoriesMap = linkedMapOf(
            "doctor" to "ডাক্তার",
            "hospital" to "হাসপাতাল",
            "police" to "পুলিশ স্টেশন",
            "fire" to "ফায়ার সার্ভিস",
            "mechanic" to "মেকানিক ও গ্যারেজ",
            "electronics" to "ইলেকট্রনিক্স",
            "mobile" to "মোবাইল সার্ভিস",
            "grocery" to "মুদি খানা",
            "pharmacy" to "ফার্মেসি",
            "diagnostic" to "ডায়াগনস্টিক",
            "computer" to "কম্পিউটার",
            "hotel" to "হোটেল",
            "restaurant" to "রেস্টুরেন্ট",
            "petrol" to "পেট্রোল পাম্প",
            "gas" to "গ্যাস সেবা",
            "ambulance" to "অ্যাম্বুলেন্স",
            "courier" to "কুরিয়ার",
            "other" to "অন্যান্য"
        )

        val titles = categoriesMap.values.toTypedArray()

        val dialogAdapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            titles
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.parseColor("#006A4E"))
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                view.setTypeface(null, Typeface.BOLD)
                view.setPadding(36, 28, 36, 28)
                return view
            }
        }

        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("সেবার ক্যাটাগরি নির্বাচন করুন")
            .setAdapter(dialogAdapter) { _, which ->
                selectedCategoryKey = categoriesMap.keys.elementAt(which)
                btnSelectCategory.text = "${titles[which]} ▾"
            }
            .show()
    }

    private fun saveContactToFirestore() {
        val name = etName.text.toString().trim()
        val title = etTitle.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val whatsapp = etWhatsapp.text.toString().trim()
        val facebook = etFacebook.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "ব্যক্তি বা প্রতিষ্ঠানের নাম প্রদান করুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCategoryKey.isEmpty()) {
            Toast.makeText(this, "সেবার ক্যাটাগরি নির্বাচন করুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.isEmpty()) {
            Toast.makeText(this, "মোবাইল নম্বর প্রদান করুন", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmit.isEnabled = false

        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val authUser = FirebaseAuth.getInstance().currentUser
        val authUid = authUser?.uid ?: ""
        val prefUid = sharedPref.getString("user_uid", "") ?: ""
        val finalUid = if (authUid.isNotEmpty()) authUid else if (prefUid.isNotEmpty()) prefUid else "guest"

        val firestore = FirebaseFirestore.getInstance()
        val contactMap = hashMapOf<String, Any>(
            "name" to name,
            "title" to title,
            "location" to location,
            "category" to selectedCategoryKey,
            "phone" to phone,
            "whatsapp" to whatsapp,
            "facebook" to facebook,
            "isApproved" to false,
            "createdBy" to finalUid,
            "createdAt" to Timestamp.now()
        )

        firestore.collection("contacts").add(contactMap)
            .addOnSuccessListener {
                TopNotification.show(this, "কন্টাক্টটি সফলভাবে পর্যালোচনার জন্য জমা দেওয়া হয়েছে!")
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 1400)
            }
            .addOnFailureListener { err ->
                btnSubmit.isEnabled = true
                Toast.makeText(this, "কন্টাক্ট সেভ ব্যর্থ: ${err.message}", Toast.LENGTH_LONG).show()
            }
    }
}
