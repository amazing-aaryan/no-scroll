# Configurable blocker destination

Feature branch: `feature/choose-blocker-destination`.
Base: `6038d17fd5909642c67d824347444f654afb9762` (`master`).

## Product behavior

The visible feed blocker and smaller Reels entry point share one saved destination. Existing installations and new installs default to the NoScroll reader. Users may choose any other launchable application visible in their current Android profile, not just reading or productivity apps. NoScroll itself is represented by the reader option to prevent a self-launch loop. Selecting Instagram is allowed, but does not bypass or disable its blocking policy.

The library has a persistent **Blocker opens / Change app** control, including when empty. The full blocker has **Change app**; long-pressing either clickable overlay opens the picker. Selection saves immediately and closes the picker. Back/cancel does not modify the selection. Subsequent blocker taps open the saved destination. Direct launcher and explicit PDF-opening routes remain unchanged.

## Implementation

- `redirect/BlockerDestination.kt`: destination model, deterministic per-package app filtering/search, and a pure launch router.
- `redirect/AndroidBlockerDestination.kt`: private SharedPreferences storage, launcher-intent discovery, label/icon resolution, explicit reader/settings intents and package-manager external launches.
- `redirect/BlockerDestinationActivity.kt` and `BlockerDestinationScreen.kt`: searchable chooser, selected radio row, async discovery with lifecycle cancellation, missing-app handling, and the library settings control.
- `OverlayService.kt`: both clickable surfaces call the same router. Labels, app icons, content descriptions and tutorial text match the destination. Labels/icons are cached on each overlay view to avoid package-manager work during every geometry update.
- Touch-only blocking regions continue to consume touches; they do not become app launchers. No Instagram classification, geometry, DM, story, search, or reader data logic is changed.

The preference stores the package name, not an activity/component or localized label. Launch intents are resolved again on every tap so app upgrades can change launcher activities. Failed or inaccessible external launches open a non-exported picker with a warning, keep the previous selection until replaced, and never silently force reading. If both the target and picker fail to launch, the overlay remains and a message directs the user to NoScroll settings.

## Visibility and privacy

`AndroidManifest.xml` declares only an `ACTION_MAIN` / `CATEGORY_LAUNCHER` query. It adds no runtime permission and no `QUERY_ALL_PACKAGES`. The app list is read on demand, off the main thread, with one row per package. Hidden, disabled, non-exported and non-launchable entries are filtered. Other/locked profiles are outside this picker. The package preference and discovered list remain local; no analytics/network calls are added.

Official Android references:
- https://developer.android.com/training/package-visibility/use-cases
- https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentForPackage(java.lang.String)

## Automated verification

`app/src/test/java/com/noscroll/redirect/BlockerDestinationTest.kt` adds 21 JUnit scenarios for defaults, preference round-trips, reader reset, arbitrary app categories, self-exclusion, deduplication, sorting, searches, empty results, selected-app dispatch, chooser fallback, all-launches failure, and changes between taps.

The assertion bodies were also compiled and executed locally with Kotlin 1.9/JDK 21 using a standalone runner. This is pure-JVM behavior coverage, not Android package-manager, UI, or device testing. The Android CI workflow remains the authority for `testDebugUnitTest`, `assembleDebug`, and `lintDebug` on the complete repository. No new third-party dependencies are added.

## Required device regression before merging or distributing

Run alongside all checks in `RELEASE.md`; a green JVM suite does not prove overlay/app switching works on hardware.

- Fresh install and upgrade without a preference: both clickable blockers still open the reader/library; existing PDFs, highlights and reading position survive.
- Empty library: Change app is visible; select a non-reading app without importing a PDF. Confirm both the small and full blocker open it, display its label/icon, and do not open the reader.
- Change app A to app B, then to the reader. Repeat after force-stop/relaunch and device reboot; confirm the selection survives and only the reader resumes reading progress.
- Back/cancel and rotate the picker; confirm no unintended selection change, responsive searching, long-label layout, keyboard insets and accessible radio/button controls.
- Uninstall/disable the selected app and tap each blocker; confirm the picker explains the failure, there is no crash, and no automatic reading redirect. Remove an app while the picker is open and attempt to select the stale row.
- Return to Instagram from the chosen app; confirm blocking resumes with all existing story, DM and search exceptions unchanged. Selecting Instagram must not create an automatic redirect loop or bypass blocking.
- Rapid taps, repeated Refresh, permission revocation/restoration, and service restart: no duplicate windows, crashes, or non-touchable blocker regressions.
- Check API 28, Android 11+ package visibility, API 36 background-activity/overlay behavior and at least one physical OEM device. Verify work-profile restrictions do not crash discovery.

Signing, application ID, SDK targets, versioning, distribution and Play readiness are unchanged. No physical-device execution is claimed by this document.
