package com.noscroll

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanupQuoteCache()
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
                startActivity(Intent(this, PdfLibraryActivity::class.java))
                finish()
            }
        }
    }

    private fun cleanupQuoteCache() {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        File(cacheDir, "quote_cards").listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }
}
