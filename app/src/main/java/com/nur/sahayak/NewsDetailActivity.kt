package com.nur.sahayak

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
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

        if (newsId.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("news_list").document(newsId)
                .update("viewCount", FieldValue.increment(1))
        }

        tvTitle.text = title
        val timeStr = TimeUtils.getTimeAgo(timestamp)
        val reporterStr = if (reporter.trim().isNotEmpty()) reporter.trim() else "নিজস্ব প্রতিবেদক"
        val viewsStr = "${FormatUtils.formatCount(viewCount + 1)} ভিউ"
        tvDate.text = "$timeStr • $reporterStr • $viewsStr"

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

        wvContent.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        wvContent.setBackgroundColor(Color.TRANSPARENT)
        wvContent.webChromeClient = WebChromeClient()

        wvContent.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val uri = Uri.parse(url)

                // 1. Custom Action Trigger for <phone> and <wa> tags
                if (url.startsWith("sahayak-action://")) {
                    val actionType = uri.host ?: ""
                    val number = uri.path?.removePrefix("/") ?: ""
                    showActionBottomSheet(actionType, number)
                    return true
                }

                // 2. Allow YouTube iframes / subframes to load internal streams without opening browser
                if (request != null && !request.isForMainFrame) {
                    return false
                }

                // 3. User clicked external link in main frame -> Open Phone Default Browser
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (url == "https://app-sahayak.vercel.app" || url == "https://app-sahayak.vercel.app/") {
                        return false
                    }
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        TopNotification.show(this@NewsDetailActivity, "ব্রাউজার ওপেন করা সম্ভব হয়নি")
                    }
                    return true
                }

                if (url.startsWith("tel:") || url.startsWith("mailto:")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (e: Exception) {}
                    return true
                }

                return true
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

    private fun showActionBottomSheet(type: String, rawNumber: String) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_news_action, null)
        dialog.setContentView(sheetView)

        val ivIcon = sheetView.findViewById<ImageView>(R.id.ivActionHeaderIcon)
        val tvNumber = sheetView.findViewById<TextView>(R.id.tvActionTargetNumber)
        val btnCopy = sheetView.findViewById<Button>(R.id.btnActionCopyNumber)
        val btnDirect = sheetView.findViewById<Button>(R.id.btnActionDirectOpen)
        val btnBrowser = sheetView.findViewById<Button>(R.id.btnActionOpenBrowser)

        val isWa = type.equals("wa", ignoreCase = true)
        tvNumber.text = if (isWa) "$rawNumber (WhatsApp)" else rawNumber

        if (isWa) {
            ivIcon.setImageResource(R.drawable.ic_whatsapp)
            ivIcon.setColorFilter(Color.parseColor("#25D366"))
            btnDirect.setBackgroundColor(Color.parseColor("#25D366"))
            btnDirect.text = "💬 WhatsApp-এ চ্যাট করুন"
        } else {
            ivIcon.setImageResource(R.drawable.ic_phone)
            ivIcon.setColorFilter(Color.parseColor("#006A4E"))
            btnDirect.setBackgroundColor(Color.parseColor("#006A4E"))
            btnDirect.text = "📞 সরাসরি কল করুন"
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Contact Number", rawNumber)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "নম্বরটি কপি করা হয়েছে: $rawNumber", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnDirect.setOnClickListener {
            dialog.dismiss()
            if (isWa) {
                val cleanNum = if (rawNumber.startsWith("0")) "88$rawNumber" else if (rawNumber.startsWith("+")) rawNumber.removePrefix("+") else rawNumber
                val waUri = Uri.parse("https://wa.me/$cleanNum")
                val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    setPackage("com.whatsapp")
                }
                try {
                    startActivity(waIntent)
                } catch (e: Exception) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, waUri)
                        startActivity(browserIntent)
                    } catch (e2: Exception) {
                        Toast.makeText(this, "WhatsApp ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                try {
                    val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$rawNumber"))
                    startActivity(callIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "ডায়ালার ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnBrowser.setOnClickListener {
            dialog.dismiss()
            if (isWa) {
                val cleanNum = if (rawNumber.startsWith("0")) "88$rawNumber" else rawNumber
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum")))
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$rawNumber")))
            }
        }

        dialog.show()
    }

    private fun transformCustomTags(html: String): String {
        var result = html

        val phoneRegex = Regex("""<phone>(.*?)</phone>""", RegexOption.IGNORE_CASE)
        result = phoneRegex.replace(result) { match ->
            val num = match.groupValues[1].trim()
            """<a href="sahayak-action://phone/$num" class="action-pill phone-pill"><span class="pill-icon">📞</span> <span class="pill-text">$num</span></a>"""
        }

        val waRegex = Regex("""<wa>(.*?)</wa>""", RegexOption.IGNORE_CASE)
        result = waRegex.replace(result) { match ->
            val num = match.groupValues[1].trim()
            """<a href="sahayak-action://wa/$num" class="action-pill wa-pill"><span class="pill-icon">💬</span> <span class="pill-text">$num (WhatsApp)</span></a>"""
        }

        return result
    }

    private fun sanitizeYouTubeEmbeds(html: String): String {
        if (!html.contains("youtube", ignoreCase = true) && !html.contains("youtu.be", ignoreCase = true)) {
            return html
        }

        var result = html
        if (!result.contains("referrerpolicy", ignoreCase = true)) {
            result = result.replace("<iframe", "<iframe referrerpolicy=\"strict-origin-when-cross-origin\"", ignoreCase = true)
        }

        val originParam = "origin=https://app-sahayak.vercel.app"
        val regex = Regex("""src=["'](https?://(?:www\.)?youtube(?:-nocookie)?\.com/embed/[a-zA-Z0-9_-]+)(\?[^"']*)?["']""")
        result = regex.replace(result) { matchResult ->
            val baseUrl = matchResult.groupValues[1]
            val existingQuery = matchResult.groupValues[2]
            val cleanQuery = if (existingQuery.isEmpty()) {
                "?enablejsapi=1&$originParam&playsinline=1"
            } else if (!existingQuery.contains("origin=")) {
                "$existingQuery&$originParam&playsinline=1"
            } else {
                existingQuery
            }
            """src="$baseUrl$cleanQuery""""
        }

        return result
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
        
        val contentWithActions = transformCustomTags(rawHtmlDesc)
        val cleanContent = sanitizeYouTubeEmbeds(contentWithActions)

        val styledHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="referrer" content="strict-origin-when-cross-origin">
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
                    .action-pill {
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                        padding: 5px 12px;
                        border-radius: 18px;
                        text-decoration: none !important;
                        font-weight: bold;
                        font-size: 14px;
                        margin: 4px 2px;
                        box-shadow: 0 1px 4px rgba(0,0,0,0.08);
                    }
                    .phone-pill {
                        background-color: #E8F5E9;
                        color: #006A4E !important;
                        border: 1px solid #006A4E;
                    }
                    .wa-pill {
                        background-color: #E8F8F0;
                        color: #25D366 !important;
                        border: 1px solid #25D366;
                    }
                    iframe {
                        width: 100% !important;
                        max-width: 100% !important;
                        border: 0;
                        border-radius: 10px;
                        margin: 12px 0;
                        aspect-ratio: 16 / 9;
                    }
                    .video-container {
                        position: relative;
                        width: 100%;
                        padding-bottom: 56.25%;
                        height: 0;
                        overflow: hidden;
                        border-radius: 10px;
                        margin: 12px 0;
                    }
                    .video-container iframe {
                        position: absolute;
                        top: 0;
                        left: 0;
                        width: 100% !important;
                        height: 100% !important;
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
                $cleanContent
            </body>
            </html>
        """.trimIndent()

        wvContent.loadDataWithBaseURL("https://app-sahayak.vercel.app", styledHtml, "text/html", "UTF-8", null)
    }
}
