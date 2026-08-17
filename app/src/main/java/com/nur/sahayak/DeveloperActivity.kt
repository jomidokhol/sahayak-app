package com.nur.sahayak

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class DeveloperActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer)

        // Make Status Bar Transparent to match header gradient
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        val btnBack = findViewById<ImageButton>(R.id.btnBackDev)
        val ivCover = findViewById<ImageView>(R.id.ivDevCover)
        val ivAvatar = findViewById<ImageView>(R.id.ivDevAvatar)
        val btnMail = findViewById<Button>(R.id.btnDevMail)
        val btnFb = findViewById<Button>(R.id.btnDevFb)
        val btnWa = findViewById<Button>(R.id.btnDevWa)

        btnBack.setOnClickListener { finish() }

        // Direct local drawable resource set for dev-banner.png & developer.png
        try {
            ivCover.setImageResource(R.drawable.dev_banner)
        } catch (e: Exception) {
            ivCover.setBackgroundColor(Color.parseColor("#006A4E"))
        }

        try {
            ivAvatar.setImageResource(R.drawable.developer)
        } catch (e: Exception) {
            ivAvatar.setImageResource(R.drawable.ic_profile)
        }

        // Mail Action
        btnMail.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:nurhossan7020@gmail.com")
                }
                startActivity(intent)
            } catch (e: Exception) {
                openUrl("mailto:nurhossan7020@gmail.com")
            }
        }

        // Facebook Action
        btnFb.setOnClickListener {
            openUrl("https://www.facebook.com/NURtheBackBencher")
        }

        // WhatsApp Action
        btnWa.setOnClickListener {
            openWhatsApp("8801851956615")
        }
    }

    private fun openWhatsApp(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$phone")
            }
            startActivity(intent)
        } catch (e: Exception) {
            openUrl("https://wa.me/$phone")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {}
    }
}
