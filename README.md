# NoScroll

Android app that replaces Instagram's Reels tab button with a book icon. Tap it to read a locally stored PDF. Remembers your PDF and page across sessions.

## How it works

1. Accessibility Service watches for Instagram in the foreground.
2. Finds the Reels button in Instagram's UI tree and extracts its screen coordinates.
3. System Overlay draws a book icon at those exact coordinates.
4. Tapping the book icon opens a full-screen PDF reader.
5. PDF selection and current page persist across app restarts.

## Prerequisites

- **Android Studio** (includes JDK 17) — https://developer.android.com/studio
- Android phone (API 26 / Android 8.0+) with USB debugging enabled
- **Instagram** installed on the phone

## Build

1. Open Android Studio → **Open** → select this `no-scroll/` folder
2. Wait for Gradle sync (~2 min first time, downloads dependencies)
3. Plug in phone via USB with USB debugging ON
4. Click **Run ▶** or run:

```bash
# macOS/Linux
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Windows
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> Android Studio generates `gradlew`/`gradlew.bat` automatically on first Gradle sync.

## First-run setup

1. Open **NoScroll** on your phone.
2. **Step 1** — Tap "Grant Permission" → find NoScroll → toggle "Allow display over other apps" ON → go back.
3. **Step 2** — Tap "Enable Service" → find **NoScroll** in the Accessibility list → toggle ON → confirm.
4. Tap **Done**.

## Using the app

1. Open Instagram.
2. A book icon appears over the Reels tab in the bottom nav bar.
3. Tap it → PDF reader opens.
   - First time: file picker opens automatically — pick your PDF once.
   - After that: last PDF opens at the page you left off.
4. Swipe up/down to flip pages (snap-per-page, Instagram Reels style).
5. Tap the floating book button (bottom-right) anytime to swap PDFs.
6. Press Back to return to Instagram.

## Caveats

- Instagram updates may change the Reels button's accessibility label. Fallback: position-based detection (center of bottom nav).
- Works with both `com.instagram.android` and `com.instagram.lite`.
- A persistent notification appears while Instagram is open — required to keep the overlay service alive on Android 8+.
- PDF rendering uses Android's built-in `PdfRenderer`. Pages render on-demand.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Book icon doesn't appear | Verify both permissions in Setup screen |
| Book icon in wrong spot | Re-enable accessibility service after an Instagram update |
| PDF fails to open | URI may have expired — tap FAB to re-pick file |
| Notification won't dismiss | Intentional — keeps service alive; minimize via notification settings |
