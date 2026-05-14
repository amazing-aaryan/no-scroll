package com.noscroll

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var overlayCheck: ImageView
    private lateinit var overlayBtn: Button
    private lateinit var accessibilityCheck: ImageView
    private lateinit var accessibilityBtn: Button
    private lateinit var doneBtn: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        overlayCheck = findViewById(R.id.overlay_check)
        overlayBtn = findViewById(R.id.overlay_btn)
        accessibilityCheck = findViewById(R.id.accessibility_check)
        accessibilityBtn = findViewById(R.id.accessibility_btn)
        doneBtn = findViewById(R.id.done_btn)
        statusText = findViewById(R.id.status_text)

        overlayBtn.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        accessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        doneBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        lifecycleScope.launch {
            while (isActive) {
                updateUI()
                delay(500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val hasOverlay = PermissionUtils.hasOverlayPermission(this)
        val hasA11y = PermissionUtils.hasAccessibilityEnabled(this)

        overlayCheck.visibility = if (hasOverlay) View.VISIBLE else View.GONE
        overlayBtn.isEnabled = !hasOverlay

        accessibilityCheck.visibility = if (hasA11y) View.VISIBLE else View.GONE
        accessibilityBtn.isEnabled = !hasA11y

        doneBtn.visibility = if (hasOverlay && hasA11y) View.VISIBLE else View.GONE

        statusText.text = when {
            hasOverlay && hasA11y ->
                "All set! Open Instagram and tap the book icon to read."
            !hasOverlay && !hasA11y ->
                "Complete both steps below to activate NoScroll."
            !hasOverlay ->
                "Step 1 remaining: allow display over other apps."
            else ->
                "Step 2 remaining: enable NoScroll accessibility service."
        }
    }
}
