package com.noscroll.redirect

import org.junit.Test

class BlockerDestinationTest {
    @Test fun freshInstallKeepsReader() {
        check(BlockerDestination.fromStoredPackage(null, "com.noscroll") == BlockerDestination.Reader)
    }
    @Test fun blankPreferenceKeepsReader() {
        check(BlockerDestination.fromStoredPackage("  ", "com.noscroll") == BlockerDestination.Reader)
    }
    @Test fun ownPackageCannotRedirectToItself() {
        check(BlockerDestination.fromStoredPackage("com.noscroll", "com.noscroll") == BlockerDestination.Reader)
    }
    @Test fun storedAppSurvivesReload() {
        val saved = BlockerDestination.App("com.example.learn").storedPackage
        check(BlockerDestination.fromStoredPackage(saved, "com.noscroll") == BlockerDestination.App("com.example.learn"))
    }
    @Test fun switchingBackToReaderClearsPackage() {
        check(BlockerDestination.Reader.storedPackage == null)
    }
    @Test fun appChoiceIsNotRestrictedToReadingApps() {
        check(BlockerDestination.fromStoredPackage("com.example.music", "com.noscroll") == BlockerDestination.App("com.example.music"))
    }
    @Test fun candidatesExcludeOnlyOwnAndBlankPackages() {
        val result = destinationApps(listOf(
            DestinationApp("com.noscroll", "NoScroll"), DestinationApp("", "Invalid"),
            DestinationApp("com.example.music", "Music"), DestinationApp("com.instagram.android", "Instagram")
        ), "com.noscroll")
        check(result.map { it.packageName }.toSet() == setOf("com.example.music", "com.instagram.android"))
    }
    @Test fun multipleLauncherActivitiesProduceOneApp() {
        val result = destinationApps(listOf(DestinationApp("com.example.app", "App"), DestinationApp("com.example.app", "App shortcut")), "com.noscroll")
        check(result.size == 1)
    }
    @Test fun appsSortCaseInsensitively() {
        val result = destinationApps(listOf(DestinationApp("c", "Zulu"), DestinationApp("b", "beta"), DestinationApp("a", "Alpha")), "self")
        check(result.map { it.label } == listOf("Alpha", "beta", "Zulu"))
    }
    @Test fun matchingLabelsSortByPackage() {
        check(destinationApps(listOf(DestinationApp("b", "Notes"), DestinationApp("a", "Notes")), "self").map { it.packageName } == listOf("a", "b"))
    }
    @Test fun searchMatchesLabelWithoutCase() {
        check(destinationApps(listOf(DestinationApp("learn", "Language Lessons"), DestinationApp("music", "Music")), "self", "  LANGUAGE ").single().packageName == "learn")
    }
    @Test fun searchAlsoMatchesPackage() {
        check(destinationApps(listOf(DestinationApp("com.example.notes", "Notebook")), "self", "example.notes").single().label == "Notebook")
    }
    @Test fun searchWithNoMatchesIsEmpty() {
        check(destinationApps(listOf(DestinationApp("music", "Music")), "self", "missing").isEmpty())
    }
    @Test fun emptyInstalledListIsSupported() {
        check(destinationApps(emptyList(), "self").isEmpty())
    }
    @Test fun readerLaunchDoesNotOpenAnotherApp() {
        val host = RecordingHost()
        check(launchBlockerDestination(BlockerDestination.Reader, host) == DestinationLaunchResult.READER)
        check(host.calls == listOf("reader"))
    }
    @Test fun chosenAppLaunchDoesNotOpenReader() {
        val host = RecordingHost()
        check(launchBlockerDestination(BlockerDestination.App("music"), host) == DestinationLaunchResult.APP)
        check(host.calls == listOf("app:music"))
    }
    @Test fun unavailableAppOpensChooserNotReader() {
        val host = RecordingHost(appAvailable = false)
        check(launchBlockerDestination(BlockerDestination.App("removed"), host) == DestinationLaunchResult.CHOOSER)
        check(host.calls == listOf("app:removed", "chooser"))
    }
    @Test fun deniedAppLaunchCanRecoverWithChooser() {
        val host = RecordingHost(appAvailable = false)
        check(launchBlockerDestination(BlockerDestination.App("restricted"), host) == DestinationLaunchResult.CHOOSER)
    }
    @Test fun failedReaderLaunchCanRecoverWithChooser() {
        val host = RecordingHost(readerAvailable = false)
        check(launchBlockerDestination(BlockerDestination.Reader, host) == DestinationLaunchResult.CHOOSER)
        check(host.calls == listOf("reader", "chooser"))
    }
    @Test fun failedFallbackReportsFailureSoOverlayCanRemain() {
        val host = RecordingHost(appAvailable = false, chooserAvailable = false)
        check(launchBlockerDestination(BlockerDestination.App("removed"), host) == DestinationLaunchResult.FAILED)
        check(host.calls == listOf("app:removed", "chooser"))
    }
    @Test fun eachTapUsesTheProvidedCurrentChoice() {
        val host = RecordingHost()
        launchBlockerDestination(BlockerDestination.App("notes"), host)
        launchBlockerDestination(BlockerDestination.App("music"), host)
        launchBlockerDestination(BlockerDestination.Reader, host)
        check(host.calls == listOf("app:notes", "app:music", "reader"))
    }

    private class RecordingHost(
        val readerAvailable: Boolean = true,
        val appAvailable: Boolean = true,
        val chooserAvailable: Boolean = true
    ) : DestinationLaunchHost {
        val calls = mutableListOf<String>()
        override fun openReader(): Boolean { calls += "reader"; return readerAvailable }
        override fun openApp(packageName: String): Boolean { calls += "app:$packageName"; return appAvailable }
        override fun openChooser(): Boolean { calls += "chooser"; return chooserAvailable }
    }
}
