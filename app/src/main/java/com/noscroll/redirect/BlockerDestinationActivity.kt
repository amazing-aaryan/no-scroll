package com.noscroll.redirect

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.noscroll.R
import com.noscroll.ui.NoScrollTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockerDestinationActivity : AppCompatActivity() {
    private lateinit var destination: AndroidBlockerDestination
    private var apps by mutableStateOf<List<DestinationApp>>(emptyList())
    private var selected by mutableStateOf<BlockerDestination>(BlockerDestination.Reader)
    private var loading by mutableStateOf(true)
    private var errorMessage by mutableStateOf<String?>(null)
    private var loadJob: Job? = null
    private var loadGeneration = 0

    companion object {
        const val EXTRA_UNAVAILABLE = "destination_unavailable"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destination = AndroidBlockerDestination(this)
        if (intent.getBooleanExtra(EXTRA_UNAVAILABLE, false)) {
            errorMessage = getString(R.string.blocker_destination_unavailable)
        }
        setContent {
            NoScrollTheme {
                BlockerDestinationScreen(
                    apps = apps,
                    selected = selected,
                    loading = loading,
                    errorMessage = errorMessage,
                    onBack = { finish() },
                    onSelect = { choose(it) },
                    onRefresh = { errorMessage = null; reloadApps() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        selected = destination.preferences.read()
        reloadApps()
    }

    private fun reloadApps() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            loading = true
            try {
                apps = withContext(Dispatchers.IO) { destination.installedApps() }
                val savedApp = selected as? BlockerDestination.App
                if (savedApp != null && apps.none { it.packageName == savedApp.packageName }) {
                    errorMessage = getString(R.string.blocker_destination_unavailable)
                }
            } catch (_: SecurityException) {
                apps = emptyList()
                errorMessage = getString(R.string.blocker_apps_load_failed)
            } finally {
                if (generation == loadGeneration) loading = false
            }
        }
    }

    private fun choose(choice: BlockerDestination) {
        // Revalidate after listing: the app may have been removed/disabled while the picker was open.
        if (choice is BlockerDestination.App && !destination.isLaunchable(choice.packageName)) {
            errorMessage = getString(R.string.blocker_destination_unavailable)
            reloadApps()
            return
        }
        destination.preferences.save(choice)
        selected = choice
        finish()
    }
}
