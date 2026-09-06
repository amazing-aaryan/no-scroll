# NoScroll

NoScroll is an Android Instagram blocker with a configurable destination: tap a blocker to open the built-in PDF reader or another installed app of your choice. Its standalone PDF library, reading progress, highlights, and shareable quote cards remain available, but reading is optional.

## What is implemented

- Instagram blocking for high-scroll surfaces such as Home and Reels.
- A system overlay that can show a destination entry point or cover/block distracting content.
- A searchable installed-app picker with a saved blocker destination; the NoScroll reader remains the default.
- A local PDF library backed by app-private storage.
- Import from Android file picker or share sheet.
- Full-screen PDF reader using AndroidX PDF.
- Saved reading progress per book.
- PDF text selection where the PDF exposes selectable text.
- OCR page fallback for scanned/non-selectable pages.
- Highlights, notes, highlight list, and jump-back navigation.
- Quote card creation from selected text or saved highlights.
- Eight preloaded quote-card styles, including classic, modern, and procedural scenic backgrounds.
- Sharing quote cards to Instagram Stories, Instagram Feed, Instagram Direct, Messages, or the Android share sheet.

## How it works

1. The Accessibility Service watches supported Instagram screens while Instagram is foregrounded.
2. When a blocked surface is detected, NoScroll computes the content region and asks the overlay service to block it.
3. Tapping the visible blocker opens the saved destination (the reader by default).
4. The smaller entry point uses the same saved destination and shows its app icon when available.
5. The reader opens your library or the last active PDF and restores reading progress.
6. Selected passages can be highlighted, annotated, exported, or turned into quote-card images.

## Platform and package

Current Android configuration is defined in `app/build.gradle`:

- Package/application ID: `com.noscroll`
- Minimum SDK: API 28 / Android 9
- Compile SDK: API 36
- Target SDK: API 36 / Android 16
- Version: `1.0` (`versionCode` 1)

## Release / beta status

Local debug installation and sideloaded testing do **not** require Google Play registration. Google Play registration/setup is only required when the team wants Google Play to distribute the beta or production app.

As of September 1, 2026, NoScroll targets API 36, resolving the repository's Google Play target-API discrepancy. Google Play requires new apps and updates submitted from August 31, 2026 onward to target Android 16 / API 36 or higher.

Targeting API 36 does not by itself make a Play beta upload-ready. Before the first Play-distributed beta, the team still needs to validate the Android 16 behavior changes on device, configure release/upload signing outside source control, confirm or create the `com.noscroll` Play Console app, and pass the release checklist.

See [`RELEASE.md`](RELEASE.md) for the complete first-beta setup, signing, versioning, update, debug-to-Play migration, and release-gate checklist.

## Prerequisites

- Android Studio, including JDK 17: https://developer.android.com/studio
- Android phone, API 28 / Android 9+
- USB debugging enabled for local install
- Instagram installed on the phone if testing blocker behavior
- Gradle 8.11.1 when building from Linux/macOS or CI without a Unix wrapper launcher

The repository contains `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties`, so Windows command-line builds can use the Gradle wrapper normally. The Unix `gradlew` launcher is currently missing; CI therefore provisions Gradle 8.11.1 explicitly, matching the wrapper configuration.

## Build

Open this folder in Android Studio and run the `app` configuration, or build from PowerShell on Windows:

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

For a fuller pre-beta verification pass on Windows:

```powershell
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

On Linux/macOS until the `gradlew` launcher is restored, use an installed Gradle 8.11.1 and the equivalent `gradle` commands. CI pins the same version.

## First-run setup

1. Open NoScroll.
2. Grant Display Over Other Apps so NoScroll can draw the blocker/reader entry point.
3. Enable the NoScroll Accessibility Service so it can detect supported Instagram surfaces.
4. Return to NoScroll. The app opens the PDF library when setup is complete.

## Using the reader

1. Open NoScroll directly, or tap the blocker while the NoScroll reader is selected.
2. Add any PDF from the library.
3. Open a PDF and read with saved page progress.
4. Select text to highlight, annotate, or make a quote card.
5. On scanned pages, use OCR page to extract text for quote/highlight creation.
6. Share quote cards to friends through Instagram, Messages, or the Android share sheet.

## Choose what opens from the blocker

Open NoScroll and use **Blocker opens → Change app** at the bottom of the library, even when the library is empty. Choose **NoScroll reader** or search for another installed, launchable app. The choice is saved on this device and takes effect the next time either clickable blocker is tapped. No PDF import is required for an external app.

The full blocker also has **Change app**, and holding the small entry point opens the same picker. Canceling the picker leaves the saved choice unchanged. Opening NoScroll from its launcher still opens the library, so you can always change the setting or use the reader.

If the selected app is removed, disabled, or cannot be launched, NoScroll opens the picker with an explanation instead of silently forcing reading. Choosing Instagram itself does not disable its blocking rules. Apps without launcher entries and apps in other or locked profiles are not offered.

Only launcher-intent package visibility is declared; no `QUERY_ALL_PACKAGES` permission is added. The installed-app list and package choice are not uploaded.

See [blocker destination implementation and regression checks](docs/blocker-destination.md).

## Blocker behavior

- Home and Reels are treated as scroll-risk surfaces and blocked.
- Search typing/results and profile pages are allowed where implemented, so useful navigation is not blocked unnecessarily.
- Story viewer handling suppresses the overlay while stories are active.
- Instagram and Instagram Lite package names are supported.
- A persistent notification may appear while Instagram is open because Android requires foreground service visibility for overlays.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Blocker or book entry point does not appear | Verify both setup permissions are enabled |
| Blocker appears in the wrong place | Re-enable the Accessibility Service after an Instagram update |
| PDF fails to open | Re-import the file so NoScroll has a fresh persisted URI |
| No text selection appears | The PDF may be scanned; use OCR page |
| Quote card share fails | Use generic Android share sheet as fallback |
| `adb install -r` reports a signature mismatch | The installed build was signed with a different key; uninstall/reinstall or use the same signing identity |
| `./gradlew` is missing on Linux/macOS | Use installed Gradle 8.11.1 until the Unix wrapper launcher is restored; Windows can use `gradlew.bat` |
| Play Console rejects target API level | Verify `targetSdk` still meets Google's current Play requirement and bump it before uploading if the requirement has advanced |
