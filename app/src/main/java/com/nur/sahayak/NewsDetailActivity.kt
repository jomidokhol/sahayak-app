package com.nur.sahayak

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.utils.FormatUtils
import com.nur.sahayak.utils.TimeUtils
import com.nur.sahayak.utils.TopNotification

class NewsDetailActivity : AppCompatActivity() {

    private lateinit var rlRoot: RelativeLayout
    private lateinit var wvContent: WebView
    private lateinit var tvTitle: TextView
    private lateinit var tvDate: TextView
    private lateinit var vDivider: View
    private lateinit var btnFontPlus: ImageButton
    private lateinit var btnFontMinus: ImageButton
    private lateinit var btnReadingMode: ImageButton
    private lateinit var btnShareHeader: ImageButton

    private var currentFontSizeSp = 15
    private var isSepiaMode = false
    private var rawHtmlDesc = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news_detail)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        rlRoot = findViewById(R.id.rlNewsDetailRoot)
        wvContent = findViewById(R.id.wvNewsDetailContent)
        tvTitle = findViewById(R.id.tvNewsDetailTitle)
        tvDate = findViewById(R.id.tvNewsDetailDate)
        vDivider = findViewById(R.id.vDivider)
        btnFontPlus = findViewById(R.id.btnFontPlus)
        btnFontMinus = findViewById(R.id.btnFontMinus)
        btnReadingMode = findViewById(R.id.btnReadingMode)
        btnShareHeader = findViewById(R.id.btnShareNewsHeader)

        val btnBack = findViewById<ImageButton>(R.id.btnBackNewsDetail)
        val ivBlurBg = findViewById<ImageView>(R.id.ivNewsDetailBlurBg)
        val ivCover = findViewById<ImageView>(R.id.ivNewsDetailCover)

        btnBack.setOnClickListener { finish() }

        val newsId = intent.getStringExtra("id") ?: ""
        val title = intent.getStringExtra("title") ?: "সংবাদ"
        val reporter = intent.getStringExtra("reporter") ?: ""
        val imageUrl = intent.getStringExtra("imageUrl") ?: ""
        rawHtmlDesc = intent.getStringExtra("desc") ?: ""
        val viewCount = intent.getIntExtra("viewCount", 0)
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

        // Increment View Count
        if (newsId.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("news_list").document(newsId)
                .update("viewCount", FieldValue.increment(1))
        }

        tvTitle.text = title
        val timeStr = TimeUtils.getTimeAgo(timestamp)
        val reporterStr = if (reporter.trim().isNotEmpty()) reporter.trim() else "নিজস্ব প্রতিবেদক"
        val viewsStr = "${FormatUtils.formatCount(viewCount + 1)} ভিউ"
        tvDate.text = "$timeStr • $reporterStr • $viewsStr"

        // Reliable Deep Link Share Text
        btnShareHeader.setOnClickListener {
            val shareId = if (newsId.isNotEmpty()) newsId else "article"
            val shareUrl = "https://app-sahayak.vercel.app/news/$shareId"
            val shareText = "📰 $title\n\nবিস্তারিত পড়তে ক্লিক করুন:\n$shareUrl"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, "সংবাদ শেয়ার করুন"))
        }

        val loadingRes = try { R.drawable.news_loading } catch (e: Exception) { R.drawable.flogo }

        if (imageUrl.isNotEmpty()) {
            Glide.with(this).load(imageUrl).placeholder(loadingRes).into(ivCover)
            Glide.with(this).load(imageUrl).placeholder(loadingRes).into(ivBlurBg)
        } else {
            ivCover.setImageResource(loadingRes)
            ivBlurBg.setImageResource(loadingRes)
        }

        wvContent.settings.javaScriptEnabled = true
        wvContent.settings.domStorageEnabled = true
        wvContent.setBackgroundColor(Color.TRANSPARENT)

        wvContent.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (!url.isNullOrEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {}
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

        renderHtmlContent()

        btnFontPlus.setOnClickListener {
            if (currentFontSizeSp < 24) {
                currentFontSizeSp += 2
                renderHtmlContent()
                TopNotification.show(this, "লেখা বড় করা হয়েছে ($currentFontSizeSp px)")
            } else {
                TopNotification.show(this, "সর্বোচ্চ ফন্ট সাইজ")
            }
        }

        btnFontMinus.setOnClickListener {
            if (currentFontSizeSp > 11) {
                currentFontSizeSp -= 2
                renderHtmlContent()
                TopNotification.show(this, "লেখা ছোট করা হয়েছে ($currentFontSizeSp px)")
            } else {
                TopNotification.show(this, "সর্বনিম্ন ফন্ট সাইজ")
            }
        }

        btnReadingMode.setOnClickListener {
            isSepiaMode = !isSepiaMode
            applyReadingModeTheme()
        }
    }

    private fun applyReadingModeTheme() {
        if (isSepiaMode) {
            val sepiaBg = Color.parseColor("#FBF0D9")
            val sepiaText = Color.parseColor("#2C221E")

            rlRoot.setBackgroundColor(sepiaBg)
            tvTitle.setTextColor(Color.parseColor("#006A4E"))
            tvDate.setTextColor(Color.parseColor("#665A52"))
            vDivider.setBackgroundColor(Color.parseColor("#D8C8B8"))
            btnReadingMode.setColorFilter(Color.parseColor("#FFC107"))

            TopNotification.show(this, "ওয়ার্ম বুক রিডিং মোড অন করা হয়েছে")
        } else {
            rlRoot.setBackgroundColor(Color.parseColor("#F0F2F5"))
            tvTitle.setTextColor(Color.parseColor("#006A4E"))
            tvDate.setTextColor(Color.parseColor("#757575"))
            vDivider.setBackgroundColor(Color.parseColor("#E0E0E0"))
            btnReadingMode.setColorFilter(Color.WHITE)

            TopNotification.show(this, "নরমাল মোডে ফেরত আসা হয়েছে")
        }
        renderHtmlContent()
    }

    private fun renderHtmlContent() {
        val textColorHex = if (isSepiaMode) "#2C221E" else "#212121"
        val linkColorHex = "#006A4E"

        val styledHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: 'Segoe UI', Roboto, sans-serif;
                        color: $textColorHex;
                        font-size: ${currentFontSizeSp}px;
                        line-height: 1.65;
                        margin: 0;
                        padding: 0;
                        background-color: transparent;
                    }
                    img {
                        max-width: 100% !important;
                        height: auto !important;
                        border-radius: 8px;
                        margin: 10px 0;
                    }
                    a {
                        color: $linkColorHex;
                        font-weight: bold;
                        text-decoration: underline;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        color: $linkColorHex;
                        margin-top: 15px;
                        margin-bottom: 8px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 10px 0;
                    }
                    table, th, td {
                        border: 1px solid #CCCCCC;
                        padding: 8px;
                    }
                </style>
            </head>
            <body>
                $rawHtmlDesc
            </body>
            </html>
        """.trimIndent()

        wvContent.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
    }
}
