package com.nur.sahayak

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nur.sahayak.utils.TopNotification

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var authLoadingDialog: Dialog? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            hideRippleLoading()
            TopNotification.show(this, "গুগল সাইন-ইন ব্যর্থ হয়েছে")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        auth = FirebaseAuth.getInstance()

        val defaultWebClientId = try {
            getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            ""
        }

        if (defaultWebClientId.isNotEmpty()) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(defaultWebClientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        }

        val etEmail = findViewById<EditText>(R.id.etLoginEmail)
        val etPassword = findViewById<EditText>(R.id.etLoginPassword)
        val btnLogin = findViewById<Button>(R.id.btnLoginSubmit)
        val btnGoogle = findViewById<Button>(R.id.btnGoogleLogin)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                showRippleLoading()
                auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: ""
                        // Fetch existing profile data from Firestore without resetting
                        fetchExistingUserProfileAndRedirect(uid, authResult.user?.displayName ?: "ইউজার", "")
                    }
                    .addOnFailureListener { err ->
                        hideRippleLoading()
                        TopNotification.show(this, "লগইন ব্যর্থ: ${err.message}")
                    }
            } else {
                TopNotification.show(this, "ইমেইল ও পাসওয়ার্ড প্রদান করুন")
            }
        }

        btnGoogle.setOnClickListener {
            if (::googleSignInClient.isInitialized) {
                showRippleLoading()
                googleSignInClient.signOut().addOnCompleteListener {
                    val signInIntent = googleSignInClient.signInIntent
                    googleSignInLauncher.launch(signInIntent)
                }
            } else {
                TopNotification.show(this, "google-services.json ফাইল আপডেট করা আবশ্যক!")
            }
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                val uid = firebaseUser?.uid ?: ""
                val googleName = firebaseUser?.displayName ?: "ইউজার"
                val googlePhoto = firebaseUser?.photoUrl?.toString() ?: ""

                val firestore = FirebaseFirestore.getInstance()
                
                // Smart Check: DO NOT overwrite existing customized user data!
                firestore.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            // User already exists -> Restore customized name & photo
                            val fName = doc.getString("firstName") ?: ""
                            val lName = doc.getString("lastName") ?: ""
                            val fullName = if ("$fName $lName".trim().isNotEmpty()) "$fName $lName".trim() else googleName
                            val photoUrl = doc.getString("photoUrl") ?: googlePhoto

                            saveUserSession(uid, fullName, photoUrl)
                            hideRippleLoading()
                            redirectToHome()
                        } else {
                            // First time Google login -> Create new user with merge
                            val userMap = hashMapOf<String, Any>(
                                "uid" to uid,
                                "firstName" to googleName,
                                "email" to (firebaseUser?.email ?: ""),
                                "role" to "user",
                                "photoUrl" to googlePhoto,
                                "coverUrl" to "",
                                "mobile" to "",
                                "age" to 0
                            )

                            firestore.collection("users").document(uid).set(userMap, SetOptions.merge())
                                .addOnSuccessListener {
                                    saveUserSession(uid, googleName, googlePhoto)
                                    hideRippleLoading()
                                    redirectToHome()
                                }
                        }
                    }
                    .addOnFailureListener {
                        saveUserSession(uid, googleName, googlePhoto)
                        hideRippleLoading()
                        redirectToHome()
                    }
            }
            .addOnFailureListener { err ->
                hideRippleLoading()
                TopNotification.show(this, "অথেনটিফিকেশন ব্যর্থ: ${err.message}")
            }
    }

    private fun fetchExistingUserProfileAndRedirect(uid: String, fallbackName: String, fallbackPhoto: String) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = if (doc.exists()) {
                    val fName = doc.getString("firstName") ?: ""
                    val lName = doc.getString("lastName") ?: ""
                    if ("$fName $lName".trim().isNotEmpty()) "$fName $lName".trim() else fallbackName
                } else fallbackName

                val photo = if (doc.exists()) {
                    doc.getString("photoUrl") ?: fallbackPhoto
                } else fallbackPhoto

                saveUserSession(uid, name, photo)
                hideRippleLoading()
                redirectToHome()
            }
            .addOnFailureListener {
                saveUserSession(uid, fallbackName, fallbackPhoto)
                hideRippleLoading()
                redirectToHome()
            }
    }

    private fun showRippleLoading() {
        if (authLoadingDialog == null) {
            authLoadingDialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar).apply {
                setContentView(R.layout.dialog_auth_ripple)
                setCancelable(false)
            }
        }
        val vRed = authLoadingDialog?.findViewById<View>(R.id.vRippleRed)
        val vGreen = authLoadingDialog?.findViewById<View>(R.id.vRippleGreen)

        val animRed = AnimationUtils.loadAnimation(this, R.anim.ripple_red)
        val animGreen = AnimationUtils.loadAnimation(this, R.anim.ripple_green)

        vRed?.startAnimation(animRed)
        vGreen?.startAnimation(animGreen)

        authLoadingDialog?.show()
    }

    private fun hideRippleLoading() {
        try {
            authLoadingDialog?.dismiss()
        } catch (e: Exception) {}
    }

    private fun saveUserSession(uid: String, name: String, photoUrl: String = "") {
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putBoolean("is_logged_in", true)
            putString("user_uid", uid)
            putString("user_name", name)
            if (photoUrl.isNotEmpty()) {
                putString("user_photo_url", photoUrl)
            }
            apply()
        }
    }

    private fun redirectToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
