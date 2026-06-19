package com.noscroll

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.noscroll.tutorial.SetupTutorialSteps
import com.noscroll.tutorial.TutorialAnchor
import com.noscroll.tutorial.TutorialController
import com.noscroll.tutorial.TutorialOverlay
import com.noscroll.tutorial.TutorialPrefs
import com.noscroll.tutorial.TutorialStepId
import com.noscroll.ui.NoScrollTheme
import com.noscroll.ui.PaperActionButton
import com.noscroll.ui.PaperButtonTone
import com.noscroll.ui.PaperColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {
    private var hasOverlay by mutableStateOf(false)
    private var hasAccessibility by mutableStateOf(false)

    private val tutorialController = TutorialController()
    private lateinit var tutorialPrefs: TutorialPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPaperSystemBars()
        tutorialPrefs = TutorialPrefs(this)

        setContent {
            NoScrollTheme {
                var showOptIn by remember { mutableStateOf(!tutorialPrefs.hasMadeChoice()) }

                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(PaperColors.Paper)
                        .padding(28.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.noscroll_logo_inverted_128),
                            contentDescription = "NoScroll logo",
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Setup NoScroll", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            statusText(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = PaperColors.Graphite
                        )
                        Spacer(Modifier.height(28.dp))
                        TutorialAnchor(TutorialStepId.SETUP_OVERLAY, tutorialController) {
                            PermissionRow(
                                title = "Display over other apps",
                                detail = "Lets NoScroll draw the NoScroll logo over Instagram.",
                                done = hasOverlay,
                                action = "Grant",
                                onClick = {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                }
                            )
                        }
                        TutorialAnchor(TutorialStepId.SETUP_ACCESSIBILITY, tutorialController) {
                            PermissionRow(
                                title = "Accessibility service",
                                detail = "Lets NoScroll detect Instagram and find the Reels button.",
                                done = hasAccessibility,
                                action = "Enable",
                                onClick = { startActivity(accessibilityServiceIntent()) }
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        if (hasOverlay && hasAccessibility) {
                            PaperActionButton(
                                label = "Done",
                                onClick = {
                                    startActivity(
                                        Intent(this@SetupActivity, MainActivity::class.java)
                                    )
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    TutorialOverlay(tutorialController)
                }

                if (showOptIn) {
                    AlertDialog(
                        onDismissRequest = {
                            tutorialPrefs.skipAll()
                            showOptIn = false
                        },
                        title = { Text("Want a guided tour?") },
                        text = {
                            Text(
                                "We'll walk you through each feature step by step. " +
                                "You can restart the tour anytime from the home screen."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    tutorialPrefs.setOptedIn(true)
                                    showOptIn = false
                                    tutorialController.start(SetupTutorialSteps)
                                    tutorialController.onDone = {
                                        tutorialPrefs.markSetupDone()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PaperColors.Ink
                                )
                            ) {
                                Text("Yes, show me", color = PaperColors.Raised)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                tutorialPrefs.skipAll()
                                showOptIn = false
                            }) {
                                Text("Skip", color = PaperColors.Muted)
                            }
                        }
                    )
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

    private fun applyPaperSystemBars() {
        window.statusBarColor = Color.parseColor("#F7F3EA")
        window.navigationBarColor = Color.parseColor("#F7F3EA")
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
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

    private fun accessibilityServiceIntent(): Intent {
        val component =
            ComponentName(packageName, NoScrollAccessibilityService::class.java.name)
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            val args = Bundle().apply {
                putString(":settings:fragment_args_key", component.flattenToString())
            }
            putExtra(":settings:show_fragment_args", args)
            putExtra(":settings:fragment_args_key", component.flattenToString())
        }
        return if (intent.resolveActivity(packageManager) != null) intent
        else Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    private fun statusText(): String = when {
        hasOverlay && hasAccessibility ->
            "All set. Open Instagram and tap the NoScroll logo to read."
        !hasOverlay && !hasAccessibility ->
            "Complete both steps below to activate NoScroll."
        !hasOverlay ->
            "Step 1 remaining: allow display over other apps."
        else ->
            "Step 2 remaining: enable NoScroll accessibility service."
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
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = PaperColors.Graphite
            )
        }
        if (done) {
            Text(
                "Granted",
                style = MaterialTheme.typography.labelLarge,
                color = PaperColors.Sage
            )
        } else {
            PaperActionButton(label = action, onClick = onClick, tone = PaperButtonTone.Quiet)
        }
    }
}
