# reasoning.md — no-scroll

## [2026-05-15 08:34] Session: diagnose and fix all app crashes + navigation loop

**Decision:** Fixed 6 bugs across 4 files on branch `codex/reader-selection-highlights-zen`.

**Why (each fix):**

### Fix 1 — `UninitializedPropertyAccessException: _toolboxView`
`PdfViewerActivity.setupControls()` called `pdfFragment.setZenToolboxVisible()` in `onCreate()`, before `PdfViewerFragment`'s internal `_toolboxView` was initialized. fragment was added with `commitNow()` (synchronous at activity level), but `_toolboxView` is only initialized in `onPdfViewCreated()`, fires later when PDF rendering context is ready.

Fix: Added `pendingToolboxVisible: Boolean?` to `NoScrollPdfViewerFragment`. `setZenToolboxVisible()` catches `UninitializedPropertyAccessException` and stores value; `onPdfViewCreated()` applies it.

### Fix 2 — `InflateException: Error inflating ToolBoxView`
`ToolBoxView` inside `PdfViewerFragment` contains `FloatingActionButton` reads `backgroundTint` from activity theme at inflation time. App theme (`Theme.MaterialComponents.DayNight.DarkActionBar`) is Material2. `androidx.pdf:1.0.0-alpha18` FAB uses Material3 attribute patterns — unresolved in M2 → `TypedValue.TYPE_ATTRIBUTE` (0x2) at index 1 → crash.

Fix: Added `Theme.NoScroll.PdfViewer` extending `Theme.Material3.DayNight.NoActionBar` to `themes.xml`. Applied only to `PdfViewerActivity` via `android:theme` in `AndroidManifest.xml`. All other activities keep M2 theme.

### Fix 3 — `IllegalStateException: Can't scrollToPage without PdfDocument`
`PdfViewerActivity.onPdfLoaded()` calls `pdfFragment.scrollToPage()`. This fires from `onLoadDocumentSuccess()` — fires when `PdfDocument` object is created, but BEFORE `PdfView` internally has its document set. So `PdfView.scrollToPage()` throws.

Fix: Added `pendingScrollPage: Int?` to `NoScrollPdfViewerFragment`. `scrollToPage()` try-catches `IllegalStateException` and stores page. Applied in both `onLoadDocumentSuccess` and `onPdfViewCreated` as two retry points.

### Fix 4 — `BadTokenException: permission denied for window type 2038`
`OverlayService.updateOverlay()` called `windowManager.addView(view, TYPE_APPLICATION_OVERLAY)` without checking `SYSTEM_ALERT_WINDOW` permission. If accessibility was enabled but overlay was NOT granted, every accessibility event triggered OverlayService → `addView()` → crash → process kill → accessibility restart → repeat.

Fix: `OverlayService.onStartCommand()` checks `Settings.canDrawOverlays()` before calling `updateOverlay()`. Returns `START_NOT_STICKY` when permission missing so Android doesn't auto-restart.

### Fix 5 — OverlayService restart-loop / Samsung deep-sleep
`START_STICKY` caused stopped OverlayService to be auto-restarted by Android immediately, stopped again. Samsung's battery optimizer detected this as crash-loop and put app into deep sleep, **disabling accessibility service from Settings**.

Fix: `START_NOT_STICKY` in no-permission path. Each invocation now driven solely by accessibility events, not Android's restart mechanism.

### Fix 6 — Accessibility service spamming OverlayService without permission
`NoScrollAccessibilityService.onAccessibilityEvent()` fired on every window/content change regardless of overlay permission. Even with Fixes 4+5, accessibility service still called `startService(OverlayService)` on every Instagram scroll in partial-setup state.

Fix: Added `if (!Settings.canDrawOverlays(this)) return` at top of `onAccessibilityEvent()`.

---

**Navigation loop root cause:** 4 crash bugs caused tight crash-loop → Samsung deep-sleep disabled accessibility service → every launch saw `hasAccessibilityEnabled() == false` → SetupActivity loop. Resolution: fix all crashes, user re-enables accessibility service once manually.

**Impact:** Future work touching `PdfViewerFragment` lifecycle must account for `onPdfViewCreated` vs `onLoadDocumentSuccess` ordering. Do not call `isToolboxVisible` or `PdfView.scrollToPage()` before `onPdfViewCreated` fires. Always guard OverlayService dispatch with `Settings.canDrawOverlays()`.

## [2026-06-12 11:05] Session: Android emulator E2E test run for Instagram blocking oracle

**Decision:** FAIL for deploy-readiness oracle. emulator, NoScroll permissions, build, and Instagram login state were usable, but Home scrolling and Search/Explore scrolling were not blocked. Direct Reels content and DM shared-item testing were blocked by Instagram loading/test-fixture state.

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

accessibility service detected Home and computed block rectangles, for example `scan surface=HOME ... blockRect=Rect(...) ... navSelection=BLOCKED`, but no full blocking overlay was visible. vertical swipe advanced Home feed content, so homepage scrolling is not blocked.

### T2 - Reels blocking
Result: PARTIAL / BLOCKED

Evidence:
- Reels-tab tap opened NoScroll library: `artifacts/e2e-instagram-oracle-20260612-104446/T2-reels-tab-after-tap.png`
- Direct Reels attempt spinner: `artifacts/e2e-instagram-oracle-20260612-104446/T2-reels-before.png`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T2-reels.log`

Reels tab entry point was intercepted once by bottom-nav book overlay and opened NoScroll, blocks entry path. later direct attempt to enter Reels landed on Instagram loading spinner, so vertical in-Reels scroll blocking could not be fairly validated.

### T3 - Search/Explore grid scroll blocking
Result: FAIL

Evidence:
- Explore before: `artifacts/e2e-instagram-oracle-20260612-104446/T3-search-actual-before.png`
- Explore after swipe: `artifacts/e2e-instagram-oracle-20260612-104446/T3-search-actual-after.png`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T3-search-actual.log`

service detected `SEARCH_EXPLORE` and computed block rectangle, but log also showed `navSelection=UNBLOCKED`. No blocking overlay was visible, and Explore grid moved after vertical swipe.

### T4 - Search typing allowance
Result: PASS

Evidence:
- Search typing: `artifacts/e2e-instagram-oracle-20260612-104446/T4-search-typing.png`
- UI dump: `artifacts/e2e-instagram-oracle-20260612-104446/T4-search-typing.xml`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T4-search-typing.log`

search field focused and accepted `noscrolltest`. No NoScroll blocker covered search input while typing.

### T5 - Account page allowance
Result: PARTIAL PASS / BLOCKED

Evidence:
- Account page: `artifacts/e2e-instagram-oracle-20260612-104446/T5-account-private.png`
- Window dump: `artifacts/e2e-instagram-oracle-20260612-104446/T5-account-private-window.txt`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T5-account-private.log`

Instagram opened account page without NoScroll blocker, so account-page access is allowed. account was private/empty, so account posts and account reels could not be validated in this run.

### T6 - Friend-sent DM post/reel allowance
Result: BLOCKED

Evidence:
- Direct/Messages screen: `artifacts/e2e-instagram-oracle-20260612-104446/T6-direct-retry.png`
- Window dump: `artifacts/e2e-instagram-oracle-20260612-104446/T6-direct-retry-window.txt`
- Logcat: `artifacts/e2e-instagram-oracle-20260612-104446/T6-direct-retry.log`

Direct/Messages opened, but account showed no visible chats or shared post/reel fixture. rule "only allow friend-sent post/reel" could not be tested without DM thread containing shared media.

### Regression checks
Result: PASS

Evidence:
- Final accessibility dump: `artifacts/e2e-instagram-oracle-20260612-104446/final-accessibility-dumpsys.txt`
- Final focused logcat: `artifacts/e2e-instagram-oracle-20260612-104446/final-noscroll.log`

No fatal `AndroidRuntime` or `BadTokenException` evidence was found in focused logs. Overlay permission remained allowed and accessibility service remained enabled.

**Impact:** NoScroll is not deployment-ready for requested blocking behavior. app can detect Home/Search surfaces and can sometimes redirect Reels tab, but full-region blocking path is not being applied to Home or Search/Explore. Next impl work should make computed `blockRect` drive `OverlayService.ACTION_BLOCK_REGION` for blocked surfaces, preserve search typing/account/DM exceptions, and add explicit state for DM-opened shared media.

## [2026-06-12 16:56] Session: Implement and retest Instagram block-region behavior

**Decision:** PARTIAL PASS. Home, Reels, and Search/Explore blocking now work on `emulator-5554`, and Search typing remains usable. Direct inbox is reachable and unblocked. friend-sent shared-post fixture exists, but opening it produced persistent black/loading Instagram surface, so "only this shared item is viewable" rule is still not fully proven.

**Environment:**
- Host: Windows, workspace `C:\Users\aarya\Desktop\no-scroll`
- Evidence directory: `artifacts/e2e-instagram-oracle-fix-20260612-161544`
- Emulator: `emulator-5554`, Android `17`
- Instagram: `com.instagram.android`, version `433.0.0.47.68`
- NoScroll: `com.noscroll`, version `1.0`
- Permissions restored before retest: overlay=`SYSTEM_ALERT_WINDOW: allow`, accessibility=`com.noscroll/com.noscroll.NoScrollAccessibilityService`
- Build gates: `:app:testDebugUnitTest` PASS and `:app:assembleDebug` PASS using `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`

**Implementation changes:**
- `NoScrollAccessibilityService.kt`: dispatches `OverlayService.ACTION_BLOCK_REGION` whenever `scanInstagramTree()` returns blocked surface and concrete `blockRect`.
- `NoScrollAccessibilityService.kt`: adds fallback Home detection for current Instagram builds where selected tab state may live on child icon or not surface reliably, using Home chrome plus bottom-nav presence.
- `NoScrollAccessibilityService.kt`: constrains selected-child bottom-nav fallback to small tab-related nodes to avoid treating arbitrary selected controls as nav tabs.
- `NoScrollAccessibilityService.kt`: reserves detected Instagram tab-bar height below block region so Home/Search bottom navigation remains reachable.
- `OverlayService.kt`: uses screen-coordinate overlay placement for block windows with `FLAG_LAYOUT_IN_SCREEN` and `setFitInsetsTypes(0)` on Android R+ so accessibility-screen rectangles line up with actual touch windows.
- Final review correction: selected-child nav fallback now uses selected node/parent tab identity rather than horizontal-position guesses, and block bottom returned to smaller nav gap after overlay coordinate fix made touch-window placement match accessibility coordinates.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- Home block visible with nav still reachable: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-home-wait.png`
- Home logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-home-wait.log`
- Earlier post-fix swipe proof: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix5-home-postswipe.png`

service now logs `scan surface=HOME ... blockRect=Rect(0, 583 - 1080, 2046)`. full NoScroll block overlay appears over feed content, and vertical swipe in blocked region did not advance feed.

### T2 - Reels scroll blocking
Result: PASS

Evidence:
- Reels blocked after navigation/swipe: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-reels-after-swipe.png`
- Reels logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-reels-after-swipe.log`

Reels surface is covered by full NoScroll block overlay. vertical swipe remained on blocked NoScroll surface.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- Search/Explore blocked while search input remains visible: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-before.png`
- Search logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-before.log`
- Final smoke after review fixes: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix7-search.png`, `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix7-smoke.log`

Search/Explore page opens from bottom nav and Explore grid is covered by NoScroll block overlay. search field remains outside overlay and reachable.

### T4 - Search typing allowance
Result: PASS

Evidence:
- Search typing: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.png`
- UI dump: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.xml`
- Logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.log`

search field focused and accepted `noscrolltest`. blocker cleared while search input was focused, leaving results/input usable.

### T5 - Account page allowance
Result: NOT RETESTED IN THIS impl PASS

Previous evidence from `2026-06-12 11:05` showed account page opened without NoScroll blocker, but account was private/empty. This run focused on fixing failing Home/Reels/Search paths and did not add stronger account-post/account-reel evidence.

### T6 - Friend-sent DM post/reel allowance
Result: PARTIAL / BLOCKED

Evidence:
- Direct inbox reachable and unblocked: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-dm-from-home.png`
- DM thread black/loading surface: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-dm-thread-wait.png`
- DM logs: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-dm-thread-wait.log`

Direct opened from Home and remained unblocked. visible DM fixture existed: `Aaryan Srivastava - Sent a post by everythingquant`. Opening thread produced persistent black/loading Instagram surface in screenshots. Logcat showed NoScroll scanning `surface=null`/no block region for opened DM/thread surface, but shared post itself was not visually verifiable, and swiping from shared item into unrelated content was not proven.

### Regression checks
Result: PASS for local gates and focused crash scan

Evidence:
- Build/test logs: `gradle-test-assemble-6.log`
- Focused crash scan found no `FATAL EXCEPTION` or `BadTokenException` in final captured log window.

**Impact:** core block-region impl is now aligned with objectives 1, 2, and blocking/typing parts of 3. Objective 4 still needs reliable shared-post/shared-reel fixture renders on emulator, or more stateful impl to distinguish DM-opened shared media surface from general feed/reels navigation after item opens.

## [2026-06-12 17:59] Session: DM shared-media state retest and partial implementation

**Environment:**
- Evidence directory: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900`
- Emulator: `emulator-5554`
- Build gates: `:app:testDebugUnitTest` PASS and `:app:assembleDebug` PASS using `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`
- Instagram fixture: Direct thread `Aaryan Srivastava`, shared post/reel from `everythingquant`

**Implementation changes:**
- `NoScrollAccessibilityService.kt`: added DM shared-media state (`lastDirectSharedMediaThreadMs`, `dmSharedMediaSessionActive`, `dmSharedMediaAllowedUntilMs`) and inferred DM-open handling after Direct thread containing shared media opens Reels/Post surface.
- `NoScrollAccessibilityService.kt`: constrained shared-media click arming to clicks inside Direct-thread ancestry, added descendant media detection, and added startup polling in `onServiceConnected()`.
- `NoScrollAccessibilityService.kt`: added direct viewer detection for Instagram reply-bar/header IDs on opened DM-shared Reels surfaces.
- `OverlayService.kt`: added transparent touch-block mode and changed transparent blocker clicks to no-op instead of launching `PdfViewerActivity`.
- Code-review subagent findings addressed: generic media arming outside DMs and transparent blocker launching PDF viewer. Reels full-surface block is intentional for current product oracle.

### DM shared-media test
Result: FAIL / PARTIAL

Evidence:
- Direct thread detected with shared media: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/dm-thread5.log`
- DM shared open inferred and transparent touch block sent: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/dm-opened5.log`
- Swipe escaped from sent `everythingquant` item to unrelated Suggested reel before later blocker caught it: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/dm-viewer-detect.png`
- Suggested reel blocked after accessibility restart: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/suggested-after-toggle.log`
- UI dump failure after overlay capture: `artifacts/e2e-instagram-dm-shared-fix-20260612-172900/dm-after-swipe3.png` plus missing XML noted from `uiautomator` error `null root node returned by UiTestAutomationBridge`

Observed behavior:
- app can now identify Direct thread fixture and can infer when DM-shared item opens.
- service logs `dm shared media open inferred` and `dm shared media touch block active` for opened `everythingquant` item.
- current impl still does not reliably keep sent item visible. vertical swipe escaped to unrelated Suggested reel in one run. Once service was restarted on Suggested reel, NoScroll correctly classified it as `surface=REELS` and blocked it, but is too late for objective 4.

**Current status:** Objectives 1, 2, Search/Explore blocking, and Search typing remain passing from previous evidence. Objective 4 is still not complete; remaining work is to prevent first swipe from leaving DM-opened media, not merely block after escape.

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
- `NoScrollAccessibilityService.kt`: removed broad "recent Direct shared-media thread implies any blocked surface is DM media" inference because it leaked DM transparent touch-block mode onto Home/Reels/Search after leaving Direct.
- `NoScrollAccessibilityService.kt`: returning to Direct thread now clears `dmSharedMediaSessionActive` and `dmSharedMediaAllowedUntilMs`.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- Screenshot before/after swipe: `artifacts/e2e-instagram-final-20260612-181300/final-home-before.png`, `artifacts/e2e-instagram-final-20260612-181300/final-home-after.png`
- Log: `artifacts/e2e-instagram-final-20260612-181300/final-home.log`

choice reason:
- Logs repeatedly classify `surface=HOME` with `directThread=false directShared=false directViewer=false`.
- No stale `dm shared media touch block active` appeared after final inference patch.

### T2 - Reels scroll blocking
Result: PASS

Evidence:
- Screenshot before/after swipe: `artifacts/e2e-instagram-final-20260612-181300/final-reels-before.png`, `artifacts/e2e-instagram-final-20260612-181300/final-reels-after.png`
- Log: `artifacts/e2e-instagram-final-20260612-181300/final-reels.log`

choice reason:
- Logs classify `surface=REELS` with full content block rect `Rect(0, 142 - 1080, 2172)`.
- No stale DM touch-block state appeared.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- Search/Explore deep-link screenshot before/after swipe: `artifacts/e2e-instagram-final-20260612-181300/final-search-deeplink-before.png`, `artifacts/e2e-instagram-final-20260612-181300/final-search-deeplink-after.png`
- UI dump: `artifacts/e2e-instagram-final-20260612-181300/final-search-deeplink-before.xml`
- Log: `artifacts/e2e-instagram-final-20260612-181300/final-search-deeplink.log`

choice reason:
- UI dump shows Instagram Explore grid and selected `search_tab`.
- Logs classify `surface=SEARCH_EXPLORE searchSelected=true searchFocused=false` with block rect `Rect(0, 245 - 1080, 2172)`.
- visible search field remains above block rect.

### T4 - Search typing allowance
Result: PASS from previous focused evidence; FINAL RUN HAS RESIDUAL RISK

Evidence:
- Previous focused typing pass: `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.png`, `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.xml`, `artifacts/e2e-instagram-oracle-fix-20260612-161544/fix6-search-typing.log`
- Final run attempted focused capture: `artifacts/e2e-instagram-final-20260612-181300/final-search-typing.png`
- Final window dump/log check after timeout: current focus remained Instagram and IME was present, but Android also reported input-dispatch timeout for `com.noscroll` overlay window.

choice reason:
- search field behavior passed earlier and final code changes did not touch search-focus classification.
- final focused capture hit overlay/input-dispatch timeout, so this should be treated as deployment-quality risk even though no `FATAL EXCEPTION` or `BadTokenException` was observed.

### T5 - Account page allowance
Result: NOT RETESTED IN FINAL RUN

Evidence:
- Previous account navigation evidence remains from earlier runs; final work focused on DM containment failure and regression smoke for Home/Reels/Search.

### T6 - Friend-sent DM post/reel containment
Result: PASS

Evidence:
- Thread fixture opened through New Message search: `artifacts/e2e-instagram-final-20260612-181300/new-message-search.xml`, `artifacts/e2e-instagram-final-20260612-181300/new-message-selected.xml`
- Shared `everythingquant` item before/after swipe: `artifacts/e2e-instagram-final-20260612-181300/final-dm-open.png`, `artifacts/e2e-instagram-final-20260612-181300/final-dm-after-swipe.png`
- UI dumps: `artifacts/e2e-instagram-final-20260612-181300/final-dm-open.xml`, `artifacts/e2e-instagram-final-20260612-181300/final-dm-after-swipe.xml`
- Window dumps: `artifacts/e2e-instagram-final-20260612-181300/final-dm-open-window.txt`, `artifacts/e2e-instagram-final-20260612-181300/final-dm-after-swipe-window.txt`
- Log: `artifacts/e2e-instagram-final-20260612-181300/final-dm-after-swipe.log`

choice reason:
- `final-dm-open.xml` and `final-dm-after-swipe.xml` have identical SHA-256 hashes.
- after-swipe UI dump still shows `Reel by everythingquant`, `sender_username_or_fullname=Aaryan Srivastava`, and `Reply to Aaryan Srivastava`.
- Logs show repeated `dm shared media viewer detected` and `dm shared media touch block active`.
- Logs do not show removed `dm shared media open inferred` path.

### Regression checks
Result: PASS with one residual deployment risk

Evidence:
- No captured final logs showed `FATAL EXCEPTION` or `BadTokenException`.
- `uiautomator dump` timed out during focused Search typing attempt while Android reported input dispatch timeout against `com.noscroll`.

Residual risk:
- visible block path can foreground NoScroll/PDF reader and may leave overlay window Android reports as not responding during input-dispatch-heavy captures. This did not crash app, but it is deployment readiness issue to address next.

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
- `NoScrollAccessibilityService.kt`: keeps Direct inbox/thread unblocked, arms short DM-shared-media session from Direct shared-media clicks, detects opened DM shared media viewer, and applies transparent touch-block only to viewer.
- `NoScrollAccessibilityService.kt`: Search/Explore blocking now starts below search input fallback line and profile pages opened from Search are excluded from blocking.
- `NoScrollAccessibilityService.kt`: story viewer detection again treats `swipe_navigation_container` as sufficient unless view is DM shared-media viewer path.
- `OverlayService.kt`: added visible full-region blocker and transparent touch-block mode. Visible blocker consumes touches and launches `PdfViewerActivity` on tap; transparent DM blocker consumes touches with no-op click.
- Attempted focusable overlay blocker was rejected because it made bottom navigation unreliable; final installed build uses safer non-focusable overlay.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- Current-build pass: `artifacts/e2e-instagram-final-20260612-190500/27-patched-reels-before.png`, `artifacts/e2e-instagram-final-20260612-190500/28-patched-reels-after-swipe.png`
- Earlier current-build focused pass: `artifacts/e2e-instagram-current-20260612-verify/11-final-home-before.png`, `artifacts/e2e-instagram-current-20260612-verify/12-final-home-after-swipe.png`

choice reason:
- After Home swipe, visible `NoScroll` blocker is present over feed content.
- Logs classify `surface=HOME` with block rects such as `Rect(0, 583 - 1080, 2172)`.

### T2 - Reels scroll blocking
Result: FAIL

Evidence:
- Reels before swipe: `artifacts/e2e-instagram-final-20260612-190500/30-patched-reels-real-before.png`
- Reels after swipe: `artifacts/e2e-instagram-final-20260612-190500/31-patched-reels-real-after-swipe.png`
- Supporting logs: `artifacts/e2e-instagram-final-20260612-190500/30-patched-reels-real-before.log`, `artifacts/e2e-instagram-final-20260612-190500/31-patched-reels-real-after-swipe.log`

choice reason:
- service repeatedly classifies `surface=REELS` and emits `blockRect=Rect(0, 142 - 1080, 2172)`.
- visual after-swipe screenshot shows Instagram advanced to visible reel content instead of remaining blocked.
- focusable overlay experiment did not fix problem and made navigation worse, so it was reverted before final installed build.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- Grid: `artifacts/e2e-instagram-current-20260612-verify/23-final-search-deeplink-grid.png`, `.xml`, `.log`

choice reason:
- Logs classify `surface=SEARCH_EXPLORE searchFocused=false profilePage=false` with `blockRect=Rect(0, 301 - 1080, 2172)`.
- 301px top offset leaves search field usable while blocking grid content.

### T4 - Search typing allowance
Result: PASS

Evidence:
- Typing: `artifacts/e2e-instagram-current-20260612-verify/24-final-search-deeplink-typing.png`, `.xml`, `.log`

choice reason:
- Logs show `searchFocused=true` with `blockRect=null`.
- UI dump/screenshot show typed `everythingquant`/search results path remains usable.

### T5 - Account page allowance from Search
Result: PASS

Evidence:
- Account page: `artifacts/e2e-instagram-current-20260612-verify/25-final-search-deeplink-account.png`, `.xml`, `.log`

choice reason:
- Logs show `profilePage=true` and `blockRect=null`.
- Account profile browsing from Search is not blocked.

### T6 - Friend-sent DM post/reel containment
Result: PASS for available shared post/reel fixture

Evidence:
- DM inbox fixture: `artifacts/e2e-instagram-current-20260612-verify/26-final-dm-inbox.xml`
- DM thread: `artifacts/e2e-instagram-current-20260612-verify/27-final-dm-thread.xml`
- Opened shared item: `artifacts/e2e-instagram-current-20260612-verify/28-final-dm-open-shared-post.xml`, `.log`
- After swipe: `artifacts/e2e-instagram-current-20260612-verify/29-final-dm-after-swipe.xml`, `.log`

choice reason:
- Inbox fixture exposed `Aaryan Srivastava, Sent a post by everythingquant`.
- `28-final-dm-open-shared-post.xml` and `29-final-dm-after-swipe.xml` have identical SHA-256 hashes.
- Logs show `dm shared media viewer detected` and `dm shared media touch block active`.

### Regression checks
Result: PARTIAL PASS

Evidence:
- Final build/unit test output: terminal run at 19:22, `BUILD SUCCESSFUL`.
- Crash scans: `artifacts/e2e-instagram-final-20260612-190500/13-crash-scan.txt`, `20-crash-scan-after-reels.txt`, `23-crash-scan-final.txt`, `26-crash-scan-current-reels.txt`, `29-crash-scan-patched-reels.txt`, `32-crash-scan-patched-real-reels.txt`, `35-crash-scan-focusable-reels.txt`

choice reason:
- No fresh `FATAL EXCEPTION` or `BadTokenException` was captured in final crash scans.
- `uiautomator dump` sometimes reports `ERROR: could not get idle state` during animated Instagram/Reels states; screenshots, window dumps, and logs were used when XML was still pulled.
- There is historical/stale window ANR in earlier dumps from 18:23; it was not reproduced as fresh final crash, but remains deployment-risk signal.

**Final readiness decision:** Not deployment-ready yet. Home, Search/Explore, Search typing, account-page allowance, and DM shared-media containment are in acceptable shape on emulator. Reels scrolling is still bypassable in emulator despite correct service classification, so next fix should target input-blocking mechanism for Reels specifically rather than more surface-detection changes.

## 2026-06-12 20:18 EET - Post-Reels-Rollback / Non-Touchable Overlay Verification

### Metadata
- Emulator: `emulator-5554`
- Build: `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`; `:app:testDebugUnitTest :app:assembleDebug` passed after latest code changes.
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk` succeeded.
- App permissions: `SYSTEM_ALERT_WINDOW` allowed; `com.noscroll/.NoScrollAccessibilityService` re-enabled.
- Code changes under test:
  - Reels/Home/Search blocked-scroll rollback now targets active scroll container.
  - Reels rollback now prefers `com.instagram.android:id/clips_viewer_view_pager`.
  - Search-focused state suppresses prior stable-block grace overlay.
  - Block-region overlays are now visible but `FLAG_NOT_TOUCHABLE` to avoid NoScroll input-dispatch ANRs; scroll blocking relies on accessibility rollback plus overlay feedback.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-regression-20260612-194225/12-home-before-swipe.png`
- `artifacts/e2e-instagram-regression-20260612-194225/14-home-after-swipe.png`

choice reason:
- After vertical swipe, Home feed is covered by visible `NoScroll` blocker.
- Logs classify page as `surface=HOME`.

### T2 - Reels scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-reels-targeted-20260612-193951/06-reels-before-swipe.png`
- `artifacts/e2e-instagram-reels-targeted-20260612-193951/08-reels-after-swipe.png`
- `artifacts/e2e-instagram-reels-targeted-20260612-193951/08-reels-after-swipe-nos.log`

choice reason:
- Reels tab was selected before swipe.
- After swipe, visible `NoScroll` blocker covers Reels content.
- Log confirms rollback targeted vertical pager: `target=com.instagram.android:id/clips_viewer_view_pager`.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-search-valid-20260612-195914/01-search-before-swipe.png`
- `artifacts/e2e-instagram-search-valid-20260612-195914/03-search-after-swipe.png`

choice reason:
- Search tab was selected with search input unfocused.
- After vertical swipe, visible `NoScroll` blocker remains over grid content.
- Logs classify `surface=SEARCH_EXPLORE searchFocused=false`.

### T4 - Search typing allowance
Result: PASS

Evidence:
- `artifacts/e2e-instagram-search-valid-20260612-195914/12-search-typing-results.png`
- `artifacts/e2e-instagram-search-valid-20260612-195914/12-search-typing-results-nos.log`

choice reason:
- Search input accepted `openai`.
- Results list remained visible and usable.
- Logs show `surface=null searchFocused=true blockRect=null`.

### T5 - Account page allowance from Search
Result: PASS

Evidence:
- `artifacts/e2e-instagram-account-valid-20260612-200054/01-account-page.png`
- `artifacts/e2e-instagram-account-valid-20260612-200054/03-account-after-swipe.png`
- `artifacts/e2e-instagram-account-valid-20260612-200054/05-account-post-or-grid.png`

choice reason:
- Search result opened `openai` account page.
- Profile grid scrolling and opening post were not blocked.
- Logs show `profilePage=true` with `blockRect=null` while on profile grid.

### T6 - Friend-sent DM post/reel containment
Result: BLOCKED in this latest emulator pass

Evidence:
- Invalid probe: `artifacts/e2e-instagram-dm-precondition-20260612-200244/03-dm-inbox.png`
- Invalid probe: `artifacts/e2e-instagram-dm-valid-probe-20260612-200434/00-main-tab-before-dm.png`

choice reason:
- latest probes did not successfully reach Direct inbox/thread; screenshots show Search/Profile states instead.
- Earlier evidence in `artifacts/e2e-instagram-current-20260612-verify/28-final-dm-open-shared-post.xml` and `29-final-dm-after-swipe.xml` showed valid shared-item fixture with identical XML after swipe, but fixture was not reproduced after latest non-touchable overlay change.
- This remains blocked by current emulator navigation/precondition, not marked passing for latest build.

### Regression checks
Result: PARTIAL PASS

Evidence:
- Build/unit tests passed after latest source changes.
- `artifacts/e2e-instagram-account-valid-20260612-200054/99-full-logcat.txt`
- `artifacts/e2e-instagram-search-valid-20260612-195914/99-full-logcat.txt`

choice reason:
- previous NoScroll input-dispatch ANR was addressed by making block-region overlays non-touchable.
- later all-in-one sweep produced Instagram-owned ANR (`Application Not Responding: com.instagram.android`), not NoScroll ANR. sweep is not used as product pass artifact.

### Readiness decision
Not fully deployment-ready yet. Home, Reels, Search grid, Search typing, and account-page behavior now pass on emulator. latest build still needs valid DM shared-post/reel re-run, and non-touchable overlay tradeoff should be reviewed because it changes blocking from direct touch interception to accessibility rollback.

### Review follow-up
Result: PATCHED / NEEDS E2E RE-RUN

Follow-up changes after code-review agent findings:
- `ACTION_TOUCH_BLOCK_REGION` now remains touchable and consumes touches for DM shared-media containment.
- Visible `ACTION_BLOCK_REGION` remains non-touchable/pass-through to avoid NoScroll input-dispatch ANRs; feed blocking is enforced by accessibility rollback and visible feedback.
- Blocked-scroll rollback now checks scroll direction before issuing `ACTION_SCROLL_BACKWARD`.
- Overlay service commands from accessibility service are wrapped with failure handling to avoid crashing on service-start failures.

Verification:
- `:app:compileDebugKotlin` passed.
- `:app:testDebugUnitTest :app:assembleDebug` passed.
- APK reinstall on `emulator-5554` succeeded.
- `graphify update .` completed after source changes.

Remaining gap:
- Because these review follow-up patches changed overlay touchability and rollback gating, latest installed APK still needs fresh targeted E2E pass for Reels, Search, and especially DM shared-post/reel fixture before it should be called deployment-ready.

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
- Code-review subagent reviewed earlier patch set and identified overlay touchability, rollback direction, and service-start failure handling risks.
- Final read-only reviewer subagent was started after this E2E pass to check readiness gaps; any returned findings should be appended separately if they arrive after this log entry.

### T1 - Home feed scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/02-home-before.png`
- `artifacts/e2e-instagram-latest-20260612-202813/03-home-after.png`
- `artifacts/e2e-instagram-latest-20260612-202813/03-home-after-nos.log`

Steps:
- Launched Instagram from clean process state.
- Confirmed Home feed was visible.
- Performed vertical swipe in content area.
- Captured screenshot, UI XML, window dump, and focused logcat.

Expected:
- Home feed should not advance meaningfully.
- NoScroll block behavior should be visible or logged.

Actual:
- visible feed remained on same Home section after swipe.
- `NoScroll` overlay stayed visible over feed.
- Logcat recorded `blocked scroll rollback surface=HOME success=true target=android:id/list`.

choice reason:
- feed attempted movement, then accessibility rollback returned it to blocked Home state.

### T2 - Reels tab scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/04-reels-before.png`
- `artifacts/e2e-instagram-latest-20260612-202813/05-reels-after.png`
- `artifacts/e2e-instagram-latest-20260612-202813/05-reels-after-nos.log`

Steps:
- Tapped Reels bottom tab.
- Waited for Reels viewer.
- Performed vertical swipe in Reels content area.
- Captured screenshot, UI XML, window dump, and focused logcat.

Expected:
- User should not advance into unrelated Reels content.
- Overlay should cover content, not bottom tab.

Actual:
- Reels surface was detected.
- Overlay covered main content region.
- Logcat recorded `blocked scroll rollback surface=REELS success=true target=com.instagram.android:id/clips_viewer_view_pager`.

choice reason:
- Reels scrolling is now actively rolled back on correct Instagram pager.

### T3 - Search/Explore grid scroll blocking
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/11-search-grid-before.png`
- `artifacts/e2e-instagram-latest-20260612-202813/12-search-grid-after-swipe.png`
- `artifacts/e2e-instagram-latest-20260612-202813/13-search-typed-valid-nos.log`

Steps:
- Tapped Search/Explore from bottom nav.
- Confirmed search input was not focused.
- Performed vertical swipe over Explore grid.
- Captured before/after screenshots, UI XML, window dumps, and focused logcat.

Expected:
- Explore grid should not scroll while search input is not focused.

Actual:
- Grid screenshots before and after swipe show same visible grid position.
- Logcat recorded `surface=SEARCH_EXPLORE searchFocused=false`.
- Logcat recorded `blocked scroll rollback surface=SEARCH_EXPLORE success=true target=com.instagram.android:id/recycler_view`.

choice reason:
- Search/Explore grid scrolling is blocked while leaving search input area uncovered.

### T4 - Search typing allowance
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/13-search-typed-valid.png`
- `artifacts/e2e-instagram-latest-20260612-202813/13-search-typed-valid-nos.log`

Steps:
- Focused Search input.
- Typed `openai`.
- Captured screenshot, UI XML, window dump, and focused logcat.

Expected:
- Search input and keyboard should remain usable.
- No blocker should cover input.

Actual:
- input accepted `openai`.
- Search results were shown.
- Logcat recorded `surface=null searchFocused=true blockRect=null`.

choice reason:
- Search remains usable while typing; blocking is removed for focused search state.

### T5 - Account page allowance from Search
Result: PASS

Evidence:
- `artifacts/e2e-instagram-latest-20260612-202813/14-account-profile.png`
- `artifacts/e2e-instagram-latest-20260612-202813/15-account-profile-after-swipe.png`
- `artifacts/e2e-instagram-latest-20260612-202813/16-account-post-open.png`
- `artifacts/e2e-instagram-latest-20260612-202813/16-account-post-open-nos.log`

Steps:
- Opened verified `openai` account from search results.
- Swiped profile/grid area.
- Opened post from account grid.
- Captured screenshots, UI XML, window dumps, and focused logcat.

Expected:
- Account profile browsing should remain usable.
- Posts/reels from account should be viewable.

Actual:
- `openai` profile opened.
- Profile/grid scrolling worked.
- post opened.
- Logcat recorded `profilePage=true` with `blockRect=null` on profile, then `surface=null` after opening post.

choice reason:
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
- Opened available thread: `Aaryan Srivastava, Sent a post by everythingquant, 4h`.
- Opened shared post/reel item in thread.
- Swiped vertically in shared media viewer.
- Captured screenshots, UI XML, window dumps, and focused logcat.

Expected:
- shared item should be viewable.
- Swiping should not move into unrelated Instagram content.

Actual:
- Thread opened and showed shared `everythingquant` item.
- Shared item opened in media viewer.
- After vertical swipe, screenshot remained on same shared item.
- Logcat recorded `directThread=true directShared=true` in thread.
- Logcat recorded `directViewer=true` and `dm shared media viewer detected surface=REELS rect=Rect(0, 0 - 1080, 2214)` in shared viewer.

choice reason:
- opened shared media item stayed contained after swipe and NoScroll recognized DM shared viewer state.

### Regression checks
Result: PASS

Evidence:
- Gradle: `:app:testDebugUnitTest :app:assembleDebug --no-daemon`
- Accessibility state: bound and enabled NoScroll service, no crashed services.
- Overlay app-op: `SYSTEM_ALERT_WINDOW: allow`
- Log sweep: no fatal/ANR/BadToken entries in latest focused `*-nos.log` files.

choice reason:
- current focused run did not reproduce prior NoScroll input-dispatch ANR or earlier Instagram-owned ANR.
- service remained bound after full latest pass.

### Current readiness decision
latest installed debug APK now passes requested emulator E2E campaign for Home, Reels, Search grid, Search typing, account-page allowance, and real DM shared-item fixture. remaining product tradeoff is intentional: visible feed/reels/search blockers are pass-through overlays with accessibility rollback, while DM shared-media touch blocker remains touch-consuming for containment. This is deployment-ready from current emulator evidence, with normal caveat Instagram UI/resource IDs can change across app updates.

## 2026-06-12 21:00 EET - Reviewer Follow-up: Overlay Touchability Lifecycle

### Reviewer finding addressed
Result: PATCHED

Finding:
- Read-only reviewer flagged `ACTION_UNFREEZE` could remove `FLAG_NOT_TOUCHABLE` from existing visible block overlay, and existing-overlay path in `showBlockRegion()` could return without restoring visible-mode flags.

Fix:
- `OverlayService.setTouchable(enabled)` is now mode-aware. `ACTION_UNFREEZE` can only make overlays touchable when `overlayMode != OverlayMode.BLOCK`.
- `OverlayService.showBlockRegion()` now computes block-region flags once and reapplies them on existing overlays, even when only flags differ.
- Visible `ACTION_BLOCK_REGION` overlays remain `FLAG_NOT_TOUCHABLE`.
- Transparent `ACTION_TOUCH_BLOCK_REGION` overlays remain touch-consuming for DM shared-media containment.

Verification:
- `:app:testDebugUnitTest :app:assembleDebug --no-daemon` passed after patch.
- APK was reinstalled on `emulator-5554`.
- `graphify update .` completed after code change.
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
- Instagram did not return to active feed; screenshots show launcher.
- Logcat recorded `ANR in com.instagram.android`.
- ANR reason: `No response to onStartJob for #PushabilityCheckerWorker#@androidx.work.systemjobscheduler@com.instagram.android/androidx.work.impl.background.systemjob.SystemJobService`.
- This is not NoScroll ANR, but it prevents using this lifecycle attempt as clean blocker/pass-through proof.

choice reason:
- reviewer's HIGH code-path risk is fixed in source and covered by build/static checks.
- full app-switch lifecycle E2E pass is still blocked by Instagram/emulator instability, not by NoScroll crash.

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

choice reason:
- This is expected because service is not exported. It preserves production posture, but means exact flag path cannot be externally stimulated through `adb shell`.

### Final deployment note
core Instagram product oracle passed on latest focused E2E run before this final overlay lifecycle patch, and patch is narrow enough it only preserves intended non-touchable visible blocker state. remaining risk is not known NoScroll failure; it is app-switch lifecycle regression could not be completed cleanly because Instagram produced its own ANR on emulator.

## 2026-06-12 21:41 EET - Final DM Containment Rerun After Accessibility Re-enable

### Patch added after failed DM swipe attempt
Result: PATCHED

Finding:
- DM shared reel could be opened successfully, but when accessibility was not enabled after reinstall retest was invalid.
- In earlier enabled run, swipe from shared reel's content area could leave shared item and land on sender/account profile.

Fix:
- `ScanResult` now carries `profilePageActive`.
- While DM shared-media session is armed, if Instagram leaves shared viewer into profile surface, `NoScrollAccessibilityService` performs `GLOBAL_ACTION_BACK`, hides overlay, and logs `dm shared media containment back from profile`.
- Existing transparent `ACTION_TOUCH_BLOCK_REGION` behavior remains active while `directViewer=true`.

Verification:
- Build passed after code patch: `:app:testDebugUnitTest :app:assembleDebug --no-daemon`.
- APK reinstalled on `emulator-5554`.
- Accessibility was explicitly re-enabled after reinstall:
  - `enabled_accessibility_services=com.noscroll/com.noscroll.NoScrollAccessibilityService`
  - `accessibility_enabled=1`
  - `dumpsys accessibility`: NoScroll enabled, `Crashed services:{}`.
- `graphify update .` completed after final code change.
- `git diff --check` passed with only CRLF replacement warnings.
- Focused valid-run log sweep found no `AndroidRuntime`, `FATAL EXCEPTION`, `BadTokenException`, `ANR in com.noscroll`, `ANR in com.instagram.android`, or `Application Not Responding` entries in valid run logs.

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
- Opened `Aaryan Srivastava` DM thread with shared `everythingquant` content.
- Opened shared reel.
- Swiped vertically from same content/profile area previously navigated away.

Expected:
- shared reel remains viewable.
- Swipe does not advance into unrelated content or leave shared-item viewer.

Actual:
- Before swipe XML showed `Reel by everythingquant. Double tap to play or pause.`
- After swipe XML still showed same `Reel by everythingquant. Double tap to play or pause.`
- After swipe XML still showed same sender header/reply context for `Aaryan Srivastava`.
- No profile page XML appeared after valid swipe.
- Log evidence:
  - `scan surface=REELS ... directViewer=true ... blockRect=Rect(0, 0 - 1080, 2214)`
  - `dm shared media viewer detected surface=REELS rect=Rect(0, 0 - 1080, 2214)`
  - `dm shared media touch block active rect=Rect(0, 0 - 1080, 2214)`

choice reason:
- With accessibility correctly re-enabled, patched APK keeps friend-sent reel constrained to opened item. user can view shared item, and tested swipe did not escape to account profile or unrelated content.

### Final current readiness decision
latest validated state passes requested NoScroll oracle on `emulator-5554`:
- Home scrolling blocked.
- Reels scrolling blocked.
- Search/Explore scrolling blocked while search typing and account/post browsing remain usable.
- DM shared reel remains constrained to opened shared item.

Remaining caveat:
- emulator/Instagram stack can still produce Instagram-owned startup or modal instability. latest valid focused run did not show NoScroll crash, BadToken, or fresh Instagram ANR.

## [2026-06-15 22:08] NoScroll logo direction research
**Decision:** Use bold black-and-white paused-book mark as primary logo direction, preserving original strongest generated concept while refining exact vector geometry instead of continuing broad image generation.
**Why:** Web research and mini-subagent passes converged on simple, high-contrast, distinctive, scalable marks with one clear idea, plus Android adaptive-icon constraints and monochrome/negative-space legibility.
**Impact:** Future logo work should focus on deterministic SVG/Android vector variants: thick pause bars, compact book silhouette, minimal interior detail, 108dp adaptive icon canvas, 66dp safe zone, and real small-size/mask testing.

## [2026-06-15 22:55] Public social automation knowledge brief source boundary
**Decision:** Build social automation brief from `PRODUCT.md`, `README.md`, app manifest/resources, impl files, and focused tests while excluding secrets, raw Instagram dumps, `local.properties`, and private artifact details from public copy.
**Why:** user requested public-safe social knowledge; top-level product docs and source code contain enough positioning, feature, privacy, and compliance facts, while dumps/artifacts may include private account or UI data.
**Impact:** Future social content should use local-first habit-replacement messaging, avoid unverified pricing/health claims, disclose permissions plainly, and keep API config/customer/test details out of public content.

## [2026-06-16 08:50] Deterministic NoScroll logo variants built
**Decision:** Generated three black-and-white paused-book logo candidates under `artifacts/logo-variants/` and kept app source assets unchanged for review.
**Why:** User selected the original first-pass top-left direction and wanted concrete vector-style previews before replacing `ic_launcher_foreground.xml`.
**Impact:** Candidate A is closest to the selected direction; B has the strongest pause read but weakest book cue; C preserves book cues but gets busy at very small sizes.

## [2026-06-16 16:09] Launcher logo now uses exact supplied PNG
**Decision:** Copied the user's preferred logo image to `app/src/main/res/drawable-nodpi/ic_launcher_exact.png` and pointed both adaptive launcher icons at it with a black launcher background.
**Why:** Attempts to recreate the mark through generated/vector approximations changed the proportions and lost the book edge/depth; user explicitly wanted the first image exactly.
**Impact:** Future launcher-logo edits should start from `ic_launcher_exact.png` unless user requests a vector redraw; `ic_launcher_foreground.xml` is no longer the adaptive icon foreground.

## [2026-06-16 16:20] New imagegen 3D paused-book draft
**Decision:** Generated and saved a new black-and-white 3D paused-book logo concept at `artifacts/logo-variants/noscroll-imagegen-3d-pause-book.png`.
**Why:** User requested a fresh imagegen logo with the same pause-on-book concept, simple black/white styling, and stronger 3D book aspect.
**Impact:** This is a concept draft only; active launcher icon remains the exact supplied PNG unless user asks to replace it.

## [2026-06-16 16:23] Cleaner tilted paused-book imagegen draft
**Decision:** Generated and saved `artifacts/logo-variants/noscroll-imagegen-tilted-clean-book.png` with no right-side white layer and pause bars tilted with the book.
**Why:** User wanted a cleaner imagegen version: same pause-on-book concept, subtle 3D via bottom edge only, no right-side page protrusion.
**Impact:** Treat this as concept art for possible vector cleanup; active launcher icon still uses `ic_launcher_exact.png`.

## [2026-06-16 16:28] Side-tilt paused-book imagegen draft
**Decision:** Generated and saved `artifacts/logo-variants/noscroll-imagegen-side-tilt-cut-through.png` with horizontal top/bottom, slanted side edges, parallel pause bars, and bottom slit cutting through the right edge.
**Why:** User corrected the geometry: no top/bottom tilt, only side tilt, and bottom black page section must cut through the right side.
**Impact:** This draft best captures the requested geometry but remains imagegen-soft; vector cleanup is needed before replacing app icon.

## [2026-06-16 16:33] Rounded wide-slit paused-book imagegen draft
**Decision:** Generated and saved `artifacts/logo-variants/noscroll-imagegen-rounded-wide-slit.png` with rounder book/pause corners and a thicker bottom slit.
**Why:** User wanted the side-tilt concept softened because the prior version felt too sharp and the bottom slit too small.
**Impact:** This is the preferred imagegen direction so far; still needs pure vector cleanup before use as final icon.

## [2026-06-16 16:35] Sleeker paused-book imagegen draft
**Decision:** Generated and saved `artifacts/logo-variants/noscroll-imagegen-sleek-book.png` with less-rounded corners, slimmer book silhouette, and stronger book-like bottom edge.
**Why:** User said the prior rounded version felt too bubbly/off and not sleek/book-like enough.
**Impact:** This draft is closer to the desired book feel; still imagegen-soft and should be vector-cleaned for final app icon.

## [2026-06-16 18:31] Final selected logo promoted
**Decision:** Kept only `artifacts/logo-variants/noscroll-imagegen-sleek-book.png` in the logo variants folder and copied it over `app/src/main/res/drawable-nodpi/ic_launcher_exact.png` as the active launcher image.
**Why:** User selected the sleeker paused-book imagegen draft as the perfect logo and requested it be the only image in the folder.
**Impact:** Active app launcher now uses the selected imagegen logo; old logo draft images/SVGs were removed from `artifacts/logo-variants/`.

## [2026-06-16 18:34] Logo size exports added
**Decision:** Created `noscroll-imagegen-sleek-book-2048.png` and `noscroll-imagegen-sleek-book-128.png`; promoted the 2048px export to `app/src/main/res/drawable-nodpi/ic_launcher_exact.png`.
**Why:** User requested both high-resolution 2048x2048 and 128x128 versions of the final logo.
**Impact:** Active launcher image is now 2048x2048; `artifacts/logo-variants/` contains original selected logo plus 2048px and 128px exports.

## [2026-06-16 18:44] Logo variants and app-wide logo placement
**Decision:** Added normal, transparent, and inverted logo exports at 2048x2048 and 128x128; wired 128px logo assets into main/setup screens, library header, PDF toolbar, and overlay layouts while keeping 2048px as launcher foreground.
**Why:** User requested the logo anywhere app branding appears, plus transparent and inverted variants; small UI surfaces should not decode 2048px assets.
**Impact:** Brand logo resources live in `app/src/main/res/drawable-nodpi/`; generic `ic_book` remains only for notification small icon and per-book placeholders where app branding would be misleading.

## [2026-06-16 20:56] First social post concept
**Decision:** Created a square launch post asset at `artifacts/social/noscroll-first-post.png` plus copy sheet at `artifacts/social/first-post-copy.md`.
**Why:** First public post should explain NoScroll's core product promise in one glance before later quote-card posts become a recurring content format.
**Impact:** Future social assets can reuse the "Turn Reels into reading" positioning and build outward into quote/highlight graphics after the audience understands the app.

## [2026-06-16 21:02] Corrected first social post accuracy
**Decision:** Generated `artifacts/social/noscroll-first-post-v2.png` with the corrected bottom navigation order and updated the copy sheet to avoid public-release/deployment implications.
**Why:** The previous generated social UI had an inaccurate nav bar and the product is not publicly released yet.
**Impact:** Use the v2 image and development-stage caption for first-post drafts.

## [2026-06-16 21:11] Removed blocker mechanic from social post
**Decision:** Reframed the first social post around voluntary reading habit replacement and created deterministic source art at `artifacts/social/first-post-v3.html`.
**Why:** User clarified NoScroll no longer has the Reels icon blocker, so launch creative must not show overlays, interception, or button replacement.
**Impact:** Use v3 image/copy for first post; avoid blocker/interception claims in future public-facing social material unless the product changes back.

## [2026-06-16 21:24] First post refocused on doomscroll blocker
**Decision:** Switched first social concept from single generic reader image to a carousel led by blocker positioning; copied existing project slides into `artifacts/social/carousel/` and added a new cover source at `artifacts/social/first-post-blocker-cover.html`.
**Why:** User clarified doomscroll blocking is the main app story and provided prior blocker/reader/quote-share visuals to anchor the launch post.
**Impact:** Use carousel order cover -> blocker -> reader -> quote share for first public post; blocker is now the lead value proposition.

## [2026-06-19 12:51] Planned quote card style packs
**Decision:** Created implementation plan `.claude/plan/quote-card-style-packs.md` to upgrade quote sharing from simple gradient themes to style packs with typography, layouts, and bundled scenic backgrounds.
**Why:** Existing quote sharing already has Canvas rendering, Compose preview, persisted quote history, and share flows; the lowest-risk path is enriching the quote module rather than introducing a new editor stack.
**Impact:** Future implementation should keep changes inside `com.noscroll.quote`, preserve old saved `themeName` values through migration mapping, use offline bundled scenic assets, and run `graphify update .` after code changes.

## [2026-06-19 12:50] New logo applied across app surfaces
**Decision:** Pointed all app logo surfaces at the new NoScroll logo assets, exposed the round adaptive launcher icon via `android:roundIcon`, and changed remaining user-facing setup/tutorial copy from old book/floating icon wording to NoScroll logo wording.
**Why:** The app had already gained new logo PNG variants and partial references, but launcher round icon and tutorial title still needed alignment.
**Impact:** `ic_book` remains only for PDF placeholders and notification small icon constraints; UI logo surfaces use `noscroll_logo_*` assets.

## [2026-06-19 13:25] Quote sharing style packs implemented
**Decision:** Implemented quote card style packs inside `com.noscroll.quote` with 8 presets, procedural scenic backgrounds, stable style ids, legacy `QuoteCardTheme` mapping, richer Compose style picker, saved-style preference, share text fallback, and Notebook display-name mapping.
**Why:** User wanted quote sharing preloaded with better designs, fonts, and scenic options; procedural Canvas scenery avoids bundled asset licensing and app-size risk while keeping offline deterministic rendering.
**Impact:** Existing `QuoteCardEntity.themeName` remains the persistence column but now stores style ids; old enum names still resolve through `QuoteCardStyles.byId()`. Quote card rendering now uses `QuoteCardBitmapBuilder.build(context, spec)`.

## [2026-06-19 13:28] Quote card review issues fixed
**Decision:** Added bounded `StaticLayout` max-lines/ellipsize/clipping for quote and attribution text, restored radio-button selection semantics in the style picker, and guarded preview rendering with generation tokens.
**Why:** Code review found long quote overflow, accessibility regression, and async render race risks after the first implementation pass.
**Impact:** Quote card text should stay inside its assigned regions, style pack selection remains accessible to TalkBack, and stale renders are discarded if users change styles quickly.

## [2026-06-19 13:33] Product docs repositioned around blocker, reader, and quote sharing
**Decision:** Rewrote active README/PRODUCT docs and setup strings to describe NoScroll as an Instagram blocker plus standalone PDF reader and quote-sharing app, not a Reels-button interception tool.
**Why:** Current implementation blocks whole distracting Instagram regions, reads imported PDFs, saves highlights/notes, supports OCR fallback, and creates shareable quote cards; docs were stale and under-described the product.
**Impact:** Future docs should avoid framing NoScroll as only replacing/intercepting the Reels button; use blocker + reader + quote sharing as the product model.

## [2026-06-19 13:47] Logo placements fitted across app surfaces
**Decision:** Added a reusable Compose `BrandMark`, enlarged/accommodated setup and library logo headers, tuned XML logo padding/scale in the reader nav and overlays, and updated setup copy away from Reels-button wording.
**Why:** The transparent logo asset has built-in empty border, and prior placements either wasted too much space in compact controls or treated the brand mark like a generic tinted icon.
**Impact:** Future logo use should choose `BrandMark` for Compose paper-theme headers and keep overlay/nav ImageViews explicitly sized with `centerInside` plus surface-specific padding.
## [2026-06-19 13:45] Black transparent logo variants added
**Decision:** Created `noscroll-logo-black-transparent-128.png` and `noscroll-logo-black-transparent-2048.png` from existing transparent logo alpha masks, with all visible pixels forced to solid black.
**Why:** User wanted the app logo in black with a real transparent background while preserving the existing shape and cutouts.
**Impact:** Use the `noscroll-logo-black-transparent-*` files when black primary logo artwork with alpha transparency is needed; `noscroll-logo-black-transparent-preview.png` is only a checkerboard preview.
