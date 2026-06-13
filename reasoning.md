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

## [2026-06-12 11:05] Session: Android emulator E2E test run for Instagram blocking oracle

**Decision:** FAIL for the deploy-readiness oracle. The emulator, NoScroll permissions, build, and Instagram login state were usable, but Home scrolling and Search/Explore scrolling were not blocked. Direct Reels content and DM shared-item testing were blocked by Instagram loading/test-fixture state.

**Environment:**
- Host: Windows, workspace `C:\Users\aarya\Desktop\no-scroll`
- Evidence directory: `artifacts/e2e-instagram-oracle-20260612-104446`
- Emulator: `emulator-5554`, Android `17`
- Instagram: `com.instagram.android`, version `433.0.0.47.68`
- NoScroll: `com.noscroll`, version `1.0`
- Permissions: overlay=`SYSTEM_ALERT_WINDOW: allow`, accessibility=`com.noscroll/com.noscroll.NoScrollAccessibilityService`
- Build gates: `:app:testDebugUnitTest` PASS, `:app:assembleDebug` PASS using `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`

**Why:**

### T1 - Home feed scroll blocking
Result: FAIL

Evidence:
- Screenshot before: `artifacts/e2e-instagram-oracle-20260612-104446/T1-home-before.png`
- Screenshot after swipe: `artifacts/e2e-instagram-oracle-20260612-104446/T1-home-after.png`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T1-home.log`

The accessibility service detected Home and computed block rectangles, for example `scan surface=HOME ... blockRect=Rect(...) ... navSelection=BLOCKED`, but no full blocking overlay was visible. A vertical swipe advanced the Home feed content, so homepage scrolling is not blocked.

### T2 - Reels blocking
Result: PARTIAL / BLOCKED

Evidence:
- Reels-tab tap opened NoScroll library: `artifacts/e2e-instagram-oracle-20260612-104446/T2-reels-tab-after-tap.png`
- Direct Reels attempt spinner: `artifacts/e2e-instagram-oracle-20260612-104446/T2-reels-before.png`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T2-reels.log`

The Reels tab entry point was intercepted once by the bottom-nav book overlay and opened NoScroll, which blocks that entry path. A later direct attempt to enter Reels landed on an Instagram loading spinner, so vertical in-Reels scroll blocking could not be fairly validated.

### T3 - Search/Explore grid scroll blocking
Result: FAIL

Evidence:
- Explore before: `artifacts/e2e-instagram-oracle-20260612-104446/T3-search-actual-before.png`
- Explore after swipe: `artifacts/e2e-instagram-oracle-20260612-104446/T3-search-actual-after.png`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T3-search-actual.log`

The service detected `SEARCH_EXPLORE` and computed a block rectangle, but the log also showed `navSelection=UNBLOCKED`. No blocking overlay was visible, and the Explore grid moved after a vertical swipe.

### T4 - Search typing allowance
Result: PASS

Evidence:
- Search typing: `artifacts/e2e-instagram-oracle-20260612-104446/T4-search-typing.png`
- UI dump: `artifacts/e2e-instagram-oracle-20260612-104446/T4-search-typing.xml`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T4-search-typing.log`

The search field focused and accepted `noscrolltest`. No NoScroll blocker covered the search input while typing.

### T5 - Account page allowance
Result: PARTIAL PASS / BLOCKED

Evidence:
- Account page: `artifacts/e2e-instagram-oracle-20260612-104446/T5-account-private.png`
- Window dump: `artifacts/e2e-instagram-oracle-20260612-104446/T5-account-private-window.txt`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T5-account-private.log`

Instagram opened an account page without a NoScroll blocker, so account-page access is allowed. The account was private/empty, so account posts and account reels could not be validated in this run.

### T6 - Friend-sent DM post/reel allowance
Result: BLOCKED

Evidence:
- Direct/Messages screen: `artifacts/e2e-instagram-oracle-20260612-104446/T6-direct-retry.png`
- Window dump: `artifacts/e2e-instagram-oracle-20260612-104446/T6-direct-retry-window.txt`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T6-direct-retry.log`

Direct/Messages opened, but the account showed no visible chats or shared post/reel fixture. The rule "only allow the friend-sent post/reel" could not be tested without a DM thread containing shared media.

### Regression checks
Result: PASS

Evidence:
- Final accessibility dump: `artifacts/e2e-instagram-oracle-20260612-104446/final-accessibility-dumpsys.txt`
- Final focused logcat: `artifacts/e2e-instagram-oracle-20260612-104446/final-noscroll.log`

No fatal `AndroidRuntime` or `BadTokenException` evidence was found in the focused logs. Overlay permission remained allowed and the accessibility service remained enabled.

**Impact:** NoScroll is not deployment-ready for the requested blocking behavior. The app can detect Home/Search surfaces and can sometimes redirect the Reels tab, but the full-region blocking path is not being applied to Home or Search/Explore. Next implementation work should make the computed `blockRect` drive `OverlayService.ACTION_BLOCK_REGION` for blocked surfaces, preserve search typing/account/DM exceptions, and add explicit state for DM-opened shared media.

## [2026-06-12 16:56] Session: Implement and retest Instagram block-region behavior

**Decision:** PARTIAL PASS. Home, Reels, and Search/Explore blocking now work on `emulator-5554`, and Search typing remains usable. Direct inbox is reachable and unblocked. The friend-sent shared-post fixture exists, but opening it produced a persistent black/loading Instagram surface, so the "only this shared item is viewable" rule is still not fully proven.

**Environment:**
- Host: Windows, workspace `C:\Users\aarya\Desktop\no-scroll`
- Evidence directory: `artifacts/e2e-instagram-oracle-fix-20260612-161544`
- Emulator: `emulator-5554`, Android `17`
- Instagram: `com.instagram.android`, version `433.0.0.47.68`
- NoScroll: `com.noscroll`, version `1.0`
- Permissions restored before retest: overlay=`SYSTEM_ALERT_WINDOW: allow`, accessibility=`com.noscroll/com.noscroll.NoScrollAccessibilityService`
- Build gates: `:app:testDebugUnitTest` PASS and `:app:assembleDebug` PASS using `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`

**Implementation changes:**
- `NoScrollAccessibilityService.kt`: dispatches `OverlayService.ACTION_BLOCK_REGION` whenever `scanInstagramTree()` returns a blocked surface and concrete `blockRect`.
- `NoScrollAccessibilityService.kt`: adds fallback Home detection for current Instagram builds where the selected tab state may live on a child icon or not surface reliably, using Home chrome plus bottom-nav presence.
- `NoScrollAccessibilityService.kt`: constrains selected-child bottom-nav fallback to small tab-related nodes to avoid treating arbitrary selected controls as nav tabs.
- `NoScrollAccessibilityService.kt`: reserves the detected Instagram tab-bar height below the block region so Home/Search bottom navigation remains reachable.
- `OverlayService.kt`: uses screen-coordinate overlay placement for block windows with `FLAG_LAYOUT_IN_SCREEN` and `setFitInsetsTypes(0)` on Android R+ so accessibility-screen rectangles line up with actual touch windows.
- Final review correction: selected-child nav fallback now uses the selected node/parent tab identity rather than horizontal-position guesses, and the block bottom returned to the smaller nav gap after the overlay coordinate fix made touch-window placement match accessibility coordinates.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- Home block visible with nav still reachable: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-home-wait.png`
- Home logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-home-wait.log`
- Earlier post-fix swipe proof: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix5-home-postswipe.png`

The service now logs `scan surface=HOME ... blockRect=Rect(0, 583 - 1080, 2046)`. The full NoScroll block overlay appears over feed content, and a vertical swipe in the blocked region did not advance the feed.

### T2 - Reels scroll blocking
Result: PASS

Evidence:
- Reels blocked after navigation/swipe: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-reels-after-swipe.png`
- Reels logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-reels-after-swipe.log`

The Reels surface is covered by the full NoScroll block overlay. A vertical swipe remained on the blocked NoScroll surface.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- Search/Explore blocked while search input remains visible: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-before.png`
- Search logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-before.log`
- Final smoke after review fixes: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix7-search.png`, `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix7-smoke.log`

The Search/Explore page opens from the bottom nav and the Explore grid is covered by the NoScroll block overlay. The search field remains outside the overlay and reachable.

### T4 - Search typing allowance
Result: PASS

Evidence:
- Search typing: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.png`
- UI dump: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.xml`
- Logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.log`

The search field focused and accepted `noscrolltest`. The blocker cleared while search input was focused, leaving results/input usable.

### T5 - Account page allowance
Result: NOT RETESTED IN THIS IMPLEMENTATION PASS

Previous evidence from `2026-06-12 11:05` showed an account page opened without a NoScroll blocker, but the account was private/empty. This run focused on fixing the failing Home/Reels/Search paths and did not add stronger account-post/account-reel evidence.

### T6 - Friend-sent DM post/reel allowance
Result: PARTIAL / BLOCKED

Evidence:
- Direct inbox reachable and unblocked: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-dm-from-home.png`
- DM thread black/loading surface: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-dm-thread-wait.png`
- DM logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-dm-thread-wait.log`

Direct opened from Home and remained unblocked. A visible DM fixture existed: `Aaryan Srivastava - Sent a post by everythingquant`. Opening the thread produced a persistent black/loading Instagram surface in screenshots. Logcat showed NoScroll scanning `surface=null`/no block region for the opened DM/thread surface, but the shared post itself was not visually verifiable, and swiping from that shared item into unrelated content was not proven.

### Regression checks
Result: PASS for local gates and focused crash scan

Evidence:
- Build/test logs: `gradle-test-assemble-6.log`
- Focused crash scan found no `FATAL EXCEPTION` or `BadTokenException` in the final captured log window.

**Impact:** The core block-region implementation is now aligned with objectives 1, 2, and the blocking/typing parts of 3. Objective 4 still needs a reliable shared-post/shared-reel fixture that renders on the emulator, or additional stateful implementation to distinguish a DM-opened shared media surface from general feed/reels navigation after the item opens.

## [2026-06-12 17:59] Session: DM shared-media state retest and partial implementation

**Environment:**
- Evidence directory: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900`
- Emulator: `emulator-5554`
- Build gates: `:app:testDebugUnitTest` PASS and `:app:assembleDebug` PASS using `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`
- Instagram fixture: Direct thread `Aaryan Srivastava`, shared post/reel from `everythingquant`

**Implementation changes:**
- `NoScrollAccessibilityService.kt`: added DM shared-media state (`lastDirectSharedMediaThreadMs`, `dmSharedMediaSessionActive`, `dmSharedMediaAllowedUntilMs`) and inferred DM-open handling after a Direct thread containing shared media opens a Reels/Post surface.
- `NoScrollAccessibilityService.kt`: constrained shared-media click arming to clicks inside Direct-thread ancestry, added descendant media detection, and added startup polling in `onServiceConnected()`.
- `NoScrollAccessibilityService.kt`: added direct viewer detection for Instagram reply-bar/header IDs on opened DM-shared Reels surfaces.
- `OverlayService.kt`: added transparent touch-block mode and changed transparent blocker clicks to no-op instead of launching `PdfViewerActivity`.
- Code-review subagent findings addressed: generic media arming outside DMs and transparent blocker launching the PDF viewer. The Reels full-surface block is intentional for the current product oracle.

### DM shared-media test
Result: FAIL / PARTIAL

Evidence:
- Direct thread detected with shared media: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/dm-thread5.log`
- DM shared open inferred and transparent touch block sent: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/dm-opened5.log`
- Swipe escaped from sent `everythingquant` item to unrelated Suggested reel before a later blocker caught it: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/dm-viewer-detect.png`
- Suggested reel blocked after accessibility restart: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/suggested-after-toggle.log`
- UI dump failure after overlay capture: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/dm-after-swipe3.png` plus missing XML noted from `uiautomator` error `null root node returned by UiTestAutomationBridge`

Observed behavior:
- The app can now identify the Direct thread fixture and can infer when a DM-shared item opens.
- The service logs `dm shared media open inferred` and `dm shared media touch block active` for the opened `everythingquant` item.
- The current implementation still does not reliably keep the sent item visible. A vertical swipe escaped to an unrelated Suggested reel in one run. Once the service was restarted on that Suggested reel, NoScroll correctly classified it as `surface=REELS` and blocked it, but that is too late for objective 4.

**Current status:** Objectives 1, 2, Search/Explore blocking, and Search typing remain passing from the previous evidence. Objective 4 is still not complete; the remaining work is to prevent the first swipe from leaving the DM-opened media, not merely block after escape.

## [2026-06-12 18:24] Session: final DM containment implementation and emulator verification

**Environment:**
- Evidence directory: `artifacts/e2e-instagram-final-20260612-181300`
- Emulator: `emulator-5554`
- Android version: captured in `artifacts/e2e-instagram-final-20260612-181300/android-version.txt`
- Instagram package version: captured in `artifacts/e2e-instagram-final-20260612-181300/instagram-version.txt`
- NoScroll package version: captured in `artifacts/e2e-instagram-final-20260612-181300/noscroll-version.txt`
- Install result: `Success` in `artifacts/e2e-instagram-final-20260612-181300/install.txt`
- Build gates: `:app:testDebugUnitTest` PASS and `:app:assembleDebug` PASS using `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`

**Implementation changes since the 17:59 run:**
- `OverlayService.kt`: transparent DM touch-block now explicitly consumes touch events with `setOnTouchListener { _, _ -> true }`.
- `NoScrollAccessibilityService.kt`: removed the broad "recent Direct shared-media thread implies any blocked surface is DM media" inference because it leaked DM transparent touch-block mode onto Home/Reels/Search after leaving Direct.
- `NoScrollAccessibilityService.kt`: returning to Direct thread now clears `dmSharedMediaSessionActive` and `dmSharedMediaAllowedUntilMs`.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- Screenshot before/after swipe: `artifacts/e2e-instagram-final-20260612-181300/final-home-before.png`, `artifacts/e2e-instagram-final-20260612-181300/final-home-after.png`
- Log: `artifacts/e2e-instagram-final-20260612-181300/final-home.log`

Decision reason:
- Logs repeatedly classify `surface=HOME` with `directThread=false directShared=false directViewer=false`.
- No stale `dm shared media touch block active` appeared after the final inference patch.

### T2 - Reels scroll blocking
Result: PASS

Evidence:
- Screenshot before/after swipe: `artifacts/e2e-instagram-final-20260612-181300/final-reels-before.png`, `artifacts/e2e-instagram-final-20260612-181300/final-reels-after.png`
- Log: `artifacts/e2e-instagram-final-20260612-181300/final-reels.log`

Decision reason:
- Logs classify `surface=REELS` with full content block rect `Rect(0, 142 - 1080, 2172)`.
- No stale DM touch-block state appeared.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- Search/Explore deep-link screenshot before/after swipe: `artifacts/e2e-instagram-final-20260612-181300/final-search-deeplink-before.png`, `artifacts/e2e-instagram-final-20260612-181300/final-search-deeplink-after.png`
- UI dump: `artifacts/e2e-instagram-final-20260612-181300/final-search-deeplink-before.xml`
- Log: `artifacts/e2e-instagram-final-20260612-181300/final-search-deeplink.log`

Decision reason:
- UI dump shows Instagram Explore grid and selected `search_tab`.
- Logs classify `surface=SEARCH_EXPLORE searchSelected=true searchFocused=false` with block rect `Rect(0, 245 - 1080, 2172)`.
- The visible search field remains above the block rect.

### T4 - Search typing allowance
Result: PASS from previous focused evidence; FINAL RUN HAS RESIDUAL RISK

Evidence:
- Previous focused typing pass: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.png`, `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.xml`, `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.log`
- Final run attempted focused capture: `artifacts/e2e-instagram-final-20260612-181300/final-search-typing.png`
- Final window dump/log check after timeout: current focus remained Instagram and IME was present, but Android also reported an input-dispatch timeout for a `com.noscroll` overlay window.

Decision reason:
- The search field behavior passed earlier and the final code changes did not touch search-focus classification.
- The final focused capture hit an overlay/input-dispatch timeout, so this should be treated as a deployment-quality risk even though no `FATAL EXCEPTION` or `BadTokenException` was observed.

### T5 - Account page allowance
Result: NOT RETESTED IN FINAL RUN

Evidence:
- Previous account navigation evidence remains from earlier runs; final work focused on the DM containment failure and regression smoke for Home/Reels/Search.

### T6 - Friend-sent DM post/reel containment
Result: PASS

Evidence:
- Thread fixture opened through New Message search: `artifacts/e2e-instagram-final-20260612-181300/new-message-search.xml`, `artifacts/e2e-instagram-final-20260612-181300/new-message-selected.xml`
- Shared `everythingquant` item before/after swipe: `artifacts/e2e-instagram-final-20260612-181300/final-dm-open.png`, `artifacts/e2e-instagram-final-20260612-181300/final-dm-after-swipe.png`
- UI dumps: `artifacts/e2e-instagram-final-20260612-181300/final-dm-open.xml`, `artifacts/e2e-instagram-final-20260612-181300/final-dm-after-swipe.xml`
- Window dumps: `artifacts/e2e-instagram-final-20260612-181300/final-dm-open-window.txt`, `artifacts/e2e-instagram-final-20260612-181300/final-dm-after-swipe-window.txt`
- Log: `artifacts/e2e-instagram-final-20260612-181300/final-dm-after-swipe.log`

Decision reason:
- `final-dm-open.xml` and `final-dm-after-swipe.xml` have identical SHA-256 hashes.
- The after-swipe UI dump still shows `Reel by everythingquant`, `sender_username_or_fullname=Aaryan Srivastava`, and `Reply to Aaryan Srivastava`.
- Logs show repeated `dm shared media viewer detected` and `dm shared media touch block active`.
- Logs do not show the removed `dm shared media open inferred` path.

### Regression checks
Result: PASS with one residual deployment risk

Evidence:
- No captured final logs showed `FATAL EXCEPTION` or `BadTokenException`.
- `uiautomator dump` timed out during the focused Search typing attempt while Android reported an input dispatch timeout against `com.noscroll`.

Residual risk:
- The visible block path can foreground NoScroll/PDF reader and may leave an overlay window that Android reports as not responding during input-dispatch-heavy captures. This did not crash the app, but it is a deployment readiness issue to address next.

## [2026-06-12 19:22] Session: current-build emulator campaign and final readiness decision

**Environment:**
- Evidence directory: `artifacts/e2e-instagram-final-20260612-190500`
- Emulator: `emulator-5554`
- Android version: 17
- Instagram: `com.instagram.android` version `433.0.0.47.68`, versionCode `383909377`
- NoScroll: `com.noscroll`, debug APK reinstalled with `adb install -r`
- Accessibility: `com.noscroll/.NoScrollAccessibilityService` bound and enabled
- Overlay permission: `SYSTEM_ALERT_WINDOW allow`
- Build gates: `:app:testDebugUnitTest` PASS and `:app:assembleDebug` PASS using `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`
- Subagent review: code-reviewer flagged story-viewer narrowing and visible block tap-to-book regression; both were addressed before final build.

**Implementation changes in this session:**
- `NoScrollAccessibilityService.kt`: keeps Direct inbox/thread unblocked, arms a short DM-shared-media session from Direct shared-media clicks, detects opened DM shared media viewer, and applies transparent touch-block only to that viewer.
- `NoScrollAccessibilityService.kt`: Search/Explore blocking now starts below the search input fallback line and profile pages opened from Search are excluded from blocking.
- `NoScrollAccessibilityService.kt`: story viewer detection again treats `swipe_navigation_container` as sufficient unless the view is the DM shared-media viewer path.
- `OverlayService.kt`: added visible full-region blocker and transparent touch-block mode. Visible blocker consumes touches and launches `PdfViewerActivity` on tap; transparent DM blocker consumes touches with no-op click.
- Attempted focusable overlay blocker was rejected because it made bottom navigation unreliable; final installed build uses the safer non-focusable overlay.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- Current-build pass: `artifacts/e2e-instagram-final-20260612-190500/27-patched-reels-before.png`, `artifacts/e2e-instagram-final-20260612-190500/28-patched-reels-after-swipe.png`
- Earlier current-build focused pass: `artifacts/e2e-instagram-current-20260612-verify/11-final-home-before.png`, `artifacts/e2e-instagram-current-20260612-verify/12-final-home-after-swipe.png`

Decision reason:
- After a Home swipe, the visible `NoScroll` blocker is present over feed content.
- Logs classify `surface=HOME` with block rects such as `Rect(0, 583 - 1080, 2172)`.

### T2 - Reels scroll blocking
Result: FAIL

Evidence:
- Reels before swipe: `artifacts/e2e-instagram-final-20260612-190500/30-patched-reels-real-before.png`
- Reels after swipe: `artifacts/e2e-instagram-final-20260612-190500/31-patched-reels-real-after-swipe.png`
- Supporting logs: `artifacts/e2e-instagram-final-20260612-190500/30-patched-reels-real-before.log`, `artifacts/e2e-instagram-final-20260612-190500/31-patched-reels-real-after-swipe.log`

Decision reason:
- The service repeatedly classifies `surface=REELS` and emits `blockRect=Rect(0, 142 - 1080, 2172)`.
- The visual after-swipe screenshot shows Instagram advanced to visible reel content instead of remaining blocked.
- A focusable overlay experiment did not fix the problem and made navigation worse, so it was reverted before the final installed build.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- Grid: `artifacts/e2e-instagram-current-20260612-verify/23-final-search-deeplink-grid.png`, `.xml`, `.log`

Decision reason:
- Logs classify `surface=SEARCH_EXPLORE searchFocused=false profilePage=false` with `blockRect=Rect(0, 301 - 1080, 2172)`.
- The 301px top offset leaves the search field usable while blocking grid content.

### T4 - Search typing allowance
Result: PASS

Evidence:
- Typing: `artifacts/e2e-instagram-current-20260612-verify/24-final-search-deeplink-typing.png`, `.xml`, `.log`

Decision reason:
- Logs show `searchFocused=true` with `blockRect=null`.
- UI dump/screenshot show the typed `everythingquant`/search results path remains usable.

### T5 - Account page allowance from Search
Result: PASS

Evidence:
- Account page: `artifacts/e2e-instagram-current-20260612-verify/25-final-search-deeplink-account.png`, `.xml`, `.log`

Decision reason:
- Logs show `profilePage=true` and `blockRect=null`.
- Account profile browsing from Search is not blocked.

### T6 - Friend-sent DM post/reel containment
Result: PASS for the available shared post/reel fixture

Evidence:
- DM inbox fixture: `artifacts/e2e-instagram-current-20260612-verify/26-final-dm-inbox.xml`
- DM thread: `artifacts/e2e-instagram-current-20260612-verify/27-final-dm-thread.xml`
- Opened shared item: `artifacts/e2e-instagram-current-20260612-verify/28-final-dm-open-shared-post.xml`, `.log`
- After swipe: `artifacts/e2e-instagram-current-20260612-verify/29-final-dm-after-swipe.xml`, `.log`

Decision reason:
- Inbox fixture exposed `Aaryan Srivastava, Sent a post by everythingquant`.
- `28-final-dm-open-shared-post.xml` and `29-final-dm-after-swipe.xml` have identical SHA-256 hashes.
- Logs show `dm shared media viewer detected` and `dm shared media touch block active`.

### Regression checks
Result: PARTIAL PASS

Evidence:
- Final build/unit test output: terminal run at 19:22, `BUILD SUCCESSFUL`.
- Crash scans: `artifacts/e2e-instagram-final-20260612-190500/13-crash-scan.txt`, `20-crash-scan-after-reels.txt`, `23-crash-scan-final.txt`, `26-crash-scan-current-reels.txt`, `29-crash-scan-patched-reels.txt`, `32-crash-scan-patched-real-reels.txt`, `35-crash-scan-focusable-reels.txt`

Decision reason:
- No fresh `FATAL EXCEPTION` or `BadTokenException` was captured in the final crash scans.
- `uiautomator dump` sometimes reports `ERROR: could not get idle state` during animated Instagram/Reels states; screenshots, window dumps, and logs were used when XML was still pulled.
- There is a historical/stale window ANR in earlier dumps from 18:23; it was not reproduced as a fresh final crash, but remains a deployment-risk signal.

**Final readiness decision:** Not deployment-ready yet. Home, Search/Explore, Search typing, account-page allowance, and DM shared-media containment are in acceptable shape on the emulator. Reels scrolling is still bypassable in the emulator despite correct service classification, so the next fix should target the input-blocking mechanism for Reels specifically rather than more surface-detection changes.

## 2026-06-12 20:18 EET - Post-Reels-Rollback / Non-Touchable Overlay Verification

### Metadata
- Emulator: `emulator-5554`
- Build: `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`; `:app:testDebugUnitTest :app:assembleDebug` passed after the latest code changes.
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk` succeeded.
- App permissions: `SYSTEM_ALERT_WINDOW` allowed; `com.noscroll/.NoScrollAccessibilityService` re-enabled.
- Code changes under test:
  - Reels/Home/Search blocked-scroll rollback now targets the active scroll container.
  - Reels rollback now prefers `com.instagram.android:id/clips_viewer_view_pager`.
  - Search-focused state suppresses the prior stable-block grace overlay.
  - Block-region overlays are now visible but `FLAG_NOT_TOUCHABLE` to avoid NoScroll input-dispatch ANRs; scroll blocking relies on accessibility rollback plus overlay feedback.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-regression-20260612-194225/12-home-before-swipe.png`
- `artifacts/e2e-instagram-regression-20260612-194225/14-home-after-swipe.png`

Decision reason:
- After vertical swipe, the Home feed is covered by the visible `NoScroll` blocker.
- Logs classify the page as `surface=HOME`.

### T2 - Reels scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-reels-targeted-20260612-193951/06-reels-before-swipe.png`
- `artifacts/e2e-instagram-reels-targeted-20260612-193951/08-reels-after-swipe.png`
- `artifacts/e2e-instagram-reels-targeted-20260612-193951/08-reels-after-swipe-nos.log`

Decision reason:
- Reels tab was actually selected before the swipe.
- After swipe, the visible `NoScroll` blocker covers the Reels content.
- Log confirms rollback targeted the vertical pager: `target=com.instagram.android:id/clips_viewer_view_pager`.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-search-valid-20260612-195914/01-search-before-swipe.png`
- `artifacts/e2e-instagram-search-valid-20260612-195914/03-search-after-swipe.png`

Decision reason:
- Search tab was selected with the search input unfocused.
- After vertical swipe, the visible `NoScroll` blocker remains over grid content.
- Logs classify `surface=SEARCH_EXPLORE searchFocused=false`.

### T4 - Search typing allowance
Result: PASS

Evidence:
- `artifacts/e2e-instagram-search-valid-20260612-195914/12-search-typing-results.png`
- `artifacts/e2e-instagram-search-valid-20260612-195914/12-search-typing-results-nos.log`

Decision reason:
- Search input accepted `openai`.
- Results list remained visible and usable.
- Logs show `surface=null searchFocused=true blockRect=null`.

### T5 - Account page allowance from Search
Result: PASS

Evidence:
- `artifacts/e2e-instagram-account-valid-20260612-200054/01-account-page.png`
- `artifacts/e2e-instagram-account-valid-20260612-200054/03-account-after-swipe.png`
- `artifacts/e2e-instagram-account-valid-20260612-200054/05-account-post-or-grid.png`

Decision reason:
- Search result opened the `openai` account page.
- Profile grid scrolling and opening a post were not blocked.
- Logs show `profilePage=true` with `blockRect=null` while on the profile grid.

### T6 - Friend-sent DM post/reel containment
Result: BLOCKED in this latest emulator pass

Evidence:
- Invalid probe: `artifacts/e2e-instagram-dm-precondition-20260612-200244/03-dm-inbox.png`
- Invalid probe: `artifacts/e2e-instagram-dm-valid-probe-20260612-200434/00-main-tab-before-dm.png`

Decision reason:
- The latest probes did not successfully reach a Direct inbox/thread; screenshots show Search/Profile states instead.
- Earlier evidence in `artifacts/e2e-instagram-current-20260612-verify/28-final-dm-open-shared-post.xml` and `29-final-dm-after-swipe.xml` showed a valid shared-item fixture with identical XML after swipe, but that fixture was not reproduced after the latest non-touchable overlay change.
- This remains blocked by current emulator navigation/precondition, not marked passing for the latest build.

### Regression checks
Result: PARTIAL PASS

Evidence:
- Build/unit tests passed after latest source changes.
- `artifacts/e2e-instagram-account-valid-20260612-200054/99-full-logcat.txt`
- `artifacts/e2e-instagram-search-valid-20260612-195914/99-full-logcat.txt`

Decision reason:
- The previous NoScroll input-dispatch ANR was addressed by making block-region overlays non-touchable.
- A later all-in-one sweep produced an Instagram-owned ANR (`Application Not Responding: com.instagram.android`), not a NoScroll ANR. That sweep is not used as a product pass artifact.

### Readiness decision
Not fully deployment-ready yet. Home, Reels, Search grid, Search typing, and account-page behavior now pass on the emulator. The latest build still needs a valid DM shared-post/reel re-run, and the non-touchable overlay tradeoff should be reviewed because it changes blocking from direct touch interception to accessibility rollback.

### Review follow-up
Result: PATCHED / NEEDS E2E RE-RUN

Follow-up changes after code-review agent findings:
- `ACTION_TOUCH_BLOCK_REGION` now remains touchable and consumes touches for DM shared-media containment.
- Visible `ACTION_BLOCK_REGION` remains non-touchable/pass-through to avoid NoScroll input-dispatch ANRs; feed blocking is enforced by accessibility rollback and visible feedback.
- Blocked-scroll rollback now checks scroll direction before issuing `ACTION_SCROLL_BACKWARD`.
- Overlay service commands from the accessibility service are wrapped with failure handling to avoid crashing on service-start failures.

Verification:
- `:app:compileDebugKotlin` passed.
- `:app:testDebugUnitTest :app:assembleDebug` passed.
- APK reinstall on `emulator-5554` succeeded.
- `graphify update .` completed after the source changes.

Remaining gap:
- Because these review follow-up patches changed overlay touchability and rollback gating, the latest installed APK still needs a fresh targeted E2E pass for Reels, Search, and especially the DM shared-post/reel fixture before it should be called deployment-ready.

## 2026-06-12 20:42 EET - Latest Installed APK Focused E2E Pass

### Metadata
- Artifact folder: `artifacts/e2e-instagram-latest-20260612-202813`
- Emulator serial: `emulator-5554`
- Android version: `17`
- Screen size: `1080x2424`
- Instagram package: `com.instagram.android`
- Instagram version: `433.0.0.47.68` / versionCode `383909377`
- NoScroll package: `com.noscroll`
- NoScroll version: `1.0` / versionCode `1`
- Overlay permission: `SYSTEM_ALERT_WINDOW: allow`
- Accessibility service: `com.noscroll/.NoScrollAccessibilityService`
- Service state after run: bound, enabled, no crashed services
- Gradle command: `:app:testDebugUnitTest :app:assembleDebug --no-daemon`
- Gradle result: PASS
- Static whitespace check: PASS; only CRLF replacement warnings
- Focused latest log sweep: PASS; no `AndroidRuntime`, `FATAL EXCEPTION`, `BadTokenException`, `Application Not Responding`, `ANR in com.noscroll`, or `ANR in com.instagram.android` lines found in `*-nos.log`

### Subagent usage
- Code-review subagent reviewed the earlier patch set and identified overlay touchability, rollback direction, and service-start failure handling risks.
- Final read-only reviewer subagent was started after this E2E pass to check readiness gaps; any returned findings should be appended separately if they arrive after this log entry.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/02-home-before.png`
- `artifacts/e2e-instagram-latest-20260612-202813/03-home-after.png`
- `artifacts/e2e-instagram-latest-20260612-202813/03-home-after-nos.log`

Steps:
- Launched Instagram from a clean process state.
- Confirmed Home feed was visible.
- Performed a vertical swipe in the content area.
- Captured screenshot, UI XML, window dump, and focused logcat.

Expected:
- Home feed should not advance meaningfully.
- NoScroll block behavior should be visible or logged.

Actual:
- The visible feed remained on the same Home section after the swipe.
- `NoScroll` overlay stayed visible over the feed.
- Logcat recorded `blocked scroll rollback surface=HOME success=true target=android:id/list`.

Decision reason:
- The feed attempted movement, then accessibility rollback returned it to the blocked Home state.

### T2 - Reels tab scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/04-reels-before.png`
- `artifacts/e2e-instagram-latest-20260612-202813/05-reels-after.png`
- `artifacts/e2e-instagram-latest-20260612-202813/05-reels-after-nos.log`

Steps:
- Tapped the Reels bottom tab.
- Waited for Reels viewer.
- Performed a vertical swipe in the Reels content area.
- Captured screenshot, UI XML, window dump, and focused logcat.

Expected:
- User should not advance into unrelated Reels content.
- Overlay should cover the content, not just the bottom tab.

Actual:
- Reels surface was detected.
- Overlay covered the main content region.
- Logcat recorded `blocked scroll rollback surface=REELS success=true target=com.instagram.android:id/clips_viewer_view_pager`.

Decision reason:
- Reels scrolling is now actively rolled back on the correct Instagram pager.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/11-search-grid-before.png`
- `artifacts/e2e-instagram-latest-20260612-202813/12-search-grid-after-swipe.png`
- `artifacts/e2e-instagram-latest-20260612-202813/13-search-typed-valid-nos.log`

Steps:
- Tapped Search/Explore from the bottom nav.
- Confirmed the search input was not focused.
- Performed a vertical swipe over the Explore grid.
- Captured before/after screenshots, UI XML, window dumps, and focused logcat.

Expected:
- Explore grid should not scroll while search input is not focused.

Actual:
- Grid screenshots before and after the swipe show the same visible grid position.
- Logcat recorded `surface=SEARCH_EXPLORE searchFocused=false`.
- Logcat recorded `blocked scroll rollback surface=SEARCH_EXPLORE success=true target=com.instagram.android:id/recycler_view`.

Decision reason:
- Search/Explore grid scrolling is blocked while leaving the search input area uncovered.

### T4 - Search typing allowance
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/13-search-typed-valid.png`
- `artifacts/e2e-instagram-latest-20260612-202813/13-search-typed-valid-nos.log`

Steps:
- Focused the Search input.
- Typed `openai`.
- Captured screenshot, UI XML, window dump, and focused logcat.

Expected:
- Search input and keyboard should remain usable.
- No blocker should cover the input.

Actual:
- The input accepted `openai`.
- Search results were shown.
- Logcat recorded `surface=null searchFocused=true blockRect=null`.

Decision reason:
- Search remains usable while typing; blocking is removed for the focused search state.

### T5 - Account page allowance from Search
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/14-account-profile.png`
- `artifacts/e2e-instagram-latest-20260612-202813/15-account-profile-after-swipe.png`
- `artifacts/e2e-instagram-latest-20260612-202813/16-account-post-open.png`
- `artifacts/e2e-instagram-latest-20260612-202813/16-account-post-open-nos.log`

Steps:
- Opened the verified `openai` account from search results.
- Swiped the profile/grid area.
- Opened a post from the account grid.
- Captured screenshots, UI XML, window dumps, and focused logcat.

Expected:
- Account profile browsing should remain usable.
- Posts/reels from that account should be viewable.

Actual:
- The `openai` profile opened.
- Profile/grid scrolling worked.
- A post opened.
- Logcat recorded `profilePage=true` with `blockRect=null` on the profile, then `surface=null` after opening the post.

Decision reason:
- Account browsing from Search is allowed as requested.

### T6 - Friend-sent DM post/reel containment
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/18-dm-attempt.png`
- `artifacts/e2e-instagram-latest-20260612-202813/19-dm-thread.png`
- `artifacts/e2e-instagram-latest-20260612-202813/20-dm-shared-open.png`
- `artifacts/e2e-instagram-latest-20260612-202813/21-dm-shared-after-swipe.png`
- `artifacts/e2e-instagram-latest-20260612-202813/21-dm-shared-after-swipe-nos.log`

Steps:
- Navigated to Direct inbox.
- Opened the available thread: `Aaryan Srivastava, Sent a post by everythingquant, 4h`.
- Opened the shared post/reel item in that thread.
- Swiped vertically in the shared media viewer.
- Captured screenshots, UI XML, window dumps, and focused logcat.

Expected:
- The shared item should be viewable.
- Swiping should not move into unrelated Instagram content.

Actual:
- Thread opened and showed a shared `everythingquant` item.
- Shared item opened in the media viewer.
- After vertical swipe, the screenshot remained on the same shared item.
- Logcat recorded `directThread=true directShared=true` in the thread.
- Logcat recorded `directViewer=true` and `dm shared media viewer detected surface=REELS rect=Rect(0, 0 - 1080, 2214)` in the shared viewer.

Decision reason:
- The opened shared media item stayed contained after a swipe and NoScroll recognized the DM shared viewer state.

### Regression checks
Result: PASS

Evidence:
- Gradle: `:app:testDebugUnitTest :app:assembleDebug --no-daemon`
- Accessibility state: bound and enabled NoScroll service, no crashed services.
- Overlay app-op: `SYSTEM_ALERT_WINDOW: allow`
- Log sweep: no fatal/ANR/BadToken entries in latest focused `*-nos.log` files.

Decision reason:
- The current focused run did not reproduce the prior NoScroll input-dispatch ANR or the earlier Instagram-owned ANR.
- The service remained bound after the full latest pass.

### Current readiness decision
The latest installed debug APK now passes the requested emulator E2E campaign for Home, Reels, Search grid, Search typing, account-page allowance, and a real DM shared-item fixture. The remaining product tradeoff is intentional: visible feed/reels/search blockers are pass-through overlays with accessibility rollback, while the DM shared-media touch blocker remains touch-consuming for containment. This is deployment-ready from the current emulator evidence, with the normal caveat that Instagram UI/resource IDs can change across app updates.

## 2026-06-12 21:00 EET - Reviewer Follow-up: Overlay Touchability Lifecycle

### Reviewer finding addressed
Result: PATCHED

Finding:
- Read-only reviewer flagged that `ACTION_UNFREEZE` could remove `FLAG_NOT_TOUCHABLE` from an existing visible block overlay, and that the existing-overlay path in `showBlockRegion()` could return without restoring visible-mode flags.

Fix:
- `OverlayService.setTouchable(enabled)` is now mode-aware. `ACTION_UNFREEZE` can only make overlays touchable when `overlayMode != OverlayMode.BLOCK`.
- `OverlayService.showBlockRegion()` now computes block-region flags once and reapplies them on existing overlays, even when only the flags differ.
- Visible `ACTION_BLOCK_REGION` overlays remain `FLAG_NOT_TOUCHABLE`.
- Transparent `ACTION_TOUCH_BLOCK_REGION` overlays remain touch-consuming for DM shared-media containment.

Verification:
- `:app:testDebugUnitTest :app:assembleDebug --no-daemon` passed after the patch.
- APK was reinstalled on `emulator-5554`.
- `graphify update .` completed after the code change.
- `git diff --check` passed with only CRLF replacement warnings.
- Accessibility service was re-enabled after reinstall and is bound/enabled with no crashed services.
- Overlay app-op remains `SYSTEM_ALERT_WINDOW: allow`.

### Lifecycle E2E attempt
Result: BLOCKED by Instagram-owned ANR

Evidence:
- `artifacts/e2e-instagram-lifecycle-20260612-205434/03-home-block-after-return.png`
- `artifacts/e2e-instagram-lifecycle-20260612-205434/04-home-block-after-return-swipe.png`
- `artifacts/e2e-instagram-lifecycle-20260612-205434/04-home-block-after-return-swipe-nos.log`

Steps:
- Started Instagram, then switched to launcher and attempted to return to Instagram.
- Attempted to verify visible blocker after app-return and swipe.

Actual:
- Instagram did not return to an active feed; screenshots show the launcher.
- Logcat recorded `ANR in com.instagram.android`.
- ANR reason: `No response to onStartJob for #PushabilityCheckerWorker#@androidx.work.systemjobscheduler@com.instagram.android/androidx.work.impl.background.systemjob.SystemJobService`.
- This is not a NoScroll ANR, but it prevents using this lifecycle attempt as a clean blocker/pass-through proof.

Decision reason:
- The reviewer's HIGH code-path risk is fixed in source and covered by build/static checks.
- A full app-switch lifecycle E2E pass is still blocked by Instagram/emulator instability, not by a NoScroll crash.

### Direct overlay command attempt
Result: BLOCKED by Android service export rules

Evidence:
- `artifacts/overlay-flag-regression-20260612-205814/01-start-block.txt`
- `artifacts/overlay-flag-regression-20260612-205814/03-unfreeze.txt`
- `artifacts/overlay-flag-regression-20260612-205814/04-restated-block.txt`

Steps:
- Attempted to start `com.noscroll/.OverlayService` directly from `adb shell` with `ACTION_BLOCK_REGION`, `ACTION_FREEZE`, and `ACTION_UNFREEZE`.

Actual:
- Android rejected direct shell starts with `Error: Requires permission not exported from uid 10226`.

Decision reason:
- This is expected because the service is not exported. It preserves production posture, but means the exact flag path cannot be externally stimulated through `adb shell`.

### Final deployment note
The core Instagram product oracle passed on the latest focused E2E run before this final overlay lifecycle patch, and the patch is narrow enough that it only preserves the intended non-touchable visible blocker state. The remaining risk is not a known NoScroll failure; it is that the app-switch lifecycle regression could not be completed cleanly because Instagram produced its own ANR on the emulator.

## 2026-06-12 21:41 EET - Final DM Containment Rerun After Accessibility Re-enable

### Patch added after failed DM swipe attempt
Result: PATCHED

Finding:
- A DM shared reel could be opened successfully, but when accessibility was not actually enabled after reinstall the retest was invalid.
- In an earlier enabled run, a swipe from the shared reel's content area could leave the shared item and land on the sender/account profile.

Fix:
- `ScanResult` now carries `profilePageActive`.
- While a DM shared-media session is armed, if Instagram leaves the shared viewer into a profile surface, `NoScrollAccessibilityService` performs `GLOBAL_ACTION_BACK`, hides the overlay, and logs `dm shared media containment back from profile`.
- Existing transparent `ACTION_TOUCH_BLOCK_REGION` behavior remains active while `directViewer=true`.

Verification:
- Build passed after the code patch: `:app:testDebugUnitTest :app:assembleDebug --no-daemon`.
- APK reinstalled on `emulator-5554`.
- Accessibility was explicitly re-enabled after reinstall:
  - `enabled_accessibility_services=com.noscroll/com.noscroll.NoScrollAccessibilityService`
  - `accessibility_enabled=1`
  - `dumpsys accessibility`: NoScroll enabled, `Crashed services:{}`.
- `graphify update .` completed after the final code change.
- `git diff --check` passed with only CRLF replacement warnings.
- Focused valid-run log sweep found no `AndroidRuntime`, `FATAL EXCEPTION`, `BadTokenException`, `ANR in com.noscroll`, `ANR in com.instagram.android`, or `Application Not Responding` entries in the valid run logs.

### Valid DM shared reel rerun
Result: PASS

Evidence:
- Inbox screenshot: `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/39-dm-inbox-valid.png`
- Thread screenshot/XML: `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/40-dm-thread-valid.png`, `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/40-dm-thread-valid.xml`
- Shared reel before swipe: `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/41-dm-shared-open-valid.png`, `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/41-dm-shared-open-valid.xml`, `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/41-dm-shared-open-valid-nos.log`
- Shared reel after swipe: `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/42-dm-shared-after-swipe-valid.png`, `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/42-dm-shared-after-swipe-valid.xml`, `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/42-dm-shared-after-swipe-valid-nos.log`
- Window dump: `artifacts/e2e-instagram-post-overlaypatch-20260612-210541/42-dm-shared-after-swipe-valid-window.txt`

Steps:
- From Home, tapped Direct tab.
- Opened the `Aaryan Srivastava` DM thread with shared `everythingquant` content.
- Opened the shared reel.
- Swiped vertically from the same content/profile area that previously navigated away.

Expected:
- The shared reel remains viewable.
- Swipe does not advance into unrelated content or leave the shared-item viewer.

Actual:
- Before swipe XML showed `Reel by everythingquant. Double tap to play or pause.`
- After swipe XML still showed the same `Reel by everythingquant. Double tap to play or pause.`
- After swipe XML still showed the same sender header/reply context for `Aaryan Srivastava`.
- No profile page XML appeared after the valid swipe.
- Log evidence:
  - `scan surface=REELS ... directViewer=true ... blockRect=Rect(0, 0 - 1080, 2214)`
  - `dm shared media viewer detected surface=REELS rect=Rect(0, 0 - 1080, 2214)`
  - `dm shared media touch block active rect=Rect(0, 0 - 1080, 2214)`

Decision reason:
- With accessibility correctly re-enabled, the patched APK keeps a friend-sent reel constrained to the opened item. The user can view the shared item, and the tested swipe did not escape to the account profile or unrelated content.

### Final current readiness decision
The latest validated state passes the requested NoScroll oracle on `emulator-5554`:
- Home scrolling blocked.
- Reels scrolling blocked.
- Search/Explore scrolling blocked while search typing and account/post browsing remain usable.
- DM shared reel remains constrained to the opened shared item.

Remaining caveat:
- The emulator/Instagram stack can still produce Instagram-owned startup or modal instability. The latest valid focused run did not show a NoScroll crash, BadToken, or fresh Instagram ANR.
