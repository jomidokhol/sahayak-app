package com.nur.sahayak

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
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore

class TermsActivity : AppCompatActivity() {

    private lateinit var wvContent: WebView
    private lateinit var cardDynamicTerms: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        val btnBack = findViewById<ImageButton>(R.id.btnBackTerms)
        wvContent = findViewById(R.id.wvTermsContent)
        cardDynamicTerms = findViewById(R.id.cardDynamicTerms)

        btnBack.setOnClickListener { finish() }

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

                if (request != null && !request.isForMainFrame) {
                    return false
                }

                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (url == "https://app-sahayak.vercel.app" || url == "https://app-sahayak.vercel.app/") {
                        return false
                    }
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        startActivity(intent)
                    } catch (e: Exception) {}
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

        loadDynamicTermsFromFirestore()
    }

    private fun loadDynamicTermsFromFirestore() {
        FirebaseFirestore.getInstance().collection("settings").document("terms")
            .addSnapshotListener { snapshot, _ ->
                val dynamicHtml = snapshot?.getString("content")?.trim()
                    ?: snapshot?.getString("desc")?.trim() ?: ""

                if (dynamicHtml.isNotEmpty()) {
                    cardDynamicTerms.visibility = View.VISIBLE
                    renderTermsHtml(dynamicHtml)
                } else {
                    cardDynamicTerms.visibility = View.GONE
                }
            }
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

    private fun renderTermsHtml(htmlBody: String) {
        val cleanContent = sanitizeYouTubeEmbeds(htmlBody)

        val styledHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="referrer" content="strict-origin-when-cross-origin">
                <style>
                    body {
                        font-family: 'Segoe UI', Roboto, sans-serif;
                        color: #212121;
                        font-size: 14px;
                        line-height: 1.7;
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
                        color: #006A4E;
                        font-weight: bold;
                        text-decoration: underline;
                    }
                    p {
                        margin-bottom: 10px;
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
