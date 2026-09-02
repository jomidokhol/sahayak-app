package com.nur.sahayak.fragments

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.DeveloperActivity
import com.nur.sahayak.EditProfileActivity
import com.nur.sahayak.MainActivity
import com.nur.sahayak.R
import com.nur.sahayak.SettingsActivity
import com.nur.sahayak.TermsActivity
import com.nur.sahayak.UserContactActivity
import com.nur.sahayak.VerifyActivity
import com.nur.sahayak.adapters.ProfilePostAdapter
import com.nur.sahayak.models.OpenChatPost
import com.nur.sahayak.utils.FirestoreSafeParser
import com.nur.sahayak.utils.TopNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var ivCover: ImageView
    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var ivVerifiedBadge: ImageView
    private lateinit var tvEmail: TextView
    private lateinit var btnEdit: Button
    private lateinit var btnVerify: Button
    private lateinit var btnSettings: Button
    private lateinit var btnUserContacts: Button
    private lateinit var btnLogout: Button
    private lateinit var btnDeveloper: Button
    private lateinit var btnTerms: Button
    private lateinit var rvProfilePosts: RecyclerView
    private lateinit var tvNoProfilePosts: TextView
    private lateinit var llProfileSkeleton: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var profilePostAdapter: ProfilePostAdapter

    // Personal Info Views
    private lateinit var tvMobile: TextView
    private lateinit var tvAgeDob: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvGender: TextView
    private lateinit var tvBloodGroup: TextView
    private lateinit var tvAddress: TextView
    private lateinit var llContactLinksRow: LinearLayout
    private lateinit var tvContactLinks: TextView

    // Blood Donor Card Views
    private lateinit var cardDonorProfile: MaterialCardView
    private lateinit var rlDonorCardRoot: RelativeLayout
    private lateinit var llDonorUnregistered: LinearLayout
    private lateinit var btnEditDonorInfo: Button

    private lateinit var llDonorRegistered: RelativeLayout
    private lateinit var tvDonorBloodGroupBadge: TextView
    private lateinit var ivDonorAvatar: ImageView
    private lateinit var tvDonorName: TextView
    private lateinit var ivDonorVerifiedBadge: ImageView
    private lateinit var tvDonorAddressHeader: TextView
    private lateinit var btnDonorVisibility: ImageButton
    private lateinit var btnEditLastDonation: ImageButton

    private lateinit var llDonorStatusNormalView: LinearLayout
    private lateinit var llDonorStatusCapsule: LinearLayout
    private lateinit var ivDonorStatusIcon: ImageView
    private lateinit var tvDonorReadinessStatus: TextView
    private lateinit var tvDonorLastDonationDateText: TextView

    private lateinit var llDonorStatusEditView: LinearLayout
    private lateinit var btnSelectLastDonationDate: MaterialButton
    private lateinit var btnSaveLastDonationDate: MaterialButton
    private lateinit var btnClearLastDonationDate: MaterialButton
    private lateinit var btnCancelLastDonationEdit: MaterialButton

    private lateinit var btnDonorCall: MaterialButton
    private lateinit var btnDonorWhatsApp: MaterialButton
    private lateinit var btnDonorMessenger: MaterialButton

    private var currentLastDonationTimestamp: Long = 0L
    private var pendingSelectedLastDonationTimestamp: Long = 0L
    private var isDonorVisibleState: Boolean = true

    private val myPostsList = mutableListOf<OpenChatPost>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        ivCover = view.findViewById(R.id.ivCoverPhoto)
        ivAvatar = view.findViewById(R.id.ivProfileAvatar)
        tvName = view.findViewById(R.id.tvProfileName)
        ivVerifiedBadge = view.findViewById(R.id.ivProfileVerifiedBadge)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        
        // 3+2 Buttons
        btnEdit = view.findViewById(R.id.btnEditProfile)
        btnVerify = view.findViewById(R.id.btnVerifyProfile)
        btnSettings = view.findViewById(R.id.btnSettings)
        btnUserContacts = view.findViewById(R.id.btnUserContacts)
        btnLogout = view.findViewById(R.id.btnLogout)
        
        btnDeveloper = view.findViewById(R.id.btnDeveloperPage)
        btnTerms = view.findViewById(R.id.btnTerms)
        rvProfilePosts = view.findViewById(R.id.rvProfilePosts)
        tvNoProfilePosts = view.findViewById(R.id.tvNoProfilePosts)
        llProfileSkeleton = view.findViewById(R.id.llProfilePostsSkeleton)
        swipeRefresh = view.findViewById(R.id.swipeRefreshProfile)

        // Personal Info
        tvMobile = view.findViewById(R.id.tvProfileMobile)
        tvAgeDob = view.findViewById(R.id.tvProfileAgeDob)
        tvWeight = view.findViewById(R.id.tvProfileWeight)
        tvGender = view.findViewById(R.id.tvProfileGender)
        tvBloodGroup = view.findViewById(R.id.tvProfileBloodGroup)
        tvAddress = view.findViewById(R.id.tvProfileAddress)
        llContactLinksRow = view.findViewById(R.id.llProfileContactLinksRow)
        tvContactLinks = view.findViewById(R.id.tvProfileContactLinks)

        // Donor Card
        cardDonorProfile = view.findViewById(R.id.cardBloodDonorProfile)
        rlDonorCardRoot = view.findViewById(R.id.rlDonorCardRoot)
        llDonorUnregistered = view.findViewById(R.id.llDonorUnregisteredState)
        btnEditDonorInfo = view.findViewById(R.id.btnEditDonorInfo)

        llDonorRegistered = view.findViewById(R.id.llDonorRegisteredState)
        tvDonorBloodGroupBadge = view.findViewById(R.id.tvDonorBloodGroupBadge)
        ivDonorAvatar = view.findViewById(R.id.ivDonorAvatar)
        tvDonorName = view.findViewById(R.id.tvDonorName)
        ivDonorVerifiedBadge = view.findViewById(R.id.ivDonorVerifiedBadge)
        tvDonorAddressHeader = view.findViewById(R.id.tvDonorAddressHeader)
        btnDonorVisibility = view.findViewById(R.id.btnDonorVisibility)
        btnEditLastDonation = view.findViewById(R.id.btnEditLastDonation)

        llDonorStatusNormalView = view.findViewById(R.id.llDonorStatusNormalView)
        llDonorStatusCapsule = view.findViewById(R.id.llDonorStatusCapsule)
        ivDonorStatusIcon = view.findViewById(R.id.ivDonorStatusIcon)
        tvDonorReadinessStatus = view.findViewById(R.id.tvDonorReadinessStatus)
        tvDonorLastDonationDateText = view.findViewById(R.id.tvDonorLastDonationDateText)

        llDonorStatusEditView = view.findViewById(R.id.llDonorStatusEditView)
        btnSelectLastDonationDate = view.findViewById(R.id.btnSelectLastDonationDate)
        btnSaveLastDonationDate = view.findViewById(R.id.btnSaveLastDonationDate)
        btnClearLastDonationDate = view.findViewById(R.id.btnClearLastDonationDate)
        btnCancelLastDonationEdit = view.findViewById(R.id.btnCancelLastDonationEdit)

        btnDonorCall = view.findViewById(R.id.btnDonorCall)
        btnDonorWhatsApp = view.findViewById(R.id.btnDonorWhatsApp)
        btnDonorMessenger = view.findViewById(R.id.btnDonorMessenger)

        val auth = FirebaseAuth.getInstance()
        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""
        val currentUName = sharedPref.getString("user_name", "লালপুরবাসী") ?: "লালপুরবাসী"
        val currentUAvatar = sharedPref.getString("user_photo_url", "") ?: ""

        rvProfilePosts.layoutManager = LinearLayoutManager(context)
        profilePostAdapter = ProfilePostAdapter(myPostsList, currentUid, currentUName, currentUAvatar)
        rvProfilePosts.adapter = profilePostAdapter

        loadUserData()

        swipeRefresh.setOnRefreshListener {
            loadUserData()
        }

        btnEdit.setOnClickListener {
            startActivity(Intent(context, EditProfileActivity::class.java))
        }

        btnVerify.setOnClickListener {
            startActivity(Intent(context, VerifyActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(context, SettingsActivity::class.java))
        }

        btnUserContacts.setOnClickListener {
            startActivity(Intent(context, UserContactActivity::class.java))
        }

        btnLogout.setOnClickListener {
            performLogout()
        }

        btnDeveloper.setOnClickListener {
            startActivity(Intent(context, DeveloperActivity::class.java))
        }

        btnTerms.setOnClickListener {
            startActivity(Intent(context, TermsActivity::class.java))
        }

        btnEditDonorInfo.setOnClickListener {
            startActivity(Intent(context, EditProfileActivity::class.java))
        }

        setupDonorCardActions()

        return view
    }

    private fun setupDonorCardActions() {
        val auth = FirebaseAuth.getInstance()
        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val uid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        // 1. Visibility Toggle
        btnDonorVisibility.setOnClickListener {
            if (uid.isEmpty()) return@setOnClickListener
            isDonorVisibleState = !isDonorVisibleState
            updateVisibilityButtonUI(isDonorVisibleState)

            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("isDonorVisible", isDonorVisibleState)
                .addOnSuccessListener {
                    val msg = if (isDonorVisibleState) "রক্তদাতা হিসেবে প্রোফাইল দৃশ্যমান করা হয়েছে" else "রক্তদাতা হিসেবে প্রোফাইল গোপন করা হয়েছে"
                    TopNotification.show(activity, msg)
                }
        }

        // 2. Expand Last Donation Date Editor (Hides Normal Status View)
        btnEditLastDonation.setOnClickListener {
            pendingSelectedLastDonationTimestamp = currentLastDonationTimestamp
            llDonorStatusNormalView.visibility = View.GONE
            llDonorStatusEditView.visibility = View.VISIBLE

            if (pendingSelectedLastDonationTimestamp > 0) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                btnSelectLastDonationDate.text = sdf.format(Date(pendingSelectedLastDonationTimestamp))
            } else {
                btnSelectLastDonationDate.text = "তারিখ নির্বাচন করুন 📅"
            }
        }

        // 3. Date Picker
        btnSelectLastDonationDate.setOnClickListener {
            val constraints = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build()

            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("সর্বশেষ রক্তদানের তারিখ")
                .setSelection(if (pendingSelectedLastDonationTimestamp > 0) pendingSelectedLastDonationTimestamp else System.currentTimeMillis())
                .setCalendarConstraints(constraints)
                .build()

            picker.addOnPositiveButtonClickListener { selectedMillis ->
                pendingSelectedLastDonationTimestamp = selectedMillis
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                btnSelectLastDonationDate.text = sdf.format(Date(selectedMillis))
            }

            picker.show(childFragmentManager, "LAST_DONATION_PICKER")
        }

        // 4. Save Donation Date
        btnSaveLastDonationDate.setOnClickListener {
            if (uid.isEmpty()) return@setOnClickListener
            currentLastDonationTimestamp = pendingSelectedLastDonationTimestamp
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("lastDonationDateTimestamp", currentLastDonationTimestamp)
                .addOnSuccessListener {
                    calculateAndRenderDonationReadiness(currentLastDonationTimestamp)
                    llDonorStatusEditView.visibility = View.GONE
                    llDonorStatusNormalView.visibility = View.VISIBLE
                    TopNotification.show(activity, "রক্তদানের তারিখ আপডেট করা হয়েছে!")
                }
        }

        // 5. Clear Date
        btnClearLastDonationDate.setOnClickListener {
            if (uid.isEmpty()) return@setOnClickListener
            currentLastDonationTimestamp = 0L
            pendingSelectedLastDonationTimestamp = 0L
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("lastDonationDateTimestamp", 0L)
                .addOnSuccessListener {
                    calculateAndRenderDonationReadiness(0L)
                    llDonorStatusEditView.visibility = View.GONE
                    llDonorStatusNormalView.visibility = View.VISIBLE
                    TopNotification.show(activity, "তারিখ মুছে ফেলা হয়েছে!")
                }
        }

        // 6. Cancel Edit
        btnCancelLastDonationEdit.setOnClickListener {
            llDonorStatusEditView.visibility = View.GONE
            llDonorStatusNormalView.visibility = View.VISIBLE
        }
    }

    private fun updateVisibilityButtonUI(isVisible: Boolean) {
        if (isVisible) {
            btnDonorVisibility.setImageResource(R.drawable.ic_eye_open)
            btnDonorVisibility.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
            btnDonorVisibility.setColorFilter(Color.parseColor("#006A4E"))
        } else {
            btnDonorVisibility.setImageResource(R.drawable.ic_eye_off)
            btnDonorVisibility.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
            btnDonorVisibility.setColorFilter(Color.parseColor("#757575"))
        }
    }

    private fun calculateAndRenderDonationReadiness(timestamp: Long) {
        val now = System.currentTimeMillis()
        val ninetyDaysMillis = 90L * 24L * 60L * 60L * 1000L

        if (timestamp == 0L) {
            tvDonorReadinessStatus.text = "রক্তদানে সক্ষম"
            ivDonorStatusIcon.setImageResource(R.drawable.ic_check_circle)
            llDonorStatusCapsule.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
            tvDonorLastDonationDateText.text = "পূর্বে কখনো রক্তদান করেননি"
        } else {
            val nextEligibleDate = timestamp + ninetyDaysMillis
            val sdf = SimpleDateFormat("dd MMMM, yyyy", Locale.getDefault())

            if (now >= nextEligibleDate) {
                tvDonorReadinessStatus.text = "রক্তদানে সক্ষম"
                ivDonorStatusIcon.setImageResource(R.drawable.ic_check_circle)
                llDonorStatusCapsule.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                tvDonorLastDonationDateText.text = "সর্বশেষ রক্তদান: ${sdf.format(Date(timestamp))}"
            } else {
                val remainingMillis = nextEligibleDate - now
                val daysLeft = Math.ceil(remainingMillis / (24.0 * 60.0 * 60.0 * 1000.0)).toInt()
                tvDonorReadinessStatus.text = "অপেক্ষমাণ (আর $daysLeft দিন বাকি)"
                ivDonorStatusIcon.setImageResource(R.drawable.ic_timer_white)
                llDonorStatusCapsule.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E64A19"))
                tvDonorLastDonationDateText.text = "সর্বশেষ রক্তদান: ${sdf.format(Date(timestamp))}"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun showSkeleton(show: Boolean) {
        if (show) {
            llProfileSkeleton.visibility = View.VISIBLE
            rvProfilePosts.visibility = View.GONE
            val pulse = AnimationUtils.loadAnimation(context, R.anim.shimmer_pulse)
            llProfileSkeleton.startAnimation(pulse)
        } else {
            llProfileSkeleton.clearAnimation()
            llProfileSkeleton.visibility = View.GONE
            rvProfilePosts.visibility = View.VISIBLE
        }
    }

    private fun loadUserData() {
        swipeRefresh.isRefreshing = true
        val auth = FirebaseAuth.getInstance()
        val firebaseUser = auth.currentUser
        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val uid = firebaseUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        val userName = sharedPref.getString("user_name", firebaseUser?.displayName ?: "লালপুরবাসী") ?: "লালপুরবাসী"
        val userEmail = firebaseUser?.email ?: "গুগল / ইমেইল ইউজার"

        tvName.text = userName
        tvEmail.text = userEmail

        if (uid.isNotEmpty()) {
            val firestore = FirebaseFirestore.getInstance()
            showSkeleton(true)

            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val mobile = doc.getString("mobile") ?: ""
                        val age = doc.getLong("age")?.toString() ?: ""
                        val dobTimestamp = doc.getLong("dobTimestamp") ?: 0L
                        val weight = doc.getLong("weight")?.toString() ?: ""
                        val gender = doc.getString("gender") ?: ""
                        val bloodGroup = doc.getString("bloodGroup") ?: ""
                        val district = doc.getString("district") ?: "নাটোর"
                        val upazila = doc.getString("upazila") ?: "লালপুর"
                        val village = doc.getString("village") ?: ""
                        val whatsapp = doc.getString("whatsappUsername") ?: ""
                        val messenger = doc.getString("messengerLink") ?: ""
                        val isDonorReg = doc.getBoolean("isDonorRegistered") ?: (bloodGroup.isNotEmpty())

                        val isVerified = doc.getBoolean("isVerified") ?: false
                        val verifiedUntil = doc.getLong("verifiedUntil") ?: 0L
                        val now = System.currentTimeMillis()
                        val isUserVerified = isVerified && verifiedUntil > now

                        ivVerifiedBadge.visibility = if (isUserVerified) View.VISIBLE else View.GONE

                        // Populate Personal Information
                        tvMobile.text = if (mobile.isNotEmpty()) mobile else "তথ্য নেই"

                        var ageDobStr = ""
                        if (age.isNotEmpty()) ageDobStr += "$age বছর"
                        if (dobTimestamp > 0) {
                            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            ageDobStr += if (ageDobStr.isNotEmpty()) " (${sdf.format(Date(dobTimestamp))})" else sdf.format(Date(dobTimestamp))
                        }
                        tvAgeDob.text = if (ageDobStr.isNotEmpty()) ageDobStr else "তথ্য নেই"

                        tvWeight.text = if (weight.isNotEmpty() && weight != "0") "$weight কেজি" else "তথ্য নেই"
                        tvGender.text = if (gender.isNotEmpty()) gender else "তথ্য নেই"
                        tvBloodGroup.text = if (bloodGroup.isNotEmpty()) bloodGroup else "তথ্য নেই"

                        val addressParts = listOf(village, upazila, district).filter { it.isNotEmpty() }
                        tvAddress.text = if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else "তথ্য নেই"

                        val contactLinks = mutableListOf<String>()
                        if (whatsapp.isNotEmpty()) contactLinks.add("WA: @$whatsapp")
                        if (messenger.isNotEmpty()) contactLinks.add("Messenger")
                        if (contactLinks.isNotEmpty()) {
                            llContactLinksRow.visibility = View.VISIBLE
                            tvContactLinks.text = contactLinks.joinToString(" | ")
                        } else {
                            llContactLinksRow.visibility = View.GONE
                        }

                        // Populate Blood Donor Card
                        if (isDonorReg && bloodGroup.isNotEmpty()) {
                            llDonorUnregistered.visibility = View.GONE
                            llDonorRegistered.visibility = View.VISIBLE

                            tvDonorBloodGroupBadge.text = bloodGroup
                            tvDonorName.text = userName
                            ivDonorVerifiedBadge.visibility = if (isUserVerified) View.VISIBLE else View.GONE
                            tvDonorAddressHeader.text = "$upazila, $district"

                            isDonorVisibleState = doc.getBoolean("isDonorVisible") ?: true
                            updateVisibilityButtonUI(isDonorVisibleState)

                            currentLastDonationTimestamp = doc.getLong("lastDonationDateTimestamp") ?: 0L
                            calculateAndRenderDonationReadiness(currentLastDonationTimestamp)

                            // Contact Buttons
                            if (mobile.isNotEmpty()) {
                                btnDonorCall.visibility = View.VISIBLE
                                btnDonorCall.setOnClickListener {
                                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobile")))
                                }
                            } else {
                                btnDonorCall.visibility = View.GONE
                            }

                            if (whatsapp.isNotEmpty()) {
                                btnDonorWhatsApp.visibility = View.VISIBLE
                                btnDonorWhatsApp.setOnClickListener {
                                    val cleanNum = if (whatsapp.startsWith("0")) "88$whatsapp" else if (whatsapp.startsWith("+")) whatsapp.removePrefix("+") else whatsapp
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum")).apply { setPackage("com.whatsapp") })
                                    } catch (e: Exception) {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum")))
                                    }
                                }
                            } else {
                                btnDonorWhatsApp.visibility = View.GONE
                            }

                            if (messenger.isNotEmpty()) {
                                btnDonorMessenger.visibility = View.VISIBLE
                                btnDonorMessenger.setOnClickListener {
                                    val cleanLink = if (!messenger.startsWith("http")) "https://$messenger" else messenger
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanLink)))
                                    } catch (e: Exception) {}
                                }
                            } else {
                                btnDonorMessenger.visibility = View.GONE
                            }

                        } else {
                            llDonorRegistered.visibility = View.GONE
                            llDonorUnregistered.visibility = View.VISIBLE
                        }

                        val photoUrl = doc.getString("photoUrl")
                        val coverUrl = doc.getString("coverUrl")

                        if (!photoUrl.isNullOrEmpty()) {
                            Glide.with(this).load(photoUrl).circleCrop().into(ivAvatar)
                            Glide.with(this).load(photoUrl).circleCrop().into(ivDonorAvatar)
                        } else {
                            val avatarSource: Any = firebaseUser?.photoUrl ?: try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
                            Glide.with(this).load(avatarSource).circleCrop().into(ivAvatar)
                            Glide.with(this).load(avatarSource).circleCrop().into(ivDonorAvatar)
                        }

                        if (!coverUrl.isNullOrEmpty()) {
                            Glide.with(this).load(coverUrl).into(ivCover)
                        } else {
                            val coverRes = try { R.drawable.draft_cover } catch (e: Exception) { R.color.primary_green }
                            Glide.with(this).load(coverRes).into(ivCover)
                        }
                    }
                }

            firestore.collection("openchat").whereEqualTo("userid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        showSkeleton(false)
                        swipeRefresh.isRefreshing = false
                        return@addSnapshotListener
                    }

                    myPostsList.clear()
                    for (doc in snapshot.documents) {
                        val content = FirestoreSafeParser.parseString(doc.get("content"))
                        if (content.isNotEmpty()) {
                            val likedByRaw = doc.get("likedBy") as? List<*>
                            val likedByList = likedByRaw?.map { it.toString() } ?: emptyList()

                            val isVer = FirestoreSafeParser.parseBoolean(doc.get("isVerified"), false)
                            val verUntil = FirestoreSafeParser.parseLong(doc.get("verifiedUntil"), 0L)
                            val postImg = FirestoreSafeParser.parseString(doc.get("postImageUrl"))

                            myPostsList.add(
                                OpenChatPost(
                                    id = doc.id,
                                    userid = FirestoreSafeParser.parseString(doc.get("userid")),
                                    userName = FirestoreSafeParser.parseString(doc.get("userName"), userName),
                                    userAvatar = FirestoreSafeParser.parseString(doc.get("userAvatar")),
                                    content = content,
                                    postImageUrl = postImg,
                                    isVerified = isVer,
                                    verifiedUntil = verUntil,
                                    uploadtime = FirestoreSafeParser.parseTimestampToMillis(doc.get("uploadtime")),
                                    likesCount = FirestoreSafeParser.parseInt(doc.get("likesCount"), 0),
                                    repliesCount = FirestoreSafeParser.parseInt(doc.get("repliesCount"), 0),
                                    likedBy = likedByList
                                )
                            )
                        }
                    }

                    myPostsList.sortByDescending { it.uploadtime }
                    profilePostAdapter.setPosts(myPostsList)

                    showSkeleton(false)

                    if (myPostsList.isEmpty()) {
                        tvNoProfilePosts.visibility = View.VISIBLE
                        rvProfilePosts.visibility = View.GONE
                    } else {
                        tvNoProfilePosts.visibility = View.GONE
                        rvProfilePosts.visibility = View.VISIBLE
                    }

                    swipeRefresh.isRefreshing = false
                }
        } else {
            ivVerifiedBadge.visibility = View.GONE
            val avatarSource: Any = firebaseUser?.photoUrl ?: try { R.drawable.draft_user } catch (e: Exception) { R.drawable.ic_profile }
            Glide.with(this).load(avatarSource).circleCrop().into(ivAvatar)
            val coverRes = try { R.drawable.draft_cover } catch (e: Exception) { R.color.primary_green }
            Glide.with(this).load(coverRes).into(ivCover)
            swipeRefresh.isRefreshing = false
        }
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
        val sharedPref = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        (activity as? MainActivity)?.updateAuthState()
        TopNotification.show(activity, "সফলভাবে লগআউট করা হয়েছে!")

        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav?.selectedItemId = R.id.nav_home
    }
}
