package com.noscroll.redirect

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.noscroll.PdfViewerActivity
import com.noscroll.R

/** App-local preferences; no book, annotation, or reading-progress data is changed. */
class BlockerDestinationPreferences(context: Context) {
    private val ownPackage = context.packageName
    private val preferences = context.applicationContext.getSharedPreferences("blocker_destination", Context.MODE_PRIVATE)

    fun read(): BlockerDestination = BlockerDestination.fromStoredPackage(
        preferences.getString("package_name", null), ownPackage
    )

    fun save(destination: BlockerDestination) {
        preferences.edit().apply {
            val packageName = destination.storedPackage
            if (packageName == null || packageName == ownPackage) remove("package_name")
            else putString("package_name", packageName)
        }.apply()
    }
}

/** Android operations stay at this boundary; routing and app filtering are JVM-testable. */
class AndroidBlockerDestination(private val context: Context) {
    private val packageManager = context.packageManager
    val preferences = BlockerDestinationPreferences(context)

    fun displayName(destination: BlockerDestination = preferences.read()): String = when (destination) {
        BlockerDestination.Reader -> context.getString(R.string.blocker_reader_name)
        is BlockerDestination.App -> try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(destination.packageName, 0))
                .toString().ifBlank { destination.packageName }
        } catch (_: PackageManager.NameNotFoundException) {
            destination.packageName
        } catch (_: SecurityException) {
            destination.packageName
        }
    }

    fun appIcon(destination: BlockerDestination): Drawable? = when (destination) {
        BlockerDestination.Reader -> null
        is BlockerDestination.App -> try {
            packageManager.getApplicationIcon(destination.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun installedApps(): List<DestinationApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = packageManager.queryIntentActivities(intent, 0).mapNotNull { result ->
            val activity = result.activityInfo ?: return@mapNotNull null
            if (!activity.enabled || !activity.exported || !activity.applicationInfo.enabled) return@mapNotNull null
            val label = runCatching { activity.applicationInfo.loadLabel(packageManager).toString() }
                .getOrDefault(activity.packageName).ifBlank { activity.packageName }
            DestinationApp(activity.packageName, label)
        }
        return destinationApps(candidates, context.packageName).filter { isLaunchable(it.packageName) }
    }

    fun isLaunchable(packageName: String): Boolean = try {
        packageName != context.packageName && packageManager.getLaunchIntentForPackage(packageName) != null
    } catch (_: SecurityException) {
        false
    }

    fun openSelected(): DestinationLaunchResult = launchBlockerDestination(
        preferences.read(),
        object : DestinationLaunchHost {
            override fun openReader(): Boolean = startSafely(
                Intent(context, PdfViewerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            override fun openApp(packageName: String): Boolean = try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent != null && startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: SecurityException) {
                false
            }
            override fun openChooser(): Boolean = showChooser(unavailable = true)
        }
    )

    fun showChooser(unavailable: Boolean = false): Boolean = startSafely(
        Intent(context, BlockerDestinationActivity::class.java)
            .putExtra(BlockerDestinationActivity.EXTRA_UNAVAILABLE, unavailable)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    )

    private fun startSafely(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
