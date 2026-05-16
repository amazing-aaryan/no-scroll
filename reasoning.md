# reasoning.md — no-scroll

## [2026-05-15 08:34] Session: diagnose and fix all app crashes + navigation loop

**Decision:** Fixed 6 bugs across 4 files on branch `codex/reader-selection-highlights-zen`.

**Why (each fix):**

### Fix 1 — `UninitializedPropertyAccessException: _toolboxView`
`PdfViewerActivity.setupControls()` called `pdfFragment.setZenToolboxVisible()` in `onCreate()`, before `PdfViewerFragment`'s internal `_toolboxView` was initialized. The fragment was added with `commitNow()` (synchronous at activity level), but `_toolboxView` is only initialized in `onPdfViewCreated()`, which fires later when the PDF rendering context is ready.

Fix: Added `pendingToolboxVisible: Boolean?` to `NoScrollPdfViewerFragment`. `setZenToolboxVisible()` catches `UninitializedPropertyAccessException` and stores the value; `onPdfViewCreated()` applies it.

### Fix 2 — `InflateException: Error inflating ToolBoxView`
`ToolBoxView` inside `PdfViewerFragment` contains a `FloatingActionButton` that reads `backgroundTint` from the activity theme at inflation time. App theme (`Theme.MaterialComponents.DayNight.DarkActionBar`) is Material2. `androidx.pdf:1.0.0-alpha18` FAB uses Material3 attribute patterns — unresolved in M2 → `TypedValue.TYPE_ATTRIBUTE` (0x2) at index 1 → crash.

Fix: Added `Theme.NoScroll.PdfViewer` extending `Theme.Material3.DayNight.NoActionBar` to `themes.xml`. Applied only to `PdfViewerActivity` via `android:theme` in `AndroidManifest.xml`. All other activities keep M2 theme.

### Fix 3 — `IllegalStateException: Can't scrollToPage without PdfDocument`
`PdfViewerActivity.onPdfLoaded()` calls `pdfFragment.scrollToPage()`. This fires from `onLoadDocumentSuccess()` — which fires when the `PdfDocument` object is created, but BEFORE `PdfView` internally has its document set. So `PdfView.scrollToPage()` throws.

Fix: Added `pendingScrollPage: Int?` to `NoScrollPdfViewerFragment`. `scrollToPage()` try-catches `IllegalStateException` and stores the page. Applied in both `onLoadDocumentSuccess` and `onPdfViewCreated` as two retry points.

### Fix 4 — `BadTokenException: permission denied for window type 2038`
`OverlayService.updateOverlay()` called `windowManager.addView(view, TYPE_APPLICATION_OVERLAY)` without checking `SYSTEM_ALERT_WINDOW` permission. If accessibility was enabled but overlay was NOT granted, every accessibility event triggered OverlayService → `addView()` → crash → process kill → accessibility restart → repeat.

Fix: `OverlayService.onStartCommand()` checks `Settings.canDrawOverlays()` before calling `updateOverlay()`. Returns `START_NOT_STICKY` when permission missing so Android doesn't auto-restart.

### Fix 5 — OverlayService restart-loop / Samsung deep-sleep
`START_STICKY` caused the stopped OverlayService to be auto-restarted by Android immediately, which stopped again. Samsung's battery optimizer detected this as a crash-loop and put the app into deep sleep, **disabling the accessibility service from Settings**.

Fix: `START_NOT_STICKY` in the no-permission path. Each invocation now driven solely by accessibility events, not Android's restart mechanism.

### Fix 6 — Accessibility service spamming OverlayService without permission
`NoScrollAccessibilityService.onAccessibilityEvent()` fired on every window/content change regardless of overlay permission. Even with Fixes 4+5, the accessibility service still called `startService(OverlayService)` on every Instagram scroll in partial-setup state.

Fix: Added `if (!Settings.canDrawOverlays(this)) return` at top of `onAccessibilityEvent()`.

---

**Navigation loop root cause:** The 4 crash bugs caused a tight crash-loop → Samsung deep-sleep disabled the accessibility service → every launch saw `hasAccessibilityEnabled() == false` → SetupActivity loop. Resolution: fix all crashes, user re-enables accessibility service once manually.

**Impact:** Future work touching `PdfViewerFragment` lifecycle must account for `onPdfViewCreated` vs `onLoadDocumentSuccess` ordering. Do not call `isToolboxVisible` or `PdfView.scrollToPage()` before `onPdfViewCreated` fires. Always guard OverlayService dispatch with `Settings.canDrawOverlays()`.
