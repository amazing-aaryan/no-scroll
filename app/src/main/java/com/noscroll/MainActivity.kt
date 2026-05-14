package com.noscroll

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent) {
        when {
            intent.getStringExtra("action") == "OPEN_PDF" -> {
                startActivity(
                    Intent(this, PdfViewerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            }
            !PermissionUtils.hasOverlayPermission(this) ||
            !PermissionUtils.hasAccessibilityEnabled(this) -> {
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
            }
            else -> {
                setContentView(R.layout.activity_main)
                findViewById<com.google.android.material.button.MaterialButton>(R.id.open_library_btn).setOnClickListener {
                    startActivity(Intent(this, PdfLibraryActivity::class.java))
                }
            }
        }
    }
}
