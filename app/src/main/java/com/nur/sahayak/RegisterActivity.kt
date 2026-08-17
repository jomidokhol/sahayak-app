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
import com.nur.sahayak.models.User
import com.nur.sahayak.utils.TopNotification

class RegisterActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_register)

        // Make Status Bar Transparent for Notch Gradient Header
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

        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etAge = findViewById<EditText>(R.id.etAge)
        val etMobile = findViewById<EditText>(R.id.etMobile)
        val etEmail = findViewById<EditText>(R.id.etRegEmail)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnSubmit = findViewById<Button>(R.id.btnRegisterSubmit)
        val btnGoogleRegister = findViewById<Button>(R.id.btnGoogleRegister)

        btnSubmit.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val ageStr = etAge.text.toString().trim()
            val mobile = etMobile.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            val confirmPass = etConfirmPassword.text.toString().trim()

            if (firstName.isEmpty() || email.isEmpty() || pass.isEmpty() || mobile.isEmpty()) {
                TopNotification.show(this, "সকল প্রয়োজনীয় ঘর পূরণ করুন")
                return@setOnClickListener
            }

            if (pass != confirmPass) {
                TopNotification.show(this, "পাসওয়ার্ড ম্যাচ করেনি")
                return@setOnClickListener
            }

            showRippleLoading()
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: ""
                    val userObj = User(
                        uid = uid,
                        firstName = firstName,
                        lastName = lastName,
                        age = ageStr.toIntOrNull() ?: 0,
                        mobile = mobile,
                        email = email,
                        role = "user"
                    )

                    FirebaseFirestore.getInstance().collection("users").document(uid).set(userObj)
                        .addOnSuccessListener {
                            val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
                            sharedPref.edit().apply {
                                putBoolean("is_logged_in", true)
                                putString("user_name", "$firstName $lastName")
                                putString("user_uid", uid)
                                apply()
                            }
                            hideRippleLoading()
                            redirectToHome()
                        }
                }
                .addOnFailureListener { err ->
                    hideRippleLoading()
                    TopNotification.show(this, "রেজিস্ট্রেশন ব্যর্থ: ${err.message}")
                }
        }

        btnGoogleRegister.setOnClickListener {
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
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                val uid = firebaseUser?.uid ?: ""
                val displayName = firebaseUser?.displayName ?: "ইউজার"

                val userObj = User(
                    uid = uid,
                    firstName = displayName,
                    email = firebaseUser?.email ?: "",
                    role = "user"
                )

                FirebaseFirestore.getInstance().collection("users").document(uid).set(userObj)

                val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
                sharedPref.edit().apply {
                    putBoolean("is_logged_in", true)
                    putString("user_uid", uid)
                    putString("user_name", displayName)
                    apply()
                }
                hideRippleLoading()
                redirectToHome()
            }
            .addOnFailureListener { err ->
                hideRippleLoading()
                TopNotification.show(this, "অথেনটিফিকেশন ব্যর্থ: ${err.message}")
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

    private fun redirectToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
