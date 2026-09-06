package com.noscroll.redirect

/** The reader is the migration-free default. External apps are stored by package, not label. */
sealed class BlockerDestination {
    object Reader : BlockerDestination()
    data class App(val packageName: String) : BlockerDestination()

    val storedPackage: String? get() = (this as? App)?.packageName

    companion object {
        fun fromStoredPackage(packageName: String?, ownPackage: String): BlockerDestination =
            if (packageName.isNullOrBlank() || packageName == ownPackage) Reader else App(packageName)
    }
}

data class DestinationApp(val packageName: String, val label: String)

/** Only launcher activities are supplied by the Android adapter; do not impose app categories. */
fun destinationApps(
    apps: List<DestinationApp>,
    ownPackage: String,
    query: String = ""
): List<DestinationApp> {
    val search = query.trim()
    return apps.asSequence()
        .filter { it.packageName.isNotBlank() && it.packageName != ownPackage }
        .distinctBy { it.packageName }
        .filter { it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { app: DestinationApp -> app.label }.thenBy { it.packageName })
        .toList()
}

interface DestinationLaunchHost {
    fun openReader(): Boolean
    fun openApp(packageName: String): Boolean
    fun openChooser(): Boolean
}

enum class DestinationLaunchResult { READER, APP, CHOOSER, FAILED }

/** A failed external launch never silently substitutes reading or changes the saved choice. */
fun launchBlockerDestination(
    destination: BlockerDestination,
    host: DestinationLaunchHost
): DestinationLaunchResult {
    val opened = when (destination) {
        BlockerDestination.Reader -> if (host.openReader()) DestinationLaunchResult.READER else null
        is BlockerDestination.App -> if (host.openApp(destination.packageName)) DestinationLaunchResult.APP else null
    }
    return opened ?: if (host.openChooser()) DestinationLaunchResult.CHOOSER else DestinationLaunchResult.FAILED
}
