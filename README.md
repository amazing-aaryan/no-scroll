# NoScroll

NoScroll is an Android reading app with an Instagram blocker, a PDF library/reader, highlights, and shareable quote cards. It is no longer just a Reels-button replacement: it can block distracting Instagram surfaces and turn the habit into reading, while also working as a standalone reader for any PDF you import.

## What is implemented

- Instagram blocking for high-scroll surfaces such as Home and Reels.
- A system overlay that can either show a book entry point or cover/block distracting content.
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
3. Tapping the visible blocker opens the reader.
4. When a smaller book entry point is used, tapping it also opens the reader.
5. The reader opens your library or the last active PDF and restores reading progress.
6. Selected passages can be highlighted, annotated, exported, or turned into quote-card images.

## Platform and package

Current Android configuration is defined in `app/build.gradle`:

- Package/application ID: `com.noscroll`
- Minimum SDK: API 28 / Android 9
- Compile SDK: API 36
- Target SDK: API 35
- Version: `1.0` (`versionCode` 1)

## Release / beta status

Local debug installation and sideloaded testing do **not** require Google Play registration. Google Play registration/setup is only required when the team wants Google Play to distribute the beta or production app.

As of September 1, 2026, the repository is **not yet ready for a new Google Play beta upload** because Google Play requires new apps and updates submitted after August 31, 2026 to target API 36, while NoScroll currently targets API 35. Release signing also still needs to be configured outside source control before a signed Play bundle can be uploaded.

See [`RELEASE.md`](RELEASE.md) for the complete first-beta setup, signing, versioning, update, debug-to-Play migration, and release-gate checklist.

## Prerequisites

- Android Studio, including JDK 17: https://developer.android.com/studio
- Android phone, API 28 / Android 9+
- USB debugging enabled for local install
- Instagram installed on the phone if testing blocker behavior

## Build

Open this folder in Android Studio and run the `app` configuration, or build from PowerShell:

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

For a fuller pre-beta verification pass:

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat check
```

## First-run setup

1. Open NoScroll.
2. Grant Display Over Other Apps so NoScroll can draw the blocker/reader entry point.
3. Enable the NoScroll Accessibility Service so it can detect supported Instagram surfaces.
4. Return to NoScroll. The app opens the PDF library when setup is complete.

## Using the reader

1. Open NoScroll directly, or tap the blocker while using Instagram.
2. Add any PDF from the library.
3. Open a PDF and read with saved page progress.
4. Select text to highlight, annotate, or make a quote card.
5. On scanned pages, use OCR page to extract text for quote/highlight creation.
6. Share quote cards to friends through Instagram, Messages, or the Android share sheet.

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
| Play Console rejects target API level | Raise `targetSdk` to the current Play requirement before uploading |
