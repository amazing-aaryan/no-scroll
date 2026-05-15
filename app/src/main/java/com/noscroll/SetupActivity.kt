package com.noscroll

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.noscroll.ui.NoScrollTheme
import com.noscroll.ui.PaperColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {
    private var hasOverlay by mutableStateOf(false)
    private var hasAccessibility by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoScrollTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(PaperColors.Paper)
                        .padding(28.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Setup NoScroll", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(statusText(), style = MaterialTheme.typography.bodyLarge, color = PaperColors.Graphite)
                    Spacer(Modifier.height(28.dp))
                    PermissionRow(
                        title = "Display over other apps",
                        detail = "Lets NoScroll draw the book icon over Instagram.",
                        done = hasOverlay,
                        action = "Grant",
                        onClick = {
                            startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                            )
                        }
                    )
                    PermissionRow(
                        title = "Accessibility service",
                        detail = "Lets NoScroll detect Instagram and find the Reels button.",
                        done = hasAccessibility,
                        action = "Enable",
                        onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    )
                    Spacer(Modifier.height(24.dp))
                    if (hasOverlay && hasAccessibility) {
                        Button(
                            onClick = {
                                startActivity(Intent(this@SetupActivity, MainActivity::class.java))
                                finish()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Ink),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
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
        hasOverlay = PermissionUtils.hasOverlayPermission(this)
        hasAccessibility = PermissionUtils.hasAccessibilityEnabled(this)
    }

    private fun statusText(): String = when {
        hasOverlay && hasAccessibility -> "All set. Open Instagram and tap the book icon to read."
        !hasOverlay && !hasAccessibility -> "Complete both steps below to activate NoScroll."
        !hasOverlay -> "Step 1 remaining: allow display over other apps."
        else -> "Step 2 remaining: enable NoScroll accessibility service."
    }
}

@androidx.compose.runtime.Composable
private fun PermissionRow(
    title: String,
    detail: String,
    done: Boolean,
    action: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = PaperColors.Graphite)
        }
        OutlinedButton(enabled = !done, onClick = onClick) {
            Text(if (done) "Done" else action)
        }
    }
}
