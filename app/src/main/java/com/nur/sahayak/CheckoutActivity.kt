package com.nur.sahayak

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnimationUtils
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.utils.ConfettiView
import com.nur.sahayak.utils.TopNotification
import org.json.JSONObject

class CheckoutActivity : AppCompatActivity() {

    private lateinit var tvPlanTitle: TextView
    private lateinit var tvPlanDuration: TextView
    private lateinit var tvPrice: TextView

    private lateinit var cardBkash: MaterialCardView
    private lateinit var cardNagad: MaterialCardView
    private lateinit var btnProceed: Button
    private lateinit var btnBack: ImageButton

    // Payment Overlay
    private lateinit var rlOverlay: RelativeLayout
    private lateinit var btnCloseOverlay: ImageButton
    private lateinit var tvOverlayTitle: TextView
    private lateinit var llPlaceholder: LinearLayout
    private lateinit var ivLogoPulse: ImageView
    private lateinit var wvPayment: WebView

    private var selectedMethod = "bkash"

    private var planType = "1_month"
    private var planTitle = "১ মাস মেয়াদী ভেরিফিকেশন"
    private var durationDays = 30
    private var finalPrice = 50

    private var currentUid = ""
    private val registeredDomain = "https://app-sahayak.vercel.app"
    private var isPaymentHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        tvPlanTitle = findViewById(R.id.tvCheckoutPlanTitle)
        tvPlanDuration = findViewById(R.id.tvCheckoutPlanDuration)
        tvPrice = findViewById(R.id.tvCheckoutPrice)

        cardBkash = findViewById(R.id.cardPaymentBkash)
        cardNagad = findViewById(R.id.cardPaymentNagad)
        btnProceed = findViewById(R.id.btnProceedPayment)
        btnBack = findViewById(R.id.btnBackCheckout)

        rlOverlay = findViewById(R.id.rlPaymentOverlay)
        btnCloseOverlay = findViewById(R.id.btnClosePaymentOverlay)
        tvOverlayTitle = findViewById(R.id.tvPaymentOverlayTitle)
        llPlaceholder = findViewById(R.id.llPaymentPlaceholder)
        ivLogoPulse = findViewById(R.id.ivPaymentLogoPulse)
        wvPayment = findViewById(R.id.wvPaymentGateway)

        btnBack.setOnClickListener { finish() }

        btnCloseOverlay.setOnClickListener {
            dismissPaymentOverlayCleanly()
        }

        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        currentUid = auth.currentUser?.uid ?: sharedPref.getString("user_uid", "") ?: ""

        planType = intent.getStringExtra("plan_type") ?: "1_month"
        planTitle = intent.getStringExtra("plan_title") ?: "১ মাস মেয়াদী ভেরিফিকেশন"
        durationDays = intent.getIntExtra("plan_duration_days", 30)
        finalPrice = intent.getIntExtra("final_price", 50)

        tvPlanTitle.text = "প্ল্যান: $planTitle"
        tvPlanDuration.text = "মেয়াদ: $durationDays দিন"
        tvPrice.text = "মোট প্রদেয়: ৳$finalPrice"
        btnProceed.text = "প্রোসিড"

        setupMethodSelection()

        btnProceed.setOnClickListener {
            launchPaymentGateway()
        }
    }

    private fun setupMethodSelection() {
        cardBkash.setOnClickListener {
            selectedMethod = "bkash"
            highlightMethodCard(cardBkash, cardNagad)
        }

        cardNagad.setOnClickListener {
            selectedMethod = "nagad"
            highlightMethodCard(cardNagad, cardBkash)
        }
    }

    private fun highlightMethodCard(selected: MaterialCardView, unselected: MaterialCardView) {
        selected.strokeColor = Color.parseColor("#006A4E")
        selected.strokeWidth = dpToPx(2.5f)
        selected.setCardBackgroundColor(Color.parseColor("#E8F5E9"))

        unselected.strokeColor = Color.parseColor("#CCCCCC")
        unselected.strokeWidth = dpToPx(1.5f)
        unselected.setCardBackgroundColor(Color.WHITE)
    }

    private fun launchPaymentGateway() {
        if (currentUid.isEmpty()) {
            Toast.makeText(this, "পেমেন্ট করতে লগইন করুন", Toast.LENGTH_SHORT).show()
            return
        }

        isPaymentHandled = false
        btnProceed.visibility = View.GONE
        rlOverlay.visibility = View.VISIBLE
        llPlaceholder.visibility = View.VISIBLE
        wvPayment.visibility = View.GONE
        tvOverlayTitle.text = if (selectedMethod == "bkash") "বিকাশ পেমেন্ট গেটওয়ে" else "নগদ পেমেন্ট গেটওয়ে"

        val pulse = AnimationUtils.loadAnimation(this, R.anim.shimmer_pulse)
        ivLogoPulse.startAnimation(pulse)

        FirebaseFirestore.getInstance().collection("settings").document("payment_config").get()
            .addOnCompleteListener { task ->
                var merchantId = "MER-123456"
                var apiKey = if (selectedMethod == "bkash") "BK-SAHAYAK" else "NG-SAHAYAK"
                var baseGatewayUrl = "https://pay-p2p.vercel.app"

                if (task.isSuccessful && task.result != null && task.result!!.exists()) {
                    val doc = task.result!!
                    merchantId = doc.getString("merchantId") ?: merchantId
                    apiKey = if (selectedMethod == "bkash") {
                        doc.getString("bkashApiKey") ?: apiKey
                    } else {
                        doc.getString("nagadApiKey") ?: apiKey
                    }
                    val remoteGateway = doc.getString("gatewayUrl") ?: ""
                    if (remoteGateway.isNotEmpty()) baseGatewayUrl = remoteGateway.trim().trimEnd('/')
                }

                initiateWebViewPayment(merchantId, apiKey, baseGatewayUrl)
            }
    }

    private fun initiateWebViewPayment(merchantId: String, apiKey: String, baseGatewayUrl: String) {
        val orderId = "ORD-${System.currentTimeMillis()}"

        val payloadObj = JSONObject().apply {
            put("amount", finalPrice)
            put("orderId", orderId)
            put("merchantId", merchantId)
            put("apiKey", apiKey)
            put("method", selectedMethod)
            put("domain", "app-sahayak.vercel.app")
            put("appDomain", registeredDomain)
        }

        val encodedPayload = Base64.encodeToString(payloadObj.toString().toByteArray(), Base64.NO_WRAP)
        val endpoint = if (selectedMethod == "bkash") "p2p/bkash-payment.html" else "p2p/nagad-payment.html"
        val paymentUrl = "$baseGatewayUrl/$endpoint?data=$encodedPayload"

        Log.d("CheckoutActivity", "Target Gateway URL: $paymentUrl")

        val customHeaders = hashMapOf(
            "Referer" to "$registeredDomain/",
            "Origin" to registeredDomain,
            "X-Requested-With" to "com.nur.sahayak"
        )

        wvPayment.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        wvPayment.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                runOnUiThread {
                    dismissPaymentOverlayCleanly()
                }
            }
        }

        wvPayment.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun onPaymentSuccess(dataJson: String) {
                runOnUiThread {
                    handlePaymentSuccess(dataJson)
                }
            }

            @JavascriptInterface
            fun onPaymentFailure(errorMsg: String) {
                runOnUiThread {
                    handlePaymentFailure(errorMsg)
                }
            }

            @JavascriptInterface
            fun onWindowClose() {
                runOnUiThread {
                    dismissPaymentOverlayCleanly()
                }
            }
        }, "AndroidPaymentBridge")

        val jsOpenerPolyfill = """
            (function() {
                try {
                    Object.defineProperty(document, 'referrer', {
                        get: function() { return '$registeredDomain/'; },
                        configurable: true
                    });

                    var fakeOpener = {
                        postMessage: function(msg, domain) {
                            try {
                                if (typeof AndroidPaymentBridge !== 'undefined') {
                                    if (msg && msg.status === 'SUCCESS') {
                                        AndroidPaymentBridge.onPaymentSuccess(JSON.stringify(msg.data || {}));
                                    } else if (msg && (msg.status === 'FAILED' || msg.status === 'CANCELLED')) {
                                        AndroidPaymentBridge.onPaymentFailure(msg.message || 'Payment Cancelled');
                                    }
                                }
                            } catch(e) {}
                        }
                    };

                    window.opener = fakeOpener;
                    window.parent = fakeOpener;

                    window.close = function() {
                        try {
                            if (typeof AndroidPaymentBridge !== 'undefined') {
                                AndroidPaymentBridge.onWindowClose();
                            }
                        } catch(e) {}
                    };

                    window.addEventListener('message', function(event) {
                        if (event.data) {
                            if (event.data.status === 'SUCCESS') {
                                AndroidPaymentBridge.onPaymentSuccess(JSON.stringify(event.data.data || {}));
                            } else if (event.data.status === 'FAILED' || event.data.status === 'CANCELLED') {
                                AndroidPaymentBridge.onPaymentFailure(event.data.message || 'Payment Failed');
                            }
                        }
                    });
                } catch(e) {}
            })();
        """.trimIndent()

        wvPayment.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.evaluateJavascript(jsOpenerPolyfill, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                ivLogoPulse.clearAnimation()
                llPlaceholder.visibility = View.GONE
                wvPayment.visibility = View.VISIBLE
                view?.evaluateJavascript(jsOpenerPolyfill, null)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: ""
                if (url.contains("status=SUCCESS") || url.contains("payment-success")) {
                    handlePaymentSuccess("{}")
                    return true
                } else if (url.contains("status=FAILED") || url.contains("payment-failed") || url.contains("status=CANCELLED")) {
                    handlePaymentFailure("পেমেন্ট সম্পন্ন হয়নি")
                    return true
                }

                if (request != null && !url.startsWith("intent://") && !url.startsWith("tel:")) {
                    view?.loadUrl(url, customHeaders)
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

        wvPayment.loadUrl(paymentUrl, customHeaders)
    }

    private fun handlePaymentSuccess(dataJson: String) {
        if (isPaymentHandled) return
        isPaymentHandled = true

        rlOverlay.visibility = View.GONE
        btnProceed.visibility = View.VISIBLE
        wvPayment.stopLoading()

        var trxId = "TXN-${System.currentTimeMillis().toString().takeLast(8)}"
        try {
            val json = JSONObject(dataJson)
            trxId = json.optString("trxID", trxId)
        } catch (e: Exception) {}

        val loadingDialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar).apply {
            setContentView(R.layout.dialog_auth_ripple)
            setCancelable(false)
        }
        val vRed = loadingDialog.findViewById<View>(R.id.vRippleRed)
        val vGreen = loadingDialog.findViewById<View>(R.id.vRippleGreen)
        vRed?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ripple_red))
        vGreen?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ripple_green))
        loadingDialog.show()

        val verifiedUntilMillis = System.currentTimeMillis() + (durationDays.toLong() * 24L * 60L * 60L * 1000L)

        val updateMap = hashMapOf<String, Any>(
            "isVerified" to true,
            "verifiedUntil" to verifiedUntilMillis,
            "verifiedPlan" to planType,
            "verifiedTrxId" to trxId,
            "verifiedAt" to Timestamp.now()
        )

        FirebaseFirestore.getInstance().collection("users").document(currentUid)
            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                loadingDialog.dismiss()
                showCelebrationDialogWithConfetti(trxId)
            }
            .addOnFailureListener {
                loadingDialog.dismiss()
                showCelebrationDialogWithConfetti(trxId)
            }
    }

    private fun showCelebrationDialogWithConfetti(trxId: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_success_celebration, null)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.setCancelable(false)

        val tvPlan = dialogView.findViewById<TextView>(R.id.tvCelebrationPlanTitle)
        val tvTrx = dialogView.findViewById<TextView>(R.id.tvCelebrationTrxId)
        val tvDur = dialogView.findViewById<TextView>(R.id.tvCelebrationDuration)
        val btnGoProfile = dialogView.findViewById<Button>(R.id.btnCelebrationGoProfile)
        val confettiInDialog = dialogView.findViewById<ConfettiView>(R.id.confettiDialogView)

        tvPlan.text = "$planTitle সফলভাবে সক্রিয় হয়েছে"
        tvTrx.text = "TrxID: $trxId"
        tvDur.text = "মেয়াদ: $durationDays দিন"

        btnGoProfile.setOnClickListener {
            dialog.dismiss()
            redirectToProfilePage()
        }

        dialog.show()

        confettiInDialog.post {
            confettiInDialog.startConfetti()
        }
    }

    private fun redirectToProfilePage() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_tab", "PROFILE")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun handlePaymentFailure(errorMsg: String) {
        if (isPaymentHandled) return
        isPaymentHandled = true

        rlOverlay.visibility = View.GONE
        btnProceed.visibility = View.VISIBLE
        wvPayment.stopLoading()

        MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle("পেমেন্ট সম্পন্ন হয়নি")
            .setMessage(if (errorMsg.isNotEmpty()) errorMsg else "পেমেন্ট বাতিল বা ব্যর্থ হয়েছে। অনুগ্রহ করে পুনরায় চেষ্টা করুন।")
            .setPositiveButton("ঠিক আছে", null)
            .show()
    }

    private fun dismissPaymentOverlayCleanly() {
        rlOverlay.visibility = View.GONE
        btnProceed.visibility = View.VISIBLE
        wvPayment.stopLoading()
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
