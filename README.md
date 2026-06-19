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

## Prerequisites

- Android Studio, including JDK 17: https://developer.android.com/studio
- Android phone, API 26 / Android 8.0+
- USB debugging enabled for local install
- Instagram installed on the phone if testing blocker behavior

## Build

Open this folder in Android Studio and run the `app` configuration, or build from PowerShell:

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
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
